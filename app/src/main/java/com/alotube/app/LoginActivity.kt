package com.alotube.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alotube.app.data.auth.WebSessionCleaner
import com.alotube.app.ui.screens.LoginScreen
import com.alotube.app.ui.theme.AloTubeTheme
import com.alotube.app.viewmodel.LoginStatus
import com.alotube.app.viewmodel.LoginViewModel

/** Pantalla de inicio de sesión de Google (WebView). Resultado OK = sesión guardada. */
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AloTubeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.status) {
                    if (state.status == LoginStatus.DONE) {
                        // La sesión ya está guardada cifrada: se borra la copia sin cifrar del WebView
                        WebSessionCleaner.wipe()
                        setResult(RESULT_OK)
                        finish()
                    }
                }

                LoginScreen(
                    state = state,
                    onClose = { finish() },
                    onDismissError = { viewModel.dismissError() },
                    onYoutubePageLoaded = { yt, google -> viewModel.onYoutubePageLoaded(yt, google) }
                )
            }
        }
    }
}
