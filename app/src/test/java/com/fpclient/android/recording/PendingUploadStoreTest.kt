package com.fpclient.android.recording

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pending-upload registry tests (Iteration 8d): the file-backed list that makes a recorded
 * workout survive a process death and a failed upload. Plain JVM, so it runs against a
 * temporary file — the same JSON the app writes to filesDir.
 */
class PendingUploadStoreTest {

    private fun newStore(): PendingUploadStore =
        PendingUploadStore(Files.createTempFile("pending", ".json").toFile().also { it.delete() })

    private fun entry(sessionId: Long, createdAt: Long = sessionId) = PendingUpload(
        sessionId = sessionId,
        startedAtEpochMs = sessionId,
        activityType = "RIDE",
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `upsert reads back and replaces by session id`() {
        val store = newStore()
        store.upsert(entry(1L))
        store.upsert(entry(2L))
        assertEquals(listOf(1L, 2L), store.all().map { it.sessionId })

        store.upsert(entry(1L).copy(activityType = "HIKE"))
        assertEquals(2, store.all().size)
        assertEquals("HIKE", store.get(1L)?.activityType)
    }

    @Test
    fun `remove drops exactly one entry and is safe for unknown ids`() {
        val store = newStore()
        store.upsert(entry(1L))
        store.upsert(entry(2L))
        store.remove(1L)
        store.remove(99L)
        assertEquals(listOf(2L), store.all().map { it.sessionId })
        assertNull(store.get(1L))
    }

    @Test
    fun `markFailed counts attempts and remembers the metadata the user chose`() {
        val store = newStore()
        store.upsert(entry(5L))
        store.markFailed(5L, "Network unreachable", "Morning run", "felt great", "PRIVATE", "RUN")
        store.markFailed(5L, "Still offline", null, null, null, "RUN")

        val saved = store.get(5L)!!
        assertEquals(2, saved.attempts)
        assertEquals("Still offline", saved.lastError)
        // Nulls must not erase what the earlier attempt stored — the retry replays it.
        assertEquals("Morning run", saved.title)
        assertEquals("felt great", saved.description)
        assertEquals("PRIVATE", saved.visibility)
    }

    @Test
    fun `a missing or corrupt file reads as an empty list`() {
        val store = newStore()
        assertTrue(store.all().isEmpty())

        val file = Files.createTempFile("pending", ".json").toFile()
        file.writeText("{not json at all")
        val corrupt = PendingUploadStore(file)
        assertTrue(corrupt.all().isEmpty())
        // The store recovers on the next write instead of staying broken.
        corrupt.upsert(entry(3L))
        assertEquals(listOf(3L), corrupt.all().map { it.sessionId })
        file.delete()
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val file = Files.createTempFile("pending", ".json").toFile()
        file.writeText(
            """[{"sessionId":7,"startedAtEpochMs":7,"activityType":"RUN","createdAtEpochMs":7,"future":"x"}]""",
        )
        assertEquals(7L, PendingUploadStore(file).all().single().sessionId)
        file.delete()
    }
}
