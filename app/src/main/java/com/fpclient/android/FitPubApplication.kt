package com.fpclient.android

import android.app.Application
import android.content.Context
import com.fpclient.android.notifications.InstantDeliveryService
import com.fpclient.android.notifications.NotificationPollWorker
import com.fpclient.android.notifications.PushFetchWorker
import com.fpclient.android.notifications.PushNotifications
import org.osmdroid.config.Configuration

class FitPubApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Background notification delivery (Iteration 8f): create the channel up front so the
        // OS-level toggle for it is visible in system settings even before the first poll, and
        // make sure the periodic poll exists. The worker itself skips guests / signed-out
        // users, so scheduling unconditionally is safe.
        PushNotifications.ensureChannel(this)
        NotificationPollWorker.schedule(this)
        // Mailbox Web Push check (Iteration 8h): scheduled unconditionally as well — the
        // worker exits immediately until a subscription for the current session exists, and
        // scheduling with KEEP never shifts an already-running schedule.
        PushFetchWorker.schedule(this)
        // Instant delivery receiver (Iteration 8j): started only when the *current* session
        // already owns an `instantEnabled` subscription. The service re-checks that on every
        // start, so a signed-out device or another account's subscription never connects even
        // if this runs at a bad moment.
        InstantDeliveryService.startIfEnabled(this)

        // Configure osmdroid tile cache in app storage.
        @Suppress("DEPRECATION")
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "FP-Client/2.0.2"

        // osmdroid needs access to a writeable tile cache dir for modern scoped storage.
        Configuration.getInstance().osmdroidBasePath = filesDir
        Configuration.getInstance().osmdroidTileCache = filesDir.resolve("osmdroid")
    }

    companion object {
        fun from(context: Context): FitPubApplication = context.applicationContext as FitPubApplication

        fun container(context: Context): AppContainer = from(context).container
    }
}