package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import repertoire.core.Position
import repertoire.core.SpeechTest
import repertoire.core.VOICE_SAMPLE_TEXT
import repertoire.core.VoiceMove

private val COVER = Color(0xFF121212)
private val COVER_EDGE = Color(0xFF3A3A3A)
private val LISTEN = Color(0xFFE57373)
private val INFO = Color(0xFF90CAF9)
private val GOOD = Color(0xFF7BC67B)

/**
 * The board, covered – and the moves too: they are heard, not read ("repeat" says the last one
 * again). A double tap uncovers it (as do Ctrl+B n and "board").
 */
@Composable
fun CoveredBoard(vm: AppViewModel, position: Position, modifier: Modifier = Modifier) {
    val touch = LocalTouch.current
    Box(
        modifier
            .background(COVER, RoundedCornerShape(10.dp))
            .border(3.dp, COVER_EDGE, RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { vm.peek() }) }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🙈", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            // Only how far it is: which move it is, is the player's to know.
            Text(
                "Zug ${position.fullmoveNumber} – " + if (position.whiteToMove) "Weiß am Zug" else "Schwarz am Zug",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            VoiceStatus(vm, center = true)
            Spacer(Modifier.height(16.dp))
            Text(
                if (touch) {
                    "Zug ins Feld unten diktieren · zweimal tippen deckt das Brett auf"
                } else {
                    "Leertaste halten und den Zug sagen · „repeat“ wiederholt die letzte Ansage · zweimal klicken oder ${vm.library.settings.shortcutLabel(Command.PEEK)} deckt auf"
                },
                color = Color(0xFF8A8A8A),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Recording, writing down, or what was heard last. */
@Composable
fun VoiceStatus(vm: AppViewModel, center: Boolean = false) {
    val (text, color) = when {
        vm.listening -> "🎙 Ich höre … (Leertaste loslassen)" to LISTEN
        vm.transcribing -> "✍ erkenne …" to INFO
        vm.pendingRecording != null -> "Welcher Zug war gemeint? Zieh oder tipp ihn – ich merke mir, wie du ihn sagst." to INFO
        vm.heard != null -> "Gehört: „${vm.heard}“" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        if (vm.canCorrect && vm.voiceLab == null && !vm.listening && !vm.transcribing) {
            TextButton(onClick = vm::wrongRecognition) { Text("Falsch erkannt") }
        }
    }
}

/**
 * A field for a move in words, typed or dictated (voxtype's own hotkey, the phone's microphone key):
 * Enter plays it. While the field is empty, holding Space records instead, as outside of it.
 */
@Composable
fun VoiceField(vm: AppViewModel, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    fun send() {
        vm.onSpoken(text)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            // Dictation that ends with a line break: play it at once.
            if ('\n' in typed) {
                text = typed.replace("\n", "")
                send()
            } else {
                text = typed
            }
        },
        label = { Text("Zug sagen oder tippen, z. B. knight f3") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        modifier = modifier.typing(vm).fillMaxWidth().onPreviewKeyEvent { event ->
            when {
                event.key == Key.Enter || event.key == Key.NumPadEnter -> (event.type == KeyEventType.KeyDown).also { if (it) send() }
                event.key != Key.Spacebar || vm.voice == null -> false
                event.type == KeyEventType.KeyDown && text.isEmpty() -> vm.voiceDown()
                event.type == KeyEventType.KeyUp && vm.listening -> true.also { vm.voiceUp() }
                else -> false
            }
        },
    )
}

/** The speech test and the voice sample: hold Space, say what is shown, let go. */
@Composable
fun VoiceLabScreen(vm: AppViewModel, lab: VoiceLab, modifier: Modifier = Modifier) {
    val problem = remember(vm.voice) { vm.voice?.problem() ?: if (vm.voice == null) "Auf diesem Gerät gibt es keine Aufnahme mit der Leertaste." else null }
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (lab.sample) "Stimmprobe für Qwen3-TTS" else "Training: Züge einsprechen",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::closeVoiceLab) { Text(if (lab.done) "Fertig" else "Schließen") }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.widthIn(max = 720.dp)) {
            problem?.let {
                Text(it, color = LISTEN, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            if (lab.sample) VoiceSample(vm, lab) else SpeechTestPage(vm, lab)
        }
    }
}

@Composable
private fun SpeechTestPage(vm: AppViewModel, lab: VoiceLab) {
    val phrases = lab.phrases
    Text(
        "Freiwillig: die App lernt deine Stimme auch beim Üben – jeder erkannte Zug wird gemerkt, und was sie nicht versteht, " +
            "verbindest du einfach mit dem Zug, den du dann ziehst oder tippst. Hier geht es schneller: sie sagt die Züge deiner " +
            "Varianten und die Kommandos vor, du sagst sie nach – so, wie du es beim Üben sagen wirst (auch auf Deutsch, z. B. „kurze Rochade“).",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    // The one thing to know, big: unlike a button, Space is held while speaking.
    Text(
        if (vm.listening) "🎙 Ich höre … jetzt loslassen, wenn du fertig bist" else "Leertaste gedrückt HALTEN, sprechen, dann loslassen",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (vm.listening) LISTEN else INFO,
    )
    Spacer(Modifier.height(16.dp))
    val current = phrases.getOrNull(lab.index)
    if (current != null) {
        Text("${lab.index + 1} von ${phrases.size} · ${lab.said} eingesprochen · sag nach:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
        lab.heard[lab.index]?.let {
            Text("Schon eingesprochen – nochmal sprechen legt eine weitere Aufnahme dazu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        if (vm.transcribing) VoiceStatus(vm)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.speechTestStep(-1) }, enabled = lab.index > 0) { Text("←") }
            OutlinedButton(onClick = vm::askPhrase) { Text("🔊 Nochmal vorsagen") }
            OutlinedButton(onClick = { vm.speechTestStep(1) }) { Text("Überspringen →") }
        }
    } else {
        Text("Fertig: ${lab.said} von ${phrases.size} eingesprochen.", style = MaterialTheme.typography.titleLarge, color = GOOD)
        Spacer(Modifier.height(4.dp))
        Text("Die Aufnahmen liegen in ${vm.dataLocation}/$SPEECH_TEST_DIR.", style = MaterialTheme.typography.bodySmall)
        if (lab.said < phrases.size) TextButton(onClick = { vm.speechTestStep(-phrases.size) }) { Text("Zu den übersprungenen") }
        if (phrases.size < SpeechTest.PHRASES.size) TextButton(onClick = vm::openCommonTraining) { Text("Weitere gängige Züge einsprechen") }
    }
    if (lab.said > 0) {
        Spacer(Modifier.height(20.dp))
        Text("Eingesprochen (neueste zuerst) – daneben, was Whisper ohne deine Aufnahmen verstanden hätte:", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        phrases.zip(lab.heard).withIndex().filter { it.value.second != null }.reversed().take(30).forEach { (i, pair) ->
            val (said, heard) = pair
            val ok = heard!!.isNotEmpty() && SpeechTest.isRight(said, heard)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${i + 1}.", modifier = Modifier.widthIn(min = 40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(said, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                Text(if (heard.isEmpty()) "–" else "„$heard“", modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                Text(if (ok) "✓" else "✗", color = if (ok) GOOD else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
            }
        }
    }
}

@Composable
private fun VoiceSample(vm: AppViewModel, lab: VoiceLab) {
    Text(
        "Damit die Züge mit deiner eigenen Stimme angesagt werden können, braucht Qwen3-TTS eine Aufnahme von dir und den Text dazu. " +
            "Leertaste halten, den Text unten ruhig und deutlich vorlesen (etwa 15 Sekunden), dann loslassen. Am besten in einem leisen Raum.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        VOICE_SAMPLE_TEXT,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(20.dp),
    )
    Spacer(Modifier.height(12.dp))
    VoiceStatus(vm)
    lab.saved?.let {
        Spacer(Modifier.height(8.dp))
        Text("✓ Gespeichert: $it", color = GOOD, fontWeight = FontWeight.SemiBold)
        Text("Nicht zufrieden? Einfach nochmal aufnehmen, die alte Aufnahme wird ersetzt.", style = MaterialTheme.typography.bodySmall)
    }
    if (vm.listening) {
        Spacer(Modifier.height(8.dp))
        Button(onClick = {}, enabled = false) { Text("● Aufnahme läuft") }
    }
}
