package com.fpclient.android.util

import android.content.Context
import android.location.LocationManager

/**
 * Last-known-location lookup for one-shot "where am I" needs (privacy-zone placement).
 * Uses the framework [LocationManager] only — no Play services, per project policy —
 * and never requests fixes; it only reads what previous apps/sessions already cached.
 */
object DeviceLocation {

    /** A provider snapshot: coordinates, accuracy and wall-clock fix time. */
    data class LastFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timeMs: Long,
    )

    /**
     * Best (most accurate) cached fix across the standard providers, or `null` when
     * none is available (no permission yet, or the device has no cached fixes).
     */
    fun lastKnown(context: Context): LastFix? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val fixes = mutableListOf<LastFix>()
        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )) {
            try {
                val location = manager.getLastKnownLocation(provider) ?: continue
                fixes.add(
                    LastFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        timeMs = location.time,
                    ),
                )
            } catch (_: SecurityException) {
                // Permission for this provider not granted — skip it.
            } catch (_: IllegalArgumentException) {
                // Provider unknown on this device — skip it.
            }
        }
        return bestOf(fixes)
    }

    /** Most accurate of the given fixes; pure so tests can pin the selection rule. */
    fun bestOf(fixes: List<LastFix>): LastFix? = fixes.minByOrNull { it.accuracyMeters }
}
