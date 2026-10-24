@file:OptIn(UnstableApi::class)

package com.alotube.app

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alotube.app.player.PlayerProvider

/**
 * Servicio de reproducción en segundo plano. Envuelve el ExoPlayer único (PlayerProvider) en una
 * MediaSession: Media3 genera la notificación MediaStyle (miniatura, título, autor, play/pausa,
 * siguiente/anterior), los controles de la pantalla de bloqueo y los botones de auriculares/Bluetooth.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

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
            .build()

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notif_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_logo)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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
        session?.release()
        session = null
        PlayerProvider.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "alotube_playback"

        @Volatile
        var running = false
            private set
    }
}
