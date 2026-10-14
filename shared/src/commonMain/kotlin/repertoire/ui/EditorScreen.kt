package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import repertoire.core.numberedSan

@Composable
fun EditorScreen(vm: AppViewModel, e: EditorState, modifier: Modifier = Modifier) {
    val position = e.position
    Column(modifier.fillMaxSize().padding(16.dp)) {
        val playlist = e.playlist
        Text(
            when {
                playlist != null -> "Playlist: ${playlist.title} · ${playlist.index + 1} von ${playlist.ids.size}"
                e.editingId == null -> "Neue Variante"
                e.browsing -> "Variante durchgehen"
                else -> "Variante bearbeiten"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        e.note?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF90CAF9))
        }
        if (playlist != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.playlistStep(-1) }, enabled = playlist.index > 0) { Text("← Vorherige") }
                TextButton(onClick = { vm.playlistStep(1) }, enabled = !playlist.last) { Text("Nächste →") }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoardWithPanel(
            board = { modifier ->
                if (vm.boardCovered) {
                    CoveredBoard(vm, e.boardMoves.runningFold(e.start) { p, m -> p.play(m) }.zip(e.boardMoves).map { (p, m) -> numberedSan(p, m) }, modifier)
                } else ChessBoard(
                    position = position,
                    whiteBottom = e.playWhite != vm.editorFlipped,
                    lastMove = e.boardMoves.lastOrNull(),
                    marks = emptyMap(),
                    interactive = true,
                    onMove = vm::boardMove,
                    modifier = modifier,
                )
            },
            top = { Chalkboard(e.opening, e.name.ifBlank { "Name der Variante" }) },
        ) {
            Spacer(Modifier.height(12.dp))
            if (vm.blind) {
                // Entering a variant blind: moves are said (or typed) and said back.
                VoiceField(vm)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::peek) { Text(if (vm.peeking) "Brett verdecken" else "Brett aufdecken") }
                    VoiceStatus(vm)
                }
                Spacer(Modifier.height(12.dp))
            } else if (vm.heard != null || vm.listening || vm.transcribing) {
                VoiceStatus(vm)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.editorGoTo(0) }, enabled = e.cursor > 0) { Text("|◀") }
                OutlinedButton(onClick = vm::editorStepBack, enabled = e.cursor > 0) { Text("◀") }
                OutlinedButton(onClick = vm::editorStepForward, enabled = if (e.inSide) e.sideCursor < e.side.size else !e.atEnd) { Text("▶") }
                OutlinedButton(onClick = vm::editorToEnd, enabled = !e.atEnd) { Text("▶|") }
            }
            Spacer(Modifier.height(8.dp))
            Player(vm, e)
            OpponentBar(vm, e)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    e.browsing -> "Zug ${e.cursor} von ${e.moves.size}. Durchgehen: ein anderer Zug auf dem Brett öffnet einen Nebenstrang, " +
                        "die Variante bleibt, wie sie ist."
                    !e.atEnd -> "Zug ${e.cursor} von ${e.moves.size}. Ein anderer Zug auf dem Brett ersetzt ab hier den Rest."
                    e.setUp -> "Spiele auf dem Brett die Züge ab der eingefügten Stellung (beide Seiten)."
                    else -> "Spiele auf dem Brett die Züge vom Start bis zur Stellung (beide Seiten)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e.setUp) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Startet ab einer eigenen Stellung, nicht ab der Grundstellung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::editorResetStart) { Text("Doch ab Grundstellung") }
                }
            }
            Spacer(Modifier.height(8.dp))
            MoveList(vm, e)
            SideLine(vm, e)
            Spacer(Modifier.height(8.dp))
            if (e.browsing) {
                // Going through changes no move; to change them, switch over.
                OutlinedButton(onClick = vm::editorStartEditing) { Text("Züge bearbeiten") }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.atEnd) {
                    OutlinedButton(onClick = vm::editorUndo, enabled = e.moves.isNotEmpty()) { Text("Zug zurück") }
                } else {
                    OutlinedButton(onClick = vm::editorTruncate) { Text("Ab hier kürzen") }
                }
                OutlinedButton(onClick = vm::editorClear, enabled = e.moves.isNotEmpty()) { Text("Alle löschen") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                e.opening, { e.opening = it }, label = { Text("Eröffnung (z. B. Sizilianisch)") },
                singleLine = true, modifier = Modifier.typing(vm).fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                e.name, { e.name = it }, label = { Text("Variante (z. B. Scheveningen)") },
                singleLine = true, modifier = Modifier.typing(vm).fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ich spiele:", modifier = Modifier.padding(end = 8.dp))
                FilterChip(selected = e.playWhite, onClick = { e.playWhite = true }, label = { Text("Weiß") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = !e.playWhite, onClick = { e.playWhite = false }, label = { Text("Schwarz") })
            }
            vm.editorDuplicate()?.let {
                Spacer(Modifier.height(12.dp))
                Text("Diese Stellung ist schon gespeichert als „$it“.", color = Color(0xFFFFB74D))
            }
            if (vm.editorRestartsProgress()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Geänderte Züge sind ein neues Puzzle: der Lernstand dieser Variante fängt beim Speichern von vorn an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::saveEditor, enabled = e.name.isNotBlank() && e.moves.isNotEmpty()) { Text("Speichern") }
                TextButton(onClick = vm::cancelEditor) { Text("Abbrechen") }
            }
        }
    }
}

/** Play button, speed and repeat: the variant runs by itself, one move per delay, until stopped or at its end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Player(vm: AppViewModel, e: EditorState) {
    val settings = vm.library.settings
    LaunchedEffect(e, e.playing, e.cursor, settings.playDelayMs) {
        if (!e.playing) return@LaunchedEffect
        // In a playlist the final position stays twice as long, before the next variant starts.
        delay(settings.playDelayMs.toLong() * if (e.playlist != null && e.atEnd) 2 else 1)
        vm.editorPlayTick()
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = vm::editorTogglePlay, enabled = e.moves.isNotEmpty()) {
            Text(if (e.playing) "⏸ Pause" else "▶ Abspielen")
        }
        // Less time per move is faster, so "−" sits on the left like on a stepper.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { vm.setPlayDelay(settings.playDelayMs - PLAY_DELAY_INCREMENT) },
                enabled = settings.playDelayMs > PLAY_DELAY_MIN,
            ) { Text("−") }
            Text(
                formatSeconds(settings.playDelayMs.toLong()) + " / Zug",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(
                onClick = { vm.setPlayDelay(settings.playDelayMs + PLAY_DELAY_INCREMENT) },
                enabled = settings.playDelayMs < PLAY_DELAY_MAX,
            ) { Text("+") }
        }
        FilterChip(selected = settings.playLoop, onClick = { vm.setPlayLoop(!settings.playLoop) }, label = { Text("Wiederholen") })
    }
}

/**
 * Every move of the variant, clickable: the board jumps to that point without losing what comes
 * after it. The move on the board is highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveList(vm: AppViewModel, e: EditorState) {
    if (e.moves.isEmpty()) {
        Text("Noch keine Züge", style = MaterialTheme.typography.bodyLarge)
        return
    }
    val positions = e.positions
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MoveChip("Start", e.cursor == 0 && !e.inSide) { vm.editorGoTo(0) }
        e.moves.forEachIndexed { i, move ->
            val before = positions[i]
            // "1. e4" for White, the bare move for Black, as in a game score.
            val label = if (before.whiteToMove) numberedSan(before, move) else before.san(move)
            // In a side line no move of the variant is marked: the board is off it.
            MoveChip(label, e.cursor == i + 1 && !e.inSide) { vm.editorGoTo(i + 1) }
        }
    }
}

/** The side line tried while going through, under the variant, and the way back to it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideLine(vm: AppViewModel, e: EditorState) {
    if (!e.inSide) return
    Spacer(Modifier.height(8.dp))
    Text("Nebenstrang (nur zum Anschauen, wird nicht gespeichert)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    var before = e.shown.fold(e.start) { p, m -> p.play(m) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        e.side.forEachIndexed { i, move ->
            // Its first move with its number, also for Black ("5... Nc6"), then as in a game score.
            val label = if (before.whiteToMove || i == 0) numberedSan(before, move) else before.san(move)
            before = before.play(move)
            MoveChip(label, e.sideCursor == i + 1) {
                e.playing = false
                e.sideCursor = i + 1
            }
        }
    }
    TextButton(onClick = vm::backToLine) {
        Text("↩ Zurück zur Variante" + if (LocalTouch.current) "" else " (Strg+B Enter)")
    }
}

@Composable
private fun MoveChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

