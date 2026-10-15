package com.fpclient.android.data.upload

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** A picked or shared file, made ready for the single-activity upload endpoint. */
sealed interface PreparedUpload {
    /** Upload [file] as [name]. */
    data class Ready(val file: File, val name: String) : PreparedUpload

    /** Nothing can be uploaded; [message] tells the user why. The source file is already gone. */
    data class Rejected(val message: String) : PreparedUpload
}

/**
 * Marks an upload refused on the device (nothing was sent): the UI shows the message and drops
 * the selected file instead of offering to upload it again.
 */
class RejectedUploadFileException(message: String) : Exception(message)

/**
 * Unwraps an activity delivered inside a ZIP. Garmin Connect's web "Export original" downloads a
 * ZIP holding one FIT file, and Garmin offers no other way to get a recording out of its app
 * without API access (Garmin's developer API is closed to new applicants). With this, "Export
 * original" → "Open with FP Client" uploads the activity directly.
 *
 * The ZIP is recognised by its content (`PK` signature), not its name: shared content URIs often
 * carry no usable file name. A ZIP is always deleted once inspected — it never stays in the app,
 * whether it yielded an activity or was rejected. Pure JVM so it can be unit-tested.
 */
object ActivityArchive {

    /** Activity formats the FitPub upload endpoint parses. */
    val SUPPORTED_EXTENSIONS = setOf("fit", "gpx", "tcx")

    /** Far above any real single activity, low enough that a crafted ZIP cannot fill the disk. */
    const val MAX_ACTIVITY_BYTES = 64L * 1024 * 1024

    /** Stops scanning absurd archives early. */
    const val MAX_ENTRIES = 10_000

    const val MESSAGE_NO_ACTIVITY =
        "This ZIP file contains no FIT, GPX or TCX activity, so nothing was uploaded."
    const val MESSAGE_ONLY_NESTED_ARCHIVES =
        "This ZIP file only contains other ZIP files, so nothing was uploaded. For a full Garmin " +
            "account export, unzip it and upload the inner ZIP files under Settings → Batch import activities."
    const val MESSAGE_UNREADABLE = "This ZIP file could not be read, so nothing was uploaded."
    val MESSAGE_TOO_LARGE =
        "The activity in this ZIP file is larger than ${MAX_ACTIVITY_BYTES / (1024 * 1024)} MB, so nothing was uploaded."

    fun messageMultiple(count: Int) =
        "This ZIP file contains $count activities, so nothing was uploaded. To upload them all at once, " +
            "use Settings → Batch import activities."

    /** True when [file] starts with a ZIP local-file or empty-archive signature. */
    fun isZip(file: File): Boolean {
        val header = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        if (read < 4 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) return false
        return (header[2] == 3.toByte() && header[3] == 4.toByte()) || // local file header
            (header[2] == 5.toByte() && header[3] == 6.toByte()) // empty archive
    }

    /**
     * Returns [file] untouched when it is not a ZIP. For a ZIP: extracts its single supported
     * activity into [workDir] and deletes the ZIP; rejects (and deletes) a ZIP with none, with
     * several, with an oversized one, or that cannot be read.
     */
    fun prepare(
        file: File,
        name: String,
        workDir: File,
        maxActivityBytes: Long = MAX_ACTIVITY_BYTES,
    ): PreparedUpload {
        if (!isZip(file)) return PreparedUpload.Ready(file, name)
        // Created before reading so a failure mid-extraction still knows what to clean up.
        val scan = Scan()
        var handedOut = false
        return try {
            scan(file, workDir, maxActivityBytes, scan)
            val extracted = scan.extracted
            when {
                scan.tooLarge -> PreparedUpload.Rejected(MESSAGE_TOO_LARGE)
                scan.activities > 1 -> PreparedUpload.Rejected(messageMultiple(scan.activities))
                scan.activities == 1 && extracted != null -> {
                    handedOut = true
                    PreparedUpload.Ready(extracted, scan.extractedName ?: extracted.name)
                }
                scan.nestedArchives > 0 -> PreparedUpload.Rejected(MESSAGE_ONLY_NESTED_ARCHIVES)
                else -> PreparedUpload.Rejected(MESSAGE_NO_ACTIVITY)
            }
        } catch (_: IOException) {
            PreparedUpload.Rejected(MESSAGE_UNREADABLE)
        } catch (_: IllegalArgumentException) {
            // ZipInputStream throws this for malformed entry names.
            PreparedUpload.Rejected(MESSAGE_UNREADABLE)
        } finally {
            // Anything not handed out as Ready is discarded, and the ZIP itself always is.
            if (!handedOut) scan.extracted?.delete()
            file.delete()
        }
    }

    private class Scan {
        var activities = 0
        var nestedArchives = 0
        var tooLarge = false
        var extracted: File? = null
        var extractedName: String? = null
    }

    private fun scan(zipFile: File, workDir: File, maxActivityBytes: Long, scan: Scan) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) break
                if (entry.isDirectory) continue
                val path = entry.name.replace('\\', '/')
                // Entry names are only inspected, never used as a path on disk.
                val baseName = path.substringAfterLast('/')
                if (baseName.isBlank() || baseName.startsWith('.') || path.startsWith("__MACOSX/")) continue
                when (baseName.substringAfterLast('.', "").lowercase()) {
                    in SUPPORTED_EXTENSIONS -> {
                        scan.activities++
                        // Only the first is extracted; any further one means rejection anyway.
                        if (scan.activities == 1) {
                            workDir.mkdirs()
                            val target = File(workDir, "upload_${System.nanoTime()}_${sanitize(baseName)}")
                            scan.extracted = target
                            scan.extractedName = baseName
                            if (!copyBounded(zip, target, maxActivityBytes)) {
                                scan.tooLarge = true
                                return
                            }
                        }
                    }
                    "zip" -> scan.nestedArchives++
                }
            }
        }
    }

    /** Copies the current entry to [target]; false when it exceeds [limit] bytes. */
    private fun copyBounded(zip: ZipInputStream, target: File, limit: Long): Boolean {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        target.outputStream().use { out ->
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return false
                out.write(buffer, 0, read)
            }
        }
        return true
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100)
}
