package com.alotube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alotube.app.ui.screens.MainScreen
import com.alotube.app.ui.theme.AloTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AloTubeTheme {
                MainScreen(
                    onVideoClick = { video ->
                        PlayerActivity.start(this, video.url, video.title)
                    }
                )
            }
        }
    }
}
