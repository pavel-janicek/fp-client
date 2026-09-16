package com.fpclient.android.recording

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlinx.serialization.json.Json

/**
 * Append-only persistence for track points (PLAN 8b: "Room entity … or append-only file"
 * — the file wins because the project deliberately stays dependency-light and the write
 * pattern is exactly append-and-flush; Iteration 8d reads the file back to build the GPX).
 *
 * One JSON object per line (`track-<startedAtEpochMs>.jsonl` in app-private storage).
 * Each fix is flushed immediately, so a process death loses at most the fix being written.
 * Files are keyed by the session's start epoch (stable across process death restores) and
 * survive a stop — Iteration 8d consumes them for the GPX export and the summary screen.
 *
 * The class is intentionally Android-free (plain java.io) so the round trip is unit
 * testable on the JVM with a temporary directory.
 */
class TrackPointStore(private val baseDir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private var writer: BufferedWriter? = null
    private var writerSessionStart: Long? = null

    fun fileFor(startedAtEpochMs: Long): File =
        File(baseDir.apply { mkdirs() }, "$FILE_PREFIX$startedAtEpochMs.$FILE_EXT")

    /** Appends one fix and flushes it to disk. */
    @Synchronized
    fun append(startedAtEpochMs: Long, point: TrackPoint) {
        val sessionStart = startedAtEpochMs
        val w = writer?.takeIf { writerSessionStart == sessionStart } ?: newWriter(sessionStart)
        w.write(encodeLine(point))
        w.newLine()
        w.flush()
    }

    /**
     * Appends a pause/resume boundary marker (Iteration 8d). Markers are plain comment
     * lines the point decoder ignores, so readAll()/stats replay are unaffected — while
     * readSegments() uses them to cut the track into one GPX trkseg per paused segment.
     */
    @Synchronized
    fun appendMarker(startedAtEpochMs: Long, marker: String) {
        val w = writer?.takeIf { writerSessionStart == startedAtEpochMs } ?: newWriter(startedAtEpochMs)
        w.write("$marker ${System.currentTimeMillis()}")
        w.newLine()
        w.flush()
    }

    /** Reads every decodable point of a session, in file order (oldest first). */
    @Synchronized
    fun readAll(startedAtEpochMs: Long): List<TrackPoint> {
        val file = fileFor(startedAtEpochMs)
        if (!file.exists()) return emptyList()
        return file.useLines { lines -> lines.mapNotNull { decodeLine(it) }.toList() }
    }

    /**
     * Reads a session as one point list per paused segment: every [MARKER_PAUSE] /
     * [MARKER_RESUME] boundary closes the current segment and the next accepted fixes
     * open the following one. Empty segments (e.g. an immediate pause) are dropped.
     * Sessions without markers come back as a single segment.
     */
    @Synchronized
    fun readSegments(startedAtEpochMs: Long): List<List<TrackPoint>> {
        val file = fileFor(startedAtEpochMs)
        if (!file.exists()) return emptyList()
        val segments = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf<TrackPoint>()
        file.useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith(MARKER_PAUSE) || line.startsWith(MARKER_RESUME) ->
                        if (current.isNotEmpty()) {
                            segments.add(current)
                            current = mutableListOf()
                        }
                    else -> decodeLine(line)?.let(current::add)
                }
            }
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments
    }


    /** The most recently persisted fix of a session, or null. */
    @Synchronized
    fun readLast(startedAtEpochMs: Long): TrackPoint? = readAll(startedAtEpochMs).lastOrNull()

    /** Closes the open writer, if any (called on pause/stop/destroy/replay boundaries). */
    @Synchronized
    fun closeWriter() {
        writer?.let { runCatching { it.flush() } }
        runCatching { writer?.close() }
        writer = null
        writerSessionStart = null
    }

    /** Deletes a session's track file (e.g. a discarded session); safe if absent. */
    @Synchronized
    fun delete(startedAtEpochMs: Long) {
        closeWriter()
        fileFor(startedAtEpochMs).delete()
    }

    private fun newWriter(sessionStart: Long): BufferedWriter =
        BufferedWriter(OutputStreamWriter(FileOutputStream(fileFor(sessionStart), true))).also {
            writer = it
            writerSessionStart = sessionStart
        }

    companion object {
        const val FILE_PREFIX = "track-"
        const val FILE_EXT = "jsonl"

        /** Pause/resume boundary markers (Iteration 8d); written as comment lines. */
        const val MARKER_PAUSE = "#PAUSE"
        const val MARKER_RESUME = "#RESUME"

        private val lineJson = Json { ignoreUnknownKeys = true }

        /** Pure line encoder (one JSON object, no trailing newline). */
        fun encodeLine(point: TrackPoint): String = lineJson.encodeToString(TrackPoint.serializer(), point)

        /**
         * Pure line decoder; returns null for blank or corrupt lines instead of crashing —
         * a torn final line after a process death must not take the whole session down.
         */
        fun decodeLine(line: CharSequence): TrackPoint? {
            val trimmed = line.toString().trim()
            if (trimmed.isEmpty()) return null
            return runCatching { lineJson.decodeFromString(TrackPoint.serializer(), trimmed) }.getOrNull()
        }
    }
}
