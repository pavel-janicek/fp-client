package com.fpclient.android.garmin

import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.isPermanentUploadRejection
import com.fpclient.android.data.repository.ActivityRepository
import com.fpclient.android.data.session.SessionStore
import com.fpclient.android.data.settings.DefaultVisibilityStore
import com.fpclient.android.notifications.WebPushCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * FP Client's side of the Garmin Connect relay (PLAN "Garmin Connect API", step 4): linking,
 * status, disconnecting, and moving queued activities from the relay to FitPub.
 *
 * The relay is the queue. A file stays there (encrypted) until FitPub has it, so a sync that
 * stops halfway simply continues next time; an upload whose acknowledgement was lost is caught
 * by the start-time duplicate check, as for Wear OS workouts.
 */
class GarminConnectSync(
    private val store: GarminConnectStore,
    private val client: GarminConnectClient,
    private val activities: ActivityRepository,
    private val sessionStore: SessionStore,
    private val defaultVisibility: DefaultVisibilityStore,
    private val cacheDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** How a sync ended, for the worker. */
    enum class Outcome { DONE, RETRY }

    /**
     * Returns the URL to open in a Custom Tab: Garmin's sign-in for a new mailbox, or for the
     * existing one (after a lost link, or a sign-in that was abandoned).
     */
    suspend fun connect(): ApiResult<String> {
        store.mailbox.value?.let { mailbox ->
            when (val result = client.relink(mailbox)) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> if (result.statusCode != 404) return result
            }
            store.clear() // the relay forgot the mailbox (expired): start over
        }
        val keys = withContext(Dispatchers.Default) { WebPushCrypto.generateRecipientKeys() }
        val publicKey = WebPushCrypto.toBase64Url(keys.publicKey)
        val authSecret = WebPushCrypto.toBase64Url(keys.authSecret)
        val relay = GarminConnectStore.DEFAULT_RELAY
        return when (val created = client.createMailbox(relay, publicKey, authSecret)) {
            is ApiResult.Error -> created
            is ApiResult.Success -> {
                store.saveMailbox(
                    GarminConnectMailbox(
                        relayBase = relay,
                        id = created.data.id,
                        manageToken = created.data.manageToken,
                        publicKey = publicKey,
                        privateKey = WebPushCrypto.toBase64Url(keys.privateKey),
                        authSecret = authSecret,
                    ),
                )
                ApiResult.Success(created.data.linkUrl)
            }
        }
    }

    /** Asks the relay about the link and caches the answer for the card. */
    suspend fun refreshStatus(): ApiResult<MailboxStatusDto> {
        val mailbox = store.mailbox.value ?: return ApiResult.Error("Not connected")
        val result = client.status(mailbox)
        when {
            result is ApiResult.Success -> store.updateStatus {
                it.copy(
                    linked = result.data.linked,
                    activityExport = result.data.activityExport,
                    linkLost = result.data.linkLost,
                    waitingFiles = result.data.files,
                )
            }
            // Expired on the relay (30 days without this phone checking in): start clean.
            result is ApiResult.Error && result.statusCode == 404 -> store.clear()
        }
        return result
    }

    /** Disconnects: the relay deregisters at Garmin and deletes everything, then the phone forgets the keys. */
    suspend fun disconnect(): ApiResult<Unit> {
        val mailbox = store.mailbox.value ?: return ApiResult.Success(Unit)
        val result = client.delete(mailbox)
        if (result is ApiResult.Success) store.clear()
        return result
    }

    /**
     * Uploads every activity waiting on the relay. [onImported] gets a short text per activity
     * that reached FitPub.
     */
    suspend fun sync(onImported: (String) -> Unit = {}): Outcome {
        val mailbox = store.mailbox.value ?: return Outcome.DONE
        val session = sessionStore.currentSession()
        if (!session.isLoggedIn) {
            store.updateStatus { it.copy(lastError = "Sign in to import Garmin activities") }
            return Outcome.DONE
        }
        when (val status = refreshStatus()) {
            is ApiResult.Error -> return if (status.statusCode == 404) Outcome.DONE else Outcome.RETRY
            is ApiResult.Success -> if (!status.data.linked) return Outcome.DONE
        }
        val files = when (val listed = client.files(mailbox)) {
            is ApiResult.Error -> return Outcome.RETRY
            is ApiResult.Success -> listed.data
        }
        val privateKey = WebPushCrypto.fromBase64Url(mailbox.privateKey)
        val publicKey = WebPushCrypto.fromBase64Url(mailbox.publicKey)
        val authSecret = WebPushCrypto.fromBase64Url(mailbox.authSecret)
        fun open(blob: ByteArray) = WebPushCrypto.decrypt(blob, privateKey, publicKey, authSecret)

        var error: String? = null
        var remaining = files.size
        for (relayFile in files) {
            val meta = runCatching {
                json.decodeFromString<GarminFileMeta>(String(open(GarminConnectClient.decodeStandardBase64(relayFile.meta))))
            }.getOrNull()
            if (meta == null) {
                // Not encrypted to this phone's keys: nothing here can ever read it.
                client.ack(mailbox, relayFile.id)
                remaining--
                continue
            }

            // A previous run may have uploaded it and lost the acknowledgement.
            val startMs = meta.startTimeInSeconds?.times(1000)
            val already = startMs?.let { (activities.findUploaded(session.username, it) as? ApiResult.Success)?.data }
            if (already != null) {
                client.ack(mailbox, relayFile.id)
                remaining--
                continue
            }

            val blob = when (val fetched = client.file(mailbox, relayFile.id)) {
                is ApiResult.Error -> return finish(remaining, error, Outcome.RETRY)
                is ApiResult.Success -> fetched.data
            }
            val data = runCatching { open(blob) }.getOrNull()
            if (data == null) {
                client.ack(mailbox, relayFile.id)
                remaining--
                continue
            }
            val file = File(cacheDir, "garmin-connect-${meta.summaryId.replace(Regex("[^A-Za-z0-9-]"), "_")}.${extension(meta.fileType)}")
            withContext(Dispatchers.IO) { file.writeBytes(data) }
            val visibility = defaultVisibility.visibility.value
            val title = meta.activityName?.trim()?.ifBlank { null }
            val result = try {
                activities.uploadFile(file = file, title = title, description = attribution(meta.deviceName), visibility = visibility)
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
            when (result) {
                is ApiResult.Success -> {
                    val activity = result.data
                    val sport = fitPubActivityType(meta.activityType)
                    if (sport != null && !activity.activityType.equals(sport, ignoreCase = true)) {
                        activities.update(
                            activity.id,
                            ActivityUpdateRequest(
                                title = activity.title ?: title ?: "Workout",
                                description = activity.description ?: attribution(meta.deviceName),
                                visibility = activity.visibility ?: visibility,
                                activityType = sport,
                            ),
                        )
                    }
                    client.ack(mailbox, relayFile.id)
                    remaining--
                    onImported(listOfNotNull(title ?: "Workout", attribution(meta.deviceName)).joinToString(" · "))
                }
                is ApiResult.Error -> {
                    val code = result.statusCode
                    if (code == 401) return finish(remaining, "Session expired — sign in again", Outcome.DONE)
                    if (!isPermanentUploadRejection(code)) return finish(remaining, error, Outcome.RETRY)
                    // FitPub will never take it: drop it from the relay instead of retrying for
                    // 30 days. The original stays in Garmin Connect.
                    client.ack(mailbox, relayFile.id)
                    remaining--
                    error = "\"${title ?: "Workout"}\" was rejected by FitPub: ${result.message ?: "HTTP $code"}"
                }
            }
        }
        return finish(remaining, error, Outcome.DONE)
    }

    private fun finish(remaining: Int, error: String?, outcome: Outcome): Outcome {
        store.updateStatus {
            it.copy(waitingFiles = remaining, lastSyncEpochMs = System.currentTimeMillis(), lastError = error)
        }
        return outcome
    }

    companion object {
        private fun extension(fileType: String): String = when (fileType.uppercase()) {
            "GPX" -> "gpx"
            "TCX" -> "tcx"
            else -> "fit"
        }

        /**
         * The attribution Garmin's API brand guidelines require next to the activity title:
         * "Garmin [device model]", or "Garmin" when the model is unknown. It is the activity
         * description, so it travels with the activity wherever FitPub shows or federates it.
         */
        fun attribution(deviceName: String?): String {
            val model = deviceName?.trim().orEmpty()
            return when {
                model.isEmpty() || model.equals("unknown", ignoreCase = true) -> "Garmin"
                model.startsWith("Garmin", ignoreCase = true) -> model
                else -> "Garmin $model"
            }
        }

        /** Garmin's activity type (Activity API, Appendix A) as a FitPub type, or null to leave the server's guess. */
        fun fitPubActivityType(garminType: String?): String? {
            val type = garminType?.uppercase()?.removeSuffix("_V2") ?: return null
            return when {
                type.endsWith("RUNNING") || type in setOf("ULTRA_RUN", "VIRTUAL_RUN", "OBSTACLE_RUN") -> "RUN"
                type.contains("CYCLING") || type.contains("BIKING") || type.contains("BIKE") || type.endsWith("_MTB") ||
                    type in setOf("BMX", "CYCLOCROSS", "VIRTUAL_RIDE", "HANDCYCLING", "INDOOR_HANDCYCLING") -> "RIDE"
                type == "HIKING" || type == "RUCKING" -> "HIKE"
                type.endsWith("WALKING") -> "WALK"
                type.endsWith("SWIMMING") -> "SWIM"
                type.startsWith("RESORT_SKIING") -> "ALPINE_SKI"
                type == "BACKCOUNTRY_SKIING" -> "BACKCOUNTRY_SKI"
                type in setOf("CROSS_COUNTRY_SKIING_WS", "SKATE_SKIING_WS", "CROSS_COUNTRY_INDOOR_SKIING") -> "NORDIC_SKI"
                type == "SNOWBOARDING_WS" || type == "BACKCOUNTRY_SNOWBOARDING" -> "SNOWBOARD"
                type == "ROWING" || type == "INDOOR_ROWING" -> "ROWING"
                type == "KAYAKING" -> "KAYAKING"
                type == "PADDLING" -> "CANOEING"
                type == "INLINE_SKATING" -> "INLINE_SKATING"
                type in setOf("ROCK_CLIMBING", "BOULDERING", "INDOOR_CLIMBING") -> "ROCK_CLIMBING"
                type == "MOUNTAINEERING" -> "MOUNTAINEERING"
                type == "TENNIS" -> "TENNIS"
                type == "YOGA" || type == "PILATES" -> "YOGA"
                type in setOf(
                    "STRENGTH_TRAINING", "HIIT", "INDOOR_CARDIO", "FITNESS_EQUIPMENT", "ELLIPTICAL",
                    "STAIR_CLIMBING", "JUMP_ROPE", "MOBILITY",
                ) -> "WORKOUT"
                else -> null
            }
        }
    }
}
