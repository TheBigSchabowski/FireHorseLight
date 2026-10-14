package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import repertoire.core.Grade
import repertoire.core.Move
import repertoire.core.Variant
import repertoire.core.compactMoves
import repertoire.core.numberedSan

private val GOOD_COLOR = Color(0xFF7BC67B)
private val BAD_COLOR = Color(0xFFE57373)
private val INFO_COLOR = Color(0xFF90CAF9)

@Composable
fun TrainingScreen(vm: AppViewModel, session: TrainingSession, modifier: Modifier = Modifier) {
    // Read so that moves inside the (non-observable) attempt trigger recomposition.
    session.revision
    val attempt = session.attempt

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                if (session.untilClean) {
                    "Puzzle ${minOf(session.solved + 1, session.total)} von ${session.total}"
                } else {
                    "erledigt ${session.answered} · noch ${session.queue.size + if (attempt != null) 1 else 0}"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 16.dp),
            )
            OutlinedButton(onClick = vm::endSession) { Text("Beenden") }
        }
        Spacer(Modifier.height(12.dp))

        if (attempt == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (session.answered == 0) "Gerade nichts fällig." else "Fertig! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::endSession) { Text("Zurück") }
                }
            }
            return@Column
        }

        val variant = attempt.quiz.variant
        BoardWithPanel(
            board = { modifier ->
                if (vm.boardCovered) {
                    CoveredBoard(vm, attempt.played, modifier)
                } else {
                    ChessBoard(
                        position = attempt.position,
                        whiteBottom = variant.playWhite != session.flipped,
                        lastMove = attempt.lastMove,
                        marks = session.marks,
                        interactive = !attempt.finished,
                        onMove = vm::boardMove,
                        modifier = modifier,
                    )
                }
            },
            top = { Chalkboard(variant.opening, variant.name) },
        ) {
            Spacer(Modifier.height(10.dp))
            if (vm.blind) {
                BlindPanel(vm, session)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                when {
                    vm.blind && !attempt.singleMove ->
                        "Blind: du spielst ${if (variant.playWhite) "Weiß" else "Schwarz"}, die Gegenseite wird gespielt und angesagt."
                    !attempt.singleMove && variant.setUp ->
                        "Spiele beide Seiten ab dieser Stellung weiter, bis die Zielstellung auf dem Brett steht."
                    !attempt.singleMove -> "Spiele beide Seiten vom Start, bis diese Stellung auf dem Brett steht."
                    variant.positions[attempt.startPly].whiteToMove -> "Finde den nächsten Zug für Weiß."
                    else -> "Finde den nächsten Zug für Schwarz."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (attempt.played.isNotEmpty() && !vm.boardCovered) {
                Spacer(Modifier.height(10.dp))
                Text(compactMoves(attempt.played), style = MaterialTheme.typography.bodyMedium)
            }
            if (!vm.blind && vm.heard != null || vm.listening || vm.transcribing) {
                Spacer(Modifier.height(8.dp))
                VoiceStatus(vm)
            }

            session.feedback?.let { fb ->
                Spacer(Modifier.height(16.dp))
                Text(
                    fb.text,
                    color = when (fb.tone) {
                        FeedbackTone.GOOD -> GOOD_COLOR
                        FeedbackTone.BAD -> BAD_COLOR
                        FeedbackTone.INFO -> INFO_COLOR
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (attempt.finished) {
                Spacer(Modifier.height(12.dp))
                Text(
                    compactMoves(variant.numberedSan()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                if (session.graded && vm.mistakes(session) == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.grade(Grade.HARD) }) { Text("Schwer") }
                        Button(onClick = { vm.grade(Grade.GOOD) }) { Text("Gut ⏎") }
                        OutlinedButton(onClick = { vm.grade(Grade.EASY) }) { Text("Leicht") }
                    }
                } else if (session.untilClean) {
                    // Learning a playlist: on only after a clean solve.
                    Button(onClick = { vm.grade() }) {
                        Text(
                            when {
                                vm.mistakes(session) > 0 -> "Nochmal ⏎"
                                session.queue.isEmpty() -> "Gut – fertig ⏎"
                                else -> "Gut – nächstes Puzzle ⏎"
                            },
                        )
                    }
                } else {
                    Button(onClick = { vm.grade() }) { Text("Weiter ⏎") }
                }
            } else if (!attempt.singleMove) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = vm::toggleTarget) {
                    Text(if (session.showTarget) "Zielstellung ausblenden" else "Zielstellung zeigen (Hilfe)")
                }
                if (session.showTarget) {
                    ChessBoard(
                        position = variant.target,
                        whiteBottom = variant.playWhite,
                        lastMove = null,
                        marks = emptyMap(),
                        interactive = false,
                        onMove = {},
                        modifier = Modifier.size(260.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = vm::restartCurrent, enabled = attempt.started) {
                Text(if (attempt.singleMove) "↺ Nochmal" else "↺ Nochmal von vorn")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::suspendCurrent) { Text("Variante nicht mehr abfragen") }
        }
    }
}

/** Blind: the field for a move in words, and help without looking. */
@Composable
private fun BlindPanel(vm: AppViewModel, session: TrainingSession) {
    val attempt = session.attempt ?: return
    if (!attempt.finished) {
        VoiceField(vm)
        Spacer(Modifier.height(6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!attempt.finished) OutlinedButton(onClick = vm::hint) { Text("💡 Lösung") }
        OutlinedButton(onClick = vm::peek, enabled = !attempt.finished) { Text(if (vm.peeking) "Brett verdecken" else "Brett aufdecken") }
    }
}
