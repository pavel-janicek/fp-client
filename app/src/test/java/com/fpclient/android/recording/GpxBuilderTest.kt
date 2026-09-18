package com.fpclient.android.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GPX 1.1 export tests (Iteration 8d). The builder is pure JVM, so the exact document the
 * app uploads is asserted here: one <trkseg> per paused segment, <ele> + <time> on every
 * trkpt, and escaping of user-chosen names.
 */
class GpxBuilderTest {

    private val pointA = TrackPoint(47.3769, 8.5417, 400.0, 1_726_000_000_000L, 5.0)
    private val pointB = TrackPoint(47.3800, 8.5450, 405.5, 1_726_000_002_000L, 8.2)

    @Test
    fun `a single segment is exported as one trkseg with ele and time`() {
        val gpx = GpxBuilder.build(listOf(listOf(pointA, pointB)), activityType = "RUN")

        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<gpx version=\"1.1\" creator=\"FP-Client\" xmlns=\"http://www.topografix.com/GPX/1/1\">"))
        assertTrue(gpx.contains("<type>run</type>"))
        assertEquals(1, gpx.split("<trkseg>").size - 1)
        assertEquals(2, gpx.split("<trkpt ").size - 1)
        assertTrue(gpx.contains("<trkpt lat=\"47.376900\" lon=\"8.541700\">"))
        assertTrue(gpx.contains("<ele>400.0</ele>"))
        assertTrue(gpx.contains("<time>2024-09-10T20:26:40Z</time>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `every paused segment becomes its own trkseg`() {
        val gpx = GpxBuilder.build(listOf(listOf(pointA), listOf(pointB)))
        assertEquals(2, gpx.split("<trkseg>").size - 1)
        // Order is preserved: the first segment's point precedes the second's.
        assertTrue(gpx.indexOf("47.376900") < gpx.indexOf("47.380000"))
    }

    @Test
    fun `empty segments are dropped and an empty session still yields a valid document`() {
        val gpx = GpxBuilder.build(listOf(emptyList(), listOf(pointA), emptyList()))
        assertEquals(1, gpx.split("<trkseg>").size - 1)
        assertTrue(gpx.contains("</trk>"))
    }

    @Test
    fun `metadata time is the session start and the name is escaped`() {
        val gpx = GpxBuilder.build(
            listOf(listOf(pointA)),
            name = "Lunch & \"run\" <fast>",
            activityType = "RUN",
        )
        assertTrue(gpx.contains("<name>Lunch &amp; &quot;run&quot; &lt;fast&gt;</name>"))
        // <metadata><time> is the session start — here the first accepted fix, written in
        // the strict UTC second-precision shape GPX parsers expect.
        assertTrue(
            gpx.contains(
                "<metadata>\n" +
                    "    <name>Lunch &amp; &quot;run&quot; &lt;fast&gt;</name>\n" +
                    "    <time>2024-09-10T20:26:40Z</time>\n" +
                    "  </metadata>",
            ),
        )
    }

    @Test
    fun `escape covers every xml special character`() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", GpxBuilder.escape("&<>\"'"))
    }

    @Test
    fun `point timestamps without a fix are still written as second precision utc`() {
        assertEquals("1970-01-01T00:00:00Z", GpxBuilder.time(0L))
    }
}
