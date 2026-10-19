package com.alotube.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.alotube.app.ui.screens.AloTubeRoot
import com.alotube.app.ui.theme.AloTubeTheme
import com.alotube.app.ui.theme.ThemeUtils

class MainActivity : ComponentActivity() {

    private var themeVersion = 0

    // Sin el permiso la reproducción funciona igual; solo no se muestra la notificación multimedia
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                    onVideoClick = { queue, index ->
                        PlayerActivity.start(this, queue, index)
                    },
                    // Cambió el acento: se recrea la Activity para aplicarlo en toda la UI
                    onAccentChanged = { recreate() }
                )
            }
        }
        askNotificationPermissionOnce()
    }

    /** Android 13+: se pide POST_NOTIFICATIONS la primera vez que se abre la app. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIF_ASKED, false)) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        if (ThemeUtils.version() != themeVersion) recreate()
    }

    private companion object {
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
    }
}
