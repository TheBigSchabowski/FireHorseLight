package repertoire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LINE = Color(0xFF5C6B8A)
private val PICKED = Color(0xFF90CAF9)
private val NODE = Color(0xFF1E2230)

/**
 * Ctrl+B g: the user's recordings on the left, the moves (and commands) on the right, a line from
 * each recording to what it stands for. Dragging a recording onto another move – or on a phone,
 * tapping the recording and then the move – links it there instead. ▶ plays it, ✕ throws it away.
 */
@Composable
fun GraphScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val links = remember(vm.graphRevision) { vm.voiceLinks() }
    val phrases = remember(vm.graphRevision, vm.library) { vm.graphPhrases(links) }
    val touch = LocalTouch.current

    // Where each node is inside the scrolling box, to draw the lines between them.
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val recordingAt = remember { mutableStateMapOf<String, Rect>() }
    val phraseAt = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragTo by remember { mutableStateOf(Offset.Zero) }
    var typed by remember { mutableStateOf("") }

    fun Modifier.placed(into: MutableMap<String, Rect>, key: String) = onGloballyPositioned { c ->
        box?.let { b -> into[key] = Rect(b.localPositionOf(c, Offset.Zero), c.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }) }
    }

    Column(modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stimme: Klang ↔ Zug", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.graphOpen = false }) { Text("Schließen") }
        }
        Text(
            (if (touch) "Aufnahme antippen, dann den Zug, für den sie stehen soll." else "Eine Aufnahme auf einen anderen Zug ziehen (oder anklicken, dann den Zug anklicken).") +
                " ▶ spielt sie ab, ✕ löscht sie. Jede Linie heißt: so klingt dieser Zug, wenn du ihn sagst.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (links.isEmpty()) {
            Text(
                "Noch keine Aufnahmen. Sie entstehen beim Üben mit der Stimme und im „🎙 Training“.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { box = it }) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        var take = 0
                        var previous = ""
                        for (link in links) {
                            take = if (link.phrase == previous) take + 1 else 1
                            previous = link.phrase
                            val picked = vm.graphPicked == link.name || dragging == link.name
                            Row(
                                Modifier.fillMaxWidth()
                                    .placed(recordingAt, link.name)
                                    .background(NODE, RoundedCornerShape(8.dp))
                                    .border(if (picked) 2.dp else 0.dp, if (picked) PICKED else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { vm.graphPicked = if (vm.graphPicked == link.name) null else link.name }
                                    .pointerInput(link.name) {
                                        detectDragGestures(
                                            onDragStart = { at ->
                                                dragging = link.name
                                                dragTo = (recordingAt[link.name]?.topLeft ?: Offset.Zero) + at
                                            },
                                            onDrag = { change, by ->
                                                change.consume()
                                                dragTo += by
                                            },
                                            onDragEnd = {
                                                phraseAt.entries.firstOrNull { it.value.contains(dragTo) }?.let { vm.relink(link.name, it.key) }
                                                dragging = null
                                            },
                                            onDragCancel = { dragging = null },
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { vm.playRecording(link.name) }) { Text("▶") }
                                Text("#$take", modifier = Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = { vm.deleteRecording(link.name) }) { Text("✕") }
                            }
                        }
                    }
                    Spacer(Modifier.width(if (touch) 48.dp else 140.dp))
                    Column(Modifier.weight(1f).widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (phrase in phrases) {
                            val target = vm.graphPicked != null
                            Text(
                                phrase,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                                    .placed(phraseAt, phrase)
                                    .background(NODE, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (target) PICKED.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable(enabled = target) { vm.graphPicked?.let { vm.relink(it, phrase) } }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                            )
                        }
                        // A move that is not in the list yet.
                        OutlinedTextField(
                            typed, { typed = it },
                            label = { Text("Anderer Zug, z. B. Qh5") },
                            singleLine = true,
                            modifier = Modifier.typing(vm).fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                val phrase = vm.phraseFor(typed)
                                val name = vm.graphPicked
                                if (phrase != null && name != null) vm.relink(name, phrase)
                                typed = ""
                            },
                            enabled = vm.graphPicked != null && typed.isNotBlank(),
                        ) { Text("Gewählte Aufnahme diesem Zug geben") }
                    }
                }
                // The lines, under nothing: from the right edge of a recording to the left edge of its move.
                Canvas(Modifier.matchParentSize()) {
                    for (link in links) {
                        val from = recordingAt[link.name] ?: continue
                        val start = Offset(from.right, from.center.y)
                        val end = if (dragging == link.name) dragTo else phraseAt[link.phrase]?.let { Offset(it.left, it.center.y) } ?: continue
                        val picked = vm.graphPicked == link.name || dragging == link.name
                        drawLine(if (picked) PICKED else LINE, start, end, strokeWidth = if (picked) 4f else 2f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
