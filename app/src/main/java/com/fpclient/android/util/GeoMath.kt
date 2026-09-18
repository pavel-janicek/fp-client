package com.fpclient.android.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geo math for map overlays — no Android dependencies, fully unit tested.
 * Partner to `recording/TrackMath.kt` (which owns the distance side).
 */
object GeoMath {

    /** Mean Earth radius (meters) — same constant as [com.fpclient.android.recording.TrackMath]. */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Vertices of a circle of [radiusMeters] around `(latitude, longitude)`, computed with
     * the destination-point formula so the ring is metrically correct at any latitude
     * (a naive lat/lon offset would be wildly stretched towards the poles).
     *
     * Longitudes are normalized to [-180, 180], so circles centered near the antimeridian
     * wrap instead of producing ±200° nonsense.
     */
    fun circlePoints(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        segments: Int = 64,
    ): List<Pair<Double, Double>> {
        require(radiusMeters > 0.0) { "radiusMeters must be positive" }
        require(segments >= 3) { "at least 3 segments are needed to form a polygon" }
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        val angular = radiusMeters / EARTH_RADIUS_M // central angle of the radius
        val cosAngular = cos(angular)
        val sinAngular = sin(angular)
        val points = ArrayList<Pair<Double, Double>>(segments)
        for (i in 0 until segments) {
            val bearing = 2 * PI * i / segments
            val lat2 = asinCompat(sin(latRad) * cosAngular + cos(latRad) * sinAngular * cos(bearing))
            val lon2 = lonRad + atan2(
                sin(bearing) * sinAngular * cos(latRad),
                cosAngular - sin(latRad) * sin(lat2),
            )
            points.add(Math.toDegrees(lat2) to normalizeLongitude(Math.toDegrees(lon2)))
        }
        return points
    }

    /** Wraps any longitude into [-180, 180]. */
    fun normalizeLongitude(longitude: Double): Double =
        ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun asinCompat(x: Double): Double = sqrt((1 - x * x).coerceAtLeast(0.0)).let { atan2(x, it) }
}
