package com.alotube.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Pantallas que pueden tener un fondo propio. */
enum class BgScope(val key: String) {
    YOUTUBE("youtube"),   // Inicio y Biblioteca
    MUSIC("music"),       // Música (carpetas) y reproducción de audio
    VIDEO("video"),       // Video Player (carpetas) y reproductor de video local
    SETTINGS("settings")  // Pantalla principal de Ajustes
}

/** Cómo se ajusta la imagen: se guarda por pantalla en la clave "<pantalla>_scale" (Crop / FillBounds / Fit). */
enum class BgMode(val pref: String) {
    FILL("Crop"),          // Rellenar: recorta para llenar la pantalla
    STRETCH("FillBounds"), // Estirar: deforma para que quepa exacta
    CENTER("Fit");         // Centrar: imagen completa, adaptada al espacio

    companion object {
        fun from(pref: String?): BgMode = values().firstOrNull { it.pref == pref } ?: FILL
    }
}

/**
 * Fondos personalizados. La imagen elegida se COPIA (reducida, JPEG) a `filesDir/backgrounds/`,
 * así que borrar el original no la rompe, y la ruta se guarda en SharedPreferences "backgrounds"
 * (una clave por pantalla). Si el archivo falta o es inválido: se ignora y queda el fondo oscuro.
 */
object BackgroundStore {
    private const val PREFS = "backgrounds"
    private const val DIR = "backgrounds"
    private const val MAX_SIDE = 1920

    private val _version = MutableStateFlow(0)

    /** Sube cada vez que cambia algún fondo (las pantallas lo observan para repintarse). */
    val version: StateFlow<Int> = _version

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ruta de la imagen guardada, o null si no hay (o el archivo ya no existe). */
    fun path(ctx: Context, scope: BgScope): String? =
        prefs(ctx).getString(scope.key, null)?.takeIf { File(it).isFile }

    fun has(ctx: Context, scope: BgScope): Boolean = path(ctx, scope) != null

    /** Modo de escalado guardado de esa pantalla (por defecto Rellenar). */
    fun mode(ctx: Context, scope: BgScope): BgMode =
        BgMode.from(prefs(ctx).getString(scope.key + "_scale", null))

    fun setMode(ctx: Context, scope: BgScope, mode: BgMode) {
        prefs(ctx).edit().putString(scope.key + "_scale", mode.pref).apply()
        _version.value = _version.value + 1
    }

    /** Lee la imagen guardada (fuera del hilo principal). */
    suspend fun load(ctx: Context, scope: BgScope): Bitmap? = withContext(Dispatchers.IO) {
        try {
            path(ctx, scope)?.let { BitmapFactory.decodeFile(it) }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Copia la imagen elegida a la carpeta interna y guarda su ruta. Devuelve false si no se pudo. */
    suspend fun set(ctx: Context, scope: BgScope, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = ctx.applicationContext
            val resolver = app.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext false

            val dir = File(app.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${scope.key}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            bitmap.recycle()

            val old = prefs(app).getString(scope.key, null)
            // Imagen nueva = modo Rellenar por defecto
            prefs(app).edit()
                .putString(scope.key, file.absolutePath)
                .putString(scope.key + "_scale", BgMode.FILL.pref)
                .apply()
            if (old != null && old != file.absolutePath) File(old).delete()
            _version.value = _version.value + 1
            true
        } catch (_: Exception) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    /** Quita el fondo de esa pantalla (vuelve al fondo oscuro por defecto). */
    fun clear(ctx: Context, scope: BgScope) {
        val app = ctx.applicationContext
        val old = prefs(app).getString(scope.key, null)
        prefs(app).edit().remove(scope.key).remove(scope.key + "_scale").apply()
        try {
            if (old != null) File(old).delete()
        } catch (_: Exception) {
        }
        _version.value = _version.value + 1
    }
}
