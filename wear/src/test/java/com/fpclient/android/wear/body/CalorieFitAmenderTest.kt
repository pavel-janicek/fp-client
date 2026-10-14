package com.fpclient.android.wear.body

import com.garmin.fit.Decode
import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import java.io.FileInputStream
import java.nio.file.Files
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirror of `com.fpclient.android.data.body.CalorieFitAmenderTest` in `:app` — the watch copy
 * must stamp a decodable `totalCalories` exactly like the phone copy does.
 */
class CalorieFitAmenderTest {

    @Test
    fun amendFitFileWithCaloriesInsertsTotalCaloriesIntoSessionAndLap() {
        val tempDir = Files.createTempDirectory("fit-amender-test").toFile()
        val originalFitFile = java.io.File(tempDir, "original.fit")
        val amendedFitFile = java.io.File(tempDir, "amended.fit")

        // 1. Write an un-amended FIT file with HR and session duration
        val encoder = FileEncoder(originalFitFile, Fit.ProtocolVersion.V2_0)
        val fileId = FileIdMesg().apply {
            type = File.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            product = 1
            serialNumber = 12345L
            timeCreated = DateTime(Date(1000L))
        }
        encoder.write(fileId)

        // Write 3 record messages with HR 140 bpm
        for (i in 0..2) {
            val record = RecordMesg().apply {
                timestamp = DateTime(Date(1000L + i * 1000L))
                heartRate = 140.toShort()
            }
            encoder.write(record)
        }

        // Write Lap and Session (3600 seconds = 1 hour)
        val lap = LapMesg().apply {
            timestamp = DateTime(Date(3601000L))
            startTime = DateTime(Date(1000L))
            totalElapsedTime = 3600f
            totalTimerTime = 3600f
            sport = Sport.RUNNING
        }
        encoder.write(lap)

        val session = SessionMesg().apply {
            timestamp = DateTime(Date(3601000L))
            startTime = DateTime(Date(1000L))
            totalElapsedTime = 3600f
            totalTimerTime = 3600f
            sport = Sport.RUNNING
        }
        encoder.write(session)
        encoder.close()

        val profile = BodyProfile(
            optIn = true,
            sex = Sex.MALE,
            heightCm = 180.0,
            weightKg = 75.0,
            ageYears = 30,
        )

        // 2. Amend the FIT file using CalorieFitAmender
        val success = CalorieFitAmender.amendFitFileWithCalories(originalFitFile, amendedFitFile, profile)
        assertTrue(success)
        assertTrue(amendedFitFile.isFile)

        // 3. Decode amended FIT file and verify totalCalories
        var decodedSession: SessionMesg? = null
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(SessionMesgListener { mesg -> decodedSession = mesg })

        FileInputStream(amendedFitFile).use { input ->
            decode.read(input, broadcaster)
        }

        assertNotNull(decodedSession)
        val calories = decodedSession?.totalCalories
        assertNotNull(calories)
        assertTrue("Calculated calories should be positive", calories!! > 0)

        tempDir.deleteRecursively()
    }

    @Test
    fun amendFitFileWithCaloriesRejectsAnOptedOutOrIncompleteProfile() {
        val tempDir = Files.createTempDirectory("fit-amender-reject").toFile()
        val originalFitFile = java.io.File(tempDir, "original.fit")
        originalFitFile.writeBytes(byteArrayOf(0x0e, 0x10)) // any bytes; the gate runs before decoding
        val amendedFitFile = java.io.File(tempDir, "amended.fit")

        assertFalse(
            CalorieFitAmender.amendFitFileWithCalories(
                originalFitFile,
                amendedFitFile,
                BodyProfile(optIn = false, heightCm = 180.0, weightKg = 75.0),
            ),
        )
        assertFalse(
            CalorieFitAmender.amendFitFileWithCalories(
                originalFitFile,
                amendedFitFile,
                BodyProfile(optIn = true, weightKg = 75.0),
            ),
        )
        assertFalse(amendedFitFile.isFile)

        tempDir.deleteRecursively()
    }
}
