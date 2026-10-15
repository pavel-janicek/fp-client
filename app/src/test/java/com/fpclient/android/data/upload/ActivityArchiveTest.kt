package com.fpclient.android.data.upload

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivityArchiveTest {

    private lateinit var dir: File
    private lateinit var workDir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("activity-archive").toFile()
        workDir = File(dir, "work")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val fitBytes = byteArrayOf(14, 16, 0x10, 0x08, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())
    private val gpxBytes = "<?xml version=\"1.0\"?><gpx version=\"1.1\"></gpx>".toByteArray()

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (entryName, bytes) ->
                out.putNextEntry(ZipEntry(entryName))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    /** Nothing extracted may survive a rejection. */
    private fun assertWorkDirEmpty() {
        assertTrue(workDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun garminExportOriginalZipYieldsItsFitAndIsDiscarded() {
        val source = zip("21234567890.zip", "21234567890_ACTIVITY.fit" to fitBytes)

        val prepared = ActivityArchive.prepare(source, "21234567890.zip", workDir)

        prepared as PreparedUpload.Ready
        assertEquals("21234567890_ACTIVITY.fit", prepared.name)
        assertArrayEquals(fitBytes, prepared.file.readBytes())
        assertFalse("the ZIP must not stay in the app", source.exists())
    }

    @Test
    fun aGpxInsideAZipIsUploadedToo() {
        val source = zip("route.zip", "tracks/morning walk.gpx" to gpxBytes)

        val prepared = ActivityArchive.prepare(source, "route.zip", workDir)

        prepared as PreparedUpload.Ready
        assertEquals("morning walk.gpx", prepared.name)
        assertArrayEquals(gpxBytes, prepared.file.readBytes())
        assertFalse(source.exists())
    }

    @Test
    fun oneGpxAmongOtherFilesIsUploadedAndTheRestIgnored() {
        // Five files, only one of them an activity.
        val source = zip(
            "mixed.zip",
            "summary.json" to "{\"id\":1}".toByteArray(),
            "notes.txt" to "hello".toByteArray(),
            "data/track.gpx" to gpxBytes,
            "data/metadata.json" to "{}".toByteArray(),
            "readme.txt" to "read me".toByteArray(),
        )

        val prepared = ActivityArchive.prepare(source, "mixed.zip", workDir)

        prepared as PreparedUpload.Ready
        assertEquals("track.gpx", prepared.name)
        assertArrayEquals(gpxBytes, prepared.file.readBytes())
        assertFalse(source.exists())
        // Only the activity was extracted; the other four never touched the disk.
        assertEquals(listOf(prepared.file), workDir.listFiles()!!.toList())
    }

    @Test
    fun aZipWithoutAnActivityIsRejectedAndDiscarded() {
        val source = zip("photos.zip", "readme.txt" to "hi".toByteArray(), "pic.jpg" to byteArrayOf(1, 2, 3))

        val prepared = ActivityArchive.prepare(source, "photos.zip", workDir)

        assertEquals(PreparedUpload.Rejected(ActivityArchive.MESSAGE_NO_ACTIVITY), prepared)
        assertFalse(source.exists())
        assertWorkDirEmpty()
    }

    @Test
    fun anEmptyZipIsRejected() {
        val source = zip("empty.zip")

        assertEquals(PreparedUpload.Rejected(ActivityArchive.MESSAGE_NO_ACTIVITY), ActivityArchive.prepare(source, "empty.zip", workDir))
        assertFalse(source.exists())
    }

    @Test
    fun severalActivitiesPointToBatchImportInsteadOfPickingOne() {
        val source = zip("two.zip", "a.fit" to fitBytes, "b.gpx" to gpxBytes)

        val prepared = ActivityArchive.prepare(source, "two.zip", workDir)

        assertEquals(PreparedUpload.Rejected(ActivityArchive.messageMultiple(2)), prepared)
        assertFalse(source.exists())
        assertWorkDirEmpty()
    }

    @Test
    fun aFullGarminAccountExportExplainsTheInnerZips() {
        // Garmin's account export nests the activity ZIPs one level down.
        val inner = File(dir, "inner.zip").also { f ->
            ZipOutputStream(f.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("1_ACTIVITY.fit")); out.write(fitBytes); out.closeEntry()
            }
        }
        val source = zip(
            "export.zip",
            "DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip" to inner.readBytes(),
            "DI_CONNECT/DI-Connect-User/user_profile.json" to "{}".toByteArray(),
        )

        val prepared = ActivityArchive.prepare(source, "export.zip", workDir)

        assertEquals(PreparedUpload.Rejected(ActivityArchive.MESSAGE_ONLY_NESTED_ARCHIVES), prepared)
        assertFalse(source.exists())
    }

    @Test
    fun macOsMetadataAndHiddenFilesAreIgnored() {
        val source = zip(
            "mac.zip",
            "__MACOSX/._ride.fit" to byteArrayOf(0, 5, 22),
            ".hidden.gpx" to gpxBytes,
            "ride.fit" to fitBytes,
        )

        val prepared = ActivityArchive.prepare(source, "mac.zip", workDir)

        prepared as PreparedUpload.Ready
        assertEquals("ride.fit", prepared.name)
    }

    @Test
    fun aCorruptZipIsRejectedAndDiscarded() {
        val source = File(dir, "broken.zip")
        // Valid local-header signature, then garbage.
        source.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(40) { 0x7f })

        val prepared = ActivityArchive.prepare(source, "broken.zip", workDir)

        assertTrue(prepared is PreparedUpload.Rejected)
        assertFalse(source.exists())
        assertWorkDirEmpty()
    }

    @Test
    fun anOversizedActivityIsRejectedWithoutLeavingAPartialFile() {
        val source = zip("big.zip", "big.fit" to ByteArray(2_000))

        val prepared = ActivityArchive.prepare(source, "big.zip", workDir, maxActivityBytes = 1_000)

        assertEquals(PreparedUpload.Rejected(ActivityArchive.MESSAGE_TOO_LARGE), prepared)
        assertFalse(source.exists())
        assertWorkDirEmpty()
    }

    @Test
    fun aPlainActivityFilePassesThroughUntouched() {
        val source = File(dir, "msf:2323").apply { writeBytes(fitBytes) }

        val prepared = ActivityArchive.prepare(source, "msf:2323", workDir)

        assertEquals(PreparedUpload.Ready(source, "msf:2323"), prepared)
        assertTrue("a non-ZIP upload is not the archive helper's to delete", source.exists())
    }

    @Test
    fun zipDetectionUsesTheContentNotTheName() {
        assertTrue(ActivityArchive.isZip(zip("no-extension", "a.fit" to fitBytes)))
        assertFalse(ActivityArchive.isZip(File(dir, "fake.zip").apply { writeBytes(gpxBytes) }))
        assertFalse(ActivityArchive.isZip(File(dir, "tiny").apply { writeBytes(byteArrayOf(1)) }))
    }
}
