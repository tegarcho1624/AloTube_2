package com.alotube.app.data.library

import com.alotube.app.data.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Sin sesión, o YouTube indica que la sesión ya no es válida (cookies caducadas). */
class InnerTubeAuthException(message: String) : Exception(message)

/**
 * Peticiones autenticadas a la API interna de YouTube (youtubei) con las cookies guardadas.
 * Además de la cabecera Cookie, el cliente web envía Authorization: SAPISIDHASH (hash de SAPISID),
 * sin la cual YouTube trata las peticiones de biblioteca como anónimas.
 * Solo se llama a www.youtube.com; las cookies no salen a ningún otro host.
 */
object InnerTubeClient {

    private const val ORIGIN = "https://www.youtube.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val FALLBACK_VERSION = "2.20250910.01.00"
    private const val CONFIG_TTL_MS = 30 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class Config(val clientVersion: String, val apiKey: String?, val loggedIn: Boolean)

    @Volatile
    private var config: Config? = null

    @Volatile
    private var configTime = 0L

    /** POST /youtubei/v1/browse con el browseId indicado. Lanza excepciones; el repositorio las traduce. */
    suspend fun browse(browseId: String): JSONObject = withContext(Dispatchers.IO) {
        val cookieHeader = SessionStore.cookieHeader
            ?: throw InnerTubeAuthException("No hay sesión iniciada")
        val cookies = SessionStore.parse(cookieHeader)
        val authorization = authorization(cookies)
            ?: throw InnerTubeAuthException("Faltan cookies de sesión (SAPISID)")

        val cfg = loadConfig(cookieHeader)
        if (!cfg.loggedIn) {
            throw InnerTubeAuthException("YouTube indica que la sesión no está iniciada")
        }

        val body = JSONObject()
            .put(
                "context", JSONObject()
                    .put(
                        "client", JSONObject()
                            .put("clientName", "WEB")
                            .put("clientVersion", cfg.clientVersion)
                            .put("hl", "es")
                            .put("gl", "MX")
                    )
                    .put("user", JSONObject().put("lockedSafetyMode", false))
            )
            .put("browseId", browseId)

        val url = buildString {
            append("$ORIGIN/youtubei/v1/browse?prettyPrint=false")
            cfg.apiKey?.let { append("&key=").append(it) }
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookieHeader)
            .header("Authorization", authorization)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", cfg.clientVersion)
            .header("Accept-Language", "es-MX,es;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw InnerTubeAuthException("YouTube rechazó la sesión (HTTP ${response.code})")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${text.take(120)}")
            }
            JSONObject(text)
        }
    }

    // ---------- Configuración de la página (versión del cliente, sesión válida) ----------

    private fun loadConfig(cookieHeader: String): Config {
        val cached = config
        if (cached != null && System.currentTimeMillis() - configTime < CONFIG_TTL_MS) return cached

        val fresh = try {
            val request = Request.Builder()
                .url("$ORIGIN/")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookieHeader)
                .header("Accept-Language", "es-MX,es;q=0.9")
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                val apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                // Solo se considera "no logueado" si la página lo dice explícitamente
                val loggedIn = !Regex("\"LOGGED_IN\":false").containsMatchIn(html)
                Config(version ?: FALLBACK_VERSION, apiKey, loggedIn)
            }
        } catch (_: Exception) {
            Config(FALLBACK_VERSION, null, true)
        }
        config = fresh
        configTime = System.currentTimeMillis()
        return fresh
    }

    // ---------- SAPISIDHASH ----------

    private fun hash(timestamp: Long, secret: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $secret $ORIGIN".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun authorization(cookies: Map<String, String>): String? {
        val ts = System.currentTimeMillis() / 1000
        val parts = mutableListOf<String>()
        (cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"])?.let {
            parts += "SAPISIDHASH ${ts}_${hash(ts, it)}"
        }
        cookies["__Secure-1PAPISID"]?.let { parts += "SAPISID1PHASH ${ts}_${hash(ts, it)}" }
        cookies["__Secure-3PAPISID"]?.let { parts += "SAPISID3PHASH ${ts}_${hash(ts, it)}" }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }
}
