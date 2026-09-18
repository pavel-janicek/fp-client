package com.fpclient.android.recording

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the append-only track file. The store is plain java.io, so these
 * run on the JVM against a temporary directory — the same encode/decode path the service
 * uses to persist fixes and (after a process death) to rebuild stats from.
 */
class TrackPointStoreTest {

    private fun newStore(): TrackPointStore =
        TrackPointStore(Files.createTempDirectory("trackpoints").toFile())

    @Test
    fun `append and read all round trips every point in order`() {
        val store = newStore()
        val session = 1_726_000_000_000L
        val points = listOf(
            TrackPoint(47.3769, 8.5417, 400.0, 1_726_000_000_000L, 5.0),
            TrackPoint(47.3800, 8.5450, 405.5, 1_726_000_002_000L, 8.2),
            TrackPoint(47.3830, 8.5490, 412.25, 1_726_000_004_000L, 12.0),
        )
        points.forEach { store.append(session, it) }
        store.closeWriter()
        assertEquals(points, store.readAll(session))
        assertEquals(points.last(), store.readLast(session))
    }

    @Test
    fun `reads survive process death because every fix is flushed immediately`() {
        val dir = Files.createTempDirectory("trackpoints").toFile()
        val session = 1_726_000_001_000L
        // Simulate a dying process: write, then "crash" without a graceful close.
        TrackPointStore(dir).append(session, TrackPoint(10.0, 20.0, 300.0, 1L, 4.0))
        TrackPointStore(dir).append(session, TrackPoint(10.001, 20.001, 301.0, 3_000L, 4.0))
        // A brand-new service instance reads the same file:
        assertEquals(2, TrackPointStore(dir).readAll(session).size)
        dir.deleteRecursively()
    }

    @Test
    fun `sessions are isolated by their start epoch`() {
        val store = newStore()
        store.append(1L, TrackPoint(1.0, 1.0, 0.0, 1L, 1.0))
        store.append(2L, TrackPoint(2.0, 2.0, 0.0, 2L, 1.0))
        store.closeWriter()
        assertEquals(1, store.readAll(1L).size)
        assertEquals(1, store.readAll(2L).size)
        assertEquals(2.0, store.readAll(2L).first().lat, 1e-9)
    }

    @Test
    fun `empty and missing sessions read as empty lists`() {
        val store = newStore()
        assertTrue(store.readAll(42L).isEmpty())
        assertNull(store.readLast(42L))
    }

    @Test
    fun `a torn final line after process death is skipped instead of crashing`() {
        val dir = Files.createTempDirectory("trackpoints").toFile()
        val session = 99L
        val store = TrackPointStore(dir)
        store.append(session, TrackPoint(1.0, 2.0, 3.0, 4L, 5.0))
        store.closeWriter()
        // Simulate a torn write: append half a line without a newline.
        store.fileFor(session).appendText("""{"lat":9.9,"lon":8.""")
        assertEquals(1, store.readAll(session).size)
        dir.deleteRecursively()
    }

    @Test
    fun `line codec round trips and tolerates unknown fields`() {
        val point = TrackPoint(-33.8688, 151.2093, 58.0, 1_726_000_009_000L, 3.5)
        val line = TrackPointStore.encodeLine(point)
        // Unknown extra keys must be ignored (forward compatibility across versions).
        assertTrue(TrackPointStore.decodeLine(line.dropLast(1) + ""","extra":"x"}""") == point)
        assertEquals(point, TrackPointStore.decodeLine(line))
        assertNull(TrackPointStore.decodeLine(""))
        assertNull(TrackPointStore.decodeLine("   "))
        assertNull(TrackPointStore.decodeLine("not json at all"))
        assertNull(TrackPointStore.decodeLine("""{"lat":"NaN"}"""))
    }

    @Test
    fun `pause and resume markers split the track into one segment per paused stretch`() {
        val store = newStore()
        val session = 8L
        val first = TrackPoint(1.0, 1.0, 0.0, 1L, 1.0)
        val second = TrackPoint(2.0, 2.0, 0.0, 2L, 1.0)
        val third = TrackPoint(3.0, 3.0, 0.0, 3L, 1.0)
        store.append(session, first)
        store.appendMarker(session, TrackPointStore.MARKER_PAUSE)
        store.appendMarker(session, TrackPointStore.MARKER_RESUME)
        store.append(session, second)
        store.append(session, third)
        store.closeWriter()

        val segments = store.readSegments(session)
        assertEquals(listOf(listOf(first), listOf(second, third)), segments)
        // Markers are not fixes: stats-relevant point reads stay unaffected.
        assertEquals(3, store.readAll(session).size)
    }

    @Test
    fun `a session without markers reads as a single segment while a paused-only one reads empty`() {
        val store = newStore()
        val point = TrackPoint(1.0, 1.0, 0.0, 1L, 1.0)
        store.append(1L, point)
        store.appendMarker(1L, TrackPointStore.MARKER_PAUSE)
        store.closeWriter()
        assertEquals(listOf(listOf(point)), store.readSegments(1L))
        assertEquals(emptyList<List<TrackPoint>>(), store.readSegments(2L))
    }

    @Test
    fun `delete removes the session file`() {
        val store = newStore()
        val session = 7L
        store.append(session, TrackPoint(1.0, 1.0, 0.0, 1L, 1.0))
        assertTrue(store.fileFor(session).exists())
        store.delete(session)
        assertTrue(!store.fileFor(session).exists())
        assertTrue(store.readAll(session).isEmpty())
    }
}
