package com.fpclient.android.wear

import android.app.Application

/** Process entry point; exists so logging is set up before any activity, service or worker runs. */
class FitPubWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WearLog.init(this)
    }
}
