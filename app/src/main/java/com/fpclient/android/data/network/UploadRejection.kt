package com.fpclient.android.data.network

/**
 * Whether an upload failure is the server refusing *this file* — resending it unchanged would be
 * refused again (e.g. 400 INVALID_ACTIVITY_FILE: "No GPS track points found in GPX file").
 *
 * Every 4xx counts except the ones that say nothing about the file: 401 (session expired —
 * handled by signing in again), 408 (timeout) and 429 (rate limit). No status (network failure)
 * and 5xx are transient. Automatic retries skip permanent rejections; the user can still retry
 * one by hand.
 */
fun isPermanentUploadRejection(statusCode: Int?): Boolean =
    statusCode != null && statusCode in 400..499 && statusCode !in setOf(401, 408, 429)
