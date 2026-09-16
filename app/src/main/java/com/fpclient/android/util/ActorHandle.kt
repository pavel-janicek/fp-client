package com.fpclient.android.util

/**
 * Helpers for ActivityPub actor identities. FitPub is federated, so an actor can live
 * anywhere — the app must carry the full `@username@instance` handle (e.g.
 * `@starfrosch@fitpub.social`) instead of assuming every account is local to the
 * currently configured instance.
 */
object ActorHandle {

    /** Extracts a host from an OpenID/ActivityPub actor URI, e.g. `fitpub.social`. */
    fun hostOf(actorUri: String?): String? {
        if (actorUri.isNullOrBlank()) return null
        val withoutScheme = actorUri.substringAfter("://", actorUri)
        val host = withoutScheme
            .substringBefore('/')
            .substringBefore(':')
            .substringBefore('@')
            .trim()
        return host.takeIf { it.isNotBlank() }
    }

    /**
     * Builds the canonical full handle `@username@host`.
     *   - `ActorHandle.full("starfrosch", "https://fitpub.social/users/starfrosch")`
     *     -> `@starfrosch@fitpub.social`
     *   - `ActorHandle.full("paveljanicek", "https://makni.cz/users/paveljanicek")`
     *     -> `@paveljanicek@makni.cz`
     *   - local actor without an actor URI -> `@username`.
     */
    fun full(username: String?, actorUri: String? = null, handle: String? = null, domain: String? = null): String? {
        val name = username?.trim()?.trimStart('@')?.takeIf { it.isNotBlank() } ?: return null
        val host = when {
            !domain.isNullOrBlank() -> domain.trim().trimStart('@').takeIf { it.isNotBlank() }
            !handle.isNullOrBlank() && handle.contains('@') ->
                handle.trimStart('@').substringAfterLast('@').takeIf { it.isNotBlank() }
            else -> hostOf(actorUri)
        }
        return if (host.isNullOrBlank()) "@$name" else "@$name@$host"
    }

    /** True when the value is a full federated handle (`@user@host` / `user@host`). */
    fun isFullHandle(handle: String?): Boolean {
        val body = handle?.trim()?.removePrefix("@") ?: return false
        return body.contains('@') && body.substringAfterLast('@').isNotBlank()
    }

    /** Local username part of a handle or plain username (`@user@host` -> `user`). */
    fun localPart(handle: String?): String {
        val body = handle?.trim()?.removePrefix("@") ?: ""
        return if (body.contains('@')) body.substringBefore('@') else body
    }

    /**
     * Normalizes a follow target for the server's `POST/DELETE /users/{username}/follow`
     * and `follow-status` endpoints. The server only accepts PLAIN usernames for local
     * users — any `user@host` value is classified as a federated handle and routed into
     * WebFinger discovery, which fails with "user not found" for accounts that actually
     * live on our own instance. So:
     *  - `alice` (or `@alice`) + any serverUrl            -> `alice`
     *  - `@alice@fitpub.example` + serverUrl on same host -> `alice`
     *  - `@alice@other.example`                           -> `@alice@other.example`
     *       (kept as a handle: genuinely remote targets go through the remote branch)
     * Returns null for blank input.
     */
    fun normalizeToUsername(handle: String?, serverUrl: String?): String? {
        if (handle.isNullOrBlank()) return null
        val trimmed = handle.trim().removePrefix("@")
        if (!trimmed.contains('@')) return trimmed.takeIf { it.isNotBlank() }
        val local = trimmed.substringBefore('@')
        val host = hostOf("https://${trimmed.substringAfter('@')}")
        val ownHost = hostOf(serverUrl)
        return if (local.isNotBlank() && host != null && ownHost != null &&
            host.equals(ownHost, ignoreCase = true)
        ) {
            local
        } else {
            "@$local@$host".takeIf { local.isNotBlank() && host != null }
        }
    }
}