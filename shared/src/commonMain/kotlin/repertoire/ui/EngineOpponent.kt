package repertoire.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import repertoire.core.EngineLine
import repertoire.core.EngineSession
import repertoire.core.launchEngine
import repertoire.core.processorCount

/** The depths − and + step through for the engine as opponent: a move needs an end, so no "unlimited". */
private val OPPONENT_DEPTHS = ENGINE_DEPTHS.filter { it > 0 }

/** Any depth from 1 can be typed. */
private val OPPONENT_DEPTH = 1..ENGINE_DEPTH.last

private fun stepDepth(depth: Int, by: Int): Int = stepIn(OPPONENT_DEPTHS, depth, by)

/** "beste", "zweitbeste", "drittbeste": which of its moves the engine as opponent plays. */
fun rankWord(rank: Int): String = when (rank) {
    1 -> "beste"
    2 -> "zweitbeste"
    else -> "drittbeste"
}

/**
 * Ctrl+B Shift+R in the editor: which side the engine plays from here on, how deep it searches
 * each move and which of its moves it plays. Keys: W / S (or ← →) the side, ↑ ↓ (or + −) the depth,
 * 1 2 3 the best, second or third best move, Enter starts, A turns it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpponentDialog(vm: AppViewModel) {
    val e = vm.editor
    val settings = vm.library.settings
    val engine = settings.chosenEngine
    val current = e?.opponent
    // The side to move by default: the engine goes on from the position on the board.
    var white by remember { mutableStateOf(current?.white ?: e?.position?.whiteToMove ?: true) }
    var depth by remember { mutableStateOf(current?.depth ?: settings.opponentDepth.coerceIn(OPPONENT_DEPTH)) }
    var rank by remember { mutableStateOf(current?.rank ?: settings.opponentRank.coerceIn(OPPONENT_RANKS)) }
    val usable = e != null && engine != null && !LocalTouch.current
    val start = { if (usable) vm.setOpponent(EngineOpponent(white, depth, rank)) }
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = { vm.opponentDialog = false },
        title = { Text("Engine als Gegner") },
        text = {
            LaunchedEffect(Unit) { focus.requestFocus() }
            Column(
                Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !usable) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.W, Key.DirectionLeft -> white = true
                        Key.S, Key.DirectionRight -> white = false
                        Key.DirectionUp, Key.Plus, Key.NumPadAdd -> depth = stepDepth(depth, 1)
                        Key.DirectionDown, Key.Minus, Key.NumPadSubtract -> depth = stepDepth(depth, -1)
                        // Digits in the depth field are the depth.
                        Key.One, Key.NumPad1 -> if (!vm.typing) rank = 1 else return@onPreviewKeyEvent false
                        Key.Two, Key.NumPad2 -> if (!vm.typing) rank = 2 else return@onPreviewKeyEvent false
                        Key.Three, Key.NumPad3 -> if (!vm.typing) rank = 3 else return@onPreviewKeyEvent false
                        Key.Enter, Key.NumPadEnter -> start()
                        Key.A -> if (current != null) vm.setOpponent(null) else return@onPreviewKeyEvent false
                        Key.Escape -> vm.opponentDialog = false
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
            ) {
                when {
                    LocalTouch.current -> Hint("Engines laufen nur am Computer.")
                    engine == null -> {
                        Hint("Noch keine Engine eingerichtet. Unter Konfiguration → Engines den Pfad zu einer UCI-Engine (z. B. Stockfish) angeben.")
                        TextButton(onClick = { vm.opponentDialog = false; vm.openConfig() }) { Text("Engine einrichten") }
                    }
                    else -> {
                        Hint(
                            "Ab der Stellung auf dem Brett zieht die Engine für ihre Seite, sobald sie am Zug ist; du spielst die andere. " +
                                "Zurückgehen in der Variante lässt sie in Ruhe, erst am Ende zieht sie wieder.",
                        )
                        if (settings.engines.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (en in settings.engines) {
                                    FilterChip(selected = en == engine, onClick = { vm.chooseEngine(en.path) }, label = { Text(en.name) })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Die Engine spielt", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = white, onClick = { white = true }, label = { Text("Weiß (W)") })
                            FilterChip(selected = !white, onClick = { white = false }, label = { Text("Schwarz (S)") })
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Tiefe pro Zug", fontWeight = FontWeight.SemiBold)
                        NumberStepper(
                            vm, depth, OPPONENT_DEPTH, onValue = { depth = it },
                            onMinus = { depth = stepDepth(depth, -1) }, onPlus = { depth = stepDepth(depth, 1) },
                        )
                        Hint("Eintippen geht jede Tiefe bis ${OPPONENT_DEPTH.last}; ↑ ↓ springen in Schritten, Enter startet.")
                        Spacer(Modifier.height(12.dp))
                        Text("Sie spielt", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (r in OPPONENT_RANKS) {
                                FilterChip(selected = rank == r, onClick = { rank = r }, label = { Text("$r. ($r)") })
                            }
                        }
                        Hint(
                            "Der ${rankWord(rank)} Zug nach ihrer Rechnung. Mit dem zweit- oder drittbesten Zug spielt sie " +
                                "Nebenvarianten, auf die du auch vorbereitet sein willst. Hat sie weniger Züge zur Wahl, nimmt sie den schlechtesten davon.",
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Kerne und Speicher", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Threads", modifier = Modifier.width(72.dp))
                            NumberStepper(
                                vm, engine.threads, engineThreads(engine.threads), onValue = { vm.setEngineThreads(engine.path, it) },
                                onMinus = { vm.setEngineThreads(engine.path, engine.threads - 1) },
                                onPlus = { vm.setEngineThreads(engine.path, engine.threads + 1) },
                                fieldWidth = 80,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hash", modifier = Modifier.width(72.dp))
                            NumberStepper(
                                vm, engine.hashMb, ENGINE_HASH_MB, onValue = { vm.setEngineHash(engine.path, it) },
                                onMinus = { vm.setEngineHash(engine.path, engine.hashMb / 2) },
                                onPlus = { vm.setEngineHash(engine.path, engine.hashMb * 2) },
                                unit = "MB", fieldWidth = 80,
                            )
                        }
                        Hint("Gilt für ${engine.name} überall, auch in der Analyse (Konfiguration → Engines). Dieser Computer hat ${processorCount()} Kerne.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = start, enabled = usable) { Text(if (current != null) "Übernehmen" else "Starten") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { vm.setOpponent(null) }) { Text("Ausschalten (A)") }
                TextButton(onClick = { vm.opponentDialog = false }) { Text("Abbrechen") }
            }
        },
    )
}

/**
 * Under the board while the engine plays a side: what it does, and the engine itself. It runs as
 * long as this is on screen; when it is its move, it searches to the depth with as many lines as
 * the rank needs and plays the first move of that line.
 */
@Composable
fun OpponentBar(vm: AppViewModel, e: EditorState) {
    val opponent = e.opponent ?: return
    val engine = vm.library.settings.chosenEngine
    var session by remember { mutableStateOf<EngineSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // The depth reached while it searches; null while it waits for its move.
    var searching by remember { mutableStateOf<Int?>(null) }

    if (engine != null && !LocalTouch.current) {
        LaunchedEffect(engine.path, engine.threads, engine.hashMb, opponent.rank) {
            error = null
            session = null
            val s = try {
                EngineSession(launchEngine(engine.path)).also {
                    it.start(mapOf("Threads" to engine.threads.toString(), "Hash" to engine.hashMb.toString(), "MultiPV" to opponent.rank.toString()))
                }
            } catch (ex: IllegalStateException) {
                error = ex.message
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

        val position = e.position
        val toMove = vm.opponentToMove
        LaunchedEffect(session, position.fen, toMove, opponent) {
            searching = null
            val s = session ?: return@LaunchedEffect
            if (!toMove) return@LaunchedEffect
            searching = 0
            var found = emptyList<EngineLine>()
            s.analyse(position, opponent.depth) { lines ->
                found = lines
                lines.firstOrNull()?.let { searching = it.depth }
            }
            searching = null
            opponentMove(found, opponent.rank)?.let { vm.opponentPlay(position.fen, it) }
        }
    }

    val side = if (opponent.white) "Weiß" else "Schwarz"
    val status = when {
        LocalTouch.current -> "Engines laufen nur am Computer."
        engine == null -> "Keine Engine eingerichtet."
        error != null -> error!!
        session == null -> "startet …"
        searching != null -> "rechnet … Tiefe $searching von ${opponent.depth}"
        e.playing -> "wartet"
        !e.atTip -> "wartet, bis du am Ende der Variante bist"
        e.position.legalMoves().isEmpty() -> "kein Zug mehr möglich"
        else -> "du bist am Zug"
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "♞ ${engine?.name ?: "Engine"} spielt $side · Tiefe ${opponent.depth}" +
                (if (opponent.rank > 1) " · ${rankWord(opponent.rank)}r Zug" else "") + " · $status",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) Color(0xFFE57373) else Color(0xFF90CAF9),
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = vm::openOpponent) { Text("Ändern") }
        TextButton(onClick = { vm.setOpponent(null) }) { Text("Aus") }
    }
}

/** The move to play from the engine's [lines]: the first of line [rank], or of the last line if there are fewer. */
fun opponentMove(lines: List<EngineLine>, rank: Int): String? =
    (lines.firstOrNull { it.multipv == rank } ?: lines.lastOrNull())?.pv?.firstOrNull()

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
