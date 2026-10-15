package com.fpclient.android.wear

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logging for the watch app.
 *
 * - Release: warnings and errors only, to logcat. Nothing chatty on a battery-bound device.
 * - Debug: everything, to logcat *and* to rotating files on the watch ([LOG_DIRECTORY]). The
 *   logcat ring buffer on the watch is overwritten within minutes, so a workout's log was gone by
 *   the time the watch was back on adb. Pull the files with
 *   `adb exec-out run-as com.fpclient.android tar c files/logs | tar x`.
 */
object WearLog {
    private const val LOG_DIRECTORY = "logs"
    private const val FILE_PREFIX = "wear-log"
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private const val MAX_FILES = 5

    /** File logging (and verbose levels) only exist in debug builds. */
    val verbose: Boolean = BuildConfig.DEBUG

    private val writer = Executors.newSingleThreadExecutor { Thread(it, "WearLog").apply { isDaemon = true } }
    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var directory: File? = null

    /** Called once from [FitPubWearApp]; also installs a crash handler that logs the stack trace. */
    fun init(context: Context) {
        if (!verbose || directory != null) return
        directory = File(context.filesDir, LOG_DIRECTORY).apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Synchronous: the process dies right after this handler returns.
            runCatching { appendLine(format('E', "AndroidRuntime", "FATAL in ${thread.name}", error)) }
            previous?.uncaughtException(thread, error)
        }
        i("WearLog", "--- process start, version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ---")
    }

    fun d(tag: String, message: String) {
        if (!verbose) return
        Log.d(tag, message)
        toFile('D', tag, message, null)
    }

    fun i(tag: String, message: String) {
        if (!verbose) return
        Log.i(tag, message)
        toFile('I', tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        toFile('W', tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        toFile('E', tag, message, error)
    }

    private fun toFile(level: Char, tag: String, message: String, error: Throwable?) {
        if (directory == null) return
        val line = format(level, tag, message, error)
        writer.execute { runCatching { appendLine(line) } }
    }

    private fun format(level: Char, tag: String, message: String, error: Throwable?): String {
        val time = synchronized(timestamp) { timestamp.format(Date()) }
        val trace = error?.let { StringWriter().also { sw -> it.printStackTrace(PrintWriter(sw)) }.toString() }
        return buildString {
            append(time).append(' ').append(level).append(' ').append(tag).append(": ").append(message)
            if (trace != null) append('\n').append(trace.trimEnd())
        }
    }

    @Synchronized
    private fun appendLine(line: String) {
        val dir = directory ?: return
        val current = File(dir, "$FILE_PREFIX.txt")
        if (current.length() > MAX_FILE_BYTES) rotate(dir)
        current.appendText(line + "\n")
    }

    /** wear-log.txt → wear-log.1.txt → … → wear-log.(MAX_FILES-1).txt, oldest dropped. */
    private fun rotate(dir: File) {
        File(dir, "$FILE_PREFIX.${MAX_FILES - 1}.txt").delete()
        for (index in MAX_FILES - 2 downTo 1) {
            File(dir, "$FILE_PREFIX.$index.txt").renameTo(File(dir, "$FILE_PREFIX.${index + 1}.txt"))
        }
        File(dir, "$FILE_PREFIX.txt").renameTo(File(dir, "$FILE_PREFIX.1.txt"))
    }
}
