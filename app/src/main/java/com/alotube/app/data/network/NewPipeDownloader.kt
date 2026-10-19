package com.alotube.app.data.network

import com.alotube.app.data.auth.SessionStore
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** Implementación del Downloader que exige NewPipeExtractor, usando OkHttp. */
object NewPipeDownloader : Downloader() {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Sesión de Google: se añade la cookie en CADA salto de red, y solo si el destino es
        // youtube.com o un subdominio (nunca a otros hosts, ni tras una redirección externa).
        // Sin sesión iniciada no hace nada: el comportamiento es idéntico al de antes.
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val cookie = SessionStore.cookieHeader
            if (cookie != null && isYoutubeHost(request.url.host)) {
                val existing = request.header("Cookie")
                val merged = if (existing.isNullOrBlank()) cookie else "$existing; $cookie"
                chain.proceed(request.newBuilder().header("Cookie", merged).build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    private fun isYoutubeHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com")

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val dataToSend = request.dataToSend()

        var body = dataToSend?.toRequestBody()
        if (body == null && httpMethod.uppercase() in setOf("POST", "PUT", "PATCH")) {
            body = ByteArray(0).toRequestBody()
        }

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, body)
            .addHeader("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (value in values) {
                builder.addHeader(name, value)
            }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }
}
