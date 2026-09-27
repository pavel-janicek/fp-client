package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Batch import
// ---------------------------------------------------------------------------

/**
 * `BatchImportJobStatusDTO`. The counts are named `successCount` / `failedCount` on the
 * server; the client used to declare `successful` / `failed`, which the server has never
 * sent, so every import reported "0 ok · 0 failed" no matter how it had actually gone.
 */
@Serializable
data class BatchImportJobDto(
    val id: String? = null,
    val status: String? = null,
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val progressPercentage: Int? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
)

/**
 * `BatchImportFileResultDTO`. The failure text is `errorMessage` on the server (there is no
 * `error` field), so a per-file reason never populated. Nothing renders this yet — the app
 * only shows the job-level counts — but the type is kept truthful for when it does.
 */
@Serializable
data class BatchImportFileEntryDto(
    val id: String? = null,
    val filename: String? = null,
    val fileSize: Long? = null,
    val status: String? = null,
    val activityId: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val errorType: String? = null,
    val processedAt: String? = null,
)

@Serializable
data class BatchImportFilePageDto(
    val content: List<BatchImportFileEntryDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

@Serializable
data class BatchImportJobPageDto(
    val content: List<BatchImportJobDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

object BatchImportJobStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val CANCELLING = "CANCELLING"
}

// ---------------------------------------------------------------------------
// Push subscriptions (web-push)
// ---------------------------------------------------------------------------

@Serializable
data class VapidKeyResponse(val publicKey: String? = null)

@Serializable
data class PushSubscriptionRequest(
    val endpoint: String?,
    val p256dhKey: String?,
    val authKey: String?,
)