package repertoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import repertoire.core.EngineEntry
import repertoire.core.EngineLine
import repertoire.core.EngineSession
import repertoire.core.Uci
import repertoire.core.launchEngine

/**
 * A chess engine on this computer analysing the board (Ctrl+B r): the best few lines with their
 * score, deeper and deeper until the position changes. The engine runs only while the window is
 * open; a click on a line plays its first move in the editor, Shift+click the whole line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnginePanel(vm: AppViewModel, modifier: Modifier = Modifier, handle: Modifier = Modifier) {
    val settings = vm.library.settings
    Surface(modifier.width(340.dp).heightIn(max = 300.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(12.dp)) {
            val engine = settings.chosenEngine
            // The title is the handle to drag the window by.
            Row(handle, verticalAlignment = Alignment.CenterVertically) {
                Text("⠿ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(engine?.name ?: "Engine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.engineOpen = false }) { Text("✕") }
            }
            if (settings.engines.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in settings.engines) {
                        FilterChip(selected = e == engine, onClick = { vm.chooseEngine(e.path) }, label = { Text(e.name) })
                    }
                }
            }
            when {
                LocalTouch.current -> Hint("Engines laufen nur am Computer.")
                engine == null -> {
                    Hint("Noch keine Engine eingerichtet. Unter Konfiguration → Engines den Pfad zu einer UCI-Engine (z. B. Stockfish) angeben.")
                    TextButton(onClick = vm::openConfig) { Text("Engine einrichten") }
                }
                else -> Analysis(vm, engine)
            }
        }
    }
}

@Composable
private fun Analysis(vm: AppViewModel, engine: EngineEntry) {
    var session by remember { mutableStateOf<EngineSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<EngineLine>>(emptyList()) }

    // The engine runs as long as this window shows it with these settings.
    LaunchedEffect(engine.path, engine.threads, engine.hashMb, engine.lines) {
        error = null
        session = null
        val s = try {
            EngineSession(launchEngine(engine.path)).also {
                it.start(
                    mapOf(
                        "Threads" to engine.threads.toString(),
                        "Hash" to engine.hashMb.toString(),
                        "MultiPV" to engine.lines.toString(),
                    ),
                )
            }
        } catch (e: IllegalStateException) {
            error = e.message
            return@LaunchedEffect
        }
        session = s
        try {
            awaitCancellation()
        } finally {
            session = null
            s.close()
        }
    }

    val position = vm.boardPosition
    LaunchedEffect(session, position?.fen, engine.depth) {
        lines = emptyList()
        val s = session ?: return@LaunchedEffect
        if (position == null || position.legalMoves().isEmpty()) return@LaunchedEffect
        s.analyse(position, engine.depth.takeIf { it > 0 }) { lines = it }
    }

    when {
        error != null -> Text(error!!, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
        position == null -> Hint("Die Engine rechnet die Stellung auf dem Brett – öffne eine Variante oder ein Puzzle.")
        position.legalMoves().isEmpty() -> Hint("Kein Zug mehr möglich.")
        session == null -> Hint("Engine startet …")
        lines.isEmpty() -> Hint("Rechnet …")
        else -> {
            val limit = engine.depth
            val depth = lines.first().depth
            Hint("Tiefe $depth" + if (limit > 0) (if (depth >= limit) " – fertig" else " von $limit") else "")
            Spacer(Modifier.height(4.dp))
            // Whether Shift was held as the line was clicked: seen on the way in, before the click takes it.
            var shift by remember { mutableStateOf(false) }
            for (line in lines) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) shift = awaitPointerEvent(PointerEventPass.Initial).keyboardModifiers.isShiftPressed
                            }
                        }
                        .clickable { if (shift) vm.playSuggestedLine(line.pv) else line.pv.firstOrNull()?.let(vm::playSuggested) }
                        .padding(vertical = 3.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line.score.label, modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(
                        Uci.sanLine(position, line.pv),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
