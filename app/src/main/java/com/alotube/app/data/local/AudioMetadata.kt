package com.alotube.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import com.alotube.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Etiquetas de un audio. [title] nunca está vacío (si el archivo no trae título se usa el nombre del
 * archivo) y [artist] nunca está vacío ("Artista Desconocido"). [album] es null si no lo trae.
 * [cover] es la carátula (incrustada o la que da el sistema), reducida para listas; null si no hay.
 */
data class AudioInfo(
    val title: String,
    val artist: String,
    val album: String?,
    val cover: Bitmap?
)

/**
 * Lee título, artista, álbum y carátula con MediaMetadataRetriever, SIEMPRE fuera del hilo principal,
 * con un máximo de lecturas simultáneas y caché en memoria: las listas se muestran al instante con el
 * nombre del archivo y se completan solas conforme llegan las etiquetas.
 */
object AudioMetadataRepository {

    private const val SMALL_COVER_PX = 256
    private const val LARGE_COVER_PX = 800

    private val infoCache = object : LruCache<String, AudioInfo>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AudioInfo): Int = (value.cover?.byteCount ?: 0) + 512
    }
    private val largeCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Un MediaMetadataRetriever por archivo es pesado: se limitan las lecturas en paralelo
    private val gate = Semaphore(3)

    /** Etiquetas del archivo. [fallbackTitle] = nombre del archivo sin extensión. */
    suspend fun load(ctx: Context, uri: Uri, fallbackTitle: String): AudioInfo {
        val key = uri.toString()
        infoCache.get(key)?.let { return it }
        return gate.withPermit {
            infoCache.get(key) ?: withContext(Dispatchers.IO) {
                read(ctx.applicationContext, uri, fallbackTitle, SMALL_COVER_PX)
            }.also { infoCache.put(key, it) }
        }
    }

    /** Carátula grande para el reproductor a pantalla completa (null si el archivo no tiene). */
    suspend fun loadLargeCover(ctx: Context, uri: Uri, fallbackTitle: String): Bitmap? {
        val key = uri.toString()
        largeCache.get(key)?.let { return it }
        val bitmap = gate.withPermit {
            withContext(Dispatchers.IO) {
                read(ctx.applicationContext, uri, fallbackTitle, LARGE_COVER_PX).cover
            }
        }
        if (bitmap != null) largeCache.put(key, bitmap)
        return bitmap
    }

    /** Primera carátula que se encuentre entre los primeros [max] audios (para las carpetas). */
    suspend fun firstCover(ctx: Context, tracks: List<LocalMedia>, max: Int = 4): Bitmap? {
        for (track in tracks.take(max)) {
            load(ctx, track.uri, track.title).cover?.let { return it }
        }
        return null
    }

    private fun read(ctx: Context, uri: Uri, fallbackTitle: String, coverPx: Int): AudioInfo {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var cover: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            retriever.embeddedPicture?.let { cover = decodeScaled(it, coverPx) }
        } catch (_: Exception) {
            // Archivo sin etiquetas legibles o dañado: se usan los valores por defecto
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Sin carátula incrustada: puede que el sistema tenga una (p. ej. cover.jpg en la carpeta)
        if (cover == null) {
            cover = try {
                ctx.contentResolver.loadThumbnail(uri, Size(coverPx, coverPx), null)
            } catch (_: Exception) {
                null
            }
        }

        return AudioInfo(
            title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackTitle,
            artist = artist?.trim().takeUnless { it.isNullOrEmpty() }
                ?: ctx.getString(R.string.mp_unknown_artist),
            album = album?.trim().takeUnless { it.isNullOrEmpty() },
            cover = cover
        )
    }

    /** Decodifica la imagen incrustada reduciéndola (sin cargar el tamaño original completo). */
    private fun decodeScaled(bytes: ByteArray, targetPx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    }
}
