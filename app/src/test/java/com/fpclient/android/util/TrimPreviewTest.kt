package com.fpclient.android.util

import com.fpclient.android.data.dto.ActivityTrimDataDto
import com.fpclient.android.data.dto.ActivityTrimPointDto
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [TrimPreview] against the server's **own** JavaScript implementation —
 * `src/main/resources/static/js/elevation-calculation.js`, the file the web editor loads to
 * preview a trim. The expected values were produced by running that exact file over the same
 * synthetic tracks (harness: `/tmp/trim_goldens.js`), e.g.
 * `calculate(track, startIndex, endIndex) => {"ascent":96.61,"descent":0}`.
 *
 * If a golden ever disagrees with the Kotlin port, either the port or the server changed —
 * find out which one before touching the numbers.
 */
class TrimPreviewTest {

    /**
     * The elevation pipeline itself: 5 m resampling, centred 70 m median, 2 m hysteresis,
     * runs broken by unusable points and `elevationSegment` changes. Straight from the JS
     * `calculate()` output.
     */
    @Test
    fun elevationTotalsMatchServerJavaScript() {
        val cases = listOf(
            Triple("flat 100m", track(101, 10.0) { 100.0 }, 0.0 to 0.0),
            Triple("constant 10% climb", track(101, 10.0) { it.toDouble() }, 96.61 to 0.0),
            Triple(
                "up-down-up",
                track(121, 10.0) { i -> if (i <= 50) i.toDouble() else if (i <= 70) (50 - (i - 50)).toDouble() else (30 + (i - 70)).toDouble() },
                92.76 to 16.13,
            ),
            Triple("rolling +-8m", track(300, 5.0) { 200 + 8 * sin(it / 7.0) }, 85.2 to 96.74),
            Triple(
                "noisy +-0.8m (no real climb)",
                track(400, 5.0) { 200 + 0.5 * sin(it / 3.0) + 0.3 * cos(it.toDouble()) },
                0.0 to 0.0,
            ),
            Triple(
                "null elevation gap",
                track(101, 10.0) { if (it > 40 && it < 60) null else it.toDouble() },
                73.08 to 0.0,
            ),
            Triple(
                "segment break at 50",
                track(101, 10.0, segmentAt = { i -> if (i < 50) 0 else 1 }) { it.toDouble() },
                92.1 to 0.0,
            ),
            Triple(
                "sub-range 20..70 of climb",
                track(101, 10.0) { it.toDouble() }.subList(20, 71),
                46.55 to 0.0,
            ),
        )
        for ((name, points, expected) in cases) {
            val (ascent, descent) = TrimPreview.calculate(points)
            assertEquals("$name ascent", expected.first, ascent!!, 0.01)
            assertEquals("$name descent", expected.second, descent!!, 0.01)
        }
    }

    /**
     * `preview(data, 20, 70)` in the JS harness: elevation 46.55/0 plus the figures the
     * preview adds on top — retained count, `end - start` duration, and a distance recomputed
     * from the coordinates (haversine) rather than subtracting the parser's cumulative field.
     */
    @Test
    fun previewRecomputesSubRangeLikeTheServer() {
        val climb = track(101, 10.0) { it.toDouble() }
        val data = ActivityTrimDataDto(
            points = climb,
            currentStartIndex = 0,
            currentEndIndex = 100,
            currentElevationGain = 100.0,
            currentElevationLoss = 0.0,
            originalElevationGain = 100.0,
            originalElevationLoss = 0.0,
        )

        val preview = TrimPreview.preview(data, 20, 70)

        assertEquals(46.55, preview.elevationGainMeters!!, 0.01)
        assertEquals(0.0, preview.elevationLossMeters!!, 0.01)
        assertEquals(51, preview.retainedPoints)
        assertEquals(500L, preview.durationSeconds)
        // ~50 x 10 m north of each other; loose enough for any Earth radius in use.
        assertTrue("distance was ${preview.distanceMeters}", preview.distanceMeters in 495.0..501.0)
        // Not `points[70].distance - points[20].distance` (the parser's rounded cumulative).
        assertTrue(preview.distanceMeters != climb[70].distance!! - climb[20].distance!!)
    }

    /**
     * The web editor's two shortcuts, verified against JS `preview()` with sentinel stored
     * values: a selection equal to the stored range or to the whole original track reports
     * the stored numbers verbatim — 111/222 and 333/444 below — while anything in between is
     * recomputed (46.55/0, the `preview(data,20,70)` golden).
     */
    @Test
    fun previewUsesStoredValuesWhenTheServerWould() {
        val data = ActivityTrimDataDto(
            points = track(101, 10.0) { it.toDouble() },
            currentStartIndex = 0,
            currentEndIndex = 50,
            currentElevationGain = 111.0,
            currentElevationLoss = 222.0,
            originalElevationGain = 333.0,
            originalElevationLoss = 444.0,
        )

        val storedRange = TrimPreview.preview(data, 0, 50)
        assertEquals(111.0, storedRange.elevationGainMeters!!, 0.0)
        assertEquals(222.0, storedRange.elevationLossMeters!!, 0.0)

        val wholeTrack = TrimPreview.preview(data, 0, 100)
        assertEquals(333.0, wholeTrack.elevationGainMeters!!, 0.0)
        assertEquals(444.0, wholeTrack.elevationLossMeters!!, 0.0)

        val inBetween = TrimPreview.preview(data, 20, 70)
        assertEquals(46.55, inBetween.elevationGainMeters!!, 0.01)
        assertEquals(0.0, inBetween.elevationLossMeters!!, 0.01)
    }

    /** A selection that keeps no range at all is empty — the server answers 400 to such a PUT. */
    @Test
    fun degenerateSelectionIsEmpty() {
        val data = ActivityTrimDataDto(points = track(101, 10.0) { it.toDouble() })
        val preview = TrimPreview.preview(data, 50, 50)
        assertEquals(0, preview.retainedPoints)
        assertEquals(0.0, preview.distanceMeters, 0.0)
        assertNull(preview.durationSeconds)
        assertNull(preview.elevationGainMeters)
        assertNull(preview.elevationLossMeters)
    }

    /** Out-of-range indices are clamped to the track instead of failing the preview. */
    @Test
    fun clampedSelectionCoversWholeTrack() {
        val data = ActivityTrimDataDto(points = track(101, 10.0) { it.toDouble() })
        val preview = TrimPreview.preview(data, -5, 9_999)
        assertEquals(101, preview.retainedPoints)
        assertEquals(1000L, preview.durationSeconds)
        // Whole track with no stored original values → the server would report elevation as
        // unavailable rather than compute it twice.
        assertNull(preview.elevationGainMeters)
        // A middle range on the same DTO has neither shortcut to lean on → computed.
        assertTrue(TrimPreview.preview(data, 10, 90).elevationGainMeters != null)
    }

    /** Duration degrades to null rather than throwing when a timestamp cannot be parsed. */
    @Test
    fun durationIsNullWithoutParseableTimestamps() {
        val points = track(11, 10.0) { it.toDouble() }.map { it.copy(timestamp = "not-a-timestamp") }
        val preview = TrimPreview.preview(ActivityTrimDataDto(points = points), 0, 10)
        assertNull(preview.durationSeconds)
        assertEquals(11, preview.retainedPoints)
    }

    /**
     * The JS harness's point generator: steps due north from 48°N 16°E, one point every
     * 10 s, elevation/segment from callbacks.
     */
    private fun track(
        count: Int,
        stepMeters: Double,
        segmentAt: (Int) -> Int = { 0 },
        elevationAt: (Int) -> Double?,
    ): List<ActivityTrimPointDto> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        val degreesPerStep = stepMeters / 111320.0
        return (0 until count).map { i ->
            ActivityTrimPointDto(
                index = i,
                timestamp = base.plusMillis(i * 10_000L).toString(),
                latitude = 48.0 + i * degreesPerStep,
                longitude = 16.0,
                elevation = elevationAt(i),
                distance = i * stepMeters,
                elevationSegment = segmentAt(i),
            )
        }
    }
}

