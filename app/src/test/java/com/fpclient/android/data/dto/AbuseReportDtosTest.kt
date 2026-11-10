package com.fpclient.android.data.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbuseReportDtosTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun requestMatchesTheServerSubmissionRecord() {
        val body = json.encodeToString(AbuseReportRequest(ReportTargetKind.REMOTE_ACTIVITY, "mirror-id", "Spam"))
        assertEquals("""{"kind":"REMOTE_ACTIVITY","targetId":"mirror-id","reason":"Spam"}""", body)
    }

    @Test
    fun eligibilityDefaultsToNo() {
        assertTrue(json.decodeFromString<AbuseReportEligibilityDto>("""{"eligible":true}""").eligible)
        assertFalse(json.decodeFromString<AbuseReportEligibilityDto>("{}").eligible)
    }
}
