package com.alotube.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Neutros fijos de la familia Alouncher (mismos nombres que colors.xml del launcher:
 * bg, sf, sf2, sf3, sheet, ln, ln2, ink, mut, paper, danger, scrim).
 * Si cambias un neutro del launcher, se cambia SOLO aquí.
 */
object AloColors {
    val Bg = Color(0xFF0E0C0A)      // fondo base (casi negro cálido)
    val Sf = Color(0xFF161310)      // superficie
    val Sf2 = Color(0xFF1D1914)     // superficie elevada
    val Sf3 = Color(0xFF26211A)     // superficie más alta
    val Sheet = Color(0xFF1A160F)   // hojas / menús
    val Ln = Color(0xFF2A251D)      // borde normal
    val Ln2 = Color(0xFF3B3327)     // borde marcado
    val Ink = Color(0xFFF1E9D8)     // texto principal (crema)
    val Mut = Color(0xFFA3987F)     // texto secundario / íconos inactivos
    val Paper = Color(0xFFEFE6D2)   // botones crema
    val PaperInk = Color(0xFF2A2218)
    val Danger = Color(0xFFD17A68)  // rojizo / destructivo
    val Scrim = Color(0xB8080604)   // velo
}

/** Acento dinámico + su versión clara y el relleno translúcido. */
@Immutable
data class AccentColors(val accent: Color, val hi: Color, val bg: Color)

val LocalAccent = staticCompositionLocalOf {
    AccentColors(Color(0xFFC9A24B), Color(0xFFE2BD67), Color(0x1FC9A24B))
}

val MaterialTheme.accents: AccentColors
    @Composable @ReadOnlyComposable get() = LocalAccent.current

fun aloColorScheme(a: AccentColors): ColorScheme = darkColorScheme(
    primary = a.accent,
    onPrimary = AloColors.Bg,
    primaryContainer = AloColors.Sf2,
    onPrimaryContainer = a.hi,
    secondary = a.hi,
    onSecondary = AloColors.Bg,
    secondaryContainer = AloColors.Sf3,
    onSecondaryContainer = a.hi,
    tertiary = a.hi,
    onTertiary = AloColors.Bg,
    background = AloColors.Bg,
    onBackground = AloColors.Ink,
    surface = AloColors.Bg,
    onSurface = AloColors.Ink,
    surfaceVariant = AloColors.Sf2,
    onSurfaceVariant = AloColors.Mut,
    surfaceTint = a.accent,
    inverseSurface = AloColors.Ink,
    inverseOnSurface = AloColors.Bg,
    inversePrimary = a.hi,
    error = AloColors.Danger,
    onError = AloColors.Bg,
    errorContainer = AloColors.Sf2,
    onErrorContainer = AloColors.Danger,
    outline = AloColors.Ln2,
    outlineVariant = AloColors.Ln,
    scrim = AloColors.Scrim,
    surfaceContainerLowest = AloColors.Bg,
    surfaceContainerLow = AloColors.Sf,
    surfaceContainer = AloColors.Sheet,
    surfaceContainerHigh = AloColors.Sf2,
    surfaceContainerHighest = AloColors.Sf3
)
