package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import repertoire.core.Variant
import repertoire.core.compactMoves

private val ROW_BG = Color(0xFF181B22)

/** Deine Varianten nach Eröffnung, mit Üben, Bearbeiten und Löschen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.LibraryPane(vm: BlindfoldViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        Button(onClick = vm::newVariant) { Text("+ Neue Variante") }
        OutlinedButton(onClick = vm::importFireHorseJson) { Text("📂 FireHorse JSON laden") }
        Text(
            "${vm.library.active.size} Varianten",
            color = MUTED,
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
        )
    }
    if (vm.status.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(vm.status, color = STATUS_BLUE)
    }
    Spacer(Modifier.height(10.dp))

    val groups = vm.library.groups()
    if (groups.isEmpty()) {
        Text(
            "Noch keine Varianten. Leg mit „+ Neue Variante“ eine an: Züge auf dem Brett spielen oder ansagen, " +
                "Eröffnung und Namen eintragen, speichern. Varianten aus FireHorse erscheinen hier von selbst.",
            color = MUTED,
        )
        return
    }

    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (group in groups) {
            item(key = "opening:" + group.opening) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.opening,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.trainOpening(group.opening) }) {
                        Text("🎯 Alle ${group.variants.size} blind üben")
                    }
                }
            }
            items(group.variants, key = { it.id }) { v -> VariantRow(vm, v) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantRow(vm: BlindfoldViewModel, v: Variant) {
    Column(
        Modifier.fillMaxWidth()
            .background(ROW_BG, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.padding(end = 8.dp).widthIn(max = 560.dp)) {
                Text(v.name.ifBlank { "(ohne Namen)" }, fontWeight = FontWeight.Bold)
                Text(
                    (if (v.playWhite) "♙ Weiß · " else "♟ Schwarz · ") + compactMoves(v.numberedSan()),
                    color = MUTED,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (vm.pendingDelete == v.id) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Wirklich löschen?", color = Color(0xFFFFB4AB))
                    Button(
                        onClick = { vm.deleteVariant(v.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF93000A)),
                    ) { Text("Löschen") }
                    OutlinedButton(onClick = { vm.pendingDelete = null }) { Text("Nein") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { vm.trainVariant(v.id) }, enabled = v.moves.isNotEmpty()) { Text("🎯 Üben") }
                    OutlinedButton(onClick = { vm.editVariant(v.id) }) { Text("✏️") }
                    OutlinedButton(onClick = { vm.pendingDelete = v.id }) { Text("🗑") }
                }
            }
        }
    }
}

/** Eine Variante anlegen oder bearbeiten: Züge auf dem Brett oder per Stimme, dazu Eröffnung, Name und Seite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.EditorPane(vm: BlindfoldViewModel, draft: VariantDraft, voxFocus: FocusRequester) {
    Text(
        if (draft.editingId == null) "Neue Variante" else "Variante bearbeiten",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = draft.opening,
            onValueChange = { text -> vm.updateDraft { it.copy(opening = text) } },
            label = { Text("Eröffnung (z. B. Sizilianisch)") },
            singleLine = true,
            modifier = Modifier.widthIn(min = 220.dp, max = 360.dp),
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { text -> vm.updateDraft { it.copy(name = text) } },
            label = { Text("Variante (z. B. Najdorf)") },
            singleLine = true,
            modifier = Modifier.widthIn(min = 220.dp, max = 360.dp),
        )
        FilterChip(
            selected = draft.playWhite,
            onClick = { vm.updateDraft { it.copy(playWhite = true) } },
            label = { Text("Ich spiele Weiß") },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        FilterChip(
            selected = !draft.playWhite,
            onClick = { vm.updateDraft { it.copy(playWhite = false) } },
            label = { Text("Ich spiele Schwarz") },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
    // Vorhandene Eröffnungen zum Antippen, damit dieselbe Eröffnung nicht in zwei Schreibweisen entsteht.
    val others = vm.openings.filter { it != draft.opening.trim() }
    if (others.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (op in others) {
                AssistChip(onClick = { vm.updateDraft { it.copy(opening = op) } }, label = { Text(op) })
            }
        }
    }
    Spacer(Modifier.height(6.dp))

    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        ChessBoard(
            position = draft.position,
            whiteBottom = draft.playWhite,
            lastMove = draft.moves.lastOrNull(),
            marks = emptyMap(),
            interactive = true,
            onMove = vm::onBoardMove,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        draft.movesText().ifBlank { "Noch keine Züge – spiel sie auf dem Brett oder sag sie an." },
        color = if (draft.moves.isEmpty()) MUTED else Color.White,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::undoDraftMove, enabled = draft.moves.isNotEmpty()) { Text("↶ Zug zurück") }
        OutlinedButton(onClick = vm::clearDraftMoves, enabled = draft.moves.isNotEmpty()) { Text("Alle Züge löschen") }
        OutlinedButton(onClick = vm::cancelDraft) { Text("Abbrechen") }
        Button(onClick = vm::saveDraft, enabled = draft.canSave) { Text("💾 Speichern") }
    }
    if (!draft.canSave) {
        Text(
            if (draft.moves.isEmpty()) "Zum Speichern fehlen noch Züge." else "Zum Speichern fehlt noch der Name der Variante.",
            color = MUTED,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(6.dp))
    VoiceInput(vm, voxFocus, "\"e4\", \"knight f3\" · undo")
}
