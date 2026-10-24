package com.alotube.app.data.local

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File

/**
 * Datos reales de un audio para la pantalla "Detalles" (y para mostrar las etiquetas). Todo es opcional:
 * un campo que el archivo no trae queda en null y la interfaz muestra "Desconocido".
 */
data class AudioDetails(
    val name: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val composer: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val track: String? = null,
    val durationMs: Long? = null,
    /** Ruta completa del archivo (si el sistema la entrega). */
    val path: String? = null,
    /** Tamaño en bytes (File.length(); si no hay ruta, el que da MediaStore). */
    val sizeBytes: Long? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null
)

/**
 * Lee los metadatos con MediaMetadataRetriever (más MediaExtractor para frecuencia y canales, y el lector
 * de .opus de respaldo) y la ruta / tamaño con File. SIEMPRE fuera del hilo principal. Solo LEE: no
 * modifica el archivo. Cualquier fallo se ignora y el campo queda en null (nunca lanza).
 */
object AudioDetailsReader {

    suspend fun read(ctx: Context, uri: Uri?, fallbackName: String): AudioDetails =
        withContext(Dispatchers.IO) {
            if (uri == null) return@withContext AudioDetails(name = fallbackName.ifBlank { null })
            try {
                readBlocking(ctx.applicationContext, uri, fallbackName)
            } catch (_: Exception) {
                AudioDetails(name = fallbackName.ifBlank { null })
            }
        }

    private fun readBlocking(ctx: Context, uri: Uri, fallbackName: String): AudioDetails {
        var name: String? = null
        var album: String? = null
        var artist: String? = null
        var composer: String? = null
        var albumArtist: String? = null
        var genre: String? = null
        var year: String? = null
        var track: String? = null
        var durationMs: Long? = null
        var bitrateKbps: Int? = null
        var sampleRate: Int? = null
        var channels: Int? = null

        // ---- Etiquetas y datos técnicos con MediaMetadataRetriever ----
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            fun tag(key: Int): String? = try {
                retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
            name = tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
            album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            composer = tag(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE)
            year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)
            // "3/12" -> "3"
            track = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()
            durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            bitrateKbps = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?.let { (it / 1000).toInt() }?.takeIf { it > 0 }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            }
        } catch (_: Exception) {
            // Archivo sin etiquetas legibles o dañado: los campos quedan en null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // ---- Respaldo para .opus (a veces MediaMetadataRetriever no entrega sus etiquetas) ----
        if (name == null || artist == null || album == null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    OpusTagsReader.read(BufferedInputStream(stream))?.let { tags ->
                        if (name == null) name = tags.title?.trim()?.takeIf { it.isNotEmpty() }
                        if (artist == null) artist = tags.artist?.trim()?.takeIf { it.isNotEmpty() }
                        if (album == null) album = tags.album?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }
            } catch (_: Exception) {
                // Sin acceso al archivo: se queda como está
            }
        }

        // ---- Frecuencia de muestreo y canales (y respaldos de duración / tasa) con MediaExtractor ----
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ctx, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                if (sampleRate == null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (channels == null && format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (bitrateKbps == null && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrateKbps = (format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000).takeIf { it > 0 }
                }
                if (durationMs == null && format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                }
                break
            }
        } catch (_: Exception) {
            // Formato que el sistema no sabe abrir: frecuencia y canales quedan en null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        // ---- Ruta y tamaño (File) ----
        var path: String? = null
        var mediaStoreSize: Long? = null
        if (uri.scheme == "file") {
            path = uri.path
        } else {
            try {
                @Suppress("DEPRECATION")
                val dataColumn = MediaStore.MediaColumns.DATA
                ctx.contentResolver.query(
                    uri, arrayOf(dataColumn, MediaStore.MediaColumns.SIZE), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        path = cursor.getString(0)?.takeIf { it.isNotBlank() }
                        mediaStoreSize = cursor.getLong(1).takeIf { it > 0 }
                    }
                }
            } catch (_: Exception) {
                // El proveedor no entrega ruta: se muestra "Desconocido"
            }
        }
        var size: Long? = null
        try {
            val file = path?.let { File(it) }
            if (file != null && file.isFile) size = file.length().takeIf { it > 0 }
        } catch (_: Exception) {
        }
        if (size == null) size = mediaStoreSize

        // Sin tasa en los metadatos: se estima con tamaño y duración (bits por milisegundo = kbps)
        if (bitrateKbps == null && size != null && durationMs != null && durationMs!! > 0) {
            bitrateKbps = (size!! * 8 / durationMs!!).toInt().takeIf { it > 0 }
        }

        return AudioDetails(
            name = name ?: fallbackName.ifBlank { null },
            album = album,
            artist = artist,
            composer = composer,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            track = track,
            durationMs = durationMs,
            path = path,
            sizeBytes = size,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRate,
            channels = channels
        )
    }
}
