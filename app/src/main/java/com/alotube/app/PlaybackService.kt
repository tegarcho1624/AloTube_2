@file:OptIn(UnstableApi::class)

package com.alotube.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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

        val mediaSession = MediaSession.Builder(this, PlayerProvider.controlPlayer(this))
            .setSessionActivity(sessionActivity)
            .build()
        session = mediaSession
        // Se registra la sesión YA, sin esperar a que un controlador se conecte: así Media3 puede publicar
        // la notificación en cuanto el reproductor deje de estar en reposo (algunos fabricantes, como ZUI de
        // Lenovo, tardan o no llegan a conectar el controlador del sistema).
        addSession(mediaSession)

        // El canal se crea ANTES que el proveedor de Media3 (que solo crea el suyo si no existe), con
        // importancia normal y sin sonido ni vibración. Con importancia baja, algunas capas (ZUI) la
        // mandan a "Silenciosas" o la ocultan. Id nuevo: la importancia de un canal ya creado no se puede subir.
        ensureChannel()

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notif_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_logo)
        setMediaNotificationProvider(provider)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        } catch (_: Exception) {
            // Sin canal propio, Media3 crea el suyo
        }
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
        private const val CHANNEL_ID = "alotube_playback_v2"

        @Volatile
        var running = false
            private set
    }
}
