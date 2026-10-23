package com.alotube.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.alotube.app.data.local.BackgroundStore
import com.alotube.app.data.local.BgMode
import com.alotube.app.data.local.BgScope
import com.alotube.app.ui.theme.AloColors

/** Opacidad de la capa oscura sobre la imagen, para que los textos claros se lean. */
private const val DIM_ALPHA = 0.6f

/**
 * Envoltorio reutilizable: cualquier pantalla nueva que se ponga dentro hereda el fondo elegido para [scope]
 * (imagen + capa oscura, o el oscuro de siempre). La pantalla interior debe tener fondo transparente
 * (Scaffold con containerColor = Color.Transparent, sin .background(...) sólido).
 */
@Composable
fun AppBackground(scope: BgScope?, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    Box(modifier = modifier) {
        BackgroundLayer(scope)
        content()
    }
}

/**
 * Fondo de una pantalla: el oscuro de siempre y, si el usuario eligió una imagen para [scope],
 * esa imagen (según el modo guardado: Rellenar / Estirar / Centrar) con una capa oscura semitransparente encima.
 * Se dibuja DETRÁS del contenido (ponlo como primer hijo de un Box).
 */
@Composable
fun BackgroundLayer(scope: BgScope?, modifier: Modifier = Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val version by BackgroundStore.version.collectAsState()
    val image by produceState<ImageBitmap?>(initialValue = null, scope, version) {
        value = if (scope == null) null else BackgroundStore.load(ctx, scope)?.asImageBitmap()
    }
    // Modo de escalado guardado (se relee cada vez que cambia algún fondo)
    val mode = remember(scope, version) {
        if (scope == null) BgMode.FILL else BackgroundStore.mode(ctx, scope)
    }
    val contentScale = when (mode) {
        BgMode.FILL -> ContentScale.Crop
        BgMode.STRETCH -> ContentScale.FillBounds
        BgMode.CENTER -> ContentScale.Fit
    }
    Box(modifier = modifier.background(AloColors.Bg)) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = DIM_ALPHA))
            )
        }
    }
}
