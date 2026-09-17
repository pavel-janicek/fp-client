package com.fpclient.android.recording

import android.content.Context
import com.fpclient.android.data.dto.ActivityDto
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.ActivityRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Save & share of recorded workouts (Iteration 8d): turns a finished session's append-only
 * track file into a GPX 1.1 export in app-private storage, keeps the pending-upload
 * registry in sync, uploads through the very same multipart endpoint the UploadForm uses
 * (`POST api/web/activities/upload`), retries failures, and cleans up local artifacts once
 * the server has imported the activity.
 *
 * The GPX export is generated on demand (stop, upload, retry) from the JSONL file, which
 * stays the single source of truth — so a failed upload or a dismissed summary can always
 * regenerate it. Activity type is not a multipart field of the upload endpoint, so it is
 * applied with a follow-up metadata update whenever the server-derived type differs from
 * the one the user picked.
 *
 * Privacy zones need no client work: they are applied server-side on import exactly as for
 * a track uploaded through the regular upload form.
 */
class RecordingShareManager(
    context: Context,
    private val activities: ActivityRepository,
    private val activitiesVersion: MutableStateFlow<Int>,
) {

    /** Same append-only files the recording service writes ([TRACKS_DIR] under filesDir). */
    private val pointStore = TrackPointStore(File(context.filesDir, TRACKS_DIR))

    private val uploadDir = File(context.filesDir, UPLOADS_DIR)
    private val pendingStore = PendingUploadStore(File(context.filesDir, PENDING_FILE))

    private val _pendingUploads = MutableStateFlow(pendingStore.all())

    /** Recorded workouts not (yet) imported by the server, newest first. */
    val pendingUploads: StateFlow<List<PendingUpload>> = _pendingUploads.asStateFlow()

    /** Every accepted fix of a session, in file order. */
    fun readPoints(sessionId: Long): List<TrackPoint> =
        runCatching { pointStore.readAll(sessionId) }.getOrDefault(emptyList())

    /** A session's segments (one per pause/resume pair), in file order. */
    fun readSegments(sessionId: Long): List<List<TrackPoint>> =
        runCatching { pointStore.readSegments(sessionId) }.getOrDefault(emptyList())

    /** Stats recomputed from the persisted segments — the summary's numbers after a restart. */
    fun statsForSegments(segments: List<List<TrackPoint>>): TrackStats {
        val accumulator = TrackStatsAccumulator()
        var first = true
        segments.forEach { segment ->
            if (!first) accumulator.startNewSegment()
            first = false
            segment.forEach { accumulator.add(it) }
        }
        return accumulator.stats
    }

    /** Stats recomputed from the persisted track — the summary's numbers after a restart. */
    fun statsFor(points: List<TrackPoint>): TrackStats = statsForSegments(listOf(points))

    /**
     * Moving time of a persisted session, reconstructed from its segment files: the sum of
     * each segment's own first-to-last fix span. Used by the summary for a workout that was
     * recorded in an earlier process (the session snapshot with its accumulated time is
     * gone by then). Paused stretches are excluded by construction, because each pause
     * opened a new segment.
     */
    fun movingMsFor(sessionId: Long): Long {
        val segments = runCatching { pointStore.readSegments(sessionId) }.getOrDefault(emptyList())
        return segments.sumOf { segment ->
            val first = segment.firstOrNull()?.time ?: return@sumOf 0L
            val last = segment.lastOrNull()?.time ?: return@sumOf 0L
            (last - first).coerceAtLeast(0L)
        }
    }

    fun getPending(sessionId: Long): PendingUpload? = pendingStore.get(sessionId)

    fun gpxFileFor(sessionId: Long): File =
        File(uploadDir.apply { mkdirs() }, "$GPX_PREFIX$sessionId.$GPX_EXT")

    /**
     * Writes (or rewrites) the GPX 1.1 export of a session into app-private storage;
     * null when the session has no decodable fixes. [name] is the user-chosen title, kept
     * in the document as the track name as well as in the multipart field.
     */
    fun exportGpx(sessionId: Long, activityType: String?, name: String? = null): File? {
        val segments = runCatching { pointStore.readSegments(sessionId) }.getOrDefault(emptyList())
        if (segments.isEmpty()) return null
        val file = gpxFileFor(sessionId)
        return runCatching {
            file.writeText(GpxBuilder.build(segments, name = name, activityType = activityType))
            file
        }.getOrNull()
    }

    /**
     * Called by the service the moment a recording stops: exports the GPX and registers
     * the session as a pending upload, so it stays reachable even if the app dies before
     * the summary screen is used.
     */
    fun registerPending(session: TrackSessionSnapshot) {
        val sessionId = session.startedAtEpochMs
        if (exportGpx(sessionId, session.activityType) == null) return
        if (pendingStore.get(sessionId) == null) {
            pendingStore.upsert(
                PendingUpload(
                    sessionId = sessionId,
                    startedAtEpochMs = sessionId,
                    activityType = session.activityType,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        refresh()
    }

    /**
     * Uploads a recorded session with the metadata chosen on the summary screen. On
     * failure the entry stays (or is created) in the pending store carrying that metadata,
     * so a later retry replays exactly what the user entered; on success the local files
     * are deleted and [activitiesVersion] is bumped so list screens refresh.
     */
    suspend fun upload(
        sessionId: Long,
        activityType: String,
        title: String?,
        description: String?,
        visibility: String?,
    ): ApiResult<ActivityDto> {
        val file = exportGpx(sessionId, activityType, title)
            ?: return ApiResult.Error("The recorded track could not be read.")
        return when (val result = activities.uploadFile(file, title, description, visibility)) {
            is ApiResult.Success -> {
                val activity = result.data
                if (activityType.isNotBlank() &&
                    !activityType.equals(activity.activityType, ignoreCase = true)
                ) {
                    // The upload endpoint carries no activity type, so align the imported
                    // activity with the picker through a metadata update. A failure here
                    // must not lose the upload — the activity exists either way.
                    activities.update(
                        activity.id,
                        ActivityUpdateRequest(
                            title = activity.title ?: title.orEmpty(),
                            description = activity.description ?: description,
                            visibility = activity.visibility ?: visibility ?: ActivityVisibilities.PUBLIC,
                            activityType = activityType,
                        ),
                    )
                }
                pendingStore.remove(sessionId)
                refresh()
                runCatching { pointStore.delete(sessionId) }
                runCatching { file.delete() }
                activitiesVersion.value += 1
                ApiResult.Success(activity)
            }

            is ApiResult.Error -> {
                if (pendingStore.get(sessionId) == null) {
                    pendingStore.upsert(
                        PendingUpload(
                            sessionId = sessionId,
                            startedAtEpochMs = sessionId,
                            activityType = activityType,
                            createdAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
                pendingStore.markFailed(
                    sessionId = sessionId,
                    error = result.message,
                    title = title,
                    description = description,
                    visibility = visibility,
                    activityType = activityType,
                )
                refresh()
                result
            }
        }
    }

    /**
     * Retries pending uploads oldest-first, stopping at the first failure (a dead network
     * would otherwise turn every entry's attempt counter into noise). Returns the number of
     * workouts that made it to the server.
     */
    suspend fun retryPending(): Int {
        var uploaded = 0
        for (entry in pendingStore.all().sortedBy { it.createdAtEpochMs }) {
            val result = upload(
                sessionId = entry.sessionId,
                activityType = entry.activityType,
                title = entry.title,
                description = entry.description,
                visibility = entry.visibility,
            )
            if (result is ApiResult.Success) uploaded++ else break
        }
        return uploaded
    }

    /** Drops a recorded session without sharing it, removing its local files. */
    fun discard(sessionId: Long) {
        pendingStore.remove(sessionId)
        refresh()
        runCatching { pointStore.delete(sessionId) }
        runCatching { gpxFileFor(sessionId).delete() }
    }

    private fun refresh() {
        _pendingUploads.value = pendingStore.all().sortedByDescending { it.startedAtEpochMs }
    }

    companion object {
        /** Directory (under filesDir) holding the append-only track files. */
        const val TRACKS_DIR = "recordings"

        /** Directory (under filesDir) holding the generated GPX exports. */
        const val UPLOADS_DIR = "uploads"

        const val PENDING_FILE = "pending_uploads.json"
        const val GPX_PREFIX = "workout-"
        const val GPX_EXT = "gpx"
    }
}
