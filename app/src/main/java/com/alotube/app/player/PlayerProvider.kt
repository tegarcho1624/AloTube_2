@file:OptIn(UnstableApi::class)

package com.alotube.app.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alotube.app.PlaybackService

/**
 * Dueño del ÚNICO ExoPlayer de la app. La pantalla de reproducción lo usa para poner fuentes y
 * mostrarlo; PlaybackService lo envuelve en una MediaSession. Nunca hay dos reproductores.
 *
 * Audio focus (lo gestiona ExoPlayer):
 *  - Llamadas / avisos (pérdida breve): se pausa solo y, si el ajuste lo permite, se reanuda al terminar.
 *  - Otra app que toma el audio de forma permanente (p. ej. Spotify): se pausa.
 */
object PlayerProvider {

    @Volatile
    private var exo: ExoPlayer? = null
    private var control: Player? = null

    /** Activity que "posee" la reproducción actual (para que una instancia vieja no pare la nueva). */
    @Volatile
    var owner: Any? = null

    val instance: ExoPlayer? get() = exo

    @Synchronized
    fun get(ctx: Context): ExoPlayer {
        exo?.let { return it }
        val app = ctx.applicationContext
        val player = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_NETWORK)  // sigue con la pantalla apagada
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                // Pérdida breve de foco: ExoPlayer reanuda solo al volver el foco.
                // Si el usuario no quiere reanudación automática, se convierte en una pausa normal.
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                    !PlaybackPrefs.isAutoResume(app)
                ) {
                    player.pause()
                }
            }
        })

        exo = player
        control = QueueAwarePlayer(player)
        return player
    }

    /** Player con Siguiente/Anterior de la cola (para MediaSession y PlayerView). */
    @Synchronized
    fun controlPlayer(ctx: Context): Player {
        get(ctx)
        return control!!
    }

    /** Detiene la reproducción, apaga el servicio y libera el player. */
    fun stopPlayback(ctx: Context) {
        val serviceWasRunning = PlaybackService.running
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        ctx.applicationContext.stopService(Intent(ctx.applicationContext, PlaybackService::class.java))
        // Si el servicio no llegó a arrancar, su onDestroy no liberará el player: se hace aquí
        if (!serviceWasRunning) release()
    }

    @Synchronized
    fun release() {
        exo?.release()
        exo = null
        control = null
        owner = null
    }
}
