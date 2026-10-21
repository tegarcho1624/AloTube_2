package com.alotube.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.graphics.ColorUtils

/**
 * Color de acento dinámico. Mismo formato guardado que Alouncher:
 * SharedPreferences "settings", claves
 *  - `accent`        : ARGB int del acento elegido (si no existe -> dorado por defecto)
 *  - `accent_custom` : texto "RRGGBB,RRGGBB,..." con los colores HEX propios (el más nuevo primero)
 *
 * Nunca se usa un dorado fijo en la UI: siempre se lee de aquí (vía AloTubeTheme / MaterialTheme.accents).
 */
object ThemeUtils {

    private const val PREFS = "settings"
    private const val KEY_ACCENT = "accent"
    private const val KEY_CUSTOM = "accent_custom"
    private const val MAX_CUSTOM = 12

    /** Dorado por defecto y su versión clara (valores de Alouncher). */
    const val DEFAULT_ACCENT: Int = 0xFFC9A24B.toInt()
    const val DEFAULT_HI: Int = 0xFFE2BD67.toInt()

    /** Paleta básica de 12 colores (el primero es el dorado por defecto). */
    val ACCENT_COLORS: IntArray = intArrayOf(
        0xFFC9A24B.toInt(), // dorado
        0xFFD9822B.toInt(), // naranja
        0xFFD17A68.toInt(), // terracota
        0xFFC75B7A.toInt(), // rosa
        0xFFA77BCA.toInt(), // violeta
        0xFF7F8CCB.toInt(), // índigo
        0xFF7FA6C9.toInt(), // azul
        0xFF5FB3B3.toInt(), // turquesa
        0xFF8FB38A.toInt(), // verde
        0xFFA8B84A.toInt(), // lima
        0xFFE0C85A.toInt(), // amarillo
        0xFF9A9A9A.toInt()  // gris
    )

    @Volatile
    private var version = 0

    /** Sube cada vez que cambia el acento; las pantallas lo comparan en onResume para recrearse. */
    fun version(): Int = version

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun accent(ctx: Context): Int = try {
        prefs(ctx).getInt(KEY_ACCENT, DEFAULT_ACCENT)
    } catch (_: Exception) {
        DEFAULT_ACCENT // valor guardado corrupto o de otro tipo: se ignora
    }

    fun accentHi(ctx: Context): Int = hiFor(accent(ctx))

    /** Relleno translúcido (#RRGGBB + alfa 1F) de elementos activos. */
    fun accentBg(ctx: Context): Int = (accent(ctx) and 0x00FFFFFF) or 0x1F000000

    fun setAccent(ctx: Context, color: Int) {
        prefs(ctx).edit().putInt(KEY_ACCENT, color or 0xFF000000.toInt()).apply()
        version++
    }

    fun resetAccent(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ACCENT).apply()
        version++
    }

    /** Versión clara del acento: la de Alouncher para el dorado; para el resto, +10 % de luminosidad. */
    fun hiFor(color: Int): Int {
        if (color == DEFAULT_ACCENT) return DEFAULT_HI
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] + 0.10f).coerceAtMost(0.92f)
        return ColorUtils.HSLToColor(hsl) or 0xFF000000.toInt()
    }

    // ---------- Colores propios ----------

    fun customColors(ctx: Context): List<Int> = try {
        (prefs(ctx).getString(KEY_CUSTOM, "") ?: "")
            .split(",")
            .mapNotNull { it.trim().takeIf { s -> s.length == 6 }?.toIntOrNull(16) }
            .map { it or 0xFF000000.toInt() }
    } catch (_: Exception) {
        emptyList()
    }

    fun addCustom(ctx: Context, color: Int) {
        val hex = "%06X".format(color and 0x00FFFFFF)
        val current = customColors(ctx).map { "%06X".format(it and 0x00FFFFFF) }
        val updated = (listOf(hex) + current.filter { it != hex }).take(MAX_CUSTOM)
        prefs(ctx).edit().putString(KEY_CUSTOM, updated.joinToString(",")).apply()
    }

    /**
     * Elimina un color propio de `accent_custom`. Si ese color era el acento activo, se vuelve al
     * dorado por defecto (borra `accent`) para no dejar la app con un color que ya no existe.
     * Devuelve true si se restableció el acento (la UI debe recrearse para aplicarlo).
     */
    fun removeCustom(ctx: Context, color: Int): Boolean {
        val hex = "%06X".format(color and 0x00FFFFFF)
        val remaining = customColors(ctx)
            .map { "%06X".format(it and 0x00FFFFFF) }
            .filter { it != hex }
        prefs(ctx).edit().putString(KEY_CUSTOM, remaining.joinToString(",")).apply()

        val wasActive = (accent(ctx) and 0x00FFFFFF) == (color and 0x00FFFFFF)
        if (wasActive) resetAccent(ctx)
        return wasActive
    }

    /** Acepta "#RRGGBB" o "RRGGBB" (exactamente 6 dígitos). Devuelve null si no es válido. */
    fun parseHex(raw: String): Int? {
        var s = raw.trim()
        if (!s.startsWith("#")) s = "#$s"
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(s)) return null
        return s.substring(1).toInt(16) or 0xFF000000.toInt()
    }
}
