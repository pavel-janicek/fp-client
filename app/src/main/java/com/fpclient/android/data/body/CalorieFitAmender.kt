package com.fpclient.android.data.body

import com.garmin.fit.Decode
import com.garmin.fit.FileEncoder
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.MesgListener
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import java.io.FileInputStream
import java.io.File as JavaFile

object CalorieFitAmender {

    /**
     * Reads [inputFitFile], calculates total calories using [bodyProfile] and HR/duration data,
     * amends [SessionMesg] and [LapMesg] with totalCalories, and encodes the amended FIT file to [outputFitFile].
     *
     * Returns true if successful.
     */
    fun amendFitFileWithCalories(
        inputFitFile: JavaFile,
        outputFitFile: JavaFile,
        bodyProfile: BodyProfile,
    ): Boolean {
        if (!inputFitFile.isFile || !bodyProfile.optIn || !bodyProfile.isComplete) return false

        val hrList = mutableListOf<Int>()
        var sessionDurationSec = 0.0
        var sessionActivityType = "GENERIC"

        val decodeFirstPass = Decode()
        val broadcasterFirstPass = MesgBroadcaster(decodeFirstPass)

        broadcasterFirstPass.addListener(RecordMesgListener { mesg ->
            mesg.heartRate?.toInt()?.takeIf { it in 1..300 }?.let { hrList.add(it) }
        })
        broadcasterFirstPass.addListener(SessionMesgListener { mesg ->
            mesg.totalTimerTime?.toDouble()?.let { sessionDurationSec = it }
            mesg.sport?.name?.let { sessionActivityType = it }
        })

        runCatching {
            FileInputStream(inputFitFile).use { input ->
                decodeFirstPass.read(input, broadcasterFirstPass)
            }
        }.onFailure { return false }

        val avgHr = if (hrList.isNotEmpty()) hrList.average() else null
        val totalCalories = CalorieCalculator.calculateCalories(
            avgHr = avgHr,
            durationSeconds = sessionDurationSec,
            activityType = sessionActivityType,
            bodyProfile = bodyProfile,
        )

        outputFitFile.parentFile?.mkdirs()
        val encoder = FileEncoder(outputFitFile, Fit.ProtocolVersion.V2_0)

        val decodeSecondPass = Decode()
        val broadcasterSecondPass = MesgBroadcaster(decodeSecondPass)

        broadcasterSecondPass.addListener(MesgListener { mesg ->
            when (mesg.num) {
                MesgNum.SESSION -> {
                    val sessionMesg = SessionMesg(mesg)
                    sessionMesg.totalCalories = totalCalories
                    encoder.write(sessionMesg)
                }
                MesgNum.LAP -> {
                    val lapMesg = LapMesg(mesg)
                    lapMesg.totalCalories = totalCalories
                    encoder.write(lapMesg)
                }
                else -> {
                    encoder.write(mesg)
                }
            }
        })

        val success = runCatching {
            FileInputStream(inputFitFile).use { input ->
                decodeSecondPass.read(input, broadcasterSecondPass)
            }
            encoder.close()
            true
        }.getOrDefault(false)

        return success && outputFitFile.isFile && outputFitFile.length() > 0
    }
}
