package com.fpclient.android.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadRejectionTest {

    @Test
    fun theServerRefusingTheFileIsPermanent() {
        listOf(400, 403, 404, 413, 415, 422).forEach { assertTrue("$it", isPermanentUploadRejection(it)) }
    }

    @Test
    fun failuresThatSayNothingAboutTheFileAreTransient() {
        // No status = network failure; 401 = sign in again; 408/429 = try later; 5xx = server trouble.
        listOf(null, 401, 408, 429, 500, 502, 503).forEach { assertFalse("$it", isPermanentUploadRejection(it)) }
    }
}
