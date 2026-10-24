package com.alotube.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.alotube.app.PlaybackService
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ¿La "url" de la cola es un AUDIO del dispositivo? (content://media/external/audio/media/ID) */
fun isLocalAudioUrl(url: String?): Boolean =
    url != null && url.startsWith("content://") && Uri.parse(url).pathSegments.contains("audio")

/** Lo que suena ahora si es música local; [item] es null si no hay nada (o no es un audio local). */
data class NowPlaying(
    val item: VideoItem? = null,
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false
)

/**
 * Solo LEE el estado del ExoPlayer único (consulta cada 300 ms; no cambia nada de la reproducción)
 * y la cola. Sirve para el mini-reproductor y para resaltar la canción actual en las listas.
 */
@Composable
fun rememberNowPlaying(): NowPlaying {
    val queue by PlaybackQueue.state.collectAsState()
    var playing by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val player = PlayerProvider.instance
            try {
                playing = player?.isPlaying == true
                active = player != null && player.mediaItemCount > 0
            } catch (_: Exception) {
                playing = false
                active = false
            }
            delay(300)
        }
    }

    val current = queue.current?.takeIf { isLocalAudioUrl(it.url) }
    return NowPlaying(
        item = if (active) current else null,
        isPlaying = playing,
        hasNext = queue.hasNext
    )
}

/**
 * Controles del mini-reproductor para música local con la pantalla completa CERRADA.
 * Con la pantalla completa abierta, PlayerViewModel sigue cargando el siguiente como siempre.
 */
object LocalAudioController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun togglePlayPause() {
        val player = PlayerProvider.instance ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    /** Pasa a la siguiente canción de la cola (y la carga con LocalAudioLoader, sin importar si hay pantalla abierta). */
    fun next(ctx: Context) {
        if (!PlaybackQueue.next()) return
        loadCurrent(ctx)
    }

    /**
     * Canción anterior (mismo criterio que el botón de la pantalla completa): si llevas más de 3 s o es la
     * primera, reinicia la canción; si no, pasa a la anterior de la cola.
     */
    fun previous(ctx: Context) {
        val player = PlayerProvider.instance
        if (player != null && (player.currentPosition > 3_000L || !PlaybackQueue.state.value.hasPrevious)) {
            player.seekTo(0)
            return
        }
        if (!PlaybackQueue.previous()) return
        loadCurrent(ctx)
    }

    /**
     * Carga la canción actual de la cola con el cargador único (LocalAudioLoader). Es idempotente: si ya
     * la cargó el PlayerProvider o la pantalla completa, no la reinicia.
     */
    private fun loadCurrent(ctx: Context) {
        val item = PlaybackQueue.state.value.current ?: return
        if (!isLocalAudioUrl(item.url)) return
        val app = ctx.applicationContext
        scope.launch {
            try {
                LocalAudioLoader.load(app, item.url, item.title)
            } catch (_: Exception) {
                // Archivo borrado o ilegible: se queda lo que sonaba, sin crash
            }
        }
    }
}
