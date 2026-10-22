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
    val isVideo: Boolean,
    /** Carpeta que lo contiene (MediaStore BUCKET_ID / BUCKET_DISPLAY_NAME). */
    val bucketId: Long = 0L,
    val bucketName: String = ""
) {
    /** Nombre sin extensión. */
    val title: String get() = name.substringBeforeLast('.', name)
}

/** Carpeta que contiene videos o audios (agrupada por BUCKET_ID). [cover] = el primero de la lista, para la miniatura. */
data class LocalFolder(
    val id: Long,
    val name: String,
    val count: Int,
    val cover: LocalMedia
)

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
