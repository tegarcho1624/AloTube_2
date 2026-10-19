package com.alotube.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Carga la fuente de la app, con el mismo criterio que FontManager de Alouncher.
 *
 * SharedPreferences "settings", clave `font`:
 *  - `sys:serif` / `sys:sans-serif` / `sys:monospace` / `sys:cursive` -> fuente del sistema
 *  - `file:Nombre.ttf` -> archivo en assets/fonts/ (o en files/fonts/ si se importó)
 *  - sin valor -> la primera .ttf/.otf (orden alfabético) de assets/fonts/
 *
 * Si no hay fuente, el archivo falta o es inválido: cae a la fuente predeterminada, sin errores.
 */
object FontLoader {

    private const val PREFS = "settings"
    private const val KEY_FONT = "font"
    private const val DIR = "fonts"

    fun load(ctx: Context): FontFamily {
        val choice = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FONT, null)
        } catch (_: Exception) {
            null
        }

        return when {
            choice != null && choice.startsWith("sys:") -> systemFamily(choice.removePrefix("sys:"))
            choice != null && choice.startsWith("file:") ->
                fromName(ctx, choice.removePrefix("file:")) ?: firstBundled(ctx) ?: FontFamily.Default
            else -> firstBundled(ctx) ?: FontFamily.Default
        }
    }

    private fun systemFamily(name: String): FontFamily = when (name) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans-serif" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }

    private fun isFontFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    private fun firstBundled(ctx: Context): FontFamily? = try {
        ctx.assets.list(DIR)
            ?.filter { isFontFile(it) }
            ?.minOrNull()
            ?.let { fromName(ctx, it) }
    } catch (_: Exception) {
        null
    }

    private fun fromName(ctx: Context, name: String): FontFamily? {
        if (!isFontFile(name)) return null
        // 1) assets/fonts/
        try {
            val path = "$DIR/$name"
            Typeface.createFromAsset(ctx.assets, path) // lanza si no existe
            return FontFamily(Font(path, ctx.assets, FontWeight.Normal, FontStyle.Normal))
        } catch (_: Exception) {
            // no está en assets: se prueba files/fonts/
        }
        // 2) files/fonts/ (fuentes importadas)
        return try {
            val file = File(File(ctx.filesDir, DIR), name)
            if (file.isFile) {
                Typeface.createFromFile(file)
                FontFamily(Font(file, FontWeight.Normal, FontStyle.Normal))
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
