package com.alotube.app.data.model

/**
 * Una forma concreta de reproducir un video.
 *  - [audioUrl] != null  -> video sin audio (adaptativo) + pista de audio aparte, se unen al reproducir.
 *  - [audioUrl] == null  -> flujo "muxed" (video+audio juntos, normalmente <= 360p/720p).
 *  - [isHls] / [isDash]  -> manifiesto HLS o DASH (directos, o si no hay URLs directas); la URL va en [videoUrl].
 */
data class PlaybackOption(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    /** Archivo del dispositivo (content:// o file://): ExoPlayer lo abre directamente. */
    val isLocal: Boolean = false
) {
    /** Texto corto para diagnóstico. */
    val kindLabel: String
        get() = when {
            isLocal -> "local"
            isHls -> "HLS"
            isDash -> "DASH"
            audioUrl != null -> "adaptativo"
            else -> "directo"
        }
}

data class PlayableVideo(
    val title: String,
    val author: String,
    /** Calidades para el menú, de mayor a menor. */
    val options: List<PlaybackOption>,
    /** Opción compatible (video+audio juntos) para reintentar si la principal falla. */
    val fallback: PlaybackOption?,
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val uploadDate: String? = null
)
