package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Analytics / statistics
// ---------------------------------------------------------------------------

@Serializable
data class DashboardDto(
    val personalRecordsCount: Long = 0,
    val achievementsCount: Long = 0,
    val recentPersonalRecords: List<PersonalRecordDto> = emptyList(),
    val recentAchievements: List<AchievementDto> = emptyList(),
    val formStatus: String? = null,
        val currentWeekSummary: ActivitySummaryPeriodDto = ActivitySummaryPeriodDto(),
    val currentMonthSummary: ActivitySummaryPeriodDto = ActivitySummaryPeriodDto(),
    val currentYearSummary: ActivitySummaryPeriodDto = ActivitySummaryPeriodDto(),
    val analyticsPendingStatus: AnalyticsPendingStatusDto? = null,
)

@Serializable
data class PersonalRecordDto(
    val id: String? = null,
    val activityType: String? = null,
    val recordType: String? = null,
    val value: Double? = null,
    val unit: String? = null,
    val activityId: String? = null,
    val achievedAt: String? = null,
)

@Serializable
data class AchievementDto(
    val id: String? = null,
    val userId: String? = null,
    val achievementType: String? = null,
    val name: String? = null,
    val description: String? = null,
    val badgeIcon: String? = null,
    val badgeColor: String? = null,
    val earnedAt: String? = null,
    val activityId: String? = null,
    val metadata: JsonObject? = null,
    val createdAt: String? = null,
)

@Serializable
data class TrainingLoadDto(
    val date: String? = null,
    val activityCount: Int = 0,
    val totalDurationSeconds: Long = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalElevationGainMeters: Double = 0.0,
    val trainingStressScore: Double? = null,
    val acuteTrainingLoad: Double? = null,
    val chronicTrainingLoad: Double? = null,
    val trainingStressBalance: Double? = null,
)

/** Period-based activity summary (week/month/year). Maps to server ActivitySummary. */
@Serializable
data class ActivitySummaryPeriodDto(
    val id: String? = null,
    val userId: String? = null,
    val periodType: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val activityCount: Int = 0,
    val totalDurationSeconds: Long = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalElevationGainMeters: Double = 0.0,
    val avgSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val activityTypeBreakdown: Map<String, Int> = emptyMap(),
    val personalRecordsSet: Int = 0,
    val achievementsEarned: Int = 0,
)

@Serializable
data class AnalyticsPendingStatusDto(
    val pending: Boolean = false,
    val message: String? = null,
)

/**
 * `GET …/analytics/form-status` -> `{"formStatus":"FATIGUED","description":"High fatigue
 * detected. Consider taking a rest day."}`.
 *
 * [description] is the server's own plain-English wording for the state
 * (`AnalyticsResource.getFormStatusDescription`). It was absent from this DTO and the
 * endpoint was never called, which is why the app had to present the raw enum and the
 * CTL/ATL/TSB acronyms with no explanation at all.
 */
@Serializable
data class FormStatusDto(
    val formStatus: String? = null,
    val status: String? = null,
    val description: String? = null,
    val trainingStressBalance: Double? = null,
)

object FormStatus {
    const val FRESH = "FRESH"
    const val OPTIMAL = "OPTIMAL"
    const val FATIGUED = "FATIGUED"
    const val UNKNOWN = "UNKNOWN"
}