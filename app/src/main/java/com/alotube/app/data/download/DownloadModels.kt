package com.alotube.app.data.download

import android.content.Context
import android.content.Intent

/** Qué hay que hacer con lo descargado antes de guardarlo. */
enum class PostProcess {
    /** Se guarda tal cual. */
    NONE,

    /** Audio WebM (Opus) -> Ogg Opus, para que el archivo sea un .opus de verdad. */
    OPUS_REMUX,

    /** Video H.264 (sin audio) + audio AAC (m4a) -> un solo MP4. */
    MERGE_MP4
}

/** Una opción descargable (un stream concreto). */
data class DownloadChoice(
    val label: String,
    val url: String,
    val extension: String,
    val mimeType: String,
    val postProcess: PostProcess = PostProcess.NONE
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
    val mimeType: String,
    /** Solo para MERGE_MP4: URL de la pista de audio. */
    val audioUrl: String? = null,
    val postProcess: PostProcess = PostProcess.NONE
) {
    val displayName: String get() = "$name.$extension"

    fun toIntent(ctx: Context, target: Class<*>): Intent =
        Intent(ctx, target)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_NAME, name)
            .putExtra(EXTRA_EXT, extension)
            .putExtra(EXTRA_MIME, mimeType)
            .putExtra(EXTRA_AUDIO_URL, audioUrl)
            .putExtra(EXTRA_POST, postProcess.name)

    companion object {
        private const val EXTRA_URL = "dl_url"
        private const val EXTRA_NAME = "dl_name"
        private const val EXTRA_EXT = "dl_ext"
        private const val EXTRA_MIME = "dl_mime"
        private const val EXTRA_AUDIO_URL = "dl_audio_url"
        private const val EXTRA_POST = "dl_post"

        fun fromIntent(intent: Intent?): DownloadRequest? {
            val url = intent?.getStringExtra(EXTRA_URL) ?: return null
            val name = intent.getStringExtra(EXTRA_NAME) ?: return null
            val ext = intent.getStringExtra(EXTRA_EXT) ?: return null
            val mime = intent.getStringExtra(EXTRA_MIME) ?: return null
            val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
            val post = try {
                PostProcess.valueOf(intent.getStringExtra(EXTRA_POST) ?: PostProcess.NONE.name)
            } catch (_: IllegalArgumentException) {
                PostProcess.NONE
            }
            return DownloadRequest(url, name, ext, mime, audioUrl, post)
        }
    }
}
