package com.alotube.app.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Envoltorio del ExoPlayer único. La MediaSession (notificación / pantalla de bloqueo) y el PlayerView
 * usan este objeto, así Siguiente/Anterior significan "video siguiente/anterior de la lista".
 *  - Siguiente: solo disponible si hay otro video en la cola.
 *  - Anterior: si llevas más de 3 s (o es el primero) reinicia el video; si no, va al anterior.
 */
class QueueAwarePlayer(player: Player) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
        builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        if (PlaybackQueue.state.value.hasNext) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_NEXT)
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        availableCommands.contains(command)

    override fun seekToNext() {
        PlaybackQueue.next()
    }

    override fun seekToPrevious() {
        val hasPrevious = PlaybackQueue.state.value.hasPrevious
        if (currentPosition > RESTART_THRESHOLD_MS || !hasPrevious) {
            seekTo(0)
        } else {
            PlaybackQueue.previous()
        }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
