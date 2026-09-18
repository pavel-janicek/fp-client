package com.fpclient.android.recording

import kotlinx.serialization.Serializable

/**
 * One recorded GPS fix. Persisted as a line in the append-only track file, so the field
 * set is the wire format of Iteration 8d's GPX export: position, elevation, time and the
 * fix accuracy (kept so later passes can filter or weight points).
 */
@Serializable
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    /** Elevation in meters (LocationManager altitude, typically above MSL or ellipsoid). */
    val ele: Double,
    /** Epoch milliseconds of the fix (LocationManager.time, not arrival time). */
    val time: Long,
    /** Fix accuracy in meters as reported by the provider. */
    val accuracy: Double,
)
