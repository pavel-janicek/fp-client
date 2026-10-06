package com.fpclient.android.wear.recording

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutTrackStore(private val directory: File) {
    private var writer: BufferedWriter? = null
    private var openSessionId: Long? = null

    fun fileFor(sessionId: Long): File = File(directory.apply { mkdirs() }, "$PREFIX$sessionId.$EXTENSION")

    @Synchronized
    fun append(sessionId: Long, event: WorkoutTrackEvent) {
        val output = writer?.takeIf { openSessionId == sessionId } ?: newWriter(sessionId)
        output.write(encodeEvent(event))
        output.newLine()
        output.flush()
    }

    @Synchronized
    fun readEvents(sessionId: Long): List<WorkoutTrackEvent> {
        val file = fileFor(sessionId)
        if (!file.exists()) return emptyList()
        return file.useLines { lines -> lines.mapNotNull(::decodeEvent).toList() }
    }

    @Synchronized
    fun close() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        openSessionId = null
    }

    private fun newWriter(sessionId: Long): BufferedWriter {
        close()
        return BufferedWriter(OutputStreamWriter(FileOutputStream(fileFor(sessionId), true), Charsets.UTF_8))
            .also {
                writer = it
                openSessionId = sessionId
            }
    }

    companion object {
        private const val PREFIX = "workout-"
        private const val EXTENSION = "jsonl"
        private val lineJson = Json { ignoreUnknownKeys = true }

        fun encodeEvent(event: WorkoutTrackEvent): String = lineJson.encodeToString(event)

        fun decodeEvent(line: String): WorkoutTrackEvent? =
            runCatching { lineJson.decodeFromString<WorkoutTrackEvent>(line) }.getOrNull()
    }
}