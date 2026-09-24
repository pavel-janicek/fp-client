package com.fpclient.android.data.network

import com.fpclient.android.BuildConfig
import com.fpclient.android.data.dto.MailboxCreateDto
import com.fpclient.android.data.dto.MailboxMessageDto
import com.fpclient.android.data.dto.MailboxMessagesDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the Iteration 8g push relay (RFC 8030 mailbox) — deliberately **not**
 * routed through [ApiClient]'s interceptors: the mailbox lives on a different origin (the
 * user's VPS) and must never receive the FitPub session cookie or CSRF token. The relay
 * holds no credentials at all; its endpoint URL *is* the capability.
 *
 * Endpoints (per `fitpub-push-relay/README.md`):
 *  - `POST {base}/mailbox` → 201 `{"endpoint": …}` — mints a fresh mailbox;
 *  - `GET {endpoint}` → 200 `{"messages":[{"ttl","received","payload"}]}` — fetches and
 *    clears the queue (payload = standard base64 of the exact blob);
 *  - `DELETE {endpoint}` → 204 unregister (the app's "disable push").
 */
class MailboxClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Mints a mailbox on [mailboxBase] and returns the endpoint to register instance-side. */
    suspend fun mint(mailboxBase: String): ApiResult<String> {
        val base = normalizeBaseUrl(mailboxBase)
        if (base.isEmpty()) {
            return ApiResult.Error("Enter the URL of your push mailbox.")
        }
        val httpUrl = base.toHttpUrlOrNull()
            ?: return ApiResult.Error("The mailbox URL is not a valid URL.")
        val url = httpUrl.newBuilder().addPathSegment("mailbox").build()
        // The relay mints on method + path alone; it never reads the body, but OkHttp
        // requires POST to carry one — an empty body (no content type) is sent.
        return execute(
            headersBuilder(url).post(ByteArray(0).toRequestBody(null)).build(),
        ) { response, body ->
            if (response.code != 201 && response.code != 200) {
                ApiResult.Error("The mailbox rejected the request (HTTP ${response.code}).", response.code)
            } else {
                val endpoint = runCatching {
                    json.decodeFromString<MailboxCreateDto>(body).endpoint
                }.getOrNull()
                if (endpoint.isNullOrBlank()) {
                    ApiResult.Error("The mailbox did not return an endpoint.")
                } else {
                    ApiResult.Success(endpoint)
                }
            }
        }
    }

    /** Fetches every queued message (and clears the relay queue — at-most-once per RFC 8030). */
    suspend fun fetch(endpoint: String): ApiResult<List<MailboxMessageDto>> {
        val httpUrl = endpoint.toHttpUrlOrNull()
            ?: return ApiResult.Error("The stored mailbox endpoint is not a valid URL.")
        return execute(headersBuilder(httpUrl).get().build()) { response, body ->
            when {
                // Gone: expired or removed mailbox — the caller tears the subscription down
                // instead of retrying.
                response.code == 404 || response.code == 410 ->
                    ApiResult.Error("The mailbox subscription is gone.", response.code)
                !response.isSuccessful ->
                    ApiResult.Error("The mailbox could not be reached (HTTP ${response.code}).", response.code)
                else -> runCatching { ApiResult.Success(parseMessages(body)) }
                    .getOrElse { ApiResult.Error("The mailbox sent an unreadable response.") }
            }
        }
    }

    /**
     * Unregisters the mailbox. 404/410 count as success: the mailbox is already gone,
     * which is exactly what "disabled" should mean.
     */
    suspend fun unregister(endpoint: String): ApiResult<Unit> {
        val httpUrl = endpoint.toHttpUrlOrNull()
            ?: return ApiResult.Success(Unit)
        return execute(headersBuilder(httpUrl).delete().build()) { response, _ ->
            when {
                response.isSuccessful -> ApiResult.Success(Unit)
                response.code == 404 || response.code == 410 -> ApiResult.Success(Unit)
                else -> ApiResult.Error("The mailbox could not be reached (HTTP ${response.code}).", response.code)
            }
        }
    }

    /** Parses `GET /push/<id>` — visible for unit tests (the relay's exact response shape). */
    internal fun parseMessages(body: String): List<MailboxMessageDto> =
        json.decodeFromString<MailboxMessagesDto>(body).messages

    /** Common headers; no cookies, no CSRF — the relay must never see session material. */
    private fun headersBuilder(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", "FP-Client/${BuildConfig.VERSION_NAME}")

    /** One OkHttp round-trip as a suspending [ApiResult]; mirrors [UpdateChecker]'s style. */
    private suspend fun <T> execute(
        request: Request,
        interpret: (Response, String) -> ApiResult<T>,
    ): ApiResult<T> {
        val deferred = CompletableDeferred<ApiResult<T>>()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    deferred.complete(ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    deferred.complete(
                        runCatching {
                            val body = response.use { it.body?.string().orEmpty() }
                            interpret(response, body)
                        }.getOrElse {
                            ApiResult.Error(ErrorMessages.fromThrowable(it), throwable = it)
                        },
                    )
                }
            },
        )
        return deferred.await()
    }

    companion object {

        /**
         * Normalizes user input to a scheme+host(+path) base without a trailing slash:
         * `  push.example.cz/ ` → `https://push.example.cz`. Blank stays blank so the caller
         * can report it (same treatment as [com.fpclient.android.data.session.SessionStore.normalizeServerUrl]).
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isBlank()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            while (url.endsWith("/")) url = url.dropLast(1)
            return url
        }
    }
}
