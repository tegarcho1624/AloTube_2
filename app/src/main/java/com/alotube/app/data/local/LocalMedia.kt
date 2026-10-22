package com.alotube.app.data.local

import android.net.Uri
import com.alotube.app.data.model.VideoItem
import com.alotube.app.util.formatFileSize

/** Un archivo de video o audio del dispositivo (leído con MediaStore). */
data class LocalMedia(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val size: Long,
    val isVideo: Boolean
) {
    /** Nombre sin extensión. */
    val title: String get() = name.substringBeforeLast('.', name)
}

data class LocalLibrary(
    val videos: List<LocalMedia>,
    val audios: List<LocalMedia>
)

/**
 * Para reproducirlo con el reproductor de siempre: la "url" es el content:// URI.
 * (PlayerViewModel reconoce content:// y no pide nada a NewPipeExtractor.)
 */
fun LocalMedia.toVideoItem() = VideoItem(
    url = uri.toString(),
    title = title,
    author = "",
    durationSeconds = durationMs / 1000,
    thumbnailUrl = null,
    isLive = false,
    viewCount = -1L,
    uploadDateText = formatFileSize(size)
)
