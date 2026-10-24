@file:OptIn(UnstableApi::class)

package com.alotube.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.alotube.app.player.PlaybackQueue
import com.alotube.app.player.PlayerProvider
import com.alotube.app.player.QueueState
import com.alotube.app.player.isLocalAudioUrl
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción en segundo plano. Envuelve el ExoPlayer único (PlayerProvider) en una
 * MediaSession: Media3 genera la notificación MediaStyle (miniatura, título, autor, play/pausa,
 * siguiente/anterior), los controles de la pantalla de bloqueo y los botones de auriculares/Bluetooth.
 *
 * Con música local la notificación añade el botón Barajear (comando propio de la sesión). Su estado vive en
 * PlaybackQueue, la misma fuente que usa la pantalla del reproductor: tocarlo en un sitio lo refleja en el otro.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val shuffleCommand = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        running = true

        // Al tocar la notificación se vuelve a la app (la tarea existente, con el reproductor encima)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, PlayerProvider.controlPlayer(this))
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        // El botón Barajear de la notificación sigue al estado de la cola (y desaparece con videos de YouTube)
        scope.launch {
            PlaybackQueue.state
                .map { Pair(isLocalAudioUrl(it.current?.url), it.shuffle) }
                .distinctUntilChanged()
                .collect { session?.setCustomLayout(layoutFor(PlaybackQueue.state.value)) }
        }

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notif_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_logo)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Botones extra de la notificación: Barajear (solo música local). Encendido = ícono con punto. */
    private fun layoutFor(state: QueueState): ImmutableList<CommandButton> {
        if (!isLocalAudioUrl(state.current?.url)) return ImmutableList.of()
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName(getString(R.string.mp_shuffle))
                .setIconResId(if (state.shuffle) R.drawable.ic_notif_shuffle_on else R.drawable.ic_audio_shuffle)
                .setSessionCommand(shuffleCommand)
                .build()
        )
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(layoutFor(PlaybackQueue.state.value))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SHUFFLE) {
                PlaybackQueue.setShuffle(!PlaybackQueue.state.value.shuffle)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    /** El usuario deslizó la app fuera de la multitarea: se detiene todo limpiamente. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerProvider.instance?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        session?.release()
        session = null
        PlayerProvider.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "alotube_playback"
        private const val ACTION_SHUFFLE = "com.alotube.app.SHUFFLE"

        @Volatile
        var running = false
            private set
    }
}
