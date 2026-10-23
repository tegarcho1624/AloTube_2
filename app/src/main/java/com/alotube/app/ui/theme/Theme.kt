package com.alotube.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Tema oscuro SIEMPRE (como Alouncher): neutros fijos + acento leído de SharedPreferences. */
@Composable
fun AloTubeTheme(content: @Composable () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val version = ThemeUtils.version()

    val accents = remember(version) {
        AccentColors(
            accent = Color(ThemeUtils.accent(appCtx)),
            hi = Color(ThemeUtils.accentHi(appCtx)),
            bg = Color(ThemeUtils.accentBg(appCtx))
        )
    }
    // Se recalcula al elegir/importar una fuente (FontLoader.version): cambia toda la app al instante
    val fontVersion by FontLoader.version.collectAsState()
    val fontFamily = remember(fontVersion) { FontLoader.load(appCtx) }
    val typography = remember(fontFamily) { aloTypography(fontFamily) }
    val scheme = remember(accents) { aloColorScheme(accents) }

    CompositionLocalProvider(LocalAccent provides accents) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
