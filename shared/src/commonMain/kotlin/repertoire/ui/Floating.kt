package repertoire.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How much of a window always stays on screen, so it can be got hold of again. */
private const val KEEP_PX = 160f

/**
 * The engine and the database, bottom right to begin with; each can be dragged by its title to
 * wherever it is out of the way, and stays there while the app runs.
 */
@Composable
fun FloatingPanels(vm: AppViewModel, touch: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        /** Moves the window by its title; it may go left and up, as far as [KEEP_PX] of it stays in the window. */
        fun Modifier.dragsTo(at: MutableState<Offset>) = pointerHoverIcon(PointerIcon.Hand).pointerInput(width, height) {
            detectDragGestures { change, drag ->
                change.consume()
                val next = at.value + drag
                at.value = Offset(next.x.coerceIn(-(width - KEEP_PX), 0f), next.y.coerceIn(-(height - KEEP_PX), 0f))
            }
        }

        fun Modifier.at(at: MutableState<Offset>) = offset { IntOffset(at.value.x.roundToInt(), at.value.y.roundToInt()) }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, bottom = if (touch) 88.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.engineOpen) EnginePanel(vm, Modifier.at(vm.engineAt), handle = Modifier.dragsTo(vm.engineAt))
            if (vm.explorerOpen) ExplorerPanel(vm, Modifier.at(vm.explorerAt), handle = Modifier.dragsTo(vm.explorerAt))
        }
    }
}
