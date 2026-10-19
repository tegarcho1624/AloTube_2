@file:OptIn(UnstableApi::class)

package com.alotube.app.player

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView

/**
 * Aplica el tema a los controles de Media3: fondo oscuro, barra de progreso y play/pausa en el acento,
 * resto de íconos y textos en crema. Los ids se buscan por nombre y cada uno es opcional:
 * si una versión de Media3 no tiene alguno, simplemente se omite (nunca crashea).
 */
fun PlayerView.applyAloStyle(accent: Int, accentHi: Int, ink: Int, muted: Int, bg: Int) {
    fun byName(name: String): View? {
        val id = resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) null else findViewById(id)
    }

    try {
        setShutterBackgroundColor(bg)

        (byName("exo_progress") as? DefaultTimeBar)?.apply {
            setPlayedColor(accent)
            setScrubberColor(accentHi)
            setBufferedColor((accent and 0x00FFFFFF) or 0x55000000)
            setUnplayedColor((ink and 0x00FFFFFF) or 0x33000000)
        }

        // Velo oscuro cálido tras los controles
        byName("exo_controls_background")?.setBackgroundColor(0x990E0C0A.toInt())

        // Play/pausa en el acento
        (byName("exo_play_pause") as? ImageView)?.setColorFilter(accent)

        // Demás botones en crema
        listOf(
            "exo_prev", "exo_next", "exo_rew", "exo_ffwd", "exo_rew_with_amount",
            "exo_ffwd_with_amount", "exo_settings", "exo_fullscreen", "exo_repeat_toggle",
            "exo_shuffle", "exo_subtitle", "exo_overflow_show", "exo_overflow_hide"
        ).forEach { name ->
            when (val v = byName(name)) {
                is ImageView -> v.setColorFilter(ink)
                is TextView -> v.setTextColor(ink)
            }
        }

        (byName("exo_position") as? TextView)?.setTextColor(ink)
        (byName("exo_duration") as? TextView)?.setTextColor(muted)
    } catch (_: Exception) {
        // El estilo es decorativo: ante cualquier problema se deja el control por defecto
    }
}
