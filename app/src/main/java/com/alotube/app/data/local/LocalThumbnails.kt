package com.alotube.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniaturas de videos (fotograma) y audios (carátula) con ContentResolver.loadThumbnail. */
object LocalThumbnails {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** null si el archivo no tiene miniatura (p. ej. audio sin carátula) o está dañado. */
    suspend fun load(ctx: Context, uri: Uri, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@${width}x$height"
        cache.get(key)?.let { return@withContext it }
        try {
            val bitmap = ctx.contentResolver.loadThumbnail(uri, Size(width, height), null)
            cache.put(key, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
