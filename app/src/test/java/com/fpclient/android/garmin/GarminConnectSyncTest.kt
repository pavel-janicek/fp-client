package com.fpclient.android.garmin

import com.fpclient.android.notifications.WebPushCrypto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminConnectSyncTest {

    /**
     * A blob made by fitpub-garmin-relay's encryptForPhone (one record, record size = content +
     * delimiter + tag) for the RFC 8291 Appendix A recipient keys: the phone must read the
     * relay's metadata with the WebPushCrypto it uses for push.
     */
    @Test
    fun relayBlob_decryptsWithWebPushCrypto() {
        val blob = GarminConnectClient.decodeStandardBase64(
            "DGv6ra1nlYgDCS1FRnbzlwAAAHhBBP4z9KsN6nGRTbVYI/c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A/" +
                "e3chAD7/7ZFj1t6xD+BcLpTSz4egMXJDQvn4Vjb+yB7bZVzQmNHfKU0OojDj/uBlVTeQb9Qylg79swdKIf2u2+lCY8+1rfY+eI+gnUypB/ekbx3If8OBV3d0" +
                "OextvWdtktg6UVzg6kupkFLivFXNo4gjAFfrkkxs=",
        )
        val plain = WebPushCrypto.decrypt(
            blob,
            privateKeyRaw = WebPushCrypto.fromBase64Url("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"),
            publicKeyRaw = WebPushCrypto.fromBase64Url(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            ),
            authSecret = WebPushCrypto.fromBase64Url("BTBZMqHH6r4Tts7J_aSIgg"),
        )
        val meta = Json { ignoreUnknownKeys = true }.decodeFromString<GarminFileMeta>(String(plain))
        assertEquals("24817439695-file", meta.summaryId)
        assertEquals("GPX", meta.fileType)
        assertEquals("Forerunner 55", meta.deviceName)
    }

    @Test
    fun attribution_followsGarminBrandGuidelines() {
        assertEquals("Garmin Forerunner 55", GarminConnectSync.attribution("Forerunner 55"))
        assertEquals("Garmin Fenix 8", GarminConnectSync.attribution("Garmin Fenix 8"))
        assertEquals("Garmin", GarminConnectSync.attribution(null))
        assertEquals("Garmin", GarminConnectSync.attribution("unknown"))
        assertEquals("Garmin", GarminConnectSync.attribution("  "))
    }

    @Test
    fun activityTypes_mapToFitPub() {
        assertEquals("RUN", GarminConnectSync.fitPubActivityType("TRAIL_RUNNING"))
        assertEquals("RUN", GarminConnectSync.fitPubActivityType("ULTRA_RUN"))
        assertEquals("RIDE", GarminConnectSync.fitPubActivityType("GRAVEL_CYCLING"))
        assertEquals("RIDE", GarminConnectSync.fitPubActivityType("E_BIKE_MOUNTAIN"))
        assertEquals("RIDE", GarminConnectSync.fitPubActivityType("ENDURO_MTB"))
        assertEquals("WALK", GarminConnectSync.fitPubActivityType("CASUAL_WALKING"))
        assertEquals("HIKE", GarminConnectSync.fitPubActivityType("HIKING"))
        assertEquals("SWIM", GarminConnectSync.fitPubActivityType("OPEN_WATER_SWIMMING"))
        assertEquals("KAYAKING", GarminConnectSync.fitPubActivityType("KAYAKING_V2"))
        assertEquals("NORDIC_SKI", GarminConnectSync.fitPubActivityType("SKATE_SKIING_WS"))
        assertEquals("WORKOUT", GarminConnectSync.fitPubActivityType("STRENGTH_TRAINING"))
        assertNull(GarminConnectSync.fitPubActivityType("OTHER"))
        assertNull(GarminConnectSync.fitPubActivityType(null))
    }
}
