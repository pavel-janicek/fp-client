package com.fpclient.android.data.network

import android.content.Context
import com.fpclient.android.BuildConfig
import com.fpclient.android.data.session.Session
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * Builds and owns the Retrofit [FitPubApi]. Two interceptors make the client work with
 * arbitrary FitPub instances:
 *  - A base-URL interceptor rewrites every request to the user's configured server.
 *  - A session interceptor authenticates via the `JWT_TOKEN` cookie the server sets (since
 *    FitPub 1.3 the JWT is neither in the response body nor accepted as a Bearer header)
 *    and echoes the `XSRF-TOKEN` cookie plus `X-XSRF-TOKEN` header Spring's CSRF
 *    protection requires on every mutating call.
 *
 * CSRF tokens are minted by the server only on requests that actually render the token
 * (Spring Security defers the token, so plain JSON API GETs never emit the cookie). The
 * client therefore primes its token with a throwaway GET of the public `/login` page,
 * keeps one token per configured instance, and retries a mutating call once with a fresh
 * token if the server answers `403 Forbidden` (stale or missing CSRF proof).
 */
class ApiClient(
    context: Context,
    private val sessionStore: SessionStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun resolveUrl(rawUrl: String): String =
        rawUrl.ifBlank { "https://fitpub.invalid" }

    /** CSRF tokens per configured instance (scheme+host+port), so switching servers never
     * reuses a token minted by another instance. */
    private val csrfTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun csrfKey(base: HttpUrl): String = "${base.scheme}://${base.host}:${base.port}"

    /** Reads the current session and rewrites the outgoing URL + (re)attaches the session
     * JWT cookie and CSRF token. Also captures the latest `XSRF-TOKEN` from each response so
     * the next mutating call is allowed through. */
    private val sessionInterceptor = Interceptor { chain ->
        val session: Session = withSession()
        val original = chain.request()
        val originalUrl = original.url
        val base = resolveUrl(session.serverUrl).toHttpUrlOrNull() ?: originalUrl
        val key = csrfKey(base)

        val method = original.method
        val mutating = method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE"

        // Mutating requests need a CSRF token; prime one with a throwaway GET if we have none.
        var token: String? = csrfTokens[key]
        if (token == null && mutating) {
            token = primeCsrfToken(base)
            if (token != null) csrfTokens[key] = token
        }

        var response = chain.proceed(signed(original, base, session, token, mutating))
        token = captureCsrfToken(key, response, token)

        if (mutating && response.code == 403) {
            // A 403 on a mutating call almost always means the CSRF proof was missing or
            // stale (e.g. the instance rotated its cookie or the token was primed before
            // the server restarted). Re-prime with a fresh token and retry exactly once;
            // genuine authorization failures fail again and surface as before.
            val fresh = primeCsrfToken(base)
            if (fresh != null && fresh != token) {
                csrfTokens[key] = fresh
                response.close()
                response = chain.proceed(signed(original, base, session, fresh, mutating))
                captureCsrfToken(key, response, fresh)
            }
        }
        response
    }

    /** Builds the request for [base] with session JWT + CSRF double-submit proof attached. */
    private fun signed(
        original: Request,
        base: HttpUrl,
        session: Session,
        token: String?,
        mutating: Boolean,
    ): Request {
        val newUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()

        val builder = original.newBuilder()
            .url(newUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "FP-Client/${BuildConfig.VERSION_NAME}")

        val cookieParts = mutableListOf<String>()
        if (session.isLoggedIn) cookieParts += "JWT_TOKEN=${session.token}"
        if (token != null) {
            cookieParts += "XSRF-TOKEN=$token"
            if (mutating) builder.header("X-XSRF-TOKEN", token)
        }
        if (cookieParts.isNotEmpty()) {
            builder.header("Cookie", cookieParts.joinToString("; "))
        }
        return builder.build()
    }

    /** Stores any `XSRF-TOKEN` rotation from the response and returns the newest token. */
    private fun captureCsrfToken(key: String, response: okhttp3.Response, current: String?): String? {
        var token = current
        response.headers.values("Set-Cookie").forEach { raw ->
            val (name, value) = parseCookie(raw) ?: return@forEach
            if (name == "XSRF-TOKEN" && value.isNotBlank()) {
                token = value
                csrfTokens[key] = value
            }
        }
        return token
    }

    /** Plain client with no interceptors and no redirects — used only for the CSRF priming
     * GET so it can't recurse, and so the `Set-Cookie` of the primed response is visible. */
    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient = run {
        val logging = HttpLoggingInterceptor().apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(sessionInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Issues a throwaway GET of the public `/login` page to mint an `XSRF-TOKEN` cookie
     * that the caller can echo back on a mutating request. JSON API GETs do not render the
     * CSRF token, so Spring never emits the cookie for them — the login page does. */
    private fun primeCsrfToken(base: HttpUrl): String? {
        val url = base.newBuilder()
            .addPathSegment("login")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html")
            .header("User-Agent", "FP-Client/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        var found: String? = null
        try {
            plainClient.newCall(request).execute().use { resp ->
                resp.headers.values("Set-Cookie").forEach { raw ->
                    val (name, value) = parseCookie(raw) ?: return@forEach
                    if (name == "XSRF-TOKEN" && value.isNotBlank()) found = value
                }
            }
        } catch (_: Exception) {
            // CSRF priming is best-effort; failures surface as normal API errors.
        }
        return found
    }

    private fun parseCookie(setCookie: String): Pair<String, String>? {
        val eq = setCookie.indexOf('=')
        if (eq < 0) return null
        val name = setCookie.substring(0, eq).trim()
        val rest = setCookie.substring(eq + 1)
        val end = rest.indexOf(';')
        val value = if (end >= 0) rest.substring(0, end) else rest
        return name to value
    }

    private fun withSession(): Session = runBlocking { sessionStore.session.first() }

    val api: FitPubApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl("https://fitpub.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(FitPubApi::class.java)
    }

    private val isDebug: Boolean get() = BuildConfig.DEBUG
}