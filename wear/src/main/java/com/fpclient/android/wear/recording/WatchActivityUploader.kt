package com.fpclient.android.wear.recording

import com.fpclient.android.wear.BuildConfig
import com.fpclient.android.wear.auth.WearAuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class WatchActivityUploader(
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun upload(workout: PendingWatchWorkout, gpxFile: File, auth: WearAuthState): Boolean =
        withContext(Dispatchers.IO) {
            if (!auth.isSignedIn || auth.serverUrl.isBlank() || !gpxFile.isFile) return@withContext false
            val base = auth.serverUrl.trimEnd('/').toHttpUrlOrNull() ?: return@withContext false
            val uploadUrl = base.newBuilder().addPathSegments("api/web/activities/upload").build()
            val response = executeWithCsrf(base) { csrf ->
                val mediaType = "application/gpx+xml".toMediaTypeOrNull()
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", gpxFile.name, gpxFile.asRequestBody(mediaType))
                    .addFormDataPart("title", workout.title)
                    .addFormDataPart("visibility", workout.visibility.ifBlank { WatchWorkoutSyncStore.DEFAULT_VISIBILITY })
                    .build()
                signedRequest(uploadUrl, auth.token, csrf)
                    .post(multipart)
                    .build()
            } ?: return@withContext false

            val uploaded = response.use { result ->
                if (!result.isSuccessful) return@withContext false
                runCatching { json.decodeFromString<WatchUploadedActivity>(result.body?.string().orEmpty()) }
                    .getOrNull()
            }
            if (uploaded?.id.isNullOrBlank()) return@withContext false
            if (!uploaded.activityType.equals(workout.activityType, ignoreCase = true)) {
                val updateUrl = base.newBuilder()
                    .addPathSegments("api/web/activities/${uploaded.id}")
                    .build()
                val update = WatchActivityUpdate(
                    title = uploaded.title ?: workout.title,
                    description = uploaded.description,
                    visibility = uploaded.visibility ?: workout.visibility.ifBlank { WatchWorkoutSyncStore.DEFAULT_VISIBILITY },
                    activityType = workout.activityType,
                )
                val body = json.encodeToString(update).toRequestBody(JSON_MEDIA_TYPE)
                executeWithCsrf(base) { csrf ->
                    signedRequest(updateUrl, auth.token, csrf).put(body).build()
                }?.use { it.isSuccessful }
            }
            true
        }

    private fun executeWithCsrf(
        base: HttpUrl,
        request: (String) -> Request,
    ): okhttp3.Response? {
        var csrf = primeCsrfToken(base) ?: return null
        repeat(2) { attempt ->
            val response = runCatching { client.newCall(request(csrf)).execute() }.getOrNull() ?: return null
            if (response.code != 403 || attempt == 1) return response
            response.close()
            csrf = primeCsrfToken(base) ?: return null
        }
        return null
    }

    private fun primeCsrfToken(base: HttpUrl): String? {
        val loginUrl = base.newBuilder().addPathSegment("login").build()
        val request = Request.Builder()
            .url(loginUrl)
            .header("Accept", "text/html")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.headers.values("Set-Cookie")
                    .firstNotNullOfOrNull(::csrfCookie)
            }
        }.getOrNull()
    }

    private fun csrfCookie(setCookie: String): String? {
        val separator = setCookie.indexOf('=')
        if (separator < 0 || setCookie.substring(0, separator).trim() != "XSRF-TOKEN") return null
        return setCookie.substring(separator + 1).substringBefore(';').takeIf { it.isNotBlank() }
    }

    private fun signedRequest(url: HttpUrl, token: String, csrf: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "JWT_TOKEN=$token; XSRF-TOKEN=$csrf")
            .header("X-XSRF-TOKEN", csrf)

    @Serializable
    private data class WatchUploadedActivity(
        val id: String = "",
        val activityType: String? = null,
        val title: String? = null,
        val description: String? = null,
        val visibility: String? = null,
    )

    @Serializable
    private data class WatchActivityUpdate(
        val title: String,
        val description: String? = null,
        val visibility: String,
        val activityType: String,
    )

    companion object {
        private const val USER_AGENT = "FP-Client-Wear/${BuildConfig.VERSION_NAME}"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()!!

        private val defaultClient = OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}