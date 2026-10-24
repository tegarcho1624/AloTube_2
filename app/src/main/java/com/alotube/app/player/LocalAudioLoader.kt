package com.alotube.app.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.alotube.app.PlaybackService
import com.alotube.app.R
import com.alotube.app.data.local.AudioInfo
import com.alotube.app.data.local.AudioMetadataRepository
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * ÚNICO camino para cargar una música local en el ExoPlayer. Lo usan, sin depender de que haya una
 * pantalla abierta: PlayerProvider (cuando cambia la canción de la cola: Siguiente/Anterior de la
 * notificación, autoplay, mini-reproductor), LocalAudioController y PlayerActivity.
 *
 * Es seguro llamarlo varias veces para la misma canción: si ya está cargada y no terminó, no hace nada.
 * Llamar siempre desde el hilo principal (coroutine en Dispatchers.Main).
 *
 * Metadatos del MediaItem (los lee la notificación y la pantalla de bloqueo):
 *  - título = nombre del archivo SIN extensión (el que llega en [title]);
 *  - artista y álbum de las etiquetas (si no hay, "Artista Desconocido" / sin álbum);
 *  - carátula = la incrustada (o la del sistema); sin carátula, un ícono genérico con el acento.
 */
object LocalAudioLoader {

    private const val ART_PX = 256

    suspend fun load(ctx: Context, url: String, title: String) {
        val app = ctx.applicationContext
        val uri = Uri.parse(url)
        val info = AudioMetadataRepository.load(app, uri, title)
        // Mientras se leían las etiquetas pudo cambiar la cola: esa otra canción se cargará por su cuenta
        if (PlaybackQueue.state.value.current?.url != url) return

        val metadata = buildMetadata(app, title, info)
        val player = PlayerProvider.get(app)
        // Ya suena (o está en pausa) este mismo archivo: no se reinicia
        val sameUri = player.currentMediaItem?.localConfiguration?.uri == uri
        if (sameUri && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) return

        player.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        )
        player.prepare()
        player.playWhenReady = true
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) {
            // Sin servicio la reproducción sigue; solo no habría notificación
        }
    }

    private suspend fun buildMetadata(ctx: Context, title: String, info: AudioInfo): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(info.artist)
            .setAlbumTitle(info.album)
        val art = artworkBytes(ctx, info.cover)
        if (art != null) builder.setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        return builder.build()
    }

    /** JPEG de la carátula (o del ícono genérico si no hay). null solo si algo falla: sin imagen y sin error. */
    private suspend fun artworkBytes(ctx: Context, cover: Bitmap?): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val bitmap = cover ?: placeholder(ctx)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Ícono genérico (nota musical con el acento dinámico) sobre una superficie oscura de la paleta. */
    private fun placeholder(ctx: Context): Bitmap {
        val bitmap = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AloColors.Sf2.toArgb())
        ContextCompat.getDrawable(ctx, R.drawable.ic_nav_music)?.mutate()?.let { d ->
            d.setTint(ThemeUtils.accent(ctx))
            val pad = ART_PX / 4
            d.setBounds(pad, pad, ART_PX - pad, ART_PX - pad)
            d.draw(canvas)
        }
        return bitmap
    }
}
