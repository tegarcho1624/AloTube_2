package com.alotube.app

import android.os.Bundle
import android.widget.Toast
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
                    // Fase 1: solo un Toast. El reproductor llega en la Fase 2.
                    onVideoClick = { video ->
                        Toast.makeText(
                            this,
                            getString(R.string.play_toast, video.title),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}
