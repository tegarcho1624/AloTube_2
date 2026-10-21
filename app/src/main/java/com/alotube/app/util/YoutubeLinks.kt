package com.alotube.app.util

import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory

/**
 * Detección de enlaces de YouTube en el texto del buscador.
 * Reconoce youtube.com/watch?v=..., youtu.be/..., m.youtube.com, music.youtube.com, /shorts/, /live/,
 * /embed/ ..., con o sin "https://", y también cuando el enlace viene dentro de un texto más largo.
 */
object YoutubeLinks {

    // (?<![\w.-]) evita falsos positivos como "fakeyoutube.com/..."
    private val LINK_REGEX = Regex(
        "(?<![\\w.-])(?:https?://)?(?:[a-z0-9-]+\\.)*(?:youtube\\.com|youtu\\.be|youtube-nocookie\\.com)/[^\\s]+",
        RegexOption.IGNORE_CASE
    )

    private val FALLBACK_ID_REGEX = Regex(
        "(?:[?&]v=|youtu\\.be/|/shorts/|/embed/|/live/|/v/)([A-Za-z0-9_-]{11})"
    )

    /** Primer enlace de YouTube dentro del texto, ya con https://, o null si no hay. */
    fun extractUrl(text: String): String? {
        val match = LINK_REGEX.find(text) ?: return null
        val raw = match.value.trimEnd('.', ',', ')', ']', ';')
        return if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
    }

    fun containsLink(text: String): Boolean = extractUrl(text) != null

    /** ID de 11 caracteres del video, o null si el enlace no apunta a un video (canal, lista...). */
    fun extractVideoId(url: String): String? {
        try {
            val factory = YoutubeStreamLinkHandlerFactory.getInstance()
            if (factory.acceptUrl(url)) {
                factory.getId(url)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (_: Exception) {
            // Si NewPipeExtractor no lo reconoce, se prueba con la expresión regular
        }
        return FALLBACK_ID_REGEX.find(url)?.groupValues?.get(1)
    }
}
