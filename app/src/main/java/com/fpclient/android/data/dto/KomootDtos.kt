package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Komoot import
// ---------------------------------------------------------------------------

/**
 * Body of `POST /api/web/komoot-import/activities`.
 *
 * The **Komoot** account credentials are sent to the user's own FitPub server, which uses
 * them for the duration of that one request to talk to Komoot and then forgets them — the
 * server stores no password (see `social.fitpub.komoot.entity.KomootImport`, which keeps
 * only ids and a timestamp). The app must be equally forgetful: never persisted, never
 * written to a log, and never put into a `SavedStateHandle` that survives the process.
 *
 * [userId] is the Komoot account id (letters and digits — the server validates
 * `[A-Za-z0-9]+`), which the user finds in their Komoot account settings.
 *
 * [startDate] and [endDate] are both optional, but the server rejects a request that sets
 * only one of them (`KomootImportRequest.isDateRangeConsistent` is an `@AssertTrue`), so
 * the UI offers the range as all-or-nothing.
 */
@Serializable
data class KomootImportRequest(
    val email: String,
    val password: String,
    val userId: String,
    val startDate: String? = null,
    val endDate: String? = null,
)

/** Body of `POST /api/web/komoot-import/activities/import` — one activity at a time. */
@Serializable
data class KomootActivityImportRequest(
    val email: String,
    val password: String,
    val userId: String,
    val activityId: Long,
)

/** `KomootActivitiesResponse` — the preview list. */
@Serializable
data class KomootActivitiesResponse(
    val userId: String? = null,
    val totalCount: Int = 0,
    val activities: List<KomootActivitySummaryDto> = emptyList(),
)

/**
 * `KomootActivitySummaryDTO`.
 *
 * [date] is a Jackson-serialised `OffsetDateTime`, i.e. an ISO-8601 string *with* an offset
 * (`2024-05-01T10:00:00+02:00`), not an epoch array. [Format.dateTime] parses it unchanged.
 *
 * [mappedActivityType] is already a FitPub `Activity.ActivityType` name (the server maps
 * Komoot's sport), so it can be handed straight to `ActivityTypes.icon` for the row's emoji.
 * [imported] is the server's own duplicate guard — it reports activities this account has
 * already pulled in, and the import endpoint rejects them again.
 */
@Serializable
data class KomootActivitySummaryDto(
    val id: Long = 0,
    val name: String? = null,
    val sport: String? = null,
    val mappedActivityType: String? = null,
    val status: String? = null,
    val type: String? = null,
    val date: String? = null,
    val distanceMeters: Double? = null,
    val durationSeconds: Int? = null,
    val timeInMotionSeconds: Int? = null,
    val elevationUp: Double? = null,
    val imported: Boolean = false,
    val fitPubActivityId: String? = null,
)

/**
 * `KomootImportExecutionResponse`.
 *
 * The server answers 200 with `status` and `message` even when it refused the import (an
 * already-imported activity raises `IllegalStateException`, which the resource maps to
 * **502**), so both fields are read rather than assuming success from the HTTP code.
 */
@Serializable
data class KomootImportExecutionResponse(
    val importedActivityId: String? = null,
    val importedKomootActivityId: Long? = null,
    val status: String? = null,
    val message: String? = null,
)
