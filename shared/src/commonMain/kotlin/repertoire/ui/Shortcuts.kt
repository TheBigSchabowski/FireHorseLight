package repertoire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import repertoire.core.Settings
import repertoire.core.currentTimeMillis
import repertoire.shared.resources.Res
import repertoire.shared.resources.wn

/**
 * Marks a text field: while it has the focus, keys without Ctrl+B (Shift+S, the drawing keys, …)
 * are left to it, see [AppViewModel.typing].
 */
fun Modifier.typing(vm: AppViewModel): Modifier = composed {
    val field = remember { Any() }
    DisposableEffect(field) { onDispose { vm.typingIn -= field } }
    onFocusChanged { if (it.isFocused) vm.typingIn += field else vm.typingIn -= field }
}

/** True on a phone: the horse button instead of keyboard shortcuts. */
val LocalTouch = staticCompositionLocalOf { false }

/** Room at the end of a scrolling screen, so its last lines can be scrolled out from under the horse. */
@Composable
fun HorseClearance() {
    if (LocalTouch.current) Spacer(Modifier.height(72.dp))
}

/** Bounds and increment for [repertoire.core.Settings.holdStepMs]. */
const val HOLD_STEP_MIN = 500
const val HOLD_STEP_MAX = 3000
const val HOLD_STEP_INCREMENT = 250

/** Bounds and increment for [repertoire.core.Settings.playDelayMs]. */
const val PLAY_DELAY_MIN = 500
const val PLAY_DELAY_MAX = 5000
const val PLAY_DELAY_INCREMENT = 250

/** "1 s", "1,5 s", "0,75 s": German decimals, no trailing zeros. */
fun formatSeconds(ms: Long): String {
    val whole = ms / 1000
    val rest = (ms % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (rest.isEmpty()) "$whole s" else "$whole,$rest s"
}

/**
 * What can be reached from anywhere: with Ctrl+B and then [key] on the keyboard (both can be changed);
 * on a phone by
 * holding the horse button for [holdSteps] steps (a short tap opens the list; null: not on the
 * horse) or by a double tap into a screen zone ([TapZone]). How long a step is, and which zone runs
 * what, is set in the configuration.
 */
enum class Command(val key: Char, val label: String, val holdSteps: Int?) {
    SHORTCUTS('?', "Alle Kürzel anzeigen", 0),
    // The ones needed most lie on the bottom row of the keyboard: y x c v b n m
    NEW_VARIANT('c', "Neue Variante", 2),
    PASTE_PGN('x', "PGN einfügen", 3),
    COPY_PGN('v', "PGN bis zur Stellung kopieren", null),
    COPY_FEN('y', "FEN der Stellung kopieren", null),
    COPY_ALL('p', "Alles kopieren: PGN mit Eröffnung und Namen", null),
    COPY_ALL_HERE('o', "Alles bis zur Stellung kopieren", null),
    PASTE_ALL('d', "PGN mit allem einfügen (Eröffnung und Namen aus dem Kopf)", null),
    PASTE_FEN('f', "FEN einfügen", 4),
    BLIND('m', "Blind an/aus: Brett verdeckt, die Gegenseite wird angesagt", 1),
    PEEK('n', "Blind: Brett kurz aufdecken / wieder verdecken", null),
    GRAPH('g', "Graph: welche Aufnahme für welchen Zug steht", null),
    PLAY('a', "Variante abspielen / anhalten", null),
    EXPLORER('l', "lichess-Eröffnungsdatenbank zur Stellung auf dem Brett", null),
    ENGINE('r', "Engine rechnet die Stellung auf dem Brett (am Computer)", null),
    SEARCH('s', "Suchfeld auf der Startseite ein- und ausblenden", null),
    SETTINGS('b', "Einstellungen und Tastenkürzel", null),
    ;

    companion object {
        /** The command the horse runs when it is let go after [heldMs], with steps of [stepMs]. */
        fun forHold(heldMs: Long, stepMs: Int = 1000): Command {
            val onHorse = entries.filter { it.holdSteps != null }
            val steps = (heldMs / stepMs).toInt().coerceAtMost(onHorse.maxOf { it.holdSteps!! })
            return onHorse.first { it.holdSteps == steps }
        }
    }
}

/** The other shortcuts, for the list: keyboard, then what they do. */
internal val OTHER_KEYS = listOf(
    "Leertaste halten" to "sprechen: beim Loslassen wird der angesagte Zug gespielt (nicht in einem Textfeld); kurz drücken = „Gut“ / „Weiter“",
    "← / →" to "Züge der Variante durchgehen",
    "Strg+B Enter" to "beim Durchgehen: aus einem Nebenstrang zurück auf die Variante",
    "Strg+N, dann 1–9" to "„Playlist lernen“ der 1., 2., 3. … Eröffnung auf der Startseite (nicht in einem Textfeld)",
    "Shift+S" to "diese Liste öffnen und schließen (nicht in einem Textfeld); darin blättern ↑ ↓ und ← →",
    "8" to "Brett drehen: im Editor bleibt es gedreht, beim Üben bis zur nächsten Variante (nicht in einem Textfeld)",
    "Enter" to "„Gut“ / „Weiter“, wenn die Stellung erreicht ist",
    "Shift+Klick" to "auf eine Linie der Engine: die ganze Linie spielen statt nur ihres ersten Zugs",
)

/** The same on a phone, without a keyboard. */
internal val OTHER_TOUCH = listOf(
    "🎙 im Eingabefeld" to "Zug ansagen mit der Diktierfunktion der Tastatur",
    "2× tippen" to "auf eine freie Stelle: das Kürzel dieses Bereichs (siehe Einstellungen)",
)

/** The key [command] has on the keyboard, after the prefix: its own unless changed in the configuration. */
fun Settings.keyOf(command: Command): Char = keys[command.name]?.firstOrNull() ?: command.key

/** "Strg+B", or whichever letter was chosen instead of the B. */
val Settings.prefixLabel: String get() = "Strg+" + prefixKey.uppercase()

/** "Strg+B Shift+R": the engine as opponent, on Shift and the key of the engine. */
val Settings.opponentLabel: String get() = "$prefixLabel Shift+${keyOf(Command.ENGINE).uppercaseChar()}"

/** "Strg+B p": the whole shortcut of [command] on the keyboard. */
fun Settings.shortcutLabel(command: Command): String = "$prefixLabel ${keyOf(command)}"

/** The keys differ from the defaults. */
val Settings.keysChanged: Boolean
    get() = prefixKey != "b" || Command.entries.any { keyOf(it) != it.key }

/** What a key can be set to: letters, digits and "?". */
fun isShortcutKey(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '?'

/**
 * [command] on [key] from now on. Keys stay unique: a command that had [key] gets the old key of
 * [command] in exchange.
 */
fun Settings.withKey(command: Command, key: Char): Settings {
    val old = keyOf(command)
    val other = Command.entries.firstOrNull { it != command && keyOf(it) == key }
    val changed = keys + (command.name to key.toString()) + (other?.let { mapOf(it.name to old.toString()) } ?: emptyMap())
    // Only what differs from the default is kept, so a later change of a default still reaches it.
    return copy(keys = changed.filter { (name, k) -> Command.entries.firstOrNull { it.name == name }?.key?.toString() != k })
}

/** What the next key press sets in the configuration: the prefix letter or the key of a command. */
sealed interface KeyTarget {
    data object Prefix : KeyTarget

    data class Of(val command: Command) : KeyTarget
}

/** Letters and digits by their key, for when Ctrl hides the typed character. */
private val KEY_CHARS: Map<Key, Char> = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
).mapIndexed { i, key -> key to 'a' + i }.toMap() + listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
).mapIndexed { i, key -> key to '0' + i }.toMap()

/**
 * The character of a key press: what it types if that is a letter, digit or "?" (so the layout
 * decides, e.g. "?" is Shift+ß on a German keyboard), otherwise the letter printed on the key.
 */
private fun charOf(event: KeyEvent): Char? =
    event.utf16CodePoint.toChar().lowercaseChar().takeIf(::isShortcutKey) ?: KEY_CHARS[event.key]

/**
 * Tmux-style prefix keys: Ctrl+B, then a letter; both can be changed in the configuration. The keys
 * that belong to a shortcut never reach a text field, so "p" is not typed into the theory while it
 * has the focus.
 */
class ShortcutKeys(private val vm: AppViewModel) {
    /** A key that was used for a shortcut: its typed character and its release are swallowed too. */
    private var swallow: Key? = null

    fun handle(event: KeyEvent): Boolean {
        swallow?.let { key ->
            when {
                // The next key: handled as usual.
                event.type == KeyEventType.KeyDown && event.key != key -> swallow = null
                // Letting go of Ctrl or Shift is none of our business.
                event.type == KeyEventType.KeyUp && event.key != key -> return false
                else -> {
                    if (event.type == KeyEventType.KeyUp) swallow = null
                    return true
                }
            }
        }
        if (event.type != KeyEventType.KeyDown) return false
        val settings = vm.library.settings

        // Setting a key in the configuration: the next key press is the new one.
        vm.keyCapture?.let { target ->
            if (event.key in MODIFIERS) return false
            swallow = event.key
            if (event.key == Key.Escape) {
                vm.keyCapture = null
                return true
            }
            // The prefix goes with Ctrl, which hides the typed character: the key tells the letter.
            val char = if (target == KeyTarget.Prefix) KEY_CHARS[event.key]?.takeIf { it in 'a'..'z' } else charOf(event)
            if (char == null) {
                vm.flash = if (target == KeyTarget.Prefix) "Für Strg+… geht nur ein Buchstabe." else "Nur Buchstaben, Ziffern oder ?."
            } else {
                vm.setKey(target, char)
            }
            return true
        }

        // After Ctrl+N: a digit picks the opening, any other key just ends it.
        if (vm.picking && event.key !in MODIFIERS) {
            vm.picking = false
            swallow = event.key
            val digit = charOf(event)?.takeIf { it in '1'..'9' }
            if (digit != null) vm.learnOpeningAt(digit - '0')
            return true
        }

        // With Ctrl still held, Ctrl+B Ctrl+B is the same as Ctrl+B b.
        if (!vm.prefix && KEY_CHARS[event.key] == settings.prefixKey.firstOrNull() && event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed) {
            vm.prefix = true
            swallow = event.key
            return true
        }
        if (!vm.prefix || event.key in MODIFIERS) return false

        vm.prefix = false
        swallow = event.key
        if (event.key == Key.Escape) return true
        // Ctrl+B Enter: out of a side line, back to the variant (Durchgehen).
        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
            vm.backToLine()
            return true
        }
        val char = charOf(event)
        // The list stays reachable with ? (always) and h (if free) even when its key was changed.
        val command = Command.SHORTCUTS.takeIf { char == '?' || event.key == Key.Slash }
            ?: Command.entries.firstOrNull { settings.keyOf(it) == char }
            ?: Command.SHORTCUTS.takeIf { char == 'h' }
        when {
            // Ctrl+B Shift+R: the engine as opponent, beside Ctrl+B r for its analysis.
            command == Command.ENGINE && event.isShiftPressed -> vm.openOpponent()
            command != null -> vm.run(command, fromKeyboard = true)
            else -> vm.openKeys(char)
        }
        return true
    }

    /**
     * Shift+S: the list of all shortcuts; Ctrl+N: pick an opening by its number; 8: the board turned
     * round. Not while a text field has the focus: there a capital letter is just typed. (A text
     * field does not take the key press itself, only what it types.)
     */
    fun handleDirect(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || vm.prefix || vm.keyCapture != null || vm.typing) return false
        if (event.isMetaPressed || event.isAltPressed) return false
        val plain = !event.isCtrlPressed && !event.isShiftPressed
        when {
            // 8 turns the board round (Shift+8 is a bracket on a German keyboard, so without Shift).
            plain && (event.key == Key.Eight || event.key == Key.NumPad8) -> vm.flipBoard()
            event.isCtrlPressed && !event.isShiftPressed && event.key == Key.N -> {
                vm.showShortcuts = false
                vm.picking = true
            }
            event.isCtrlPressed || !event.isShiftPressed -> return false
            event.key == Key.S -> vm.run(Command.SHORTCUTS)
            else -> return false
        }
        swallow = event.key
        return true
    }

    private companion object {
        val MODIFIERS = setOf(
            Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight,
            Key.AltLeft, Key.AltRight, Key.MetaLeft, Key.MetaRight,
        )
    }
}

private val PILL = Color(0xE6151515)

/** Everything that floats above the screens: the prefix hint, short messages, the list and the horse. */
@Composable
fun BoxScope.ShortcutOverlay(vm: AppViewModel) {
    val touch = LocalTouch.current
    if (vm.showShortcuts) ShortcutList(vm, touch)

    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        vm.flash?.let { text ->
            LaunchedEffect(text) {
                delay(2500)
                vm.flash = null
            }
            Pill(text)
            Spacer(Modifier.height(8.dp))
        }
        if (vm.picking) {
            val openings = vm.library.groups().take(9).mapIndexed { i, g -> "${i + 1} ${g.opening}" }
            Pill(
                if (openings.isEmpty()) "Strg+N …   noch keine Eröffnung" else "Strg+N …   Playlist lernen:   " + openings.joinToString("  ·  "),
            )
        }
        if (vm.listening || vm.transcribing) {
            Pill(if (vm.listening) "🎙 Ich höre …" else "✍ schreibe auf …")
            Spacer(Modifier.height(8.dp))
        }
        if (vm.prefix) {
            val settings = vm.library.settings
            Pill("${settings.prefixLabel} …   " + Command.entries.joinToString("  ·  ") { "${settings.keyOf(it)} ${it.short}" })
        }
    }
    val settings = vm.library.settings
    if (touch && settings.horseOn) {
        HorseButton(
            settings.holdStepMs, vm::run,
            Modifier.align(if (settings.horseLeft) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

internal val Command.short: String get() = when (this) {
    Command.SHORTCUTS -> "Liste"
    Command.BLIND -> "Blind"
    Command.PEEK -> "Aufdecken"
    Command.GRAPH -> "Graph"
    Command.NEW_VARIANT -> "Variante"
    Command.PASTE_PGN -> "PGN"
    Command.PASTE_FEN -> "FEN"
    Command.COPY_FEN -> "FEN kopieren"
    Command.COPY_PGN -> "PGN kopieren"
    Command.COPY_ALL -> "Alles kopieren"
    Command.COPY_ALL_HERE -> "Alles bis hier"
    Command.PASTE_ALL -> "Alles einfügen"
    Command.PLAY -> "Abspielen"
    Command.EXPLORER -> "Datenbank"
    Command.ENGINE -> "Engine"
    Command.SEARCH -> "Suchen"
    Command.SETTINGS -> "Einstellungen"
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp).background(PILL, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Every shortcut; the commands can also be clicked, which is how they are found on a phone. */
@Composable
private fun ShortcutList(vm: AppViewModel, touch: Boolean) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = { vm.showShortcuts = false },
        title = { Text(if (touch) "Das Pferd" else "Tastenkürzel") },
        text = {
            // Asked for once the dialog's content is there; before that the requester points nowhere.
            LaunchedEffect(Unit) { focus.requestFocus() }
            // The list has the keys: ↑ ↓ scroll a bit, ← → and Page Up/Down a page, Shift+S closes it again.
            Column(
                Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val page = scroll.viewportSize * 0.8f
                    val by = when (event.key) {
                        Key.DirectionDown -> 120f
                        Key.DirectionUp -> -120f
                        Key.DirectionRight, Key.PageDown -> page
                        Key.DirectionLeft, Key.PageUp -> -page
                        else -> null
                    }
                    when {
                        by != null -> scope.launch { scroll.animateScrollBy(by) }.let { true }
                        event.key == Key.S && event.isShiftPressed -> true.also { vm.showShortcuts = false }
                        event.key == Key.Escape -> true.also { vm.showShortcuts = false }
                        else -> false
                    }
                }.verticalScroll(scroll),
            ) {
                Text(
                    if (touch) {
                        "Kurz tippen öffnet diese Liste, gedrückt halten wählt nach der Zeit – beim Halten steht daneben, was beim Loslassen passiert. Wegziehen bricht ab. " +
                            "Alles Weitere erklärt die Hilfe in den Einstellungen."
                    } else {
                        "Erst ${vm.library.settings.prefixLabel} drücken, dann die Taste. Geht überall, auch mitten in einem Puzzle: danach geht es dort weiter. " +
                            "Die Tasten lassen sich in den Einstellungen ändern."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                for (command in Command.entries.filter { it != Command.SHORTCUTS }) {
                    val settings = vm.library.settings
                    val key = when {
                        !touch -> settings.shortcutLabel(command)
                        command.holdSteps != null -> formatSeconds(command.holdSteps.toLong() * settings.holdStepMs) + " halten"
                        else -> settings.zoneOf(command)?.let { "2× ${it.shortLabel(settings.tapCols, settings.tapRows)}" } ?: ""
                    }
                    ShortcutRow(key, command.label) { vm.run(command) }
                }
                if (!touch) ShortcutRow(vm.library.settings.opponentLabel, "Engine als Gegner: spielt im Editor eine Seite weiter", vm::openOpponent)
                Spacer(Modifier.height(12.dp))
                for ((key, label) in if (touch) OTHER_TOUCH else OTHER_KEYS) ShortcutRow(key, label, null)
            }
        },
        confirmButton = { TextButton(onClick = { vm.showShortcuts = false }) { Text("Schließen") } },
    )
}

@Composable
internal fun ShortcutRow(key: String, label: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            // One width for the whole column, so the descriptions line up.
            modifier = Modifier.width(if (LocalTouch.current) 124.dp else 170.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The phone's shortcut key: a small knight. A tap opens the list; holding it runs a [Command]
 * after [Command.holdSteps] steps of [stepMs], and the pill next to it says which one as the ring fills up.
 */
@Composable
private fun HorseButton(stepMs: Int, onCommand: (Command) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf<Long?>(null) }
    var cancelled by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    val last = Command.entries.maxOf { it.holdSteps ?: 0 }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        held?.let { ms ->
            Pill(if (cancelled) "Loslassen bricht ab" else "Loslassen: " + Command.forHold(ms, stepMs).label)
        }
        Box(
            Modifier.size(52.dp)
                .background(PILL, CircleShape)
                .border(1.dp, Color(0xFF4A4A4A), CircleShape)
                .pointerInput(stepMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val start = currentTimeMillis()
                        val slop = 48.dp.toPx()
                        held = 0L
                        cancelled = false
                        while (true) {
                            // Wakes up regularly even without pointer events, so the ring keeps filling.
                            val change = withTimeoutOrNull(50) { awaitPointerEvent() }
                                ?.changes?.firstOrNull { it.id == down.id }
                            held = currentTimeMillis() - start
                            if (change == null) continue
                            change.consume()
                            val p = change.position
                            if (p.x < -slop || p.y < -slop || p.x > size.width + slop || p.y > size.height + slop) cancelled = true
                            if (!change.pressed) break
                        }
                        val ms = held ?: 0L
                        held = null
                        if (!cancelled) onCommand(Command.forHold(ms, stepMs))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(Res.drawable.wn), contentDescription = "Kürzel", modifier = Modifier.size(34.dp))
            held?.let { ms ->
                Canvas(Modifier.fillMaxSize()) {
                    val sweep = if (ms >= last.toLong() * stepMs) 360f else (ms % stepMs).toFloat() / stepMs * 360f
                    val stroke = 3.dp.toPx()
                    drawArc(
                        if (cancelled) Color.Gray else ring, -90f, sweep, useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
