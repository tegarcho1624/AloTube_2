package com.alotube.app.data.model

/**
 * Una forma concreta de reproducir un video.
 *  - [audioUrl] != null  -> video sin audio (adaptativo) + pista de audio aparte, se unen al reproducir.
 *  - [audioUrl] == null  -> flujo "muxed" (video+audio juntos, normalmente <= 360p/720p).
 *  - [isHls]             -> directo (HLS); la URL va en [videoUrl].
 */
data class PlaybackOption(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false
)

data class PlayableVideo(
    val title: String,
    val author: String,
    /** Calidades para el menú, de mayor a menor. */
    val options: List<PlaybackOption>,
    /** Opción compatible (video+audio juntos) para reintentar si la principal falla. */
    val fallback: PlaybackOption?
)
