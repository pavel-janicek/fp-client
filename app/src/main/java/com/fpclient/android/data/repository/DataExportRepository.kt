package com.fpclient.android.data.repository

import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi
import retrofit2.Response
import java.io.OutputStream

/**
 * Data export: asking the instance to build a portable ZIP of the account, and downloading
 * the finished archive.
 *
 * FitPub serves this feature through its **web client's own routes** — there is no JSON API
 * for it — and that shape is what this class is built around:
 *
 *  - **Requesting** is the export page's form post (`replaceExisting=true`), answered with a
 *    `302` redirect rather than a `2xx`. The same `302` comes back when the server *refuses*
 *    (an export is already being built), and it carries the reason only in a flash attribute
 *    bound to a server-side session this stateless client never holds. "Accepted" and
 *    "already running" are therefore indistinguishable from the app — but they mean the same
 *    thing to the user (an archive is on its way), and the UI words both that way instead of
 *    guessing. `replaceExisting` is always `true` because the app cannot know whether a ready
 *    archive exists; the server keeps the existing archive downloadable until the replacement
 *    has been built successfully, so confirming is the safe side.
 *  - **Downloading** is `GET /settings/export/download`, which answers `200` with the ZIP or
 *    `404` when nothing is downloadable. There is no status route either, so readiness cannot
 *    be polled: the app finds out by asking for the archive itself, and the server's
 *    `DATA_EXPORT_READY` notification is what tells the user it is worth asking.
 *
 * Both calls go through the API built on `ApiClient.noRedirectApi`: the redirects above are
 * outcomes to read, not paths to follow.
 */
class DataExportRepository(
    private val api: FitPubApi,
) {

    /**
     * Requests a fresh archive. `Success` means the instance took the request — or was
     * already building one, which the user asked for either way (see the class comment for
     * why those two cannot be told apart).
     */
    suspend fun requestExport(): ApiResult<Unit> {
        return try {
            val response = api.requestDataExport(replaceExisting = true)
            // Acceptance is the 302 back to /settings/export: this route never answers 2xx.
            if (response.isSuccessful || response.code() in 300..399) ApiResult.Success(Unit)
            else ApiResult.Error(requestFailure(response), response.code())
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * Streams the downloadable archive into [target] and returns how many bytes were written.
     *
     * [target] is the stream behind the storage location the user picked, and writing straight
     * into it is deliberate: an account export is orders of magnitude larger than the GPX
     * routes the app downloads elsewhere, so buffering the whole ZIP in a heap `ByteArray`
     * would be a real risk on a phone. The caller owns [target]; this method neither opens nor
     * closes it.
     *
     * [onProgress] reports (bytes written so far, total bytes when the instance announced a
     * `Content-Length`), at most once per reported megabyte plus once at the end, so a fast
     * instance cannot flood the UI with updates.
     */
    suspend fun downloadArchive(
        target: OutputStream,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): ApiResult<Long> {
        return try {
            val response = api.dataExportDownload()
            if (!response.isSuccessful) {
                return ApiResult.Error(downloadFailure(response), response.code())
            }
            val body = response.body()
                ?: return ApiResult.Error("The instance returned no archive.")
            val total = body.contentLength().takeIf { it >= 0 }
            var written = 0L
            var reported = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    target.write(buffer, 0, read)
                    written += read
                    if (written - reported >= PROGRESS_STEP) {
                        reported = written
                        onProgress(written, total)
                    }
                }
            }
            // A real archive always contains at least the ZIP end-of-central-directory
            // record, so an empty transfer means it never happened. Same guard as the route
            // download.
            if (written == 0L) {
                ApiResult.Error("The instance sent an empty archive.")
            } else {
                onProgress(written, total)
                ApiResult.Success(written)
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    /**
     * These routes are not under `/api/`, so an unauthenticated caller is sent to `/login` —
     * and because this client asks for JSON, the server's entry point takes its JSON branch
     * instead, which is `403` for these paths (`401` is what `/api/` answers). Both mean
     * "sign in", the same mapping the route download uses.
     */
    private fun requestFailure(response: Response<*>): String = when (response.code()) {
        401, 403 -> "Sign in to request a data export."
        404 -> "This instance does not offer data export."
        else -> ErrorMessages.extract(
            response.errorBody()?.string(),
            "The instance refused the data export request (HTTP ${response.code()}).",
        )
    }

    private fun downloadFailure(response: Response<*>): String = when (response.code()) {
        401, 403 -> "Sign in to download your data export."
        404 -> "No data export archive is ready on this instance yet."
        else -> ErrorMessages.extract(
            response.errorBody()?.string(),
            "Downloading the archive failed (HTTP ${response.code()}).",
        )
    }

    private companion object {
        /** 64 KiB chunks: one write per chunk instead of one per 8 KiB. */
        const val BUFFER_SIZE = 64 * 1024

        /** Report progress at most once per megabyte written. */
        const val PROGRESS_STEP = 1024L * 1024L
    }
}
