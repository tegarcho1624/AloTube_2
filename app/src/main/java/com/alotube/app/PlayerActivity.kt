@file:OptIn(UnstableApi::class)

package com.alotube.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alotube.app.data.model.PlaybackOption
import com.alotube.app.player.PlayerMediaSources
import com.alotube.app.ui.screens.PlayerScreen
import com.alotube.app.ui.theme.AloTubeTheme
import com.alotube.app.viewmodel.PlayerViewModel

/** Pantalla completa del reproductor (Media3) con soporte de Picture-in-Picture. */
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var player: ExoPlayer

    private val inPip = mutableStateOf(false)
    private var pipSupported = false
    private var wasPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersive()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(describePlaybackError(error))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePipParams()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updatePipParams()
            }
        })

        viewModel.load(url, intent.getStringExtra(EXTRA_TITLE).orEmpty())

        setContent {
            AloTubeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Cuando cambia la calidad (o entra el flujo de respaldo) se vuelve a preparar el player
                LaunchedEffect(state.current) {
                    state.current?.let { applySource(it) }
                }
                LaunchedEffect(state.error) {
                    if (state.error != null) {
                        player.stop()
                        player.clearMediaItems()
                    }
                }

                PlayerScreen(
                    exoPlayer = player,
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

    // ---------- Reproducción ----------

    private fun applySource(option: PlaybackOption) {
        // Al cambiar de calidad se conserva la posición actual
        val position = if (player.mediaItemCount > 0 && !option.isHls && !option.isDash) player.currentPosition else 0L
        val source = PlayerMediaSources.create(option)
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
            // Android 12+: entra solo a PiP al ir a Inicio mientras se reproduce
            builder.setAutoEnterEnabled(player.isPlaying && viewModel.state.value.error == null)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (!pipSupported) return
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

    /** Botón Inicio: si hay video reproduciéndose, se minimiza a PiP (Android 8–11 y respaldo en 12+). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying && viewModel.state.value.error == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // El usuario cerró la ventana flotante (X): se termina para no dejar audio de fondo
            finishAndRemoveTask()
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onStart() {
        super.onStart()
        if (wasPlaying) {
            player.play()
            wasPlaying = false
        }
    }

    override fun onStop() {
        super.onStop()
        // En PiP el video sigue; fuera de PiP se pausa al salir de la pantalla
        if (!isInPictureInPictureMode) {
            wasPlaying = player.playWhenReady
            player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, url: String, title: String) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
