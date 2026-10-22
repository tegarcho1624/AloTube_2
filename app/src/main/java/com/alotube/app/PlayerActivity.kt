@file:OptIn(UnstableApi::class)

package com.alotube.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.data.model.VideoItem
import com.alotube.app.player.PlaybackQueue
import com.alotube.app.player.PlayerMediaSources
import com.alotube.app.player.PlayerProvider
import com.alotube.app.ui.screens.PlayerScreen
import com.alotube.app.ui.theme.AloTubeTheme
import com.alotube.app.viewmodel.PlayerViewModel

/**
 * Pantalla completa del reproductor. El ExoPlayer NO es de esta Activity: es el único de la app
 * (PlayerProvider) y PlaybackService lo expone como MediaSession, así el audio sigue en segundo plano.
 */
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var player: ExoPlayer
    private lateinit var controlPlayer: Player

    private val inPip = mutableStateOf(false)
    private var pipSupported = false

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            viewModel.onPlaybackError(describePlaybackError(error))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePipParams()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updatePipParams()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersive()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        // Si la cola se perdió (proceso recreado), se reconstruye con los datos del Intent
        if (PlaybackQueue.state.value.current == null) {
            PlaybackQueue.set(listOf(VideoItem(url, title, "", 0, null, false)), 0)
        }

        player = PlayerProvider.get(this)
        controlPlayer = PlayerProvider.controlPlayer(this)
        PlayerProvider.owner = this
        pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        player.addListener(playerListener)
        startPlaybackService()

        setContent {
            AloTubeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Cambia la calidad, entra el flujo de respaldo o se carga otro video de la cola
                LaunchedEffect(state.current) {
                    val option = state.current
                    if (option == null) {
                        // Video nuevo o reintento: se limpia para no heredar la posición del anterior
                        player.clearMediaItems()
                    } else {
                        applySource(option, state.title, state.author, state.thumbnailUrl)
                    }
                }
                LaunchedEffect(state.error) {
                    if (state.error != null) {
                        player.stop()
                        player.clearMediaItems()
                    }
                }

                PlayerScreen(
                    exoPlayer = controlPlayer,
                    state = state,
                    inPip = inPip.value,
                    onBack = { finish() },
                    onSelectQuality = { viewModel.selectQuality(it) },
                    onRetry = { viewModel.retry() },
                    onFullscreenToggle = { enter -> setFullscreen(enter) },
                    onEnterPip = if (pipSupported) ({ enterPip() }) else null
                )
            }
        }
    }

    // ---------- Servicio de segundo plano ----------

    private fun startPlaybackService() {
        try {
            // Desde la app en primer plano; Media3 lo promueve a foreground al empezar a sonar
            startService(Intent(this, PlaybackService::class.java))
        } catch (_: Exception) {
            // Sin servicio la reproducción en pantalla funciona igual; solo no habrá segundo plano
        }
    }

    // ---------- Reproducción ----------

    private fun applySource(option: PlaybackOption, title: String, author: String, thumb: String?) {
        // Al cambiar de calidad se conserva la posición actual
        val position =
            if (player.mediaItemCount > 0 && !option.isHls && !option.isDash) player.currentPosition else 0L

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author.ifBlank { null })
            .setArtworkUri(thumb?.let { Uri.parse(it) })
            .build()

        if (option.isLocal) {
            // Archivo local (content:// o file://): el ExoPlayer único lo abre directamente
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(option.videoUrl))
                    .setMediaMetadata(metadata)
                    .build()
            )
            player.prepare()
            player.playWhenReady = true
            return
        }

        val source = PlayerMediaSources.create(option, metadata)
        if (position > 0) player.setMediaSource(source, position) else player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    /** Resumen técnico del fallo de Media3 (código + causa, p. ej. HTTP 403) para el diagnóstico. */
    private fun describePlaybackError(e: PlaybackException): String = buildString {
        append(e.errorCodeName)
        e.cause?.let { c ->
            append(" / ").append(c.javaClass.simpleName)
            c.message?.let { append(": ").append(it.take(120)) }
        }
    }

    private fun setFullscreen(enter: Boolean) {
        requestedOrientation = if (enter) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** En segundo plano no se decodifica ni se descarga video: solo el audio. */
    private fun setVideoEnabled(enabled: Boolean) {
        if (!::player.isInitialized) return
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
                .build()
        } catch (_: Exception) {
            // Player ya liberado (servicio detenido): nada que hacer
        }
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    // ---------- Picture-in-Picture ----------

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) Rational(size.width, size.height)
        else Rational(16, 9)
        // Android solo acepta relaciones entre 0.418 y 2.39
        val safe = when {
            ratio.toFloat() > 2.39f -> Rational(239, 100)
            ratio.toFloat() < 0.5f -> Rational(1, 2)
            else -> ratio
        }
        builder.setAspectRatio(safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(player.isPlaying && viewModel.state.value.error == null)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (!pipSupported || !::player.isInitialized) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (_: Exception) {
            // Si el sistema rechaza los parámetros, simplemente no se actualizan
        }
    }

    private fun enterPip() {
        if (!pipSupported || isInPictureInPictureMode) return
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (_: Exception) {
            // PiP desactivado para la app en Ajustes del sistema: se ignora
        }
    }

    /** Botón Inicio: con video reproduciéndose se minimiza a PiP (el audio sigue de todos modos). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying && viewModel.state.value.error == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // El usuario cerró la ventana flotante (X): se termina y se detiene la reproducción
            finishAndRemoveTask()
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onStart() {
        super.onStart()
        setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // Pantalla apagada / otra app: el AUDIO sigue (servicio), el video se desactiva.
        // En PiP el video se mantiene.
        if (!isInPictureInPictureMode) setVideoEnabled(false)
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            try {
                player.removeListener(playerListener)
            } catch (_: Exception) {
                // Player ya liberado
            }
            // Cerrar la pantalla (atrás, flecha, X del PiP) detiene también el audio y el servicio.
            // Si el sistema solo destruye la Activity en segundo plano, la reproducción continúa.
            if (isFinishing && PlayerProvider.owner === this) {
                PlayerProvider.stopPlayback(this)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        /** Abre el reproductor con la lista de resultados como cola (Siguiente/Anterior). */
        fun start(context: Context, queue: List<VideoItem>, index: Int) {
            val video = queue.getOrNull(index) ?: return
            PlaybackQueue.set(queue, index)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, video.url)
                    .putExtra(EXTRA_TITLE, video.title)
            )
        }
    }
}
