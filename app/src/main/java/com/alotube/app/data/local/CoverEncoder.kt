package com.alotube.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Prepara la carátula elegida por el usuario para incrustarla en un .opus: la lee con ImageDecoder (respeta la
 * orientación de la foto), la reduce a [MAX_PX] x [MAX_PX] como máximo (sin deformarla), la comprime a JPEG y
 * la empaqueta como METADATA_BLOCK_PICTURE (bloque "picture" de FLAC en base64, el estándar de Ogg).
 * SIEMPRE fuera del hilo principal. Solo LEE la imagen elegida.
 */
object CoverEncoder {

    const val MAX_PX = 500
    private const val JPEG_QUALITY = 88

    /** Imagen elegida ya reducida (para vista previa o para incrustar); null si no se pudo leer. */
    suspend fun decode(ctx: Context, uri: Uri, maxPx: Int = MAX_PX): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(ctx.applicationContext.contentResolver, uri)
            val raw = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            // JPEG no tiene transparencia: se pinta sobre fondo negro (el de la app)
            val flat = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.BLACK)
                drawBitmap(raw, 0f, 0f, null)
            }
            if (flat !== raw) raw.recycle()
            flat
        } catch (_: Throwable) {
            null
        }
    }

    /** JPEG de [bitmap] (para la miniatura de la notificación). */
    fun jpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    /**
     * Valor listo para la etiqueta METADATA_BLOCK_PICTURE (base64), o null si la imagen no se pudo leer.
     * Bloque FLAC: tipo 3 (portada), "image/jpeg", sin descripción, ancho, alto, 24 bits, sin paleta, datos.
     */
    suspend fun pictureBlock(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decode(ctx, uri) ?: return@withContext null
        val data = jpeg(bitmap)
        val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
        val block = ByteBuffer.allocate(32 + mime.size + data.size).order(ByteOrder.BIG_ENDIAN)
        block.putInt(3)
        block.putInt(mime.size)
        block.put(mime)
        block.putInt(0) // descripción vacía
        block.putInt(bitmap.width)
        block.putInt(bitmap.height)
        block.putInt(24)
        block.putInt(0)
        block.putInt(data.size)
        block.put(data)
        Base64.encodeToString(block.array(), 0, block.position(), Base64.NO_WRAP)
    }
}
