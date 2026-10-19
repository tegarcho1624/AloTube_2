package com.alotube.app.player

import android.content.Context

/** Ajustes de reproducción en SharedPreferences "settings" (mismo archivo que el acento). */
object PlaybackPrefs {
    private const val PREFS = "settings"
    private const val KEY_AUTO_RESUME = "resume_after_interruption"

    /** Reanudar solo tras interrupciones breves (llamadas, avisos). Por defecto: sí. */
    fun isAutoResume(ctx: Context): Boolean = try {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RESUME, true)
    } catch (_: Exception) {
        true
    }

    fun setAutoResume(ctx: Context, value: Boolean) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RESUME, value).apply()
    }
}
