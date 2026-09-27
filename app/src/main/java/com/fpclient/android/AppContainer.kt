package com.fpclient.android

import android.content.Context
import com.fpclient.android.data.network.ApiClient
import com.fpclient.android.data.network.MailboxClient
import com.fpclient.android.data.repository.ActivityRepository
import com.fpclient.android.data.repository.AnalyticsRepository
import com.fpclient.android.data.repository.AuthRepository
import com.fpclient.android.data.repository.BatchImportRepository
import com.fpclient.android.data.repository.NotificationRepository
import com.fpclient.android.data.repository.PrivacyZoneRepository
import com.fpclient.android.data.repository.PushRepository
import com.fpclient.android.data.repository.TimelineRepository
import com.fpclient.android.data.repository.UserRepository
import com.fpclient.android.notifications.NotificationPollStore
import com.fpclient.android.notifications.PushSubscriptionStore
import com.fpclient.android.recording.RecordingShareManager
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-rolled service locator (plain object graph) shared across the app. It deliberately
 * avoids a DI framework so the project stays light and trivially buildable.
 */
class AppContainer(context: Context) {

    val sessionStore: SessionStore by lazy { SessionStore(context) }

    /**
     * Bumped whenever the local user creates an activity so list screens (timeline,
     * profile) can re-fetch instead of showing a stale list that "lost" the entry.
     */
    val activitiesVersion = MutableStateFlow(0)

    private val apiClient: ApiClient by lazy { ApiClient(context, sessionStore) }

    val authRepository: AuthRepository by lazy { AuthRepository(apiClient.api, sessionStore) }
    val timelineRepository: TimelineRepository by lazy { TimelineRepository(apiClient.api) }
    val activityRepository: ActivityRepository by lazy { ActivityRepository(apiClient.api) }
    val userRepository: UserRepository by lazy { UserRepository(apiClient.api) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepository(apiClient.api) }
    val notificationRepository: NotificationRepository by lazy { NotificationRepository(apiClient.api) }

    /**
     * Remembers how far background notification delivery got (Iteration 8f): the last-seen
     * notification id, which session it belongs to, and whether the push permission was
     * already offered in Settings. Shared by the periodic worker and the Settings → Push card.
     */
    val notificationPollStore: NotificationPollStore by lazy { NotificationPollStore(context) }

    /**
     * Iteration 8h — the mailbox Web Push subscription (endpoint + app-private keypair +
     * auth secret) and the repository that probes/enables/disables it and decrypts queued
     * blobs. Shared by Settings → Push, the mailbox worker and the 8f poll (which needs to
     * know when 8h owns the announcements).
     */
    val pushSubscriptionStore: PushSubscriptionStore by lazy { PushSubscriptionStore(context) }
    val pushRepository: PushRepository by lazy {
        PushRepository(apiClient.api, MailboxClient(), pushSubscriptionStore, sessionStore)
    }

    val privacyZoneRepository: PrivacyZoneRepository by lazy { PrivacyZoneRepository(apiClient.api) }
    val batchImportRepository: BatchImportRepository by lazy { BatchImportRepository(apiClient.api) }

    /**
     * Save & share of recorded workouts (Iteration 8d): GPX export from the persisted
     * track file, the pending-upload registry, upload/retry through the regular multipart
     * endpoint. Held here (not in the Record screen) so the retry-on-start pass and the
     * summary screen share one instance — and one pending-upload flow.
     */
    val recordingShareManager: RecordingShareManager by lazy {
        RecordingShareManager(context, activityRepository, activitiesVersion)
    }
}