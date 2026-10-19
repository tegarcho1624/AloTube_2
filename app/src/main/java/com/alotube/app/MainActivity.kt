package com.alotube.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alotube.app.ui.screens.AloTubeRoot
import com.alotube.app.ui.theme.AloTubeTheme
import com.alotube.app.ui.theme.ThemeUtils

class MainActivity : ComponentActivity() {

    private var themeVersion = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema siempre oscuro: íconos claros en las barras del sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        themeVersion = ThemeUtils.version()
        setContent {
            AloTubeTheme {
                AloTubeRoot(
                    onVideoClick = { video ->
                        PlayerActivity.start(this, video.url, video.title)
                    },
                    // Cambió el acento: se recrea la Activity para aplicarlo en toda la UI
                    onAccentChanged = { recreate() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeUtils.version() != themeVersion) recreate()
    }
}
