package com.alotube.app.data.download

import android.content.Context
import android.content.Intent

/** Una opción descargable (un stream concreto). */
data class DownloadChoice(
    val label: String,
    val url: String,
    val extension: String,
    val mimeType: String
)

/** Pista de audio (idioma) con sus calidades. */
data class AudioTrackOption(
    val label: String,
    val streams: List<DownloadChoice>
)

data class DownloadOptions(
    /** Video con audio incluido (progresivos). Normalmente 360p; a veces 720p. */
    val videoChoices: List<DownloadChoice>,
    val audioTracks: List<AudioTrackOption>
)

/** Lo que recibe el servicio de descarga. */
data class DownloadRequest(
    val url: String,
    /** Nombre sin extensión (ya saneado). */
    val name: String,
    val extension: String,
    val mimeType: String
) {
    val displayName: String get() = "$name.$extension"

    fun toIntent(ctx: Context, target: Class<*>): Intent =
        Intent(ctx, target)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_NAME, name)
            .putExtra(EXTRA_EXT, extension)
            .putExtra(EXTRA_MIME, mimeType)

    companion object {
        private const val EXTRA_URL = "dl_url"
        private const val EXTRA_NAME = "dl_name"
        private const val EXTRA_EXT = "dl_ext"
        private const val EXTRA_MIME = "dl_mime"

        fun fromIntent(intent: Intent?): DownloadRequest? {
            val url = intent?.getStringExtra(EXTRA_URL) ?: return null
            val name = intent.getStringExtra(EXTRA_NAME) ?: return null
            val ext = intent.getStringExtra(EXTRA_EXT) ?: return null
            val mime = intent.getStringExtra(EXTRA_MIME) ?: return null
            return DownloadRequest(url, name, ext, mime)
        }
    }
}
