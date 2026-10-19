package com.alotube.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.LoginStatus
import com.alotube.app.viewmodel.LoginUiState

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2F"

// User-Agent de Chrome móvil (sin la marca "wv" de WebView) para que Google no rechace el inicio de sesión
private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Inicio de sesión de Google en un WebView. El usuario escribe correo y contraseña en la página de
 * Google; la app solo lee las cookies cuando se llega a youtube.com.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    state: LoginUiState,
    onClose: () -> Unit,
    onDismissError: () -> Unit,
    onYoutubePageLoaded: (youtubeCookies: String?, googleCookies: String?) -> Unit
) {
    val currentOnYoutubePage by rememberUpdatedState(onYoutubePageLoaded)
    var progress by remember { mutableIntStateOf(0) }
    var netError by remember { mutableStateOf(false) }
    val webHolder = remember { arrayOfNulls<WebView>(1) }
    val accents = MaterialTheme.accents

    fun handleFinished(url: String?) {
        val host = try {
            Uri.parse(url ?: "").host
        } catch (_: Exception) {
            null
        }
        if (host != null && (host == "youtube.com" || host.endsWith(".youtube.com"))) {
            try {
                val cm = CookieManager.getInstance()
                cm.flush()
                currentOnYoutubePage(
                    cm.getCookie("https://www.youtube.com"),
                    cm.getCookie("https://accounts.google.com")
                )
            } catch (_: Exception) {
                currentOnYoutubePage(null, null) // la extracción falló: se avisa al usuario
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AloColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.login_close)
                )
            }
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleMedium,
                color = accents.hi
            )
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = accents.accent,
                trackColor = AloColors.Ln,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val bannerRes = when {
            state.error -> R.string.login_error
            netError -> R.string.login_net_error
            else -> null
        }
        if (bannerRes != null) {
            Surface(color = AloColors.Sf2, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(bannerRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        netError = false
                        onDismissError()
                        webHolder[0]?.loadUrl(LOGIN_URL)
                    }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webHolder[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = LOGIN_USER_AGENT

                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                handleFinished(url)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) netError = true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        // Se empieza sin cookies previas para no mezclar cuentas
                        cm.removeAllCookies { post { loadUrl(LOGIN_URL) } }
                    }
                },
                onRelease = {
                    webHolder[0] = null
                    it.stopLoading()
                    it.clearCache(true)
                    it.clearHistory()
                    it.destroy()
                }
            )

            if (state.status == LoginStatus.SAVING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AloColors.Bg.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accents.accent)
                        Text(
                            text = stringResource(R.string.login_saving),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AloColors.Mut,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
