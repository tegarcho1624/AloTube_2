package com.alotube.app.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Lee el correo de la cuenta recién iniciada. Es opcional: si falla, la sesión sigue siendo válida. */
object AccountRepository {

    private const val LIST_ACCOUNTS =
        "https://accounts.google.com/ListAccounts?gpsia=1&source=ChromiumBrowser&json=standard"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** [googleCookies]: cookies de accounts.google.com leídas del WebView; no se guardan. */
    suspend fun fetchEmail(googleCookies: String?): String? = withContext(Dispatchers.IO) {
        if (googleCookies.isNullOrBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(LIST_ACCOUNTS)
                .header("Cookie", googleCookies)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    EMAIL_REGEX.find(response.body?.string().orEmpty())?.value
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
