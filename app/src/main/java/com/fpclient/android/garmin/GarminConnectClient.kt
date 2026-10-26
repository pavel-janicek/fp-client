package com.fpclient.android.garmin

import com.fpclient.android.BuildConfig
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
internal data class CreateMailboxRequest(val publicKey: String, val authSecret: String)

@Serializable
data class CreatedMailboxDto(val id: String, val manageToken: String, val linkUrl: String)

@Serializable
internal data class LinkUrlDto(val linkUrl: String)

@Serializable
data class MailboxStatusDto(
    val linked: Boolean = false,
    val activityExport: Boolean = false,
    val permissions: List<String> = emptyList(),
    val linkLost: String? = null,
    val files: Int = 0,
)

/** One queued activity. [meta] is the standard-base64 ciphertext of a [GarminFileMeta] JSON. */
@Serializable
data class RelayFileDto(val id: String, val size: Long = 0, val meta: String)

@Serializable
internal data class RelayFilesDto(val files: List<RelayFileDto> = emptyList())

/** What the relay knows about an activity, decrypted on the phone. */
@Serializable
data class GarminFileMeta(
    val summaryId: String,
    val activityId: Long? = null,
    val fileType: String = "FIT",
    val activityType: String? = null,
    val activityName: String? = null,
    val deviceName: String? = null,
    val startTimeInSeconds: Long? = null,
)

/**
 * HTTP client for the Garmin Connect relay (`fitpub-garmin-relay`, its README lists the
 * endpoints). Like [com.fpclient.android.data.network.MailboxClient] it stays off [ApiClient]'s
 * interceptors: the relay is another origin and must never see FitPub session material. Every
 * mailbox call carries only `Authorization: Bearer <manageToken>`.
 */
class GarminConnectClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun createMailbox(relayBase: String, publicKey: String, authSecret: String): ApiResult<CreatedMailboxDto> {
        val url = relayBase.toHttpUrlOrNull()?.newBuilder()?.addPathSegments("garmin/mailbox")?.build()
            ?: return ApiResult.Error("The Garmin relay URL is not valid.")
        val body = json.encodeToString(CreateMailboxRequest(publicKey, authSecret))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        return execute(request(url, null).post(body).build()) { response, text ->
            if (response.code == 201) decode<CreatedMailboxDto>(text) else failure(response, text)
        }
    }

    suspend fun relink(mailbox: GarminConnectMailbox): ApiResult<String> =
        execute(request(mailboxUrl(mailbox, "link"), mailbox).post(ByteArray(0).toRequestBody(null)).build()) { response, text ->
            if (response.isSuccessful) {
                when (val dto = decode<LinkUrlDto>(text)) {
                    is ApiResult.Success -> ApiResult.Success(dto.data.linkUrl)
                    is ApiResult.Error -> dto
                }
            } else {
                failure(response, text)
            }
        }

    suspend fun status(mailbox: GarminConnectMailbox): ApiResult<MailboxStatusDto> =
        execute(request(mailboxUrl(mailbox), mailbox).get().build()) { response, text ->
            if (response.isSuccessful) decode<MailboxStatusDto>(text) else failure(response, text)
        }

    suspend fun files(mailbox: GarminConnectMailbox): ApiResult<List<RelayFileDto>> =
        execute(request(mailboxUrl(mailbox, "files"), mailbox).get().build()) { response, text ->
            if (response.isSuccessful) {
                when (val dto = decode<RelayFilesDto>(text)) {
                    is ApiResult.Success -> ApiResult.Success(dto.data.files)
                    is ApiResult.Error -> dto
                }
            } else {
                failure(response, text)
            }
        }

    /** The encrypted activity file. */
    suspend fun file(mailbox: GarminConnectMailbox, fileId: String): ApiResult<ByteArray> =
        executeBytes(request(mailboxUrl(mailbox, "files", fileId), mailbox).get().build())

    /** Tells the relay the file is safely on FitPub; it deletes its copy. 404 counts as done. */
    suspend fun ack(mailbox: GarminConnectMailbox, fileId: String): ApiResult<Unit> =
        execute(request(mailboxUrl(mailbox, "files", fileId), mailbox).delete().build()) { response, text ->
            if (response.isSuccessful || response.code == 404) ApiResult.Success(Unit) else failure(response, text)
        }

    /** Disconnects: the relay deregisters at Garmin and deletes everything. 404 counts as done. */
    suspend fun delete(mailbox: GarminConnectMailbox): ApiResult<Unit> =
        execute(request(mailboxUrl(mailbox), mailbox).delete().build()) { response, text ->
            if (response.isSuccessful || response.code == 404) ApiResult.Success(Unit) else failure(response, text)
        }

    private fun mailboxUrl(mailbox: GarminConnectMailbox, vararg more: String): HttpUrl {
        val builder = mailbox.relayBase.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments("garmin/mailbox")
            .addPathSegment(mailbox.id)
        more.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun request(url: HttpUrl, mailbox: GarminConnectMailbox?): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", "FP-Client/${BuildConfig.VERSION_NAME}")
        .apply { if (mailbox != null) header("Authorization", "Bearer ${mailbox.manageToken}") }

    private inline fun <reified T> decode(text: String): ApiResult<T> =
        runCatching { ApiResult.Success(json.decodeFromString<T>(text)) }
            .getOrElse { ApiResult.Error("The Garmin relay sent an unreadable response.") }

    private fun failure(response: Response, text: String): ApiResult.Error = ApiResult.Error(
        ErrorMessages.extract(text, "The Garmin relay answered HTTP ${response.code}."),
        response.code,
    )

    private suspend fun <T> execute(request: Request, interpret: (Response, String) -> ApiResult<T>): ApiResult<T> =
        call(request) { response -> interpret(response, response.body?.string().orEmpty()) }

    private suspend fun executeBytes(request: Request): ApiResult<ByteArray> = call(request) { response ->
        if (response.isSuccessful) {
            ApiResult.Success(response.body?.bytes() ?: ByteArray(0))
        } else {
            failure(response, response.body?.string().orEmpty())
        }
    }

    private suspend fun <T> call(request: Request, read: (Response) -> ApiResult<T>): ApiResult<T> {
        val deferred = CompletableDeferred<ApiResult<T>>()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    deferred.complete(ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    deferred.complete(
                        runCatching { response.use(read) }
                            .getOrElse { ApiResult.Error(ErrorMessages.fromThrowable(it), throwable = it) },
                    )
                }
            },
        )
        return deferred.await()
    }

    companion object {
        /** The relay's JSON carries `[]byte` as standard base64. */
        fun decodeStandardBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
    }
}
