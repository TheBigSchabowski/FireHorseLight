package repertoire.ui

import androidx.compose.animation.AnimatedVisibility
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlindfoldScreen(vm: BlindfoldViewModel, modifier: Modifier = Modifier) {
    val voxFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { voxFocus.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // A custom color has no matching content color in the scheme; without it text would be black.
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFF111318),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top-Leiste: Titel + FireHorse JSON Import + Seitenwahl
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "FireHorse Light · English Voice (" + vm.library.active.size + " Varianten)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::importFireHorseJson) {
                            Text("📂 FireHorse JSON laden")
                        }
                        FilterChip(
                            selected = vm.playWhite,
                            onClick = { vm.resetSession(newPlayWhite = true) },
                            label = { Text("White") },
                        )
                        FilterChip(
                            selected = !vm.playWhite,
                            onClick = { vm.resetSession(newPlayWhite = false) },
                            label = { Text("Black") },
                        )
                        OutlinedButton(onClick = { vm.resetSession() }) {
                            Text("↺ Reset")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quelle (FireHorse JSON vs. Lichess) + Eröffnungs-Filter + Zieh-Strategie
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (src in ResponseSource.entries) {
                        FilterChip(
                            selected = vm.source == src,
                            onClick = { vm.source = src },
                            label = { Text(src.label) },
                        )
                    }
                    for (strat in ReplyStrategy.entries) {
                        FilterChip(
                            selected = vm.strategy == strat,
                            onClick = { vm.strategy = strat },
                            label = { Text(strat.label) },
                        )
                    }
                }

                // Falls eigene Eröffnungen aus FireHorse vorhanden sind, als Schnell-Chips anzeigen:
                if (vm.openings.isNotEmpty() && vm.source == ResponseSource.FIREHORSE_LIBRARY) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = vm.selectedOpening == null,
                            onClick = { vm.selectedOpening = null },
                            label = { Text("Alle Eröffnungen") },
                        )
                        for (op in vm.openings) {
                            FilterChip(
                                selected = vm.selectedOpening == op,
                                onClick = { vm.selectedOpening = op },
                                label = { Text(op) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // GROSSE INTERAKTIONS-FLÄCHE:
                // 1x Tippen -> Führt den von Voxtype eingesprochenen Zug aus (und fokussiert das Voxtype-Feld)
                // 2x Tippen (Doppeltipp) -> Zeigt / Versteckt das echte FireHorse-Schachbrett!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF181B22), RoundedCornerShape(18.dp))
                        .border(2.dp, Color(0xFF2E3440), RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    voxFocus.requestFocus()
                                    vm.submitVoxtypeBuffer()
                                },
                                onDoubleTap = { vm.toggleBoard() },
                            )
                        }
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.showBoard) {
                        // Das originale Schachbrett aus FireHorse (mit deinen cburnett-Figuren!)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ChessBoard(
                                position = vm.position,
                                whiteBottom = vm.playWhite,
                                lastMove = vm.lastMove,
                                marks = emptyMap(),
                                interactive = true,
                                onMove = vm::onBoardMove,
                                modifier = Modifier.sizeIn(maxWidth = 420.dp, maxHeight = 420.dp).aspectRatio(1f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Double-tap anywhere outside to hide board", color = Color.Gray)
                        }
                    } else {
                        // Blindfold Kreidetafel-Ansicht
                        val lastUser = vm.history.lastOrNull { it.byUser }
                        val lastReply = vm.history.lastOrNull { !it.byUser }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (vm.history.isEmpty()) {
                                    "Blindfold Ready\nSay e.g. \"e4\", \"knight f3\", \"bishop c4\""
                                } else {
                                    "${lastUser?.spokenEnglish ?: "…"}   →   ${lastReply?.spokenEnglish ?: "…"}"
                                },
                                fontSize = 32.sp,
                                // The theme's body line height (24sp) would make the two lines overlap.
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = vm.status,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF90CAF9),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Voxtype-Direkteingabefeld (hält dauerhaft den Fokus für deinen globalen Voxtype-Hotkey!)
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
                    label = { Text("🎙️ Voxtype Input (English: \"bishop c4\", \"knight c3\", \"c takes d4\" + Enter/Tap)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.submitVoxtypeBuffer() }),
                )
            }
        }
    }
}
