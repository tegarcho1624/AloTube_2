package com.alotube.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val loggedIn: Boolean = false,
    val email: String? = null
)

/**
 * Sesión de Google: guarda SOLO las cookies de sesión de YouTube (y el correo, si se pudo leer),
 * cifradas con EncryptedSharedPreferences (clave AES-256 en el Android Keystore).
 * La contraseña NUNCA pasa por la app: se escribe en la página de Google dentro del WebView.
 */
object SessionStore {

    private const val FILE = "alotube_session"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_EMAIL = "email"

    /** Cookies que prueban una sesión iniciada en YouTube. */
    private val REQUIRED = listOf("SAPISID", "__Secure-3PAPISID")

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Cabecera Cookie lista para usar (en memoria). null = sin sesión. La lee el Downloader. */
    @Volatile
    var cookieHeader: String? = null
        private set

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Carga la sesión guardada. Llamar una vez al arrancar la app. */
    fun init(ctx: Context) {
        try {
            val p = prefs(ctx)
            val cookies = p?.getString(KEY_COOKIES, null)
            if (!cookies.isNullOrBlank() && hasSessionKeys(cookies)) {
                cookieHeader = cookies
                _state.value = SessionState(true, p.getString(KEY_EMAIL, null))
            }
        } catch (_: Exception) {
            // Almacén ilegible: se arranca sin sesión (nunca debe tumbar la app)
            cookieHeader = null
            _state.value = SessionState()
        }
    }

    /** Devuelve true si se guardó correctamente. */
    fun save(ctx: Context, cookies: String, email: String?): Boolean {
        if (!hasSessionKeys(cookies)) return false
        return try {
            val p = prefs(ctx) ?: return false
            val ok = p.edit()
                .putString(KEY_COOKIES, cookies)
                .putString(KEY_EMAIL, email)
                .commit()
            if (ok) {
                cookieHeader = cookies
                _state.value = SessionState(true, email)
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** Borra la sesión guardada y la de memoria. */
    fun clear(ctx: Context) {
        cookieHeader = null
        _state.value = SessionState()
        try {
            prefs(ctx)?.edit()?.clear()?.commit()
        } catch (_: Exception) {
            try {
                ctx.applicationContext.deleteSharedPreferences(FILE)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Cookies ----------

    /** "a=1; b=2" -> {a=1, b=2} */
    fun parse(cookies: String?): Map<String, String> {
        if (cookies.isNullOrBlank()) return emptyMap()
        return cookies.split(";")
            .mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
            }
            .toMap()
    }

    /** ¿Tiene SAPISID y __Secure-3PAPISID (las que autentican las peticiones a YouTube)? */
    fun hasSessionKeys(cookies: String?): Boolean {
        val map = parse(cookies)
        return REQUIRED.all { !map[it].isNullOrBlank() }
    }

    // ---------- Almacén cifrado ----------

    @Synchronized
    private fun prefs(ctx: Context): SharedPreferences? {
        prefs?.let { return it }
        val app = ctx.applicationContext

        fun create(): SharedPreferences {
            val key = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                app, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        val created = try {
            create()
        } catch (_: Exception) {
            // Keystore o archivo dañado: se borra el archivo y se reintenta una vez
            try {
                app.deleteSharedPreferences(FILE)
                create()
            } catch (_: Exception) {
                null
            }
        }
        prefs = created
        return created
    }
}
