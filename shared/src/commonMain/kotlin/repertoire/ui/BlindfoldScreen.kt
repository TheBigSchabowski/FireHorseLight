package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import repertoire.core.ReplyStrategy
import repertoire.core.ResponseSource

internal val STATUS_BLUE = Color(0xFF90CAF9)
internal val MUTED = Color(0xFF9AA0A6)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlindfoldScreen(vm: BlindfoldViewModel, modifier: Modifier = Modifier) {
    val voxFocus = remember { FocusRequester() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // A custom color has no matching content color in the scheme; without it text would be black.
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFF111318),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Titel + die drei Bereiche
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "FireHorse Light",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in Mode.entries) {
                            FilterChip(
                                selected = vm.mode == m,
                                onClick = { vm.switchMode(m) },
                                label = { Text(m.label) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (vm.mode) {
                    Mode.TRAIN -> TrainPane(vm, voxFocus)
                    Mode.FREE -> FreePane(vm, voxFocus)
                    Mode.LIBRARY -> {
                        val draft = vm.draft
                        if (draft != null) EditorPane(vm, draft, voxFocus) else LibraryPane(vm)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------- Blind üben

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TrainPane(vm: BlindfoldViewModel, voxFocus: FocusRequester) {
    if (vm.openings.size > 1) {
        OpeningChips(vm.openings, vm.selectedOpening, vm::chooseTrainingOpening)
        Spacer(Modifier.height(8.dp))
    }

    val drill = vm.drill
    if (drill != null) {
        Chalkboard(drill.variant.opening, drill.variant.name)
        Spacer(Modifier.height(8.dp))
    }

    BlindArea(
        vm = vm,
        voxFocus = voxFocus,
        whiteBottom = drill?.variant?.playWhite ?: true,
        interactive = drill != null && !vm.drillDone && drill.isUsersTurn(vm.position),
        modifier = Modifier.weight(1f),
    ) {
        if (drill == null) {
            Text(vm.status, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (vm.roundSize > 0) {
                Button(onClick = vm::newRound) { Text("Neue Runde") }
            } else {
                Button(onClick = { vm.switchMode(Mode.LIBRARY) }) { Text("Zu meinen Varianten") }
            }
        } else {
            LastMoves(vm, placeholder = if (drill.isUsersTurn(vm.position)) "Dein Zug" else "…")
            Spacer(Modifier.height(12.dp))
            val counter = buildString {
                append("Halbzug ${vm.history.size} / ${drill.length}")
                append(" · Fehlversuche ${vm.mistakes}")
                if (vm.roundSize > 1) append(" · Variante ${vm.roundDone + 1} / ${vm.roundSize}")
            }
            Text(counter, color = MUTED, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(vm.status, style = MaterialTheme.typography.bodyLarge, color = STATUS_BLUE, textAlign = TextAlign.Center)
        }
    }

    Spacer(Modifier.height(8.dp))
    if (drill != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::hint, enabled = !vm.drillDone) { Text("💡 Lösung") }
            OutlinedButton(onClick = vm::restartDrill) { Text("↺ Von vorn") }
            OutlinedButton(onClick = vm::nextDrill) { Text("⏭ Nächste") }
            OutlinedButton(onClick = vm::toggleBoard) { Text(if (vm.showBoard) "Brett verbergen" else "Brett zeigen") }
        }
        Spacer(Modifier.height(8.dp))
        VoiceInput(vm, voxFocus, "\"knight f3\" · hint · next · restart · repeat · board")
    }
}

// ------------------------------------------------------------------------------------- Frei spielen

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FreePane(vm: BlindfoldViewModel, voxFocus: FocusRequester) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = vm.playWhite, onClick = { vm.resetSession(newPlayWhite = true) }, label = { Text("Weiß") })
        FilterChip(selected = !vm.playWhite, onClick = { vm.resetSession(newPlayWhite = false) }, label = { Text("Schwarz") })
        OutlinedButton(onClick = { vm.resetSession() }) { Text("↺ Neue Partie") }
        OutlinedButton(onClick = vm::saveFreeAsVariant, enabled = vm.history.isNotEmpty()) { Text("💾 Als Variante speichern") }
    }
    Spacer(Modifier.height(6.dp))
    // Quelle (FireHorse-Varianten vs. Lichess) + Zieh-Strategie
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (src in ResponseSource.entries) {
            FilterChip(selected = vm.source == src, onClick = { vm.source = src }, label = { Text(src.label) })
        }
        for (strat in ReplyStrategy.entries) {
            FilterChip(selected = vm.strategy == strat, onClick = { vm.strategy = strat }, label = { Text(strat.label) })
        }
    }
    if (vm.openings.isNotEmpty() && vm.source == ResponseSource.FIREHORSE_LIBRARY) {
        OpeningChips(vm.openings, vm.selectedOpening, vm::setFreeOpening)
    }
    Spacer(Modifier.height(8.dp))

    BlindArea(vm, voxFocus, whiteBottom = vm.playWhite, interactive = true, modifier = Modifier.weight(1f)) {
        if (vm.history.isEmpty()) {
            Text(
                "Sag deinen Zug, z. B. „e4“, „knight f3“, „bishop c4“",
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        } else {
            LastMoves(vm, placeholder = "…")
        }
        Spacer(Modifier.height(12.dp))
        Text(vm.status, style = MaterialTheme.typography.bodyLarge, color = STATUS_BLUE, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(8.dp))
    VoiceInput(vm, voxFocus, "\"bishop c4\" · undo · reset · repeat · board")
}

// ------------------------------------------------------------------------------------- Bausteine

/** Die Eröffnungen als Auswahl, „Alle“ vorneweg. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpeningChips(openings: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Alle Eröffnungen") })
        for (op in openings) {
            FilterChip(selected = selected == op, onClick = { onSelect(op) }, label = { Text(op) })
        }
    }
}

/** Dein letzter Zug und die Antwort darauf, groß für den Blick aus der Ferne. */
@Composable
private fun LastMoves(vm: BlindfoldViewModel, placeholder: String) {
    val last = vm.history.lastOrNull()
    val text = when {
        last == null -> placeholder
        last.byUser -> "${last.spokenEnglish}   →   …"
        else -> {
            val before = vm.history.getOrNull(vm.history.size - 2)?.takeIf { it.byUser }
            if (before != null) "${before.spokenEnglish}   →   ${last.spokenEnglish}" else last.spokenEnglish
        }
    }
    Text(text, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
}

/**
 * Die große Fläche: 1x Tippen führt den diktierten Zug aus (und holt den Fokus ins Eingabefeld),
 * 2x Tippen zeigt oder verbirgt das Schachbrett.
 */
@Composable
private fun BlindArea(
    vm: BlindfoldViewModel,
    voxFocus: FocusRequester,
    whiteBottom: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    blind: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF181B22), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFF2E3440), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        runCatching { voxFocus.requestFocus() }
                        vm.submitVoxtypeBuffer()
                    },
                    onDoubleTap = { vm.toggleBoard() },
                )
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (vm.showBoard) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChessBoard(
                    position = vm.position,
                    whiteBottom = whiteBottom,
                    lastMove = vm.lastMove,
                    marks = emptyMap(),
                    interactive = interactive,
                    onMove = vm::onBoardMove,
                    modifier = Modifier.weight(1f, fill = false).aspectRatio(1f),
                )
                Spacer(Modifier.height(6.dp))
                Text(vm.status, color = STATUS_BLUE, textAlign = TextAlign.Center)
                Text("Doppeltipp neben das Brett verbirgt es", color = MUTED)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = blind)
        }
    }
}

/** Das Eingabefeld für Voxtype und andere Diktierprogramme; es hält den Fokus für den Diktier-Hotkey. */
@Composable
internal fun VoiceInput(vm: BlindfoldViewModel, voxFocus: FocusRequester, hint: String) {
    LaunchedEffect(Unit) { runCatching { voxFocus.requestFocus() } }
    OutlinedTextField(
        value = vm.voxtypeBuffer,
        onValueChange = { typed ->
            // Wenn Voxtype am Ende einen Zeilenumbruch schickt, sofort ausführen!
            if ("\n" in typed) {
                vm.voxtypeBuffer = typed.replace("\n", "").trim()
                vm.submitVoxtypeBuffer()
            } else {
                vm.voxtypeBuffer = typed
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(voxFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                    vm.submitVoxtypeBuffer()
                    true
                } else {
                    false
                }
            },
        singleLine = true,
        label = { Text("🎙️ Zug auf Englisch + Enter: $hint") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { vm.submitVoxtypeBuffer() }),
    )
}
