package com.alotube.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
 * Reordenar con toque largo + arrastre, sin librerías nuevas. Sirve para cuadrículas (LazyVerticalGrid)
 * y listas (LazyColumn):
 *
 *   val list = rememberReorderList(source)                 // copia de trabajo (SnapshotStateList)
 *   val reorder = rememberGridReorderState(gridState, onMove = list::move, onDrop = { key, moved -> ... })
 *   LazyVerticalGrid(state = gridState, modifier = Modifier.reorderGestures(reorder)) {
 *       items(list, key = { it.id }) { Item(modifier = Modifier.dragVisual(reorder, it.id, shape)) }
 *   }
 *
 * Toque largo SIN movimiento  -> onDrop(key, moved = false)  (en carpetas abre el menú de ícono).
 * Toque largo + movimiento    -> el elemento se levanta, sigue al dedo y cambia de sitio; al soltar
 *                                onDrop(key, moved = true) y quien llama guarda el nuevo orden.
 * Mientras dura el gesto se consumen los eventos: la lista no se desplaza y no se dispara el clic.
 * Solo cambia el orden de la lista visible: nunca toca archivos del dispositivo.
 */

/** Posición y tamaño (en píxeles, dentro de la lista) de un elemento visible. */
class ReorderItemInfo(
    val key: Any,
    val index: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private const val MAX_SCROLL_PER_FRAME = 28f

@Stable
class ReorderState internal constructor(
    private val visible: () -> List<ReorderItemInfo>,
    private val viewportHeight: () -> Int,
    private val scrollBy: suspend (Float) -> Float,
    private val edgePx: Float,
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: (Any, Boolean) -> Unit
) {
    /** Clave del elemento que se está arrastrando (null = ninguno). */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    private var pointer by mutableStateOf(Offset.Zero)
    private var grab = Offset.Zero
    private var draggedIndex = -1
    private var swapped = false

    val isDragging: Boolean get() = draggedKey != null

    fun isDragged(key: Any): Boolean = draggedKey == key

    internal fun itemAt(position: Offset): ReorderItemInfo? = visible().firstOrNull {
        position.x >= it.left && position.x <= it.left + it.width &&
            position.y >= it.top && position.y <= it.top + it.height
    }

    internal fun start(item: ReorderItemInfo, position: Offset) {
        grab = position - Offset(item.left, item.top)
        pointer = position
        draggedIndex = item.index
        swapped = false
        draggedKey = item.key
    }

    internal fun drag(position: Offset) {
        pointer = position
        checkSwap()
    }

    internal fun end(cancelled: Boolean, movedEnough: Boolean) {
        val key = draggedKey ?: return
        val moved = swapped || movedEnough
        draggedKey = null
        draggedIndex = -1
        if (cancelled) {
            // Gesto interrumpido: si ya se había cambiado el orden se conserva; si no, no se abre ningún menú
            if (swapped) onDrop(key, true)
        } else {
            onDrop(key, moved)
        }
    }

    /** Cuánto hay que desplazar el elemento arrastrado para que siga al dedo. */
    fun translationOf(key: Any): Offset {
        if (draggedKey != key) return Offset.Zero
        val current = visible().firstOrNull { it.key == key } ?: return Offset.Zero
        return pointer - grab - Offset(current.left, current.top)
    }

    /** Si el dedo está sobre otro elemento, el arrastrado ocupa su lugar. */
    private fun checkSwap() {
        val key = draggedKey ?: return
        val items = visible()
        val dragged = items.firstOrNull { it.key == key } ?: return
        // Aún no se refleja el cambio anterior en la pantalla: se espera al siguiente cuadro
        if (dragged.index != draggedIndex) return
        val p = pointer
        val target = items.firstOrNull {
            it.key != key &&
                p.x >= it.left && p.x <= it.left + it.width &&
                p.y >= it.top && p.y <= it.top + it.height
        } ?: return
        onMove(dragged.index, target.index)
        draggedIndex = target.index
        swapped = true
    }

    /** Desplaza la lista si el dedo está cerca del borde superior o inferior. */
    internal suspend fun autoScrollStep() {
        if (draggedKey == null) return
        val height = viewportHeight().toFloat()
        val y = pointer.y
        val delta = when {
            y < edgePx -> -((edgePx - y) / edgePx).coerceIn(0f, 1f) * MAX_SCROLL_PER_FRAME
            y > height - edgePx -> ((y - (height - edgePx)) / edgePx).coerceIn(0f, 1f) * MAX_SCROLL_PER_FRAME
            else -> 0f
        }
        if (delta != 0f) {
            scrollBy(delta)
            checkSwap()
        }
    }
}

@Composable
private fun AutoScrollEffect(state: ReorderState) {
    val dragging = state.isDragging
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            state.autoScrollStep()
        }
    }
}

/** Estado de reordenado para un LazyVerticalGrid. [onMove] recibe (posición actual, posición nueva). */
@Composable
fun rememberGridReorderState(
    gridState: LazyGridState,
    onMove: (Int, Int) -> Unit,
    onDrop: (Any, Boolean) -> Unit
): ReorderState {
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val reorder = remember(gridState) {
        ReorderState(
            visible = {
                gridState.layoutInfo.visibleItemsInfo.map {
                    ReorderItemInfo(
                        it.key, it.index,
                        it.offset.x.toFloat(), it.offset.y.toFloat(),
                        it.size.width.toFloat(), it.size.height.toFloat()
                    )
                }
            },
            viewportHeight = { gridState.layoutInfo.viewportSize.height },
            scrollBy = { gridState.scrollBy(it) },
            edgePx = with(density) { 72.dp.toPx() },
            onMove = { from, to -> move(from, to) },
            onDrop = { key, moved -> drop(key, moved) }
        )
    }
    AutoScrollEffect(reorder)
    return reorder
}

/** Estado de reordenado para un LazyColumn. */
@Composable
fun rememberListReorderState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDrop: (Any, Boolean) -> Unit
): ReorderState {
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val reorder = remember(listState) {
        ReorderState(
            visible = {
                val info = listState.layoutInfo
                val width = info.viewportSize.width.toFloat()
                info.visibleItemsInfo.map {
                    ReorderItemInfo(it.key, it.index, 0f, it.offset.toFloat(), width, it.size.toFloat())
                }
            },
            viewportHeight = { listState.layoutInfo.viewportSize.height },
            scrollBy = { listState.scrollBy(it) },
            edgePx = with(density) { 72.dp.toPx() },
            onMove = { from, to -> move(from, to) },
            onDrop = { key, moved -> drop(key, moved) }
        )
    }
    AutoScrollEffect(reorder)
    return reorder
}

/**
 * Copia de trabajo de [source] que se puede reordenar mientras se arrastra. Se vuelve a sincronizar
 * cuando [source] cambia (p. ej. tras guardar el orden o refrescar la lista).
 */
@Composable
fun <T> rememberReorderList(source: List<T>): SnapshotStateList<T> {
    val list = remember { mutableStateListOf<T>().apply { addAll(source) } }
    LaunchedEffect(source) {
        if (list.toList() != source) {
            list.clear()
            list.addAll(source)
        }
    }
    return list
}

fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

/**
 * Detecta toque largo y arrastre sobre la lista. Antes del toque largo todo funciona como siempre
 * (desplazar, tocar). Si el dedo se mueve antes de tiempo se trata de un desplazamiento normal.
 */
@Composable
fun Modifier.reorderGestures(state: ReorderState): Modifier {
    val haptic = LocalHapticFeedback.current
    return pointerInput(state) {
        awaitEachGesture {
            // Se observa en la fase Initial (antes que la lista y los clics de los elementos)
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val item = state.itemAt(down.position) ?: return@awaitEachGesture
            val slop = viewConfiguration.touchSlop

            val cancelledEarly = withTimeoutOrNull<Boolean>(viewConfiguration.longPressTimeoutMillis) {
                var cancelled = false
                while (!cancelled) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    cancelled = change == null || !change.pressed ||
                        (change.position - down.position).getDistance() > slop
                }
                true
            }
            if (cancelledEarly != null) return@awaitEachGesture // toque corto o desplazamiento: no es un toque largo

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            state.start(item, down.position)
            var last = down.position
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // Se consume todo (también el levantar el dedo): ni se desplaza la lista ni se abre el elemento
                    change.consume()
                    if (!change.pressed) {
                        completed = true
                        break
                    }
                    last = change.position
                    state.drag(last)
                }
            } finally {
                if (state.isDragging) {
                    // Menos de 2 veces el umbral de movimiento = toque largo "quieto" (en carpetas, el menú).
                    // Si el gesto se interrumpió (completed = false) no se abre ningún menú.
                    state.end(
                        cancelled = !completed,
                        movedEnough = (last - down.position).getDistance() > slop * 2
                    )
                }
            }
        }
    }
}

/**
 * Aspecto del elemento mientras se arrastra: se levanta (sombra), crece un poco y se vuelve algo
 * transparente. Los demás elementos no cambian.
 */
fun Modifier.dragVisual(state: ReorderState, key: Any, shape: Shape): Modifier =
    zIndex(if (state.isDragged(key)) 1f else 0f)
        .graphicsLayer {
            if (state.isDragged(key)) {
                val t = state.translationOf(key)
                translationX = t.x
                translationY = t.y
                scaleX = 1.04f
                scaleY = 1.04f
                alpha = 0.9f
                shadowElevation = 16.dp.toPx()
                this.shape = shape
                clip = false
            }
        }
