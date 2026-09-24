package com.fpclient.android.notifications

/**
 * The encrypted push fixture **produced by the FitPub server's own code**: captured from
 * `fitpub-push-relay/testdata/fixture.json`, which `FixtureGen.java` generates by invoking
 * `social.fitpub.push.WebPushService.encrypt` (RFC 8291) via reflection in the fitpub
 * repository — nothing about the crypto is reimplemented there or here, so these bytes are
 * byte-for-byte what `WebPushService.sendPush` hands to a push endpoint. Regenerate per
 * `fitpub-push-relay/README.md` when the server's encryption ever changes.
 */
object PushFixture {

    /** The plaintext payload WebPushService.buildPayload serialised for an ACTIVITY_SHARED row. */
    const val PAYLOAD =
        """{"title":"FitPub","body":"Alice boosted Morning Run","icon":"/img/fitpub-logo-256.png",""" +
            """"tag":"fitpub-activity_shared","url":"/activities/5f0e7b2c-9d31-4a6e-8c5f-2b7e4d1a9c03"}"""

    /** Recipient public key ("p256dh"), base64url without padding. */
    const val P256DH = "BHGMnBUiADL9lMjWSSfSAsnVnkKJGjlPjdtGyCdqIyVaqanVyvz5JM1We5F7C8u-3h1HsM86-voi1vLfgjZodb4"

    /** 16-byte auth secret, base64url without padding (0x00..0x0f). */
    const val AUTH = "AAECAwQFBgcICQoLDA0ODw"

    /** The matching 32-byte private scalar, base64url without padding. */
    const val PRIVATE = "iEbe96orqut9otVpyS9Z4Y8W5E38ALpUO_PwbbgceaM"

    /** The aes128gcm blob exactly as the relay stores it (base64url encoding of the bytes). */
    const val BLOB =
        "3NkGgNIEU22Xq1DZF7Sd5AAAEABBBAEwwrOWSqYEL96a7vtTBJj0g_xqctSCpVnrBaJsdgsAuzoTE61ympNTmH-qJK3HvNz1KYWK1b7LssO1YfdXITEEBRIiA2aZRrbGzApgJeIJLOtuVrAeg18-9WEsyQbPyW5TivbvRJo4uS-xbbV8EIoWL1lMBKxN6vpN7qofRraVCeiUt0fNjYJzn6AiF1KOqb_fLnE6dqdHFMo8bPyYrvNVfqS6BXTVoy2HKNyfYh-gWEBj_U1HXwUn8Iin6p2Zvn5UTXnUriXr7EaCcBdFdFJIDv1DIicO-gLRs4sSEJ9XV24f9GQY_YaG3FU5dpXOdw_shlKt4uDzQdSWj-HHeDw"
}
