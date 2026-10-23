package com.fpclient.android.util

import com.fpclient.android.data.dto.PrivacyZonePreviewDto
import com.fpclient.android.recording.TrackMath

/**
 * How much of an owner's route the FitPub server hides from other people because it lies
 * inside the owner's privacy zones. The server strips those points from everyone else's map and
 * from every route download, the owner's included, and refuses the download when fewer than two
 * points remain (`ActivityRouteService.createRoute`).
 *
 * The server also nudges each zone's centre per activity (10–15 % of the radius) so the real
 * centre cannot be triangulated, which the app cannot reproduce. [coverage] is therefore an
 * estimate near zone edges; the notice it drives says so.
 */
object RoutePrivacy {

    enum class Coverage {
        /** No point of the route lies in a zone. */
        NONE,

        /** Part of the route is hidden from others. */
        PARTIAL,

        /** At most one point lies outside the zones: others see no map, no download exists. */
        FULL,
    }

    /** Classifies [points] (latitude to longitude) against the owner's [zones]. */
    fun coverage(points: List<Pair<Double, Double>>, zones: List<PrivacyZonePreviewDto>): Coverage {
        val usable = zones.filter { it.latitude != null && it.longitude != null && (it.radiusMeters ?: 0) > 0 }
        if (points.isEmpty() || usable.isEmpty()) return Coverage.NONE
        val outside = points.count { (lat, lon) ->
            usable.none { z -> TrackMath.haversineMeters(lat, lon, z.latitude!!, z.longitude!!) <= z.radiusMeters!! }
        }
        return when {
            outside == points.size -> Coverage.NONE
            outside < 2 -> Coverage.FULL
            else -> Coverage.PARTIAL
        }
    }
}
