package com.alotube.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Iconos personalizados de las carpetas de Música y Video Player.
 * La imagen elegida se COPIA (reducida a 768 px de lado largo, JPEG o PNG si tiene transparencia) a
 * `filesDir/folder_icons/`, así que borrar el original no la rompe. La ruta se guarda en SharedPreferences
 * "folder_icons", una clave por carpeta: `<music|video>_<bucketId>`. Si el archivo falta o es inválido se
 * ignora y la carpeta vuelve a su ícono por defecto. Nunca toca los archivos de la carpeta real.
 */
object FolderIconStore {
    private const val PREFS = "folder_icons"
    private const val DIR = "folder_icons"
    private const val MAX_SIDE = 768

    private val _version = MutableStateFlow(0)

    /** Sube cada vez que cambia algún ícono (las tarjetas lo observan para repintarse). */
    val version: StateFlow<Int> = _version

    // La ruta cambia en cada imagen nueva (lleva la hora), así que cachear por ruta es seguro
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: FolderKind, bucketId: Long) = "${kind.key}_$bucketId"

    /** Ruta de la imagen guardada, o null si no hay (o el archivo ya no existe). */
    fun path(ctx: Context, kind: FolderKind, bucketId: Long): String? = try {
        prefs(ctx).getString(key(kind, bucketId), null)?.takeIf { File(it).isFile }
    } catch (_: Exception) {
        null
    }

    fun has(ctx: Context, kind: FolderKind, bucketId: Long): Boolean = path(ctx, kind, bucketId) != null

    /** Lee la imagen guardada (fuera del hilo principal). null = usar el ícono por defecto. */
    suspend fun load(ctx: Context, kind: FolderKind, bucketId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val path = path(ctx, kind, bucketId) ?: return@withContext null
        cache.get(path)?.let { return@withContext it }
        try {
            BitmapFactory.decodeFile(path)?.also { cache.put(path, it) }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Copia la imagen elegida a la carpeta interna y guarda su ruta. Devuelve false si no se pudo. */
    suspend fun set(ctx: Context, kind: FolderKind, bucketId: Long, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val app = ctx.applicationContext
                val resolver = app.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@withContext false

                // Fotos de cámara: respetar la rotación guardada en los datos EXIF
                val degrees = try {
                    resolver.openInputStream(uri)?.use { exifDegrees(ExifInterface(it)) } ?: 0
                } catch (_: Exception) {
                    0
                }
                val longSide = maxOf(bitmap.width, bitmap.height)
                val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
                if (degrees != 0 || scale != 1f) {
                    val matrix = Matrix().apply {
                        if (scale != 1f) postScale(scale, scale)
                        if (degrees != 0) postRotate(degrees.toFloat())
                    }
                    val adjusted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (adjusted !== bitmap) bitmap.recycle()
                    bitmap = adjusted
                }

                val dir = File(app.filesDir, DIR).apply { mkdirs() }
                val png = bitmap.hasAlpha()
                val file = File(dir, "${key(kind, bucketId)}_${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
                FileOutputStream(file).use { out ->
                    if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    else bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bitmap.recycle()

                val old = prefs(app).getString(key(kind, bucketId), null)
                prefs(app).edit().putString(key(kind, bucketId), file.absolutePath).apply()
                if (old != null && old != file.absolutePath) File(old).delete()
                _version.value = _version.value + 1
                true
            } catch (_: Exception) {
                false
            } catch (_: OutOfMemoryError) {
                false
            }
        }

    /** Quita el ícono personalizado: la carpeta vuelve a su ícono por defecto. Borra solo la copia interna. */
    fun clear(ctx: Context, kind: FolderKind, bucketId: Long) {
        val app = ctx.applicationContext
        val old = try {
            prefs(app).getString(key(kind, bucketId), null)
        } catch (_: Exception) {
            null
        }
        try {
            prefs(app).edit().remove(key(kind, bucketId)).apply()
            if (old != null) File(old).delete()
        } catch (_: Exception) {
            // Si no se pudo borrar la copia, ya no se usa: no pasa nada
        }
        _version.value = _version.value + 1
    }

    private fun exifDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
