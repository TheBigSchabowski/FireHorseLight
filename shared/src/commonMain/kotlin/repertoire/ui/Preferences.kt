package repertoire.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.onPreviewKeyEvent
import repertoire.core.PenColor
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.RadioButton
import repertoire.core.EngineEntry
import repertoire.core.processorCount
import repertoire.core.Explorer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import repertoire.core.Ink
import repertoire.core.Library
import repertoire.core.Mark
import repertoire.core.Position
import repertoire.core.Settings
import repertoire.core.parseSquare
import kotlin.math.roundToInt

/** The preferences screen: the configuration, or the help with an optional topic open. */
sealed interface Prefs {
    data object Config : Prefs

    /** [fromConfig]: opened from a "Hilfe" button there, so going back returns to it. */
    data class Help(val topic: HelpTopic? = null, val fromConfig: Boolean = false) : Prefs
}

/** A page of the help. [touch]: only on a phone (true), only with a keyboard (false), or both (null). */
enum class HelpTopic(val title: String, val summary: String, val touch: Boolean? = null) {
    OVERVIEW("So funktioniert FireHorse Light", "Varianten anlegen, als Puzzle lernen, Spaced Repetition"),
    BLIND("Blind üben und Stimme", "Brett verdeckt, Züge sagen statt ziehen, Spracherkennung, eigene Stimme"),
    VARIANTS("Varianten anlegen und bearbeiten", "Neue Variante, Weiterführen, Durchgehen, Bearbeiten"),
    PASTE("PGN und FEN einfügen", "Zugfolgen und Stellungen von woanders übernehmen"),
    LEARNING("Lernen und Üben", "Lernen, Frei üben, Züge üben, neue Varianten pro Tag"),
    BOARD("Brett", "Farben, Brett drehen"),
    PLAYER("Abspielen", "Eine Variante von allein durchlaufen lassen, Tempo, Wiederholen"),
    PLAYLIST("Playlists, Anpinnen, Suchen, Archiv", "Varianten nacheinander abspielen oder lernen, eigene Playlists, nach Zügen und Farbe suchen, mehrere auf einmal archivieren oder löschen"),
    HORSE("Das Pferd", "Kürzel am Handy: antippen und halten", touch = true),
    TAP_ZONES("Doppeltippen", "Bereiche des Bildschirms für Kürzel, Raster frei wählbar", touch = true),
    KEYBOARD("Tastenkürzel", "Strg+B und die übrigen Tasten, eigene Belegung", touch = false),
    LICHESS("Datenbank und Engine", "lichess-Eröffnungsdatenbank, Engines wie Stockfish"),
    DATA("Speichern und Übertragen", "Wo alles liegt, Export und Import"),
    ;

    fun shownOn(isTouch: Boolean) = touch == null || touch == isTouch
}

@Composable
fun PreferencesScreen(vm: AppViewModel, prefs: Prefs, modifier: Modifier = Modifier) {
    val touch = LocalTouch.current
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.prefs = null }) { Text("Schließen") }
        }
        PrimaryTabRow(selectedTabIndex = if (prefs is Prefs.Help) 1 else 0) {
            Tab(prefs == Prefs.Config, onClick = vm::openConfig, text = { Text("Konfiguration") })
            Tab(prefs is Prefs.Help, onClick = { vm.openHelp() }, text = { Text("Hilfe") })
        }
        // A fresh scroll position for every page, so a help page does not open halfway down.
        key(prefs) {
            val scroll = rememberScrollState()
            var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
            // Scrolls so that [target] sits at the top of the page.
            val scrollTo: suspend (LayoutCoordinates) -> Unit = { target ->
                viewport?.let { v -> scroll.animateScrollTo(scroll.value + v.localPositionOf(target, Offset.Zero).y.roundToInt()) }
            }
            Column(Modifier.fillMaxSize().onGloballyPositioned { viewport = it }.verticalScroll(scroll)) {
                // Lines and rows stay readable in a wide window instead of stretching across it.
                Column(Modifier.widthIn(max = 720.dp).padding(24.dp)) {
                    when {
                        prefs is Prefs.Help && prefs.topic != null -> HelpPage(vm, prefs, touch)
                        prefs is Prefs.Help -> HelpIndex(vm, touch)
                        else -> ConfigPage(vm, touch, scrollTo)
                    }
                    HorseClearance()
                }
            }
        }
    }
}

// ---- Configuration -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPage(vm: AppViewModel, touch: Boolean, scrollTo: suspend (LayoutCoordinates) -> Unit) {
    val settings = vm.library.settings
    Section("Blind und Stimme", onHelp = { vm.openHelp(HelpTopic.BLIND) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blind: Brett verdeckt, die Gegenseite wird gespielt" + if (touch) "" else " (${settings.shortcutLabel(Command.BLIND)})", modifier = Modifier.weight(1f))
            Switch(settings.blind, onCheckedChange = { if (it != settings.blind) vm.toggleBlind() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Züge ansagen (englisch)", modifier = Modifier.weight(1f))
            Switch(settings.speakMoves, onCheckedChange = vm::setSpeakMoves)
        }
        val voice = vm.voice
        if (voice != null) {
            Spacer(Modifier.height(8.dp))
            Text(if (touch) "Sprechen mit dem 🎙-Knopf" else "Sprechen mit der Leertaste")
            Hint(
                remember(voice) { voice.problem() } ?: if (touch) {
                    "🎙 halten, Zug sagen, loslassen. Erkannt wird am Klang deiner Aufnahmen; was noch unbekannt ist, verbindest du mit dem Zug, den du dann ziehst."
                } else {
                    "Leertaste (oder 🎙) halten, Zug sagen, loslassen. Erkannt wird am Klang deiner Aufnahmen" +
                        if (voice.canWriteDown) ", sonst mit voxtype (Whisper)." else "; was noch unbekannt ist, verbindest du mit dem Zug, den du dann ziehst. " +
                            "Mit voxtype (am Mac: brew install --cask voxtype) wird auch Unbekanntes aufgeschrieben."
                },
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::openSpeechTest) { Text("Züge einsprechen") }
                OutlinedButton(onClick = vm::openVoiceSample) { Text("Stimmprobe aufnehmen") }
                OutlinedButton(onClick = { vm.run(Command.GRAPH) }) { Text("Graph: Klang ↔ Zug") }
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Hint("Züge sagst du über das Mikrofon der Tastatur ins Feld unter dem Brett.")
        }
    }
    Section("Lernen", onHelp = { vm.openHelp(HelpTopic.LEARNING) }) {
        Text("Neue Varianten pro Tag")
        Spacer(Modifier.height(6.dp))
        Stepper(
            "${settings.newPerDay}",
            onMinus = { vm.setNewPerDay(settings.newPerDay - 5) },
            onPlus = { vm.setNewPerDay(settings.newPerDay + 5) },
            minus = "−5",
            plus = "+5",
        )
    }
    Section("Abspielen", onHelp = { vm.openHelp(HelpTopic.PLAYER) }) {
        Text("Zeit pro Zug")
        Spacer(Modifier.height(6.dp))
        Stepper(
            formatSeconds(settings.playDelayMs.toLong()),
            onMinus = { vm.setPlayDelay(settings.playDelayMs - PLAY_DELAY_INCREMENT) },
            onPlus = { vm.setPlayDelay(settings.playDelayMs + PLAY_DELAY_INCREMENT) },
            minusEnabled = settings.playDelayMs > PLAY_DELAY_MIN,
            plusEnabled = settings.playDelayMs < PLAY_DELAY_MAX,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nach dem letzten Zug von vorn beginnen", modifier = Modifier.weight(1f))
            Switch(settings.playLoop, onCheckedChange = vm::setPlayLoop)
        }
    }
    Section("lichess", onHelp = { vm.openHelp(HelpTopic.LICHESS) }) {
        LichessToken(vm, settings.lichessToken)
    }
    if (!touch) {
        Section("Engines", onHelp = { vm.openHelp(HelpTopic.LICHESS) }) {
            EngineSettings(vm, settings)
        }
    }
    Section("Brett", onHelp = { vm.openHelp(HelpTopic.BOARD) }) {
        Text("Farben")
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (theme in BoardTheme.entries) {
                val selected = BoardTheme.named(settings.boardTheme) == theme
                OutlinedButton(
                    onClick = { vm.setBoardTheme(theme) },
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) {
                    Box(Modifier.size(14.dp).background(theme.light))
                    Box(Modifier.size(14.dp).background(theme.dark))
                    Text(theme.label, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (touch) {
        val step = settings.holdStepMs
        Section("Das Pferd", onHelp = { vm.openHelp(HelpTopic.HORSE) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pferd anzeigen", modifier = Modifier.weight(1f))
                Switch(settings.horseOn, onCheckedChange = vm::setHorseOn)
            }
            if (!settings.horseOn) {
                Hint("Ohne Pferd erreichst du die Kürzel nur noch durch Doppeltippen" + (if (settings.tapZonesOn) "." else " – und das ist gerade aus. Die Einstellungen gibt es weiter auf der Startseite."))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unten links statt rechts", modifier = Modifier.weight(1f))
                    Switch(settings.horseLeft, onCheckedChange = vm::setHorseLeft)
                }
                Spacer(Modifier.height(8.dp))
                Text("Haltezeit pro Schritt")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    formatSeconds(step.toLong()),
                    onMinus = { vm.setHoldStep(step - HOLD_STEP_INCREMENT) },
                    onPlus = { vm.setHoldStep(step + HOLD_STEP_INCREMENT) },
                    minusEnabled = step > HOLD_STEP_MIN,
                    plusEnabled = step < HOLD_STEP_MAX,
                )
                Hint(
                    Command.entries.filter { (it.holdSteps ?: 0) > 0 }
                        .joinToString(", ") { "${it.short} nach ${formatSeconds(it.holdSteps!!.toLong() * step)}" } + ".",
                )
            }
        }
        Section("Doppeltippen", onHelp = { vm.openHelp(HelpTopic.TAP_ZONES) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zweimal auf eine freie Stelle tippen führt ein Kürzel aus", modifier = Modifier.weight(1f))
                Switch(settings.tapZonesOn, onCheckedChange = vm::setTapZonesOn)
            }
            if (settings.tapZonesOn) {
                Spacer(Modifier.height(8.dp))
                Text("Spalten")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "${settings.tapCols}",
                    onMinus = { vm.setTapGrid(settings.tapCols - 1, settings.tapRows) },
                    onPlus = { vm.setTapGrid(settings.tapCols + 1, settings.tapRows) },
                    minusEnabled = settings.tapCols > 1,
                    plusEnabled = settings.tapCols < TAP_COLS_MAX,
                )
                Spacer(Modifier.height(8.dp))
                Text("Reihen")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "${settings.tapRows}",
                    onMinus = { vm.setTapGrid(settings.tapCols, settings.tapRows - 1) },
                    onPlus = { vm.setTapGrid(settings.tapCols, settings.tapRows + 1) },
                    minusEnabled = settings.tapRows > 1,
                    plusEnabled = settings.tapRows < TAP_ROWS_MAX,
                )
                Spacer(Modifier.height(8.dp))
                Hint("Der Bildschirm in ${settings.zones.size} Bereich(en). Antippen, um festzulegen, was ein Bereich macht:")
                Spacer(Modifier.height(8.dp))
                ZoneGrid(settings, onPick = vm::setTapZone)
            }
        }
    } else {
        // Ctrl+B with a free key opens the configuration right here, once it is known where "here" is.
        var keysAt by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val placed = keysAt != null
        LaunchedEffect(vm.showKeys, placed) {
            val at = keysAt
            if (vm.showKeys && at != null) {
                scrollTo(at)
                vm.showKeys = false
            }
        }
        Column(Modifier.onGloballyPositioned { keysAt = it }) { KeySettings(vm, settings) }
    }
    Section("Daten", onHelp = { vm.openHelp(HelpTopic.DATA) }, last = true) {
        Hint("Gespeichert in: ${vm.dataLocation}")
        if (vm.canTransfer) {
            Spacer(Modifier.height(8.dp))
            Text("Varianten und Lernstand übertragen")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.export() }, enabled = vm.library.variants.isNotEmpty()) { Text("Alle exportieren") }
                OutlinedButton(onClick = vm::import) { Text("Importieren") }
            }
            vm.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            vm.importing?.let { ImportChoice(vm, it, vm.library.variants.size) }
        }
    }
}

/** After a file is opened: add its variants to the ones there are, or delete those and keep only the file's. */
@Composable
private fun ImportChoice(vm: AppViewModel, file: AppViewModel.PendingImport, have: Int) {
    AlertDialog(
        onDismissRequest = vm::cancelImport,
        title = { Text("Importieren") },
        text = {
            Column {
                Text("Die Datei hat ${file.variants} Variante(n). Du hast gerade $have.")
                Spacer(Modifier.height(10.dp))
                Text("Dazunehmen", fontWeight = FontWeight.Bold)
                Hint("Die Varianten der Datei kommen dazu, deine bleiben. Eine, die es schon gibt, wird aktualisiert.")
                Spacer(Modifier.height(8.dp))
                Text("Ersetzen", fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                Hint(
                    "Alle deine Varianten werden mit ihrem Lernstand gelöscht, danach gibt es nur noch die aus der Datei. " +
                        "Notizen, Einstellungen und Playlists bleiben (Playlists ohne die gelöschten Varianten).",
                )
            }
        },
        confirmButton = { Button(onClick = { vm.finishImport(replace = false) }) { Text("Dazunehmen") } },
        dismissButton = {
            Row {
                TextButton(onClick = vm::cancelImport) { Text("Abbrechen") }
                TextButton(onClick = { vm.finishImport(replace = true) }) { Text("Ersetzen", color = Color(0xFFE57373)) }
            }
        },
    )
}

/** The personal lichess API token for the opening database, hidden unless asked to show it. */
@Composable
private fun LichessToken(vm: AppViewModel, saved: String) {
    var text by remember { mutableStateOf(saved) }
    var shown by remember { mutableStateOf(false) }
    Hint(
        "Für die Eröffnungsdatenbank (" + (if (LocalTouch.current) "Kürzel „Datenbank“" else vm.library.settings.shortcutLabel(Command.EXPLORER)) +
            ") will lichess einen persönlichen API-Schlüssel. Auf ${Explorer.TOKEN_PAGE} einen neuen anlegen – kein Häkchen nötig – und hier einfügen. " +
            "Er bleibt auf diesem Gerät und kommt nicht mit in Exporte.",
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        text,
        {
            text = it
            vm.setLichessToken(it)
        },
        label = { Text("API-Schlüssel (lip_…)") },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.typing(vm).fillMaxWidth().onPreviewKeyEvent(vm.shortcutKeys::handle),
    )
    TextButton(onClick = { shown = !shown }, enabled = text.isNotEmpty()) { Text(if (shown) "Verbergen" else "Anzeigen") }
}

/**
 * The engines on this computer: those set up, those lying in the engine folder, a path to add one,
 * and how hard they work. Nothing is downloaded; an engine is whatever UCI program is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineSettings(vm: AppViewModel, settings: Settings) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun add(p: String) {
        busy = true
        error = null
        scope.launch {
            try {
                vm.addEngine(p)
                path = ""
            } catch (e: IllegalStateException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    Hint(
        "Eine Engine ist ein UCI-Programm auf diesem Computer, z. B. Stockfish. Gib den Pfad zur Programmdatei an " +
            "oder leg sie in den Ordner ${vm.engineFolder} – dann steht sie hier zum Hinzufügen. " +
            "${settings.shortcutLabel(Command.ENGINE)} lässt die gewählte Engine die Stellung auf dem Brett rechnen.",
    )
    Spacer(Modifier.height(8.dp))
    val chosen = settings.chosenEngine
    for (e in settings.engines) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = e == chosen, onClick = { vm.chooseEngine(e.path) })
            Column(Modifier.weight(1f)) {
                Text(e.name)
                Hint(e.path)
            }
            TextButton(onClick = { vm.removeEngine(e.path) }) { Text("Entfernen") }
        }
        // Each engine works its own way; the chosen one shows how.
        if (e == chosen) EngineOptions(vm, e)
    }
    val found = remember(settings.engines) { vm.enginesInFolder() }
    for (f in found) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Im Ordner: " + f.substringAfterLast('/'))
                Hint(f)
            }
            TextButton(onClick = { add(f) }, enabled = !busy) { Text("Hinzufügen") }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            path,
            {
                path = it
                error = null
            },
            label = { Text("Pfad zur Engine") },
            placeholder = { Text("/Pfad/zu/stockfish") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.typing(vm).weight(1f).onPreviewKeyEvent(vm.shortcutKeys::handle),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { add(path) }, enabled = path.isNotBlank() && !busy) { Text(if (busy) "Prüfe …" else "Hinzufügen") }
    }
    error?.let { Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium) }
}

/** How hard one engine works: lines, depth, threads, hash; each by − and + or typed. */
@Composable
private fun EngineOptions(vm: AppViewModel, e: EngineEntry) {
    Column(Modifier.padding(start = 48.dp, bottom = 12.dp)) {
        Text("Linien")
        NumberStepper(
            vm, e.lines, ENGINE_LINES, onValue = { vm.setEngineLines(e.path, it) },
            onMinus = { vm.setEngineLines(e.path, e.lines - 1) }, onPlus = { vm.setEngineLines(e.path, e.lines + 1) },
        )
        Spacer(Modifier.height(8.dp))
        Text("Tiefe")
        NumberStepper(
            vm, e.depth, ENGINE_DEPTH, onValue = { vm.setEngineDepth(e.path, it) },
            onMinus = { vm.stepEngineDepth(e.path, -1) }, onPlus = { vm.stepEngineDepth(e.path, 1) },
            zeroLabel = "unbegrenzt", fieldWidth = 120,
        )
        Hint(
            "Leer (unbegrenzt) rechnet die Engine weiter, bis sich die Stellung ändert; mit einer Tiefe hört sie dort auf und die Linien bleiben stehen. " +
                "− und + springen in Schritten, eintippen geht jede Tiefe bis ${ENGINE_DEPTH.last}.",
        )
        Spacer(Modifier.height(8.dp))
        Text("Threads (Prozessorkerne)")
        val cores = processorCount()
        NumberStepper(
            vm, e.threads, engineThreads(e.threads), onValue = { vm.setEngineThreads(e.path, it) },
            onMinus = { vm.setEngineThreads(e.path, e.threads - 1) }, onPlus = { vm.setEngineThreads(e.path, e.threads + 1) },
        )
        Hint("Dieser Computer hat $cores Kerne. Alle zu nehmen macht die Engine am schnellsten, lässt aber für anderes wenig übrig; ${maxOf(1, cores - 2)} ist ein guter Wert.")
        Spacer(Modifier.height(8.dp))
        Text("Hash (Speicher)")
        NumberStepper(
            vm, e.hashMb, ENGINE_HASH_MB, onValue = { vm.setEngineHash(e.path, it) },
            onMinus = { vm.setEngineHash(e.path, e.hashMb / 2) }, onPlus = { vm.setEngineHash(e.path, e.hashMb * 2) },
            unit = "MB",
        )
        Hint("− und + halbieren und verdoppeln. Mehr Hash hilft vor allem bei großen Tiefen; mehr als die Hälfte des Arbeitsspeichers lohnt sich nicht.")
    }
}

/** The keys: Ctrl+B and its letters. */
@Composable
private fun KeySettings(vm: AppViewModel, settings: Settings) {
    Section("Tastenkürzel", onHelp = { vm.openHelp(HelpTopic.KEYBOARD) }) {
        Hint(
            "Erst ${settings.prefixLabel}, dann die Taste. Zum Ändern auf eine Taste klicken und die neue drücken " +
                "(Esc bricht ab). Ist sie schon vergeben, tauschen die beiden. Ein freier Buchstabe nach ${settings.prefixLabel} führt hierher.",
        )
        Spacer(Modifier.height(8.dp))
        KeyRow("Präfix: Strg + …", settings.prefixKey.uppercase(), vm, KeyTarget.Prefix)
        for (command in Command.entries) KeyRow(command.label, settings.keyOf(command).toString(), vm, KeyTarget.Of(command))
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = vm::resetKeys, enabled = settings.keysChanged) { Text("Standard wiederherstellen") }
    }
}

/** One configurable key: a click, then the next key press sets it. */
@Composable
private fun KeyRow(label: String, key: String, vm: AppViewModel, target: KeyTarget) {
    val waiting = vm.keyCapture == target
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { vm.keyCapture = if (waiting) null else target },
            border = BorderStroke(1.dp, if (waiting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(min = 120.dp),
        ) {
            Text(
                if (waiting) "Taste drücken …" else key,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (waiting) FontWeight.Normal else FontWeight.Bold,
            )
        }
    }
}

/** A group of settings with a link to the help page that explains it. */
@Composable
private fun Section(title: String, onHelp: () -> Unit, last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onHelp) { Text("Hilfe") }
    }
    Spacer(Modifier.height(4.dp))
    Column(content = content)
    if (!last) {
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minus: String = "−",
    plus: String = "+",
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus, enabled = minusEnabled) { Text(minus) }
        Text(value, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = onPlus, enabled = plusEnabled) { Text(plus) }
    }
}

/** The six zones as on the screen; with [onPick] each one can be given a shortcut. */
@Composable
private fun ZoneGrid(settings: Settings, onPick: ((TapZone, Command?) -> Unit)?) {
    Column(
        Modifier.widthIn(max = 360.dp).fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until settings.tapRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0 until settings.tapCols) {
                    val zone = TapZone(column, row)
                    ZoneTile(zone.label(settings.tapCols, settings.tapRows), settings.commandAt(zone), zone, onPick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ZoneTile(label: String, command: Command?, zone: TapZone, onPick: ((TapZone, Command?) -> Unit)?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .then(if (onPick != null) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                command?.short ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (command != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPick != null) {
            DropdownMenu(open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Nichts") }, onClick = { onPick(zone, null); open = false })
                for (c in Command.entries) {
                    DropdownMenuItem(text = { Text(c.label) }, onClick = { onPick(zone, c); open = false })
                }
            }
        }
    }
}

// ---- Help ----------------------------------------------------------------------------------

@Composable
private fun HelpIndex(vm: AppViewModel, touch: Boolean) {
    Para(
        "FireHorse trainiert Eröffnungen als Puzzle. Hier steht, wie alles funktioniert – " +
            "such dir ein Thema aus. In der Konfiguration führt „Hilfe“ neben jedem Bereich direkt zur passenden Seite.",
    )
    Spacer(Modifier.height(8.dp))
    for (topic in HelpTopic.entries.filter { it.shownOn(touch) }) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.openHelp(topic) }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HelpPage(vm: AppViewModel, prefs: Prefs.Help, touch: Boolean) {
    val topic = prefs.topic ?: return
    val settings = vm.library.settings
    TextButton(onClick = vm::prefsBack) { Text(if (prefs.fromConfig) "← Konfiguration" else "← Alle Themen") }
    Text(topic.title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    when (topic) {
        HelpTopic.OVERVIEW -> {
            Para(
                "Du legst Varianten selbst an: die Züge vom Start bis zu einer Stellung, dazu Eröffnung und Namen " +
                    "(„Sizilianisch“ / „Scheveningen“).",
            )
            Para(
                "Beim Üben steht nur der Name auf einer schwarzen Tafel, und du spielst beide Seiten vom Start, bis die " +
                    "Stellung auf dem Brett steht. Jede Zugfolge zählt, die in deinen Varianten vorkommt, also auch Zugumstellungen.",
            )
            Para(
                "Wann eine Variante wieder drankommt, rechnet die App mit Spaced Repetition (FSRS) aus: was sitzt, " +
                    "kommt erst in Tagen oder Wochen wieder, was wackelt, bald.",
            )
            SubHeading("Loslegen")
            Para("1.  „+ Neue Variante“ oder „PGN/FEN einfügen“ auf der Startseite.")
            Para("2.  Züge spielen, Eröffnung und Namen eintragen, speichern.")
            Para("3.  „Lernen“ fragt ab, was heute dran ist.")
            Para(
                if (touch) {
                    "Kürzel erreichst du am Handy über das Pferd unten rechts und durch Doppeltippen auf eine freie Stelle."
                } else {
                    "Kürzel erreichst du mit ${settings.prefixLabel} und einer Taste; ${settings.shortcutLabel(Command.SHORTCUTS)} zeigt alle."
                },
            )
        }
        HelpTopic.BLIND -> {
            Para(
                "Blind" + (if (touch) "" else " (${settings.shortcutLabel(Command.BLIND)}, oder „Blind“ auf der Startseite)") +
                    " verdeckt das Brett beim Üben und im Editor. Statt der Stellung siehst du den letzten Zug groß und die Züge bisher klein darunter.",
            )
            Bullet("Gegenseite", "beim Üben spielt die App die Seite, die du nicht spielst, und sagt ihre Züge auf Englisch an. Du spielst nur deine Farbe – aus dem Kopf.")
            Bullet("Falscher Zug", "„No“ und der richtige Zug werden angesagt, und es geht weiter; ein Zug aus einer anderen deiner Varianten: „Other variant“, nochmal.")
            Bullet("Am Ende", "wird das Brett aufgedeckt: „Correct“ ohne Fehler, sonst „Done“.")
            Bullet("Aufdecken", (if (touch) "zweimal aufs verdeckte Brett tippen" else "${settings.shortcutLabel(Command.PEEK)} oder zweimal klicken") + " zeigt das Brett kurz, nochmal verdeckt es wieder. Mit der nächsten Variante ist es wieder zu.")
            Bullet("Abspielen", "im Editor sagt der Player die Züge an, statt sie zu zeigen.")
            Bullet("Ansage", "„🔊 Ansage an/aus“ beim Üben (oder hier in der Konfiguration): ohne Ansage steht der zuletzt gespielte Zug groß auf dem verdeckten Brett.")
            SubHeading("Züge sagen")
            if (vm.voice != null) {
                Para(
                    (if (touch) "Den 🎙-Knopf halten" else "Leertaste (oder den 🎙-Knopf) halten") + ", den Zug sagen, loslassen – beim Üben wie im Editor, auch ohne Blind. " +
                        "Erkannt wird am Klang deiner eigenen Aufnahmen; was die App noch nicht kennt, schreibt am Computer voxtype (Whisper) auf. " +
                        if (touch) "" else "Kurz antippen bleibt „Gut“ / „Weiter“. In einem Textfeld tippt die Leertaste wie immer, außer im leeren Zugfeld.",
                )
                Bullet(
                    "Graph" + if (touch) "" else " (${settings.shortcutLabel(Command.GRAPH)})",
                    "links deine Aufnahmen, rechts die Züge, dazwischen Linien. " +
                        (if (touch) "Aufnahme antippen, dann den Zug" else "Eine Aufnahme auf einen anderen Zug ziehen (oder anklicken, dann den Zug)") +
                        " verbindet sie neu; ▶ spielt sie ab, ✕ löscht sie.",
                )
            }
            Para("Das Feld unter dem Brett nimmt einen Zug auch getippt oder diktiert (voxtypes eigene Taste, am Handy das Mikrofon der Tastatur); Enter spielt ihn.")
            Bullet("Züge", "„e4“, „knight f3“, „bishop takes c4“, „e takes d5“, „castles kingside“ / „short castle“, „long castle“, „e8 queen“. „pawn“ und „to“ darf man sagen oder weglassen.")
            Bullet("Kommandos", "„board“ deckt auf, „repeat“ wiederholt die letzte Ansage, „hint“ sagt den richtigen Zug (zählt als Fehler), „next“ = Gut / Weiter, „restart“ von vorn, „undo“ nimmt im Editor einen Zug zurück.")
            if (vm.voice != null) {
                SubHeading("Die App lernt deine Stimme")
                Para(
                    "Jeder Zug, den sie erkennt, wird als Aufnahme gemerkt; beim nächsten Mal erkennt sie ihn am Klang („am Klang erkannt“) – " +
                        "egal wie du ihn sagst, auch auf Deutsch. Versteht sie etwas nicht, zieh oder tipp einfach den gemeinten Zug: die Aufnahme " +
                        "gehört dann zu ihm. „Falsch erkannt“ unter dem Gehörten löst eine falsche Zuordnung (im Editor wird der Zug zurückgenommen), " +
                        "und der Zug, den du danach ziehst oder tippst, bekommt die Aufnahme.",
                )
                Para(
                    "Schneller geht es mit „🎙 Training“ auf der Startseite: die App sagt die Züge deiner Varianten und die Kommandos vor, du sagst sie nach. " +
                        "Die Aufnahmen liegen im Ordner $SPEECH_TEST_DIR im Datenordner, bis zu $TAKES pro Zug.",
                )
                SubHeading("Eigene Stimme")
                Para(
                    "„Stimmprobe aufnehmen“ speichert etwa 15 Sekunden deiner Stimme samt Text im Ordner $VOICE_DIR. Daraus kann Qwen3-TTS die Ansagen " +
                        "mit deiner Stimme sprechen. Bis das eingerichtet ist, spricht die Systemstimme.",
                )
            }
            ConfigButton(vm, "Blind und Stimme einstellen")
        }
        HelpTopic.VARIANTS -> {
            Bullet("+ Neue Variante", "Züge vom Start bis zur Stellung spielen, Eröffnung und Namen eintragen, die Farbe wählen, mit der du spielst, speichern.")
            Bullet("Weiterführen", "übernimmt die Züge einer vorhandenen Variante, dann hängst du nur noch an, was anders ist.")
            Bullet(
                "Durchgehen",
                "öffnet die Variante am Start. Mit |◀ ◀ ▶ ▶| oder einem Tipp auf einen Zug in der Liste gehst du die Züge durch – die Variante bleibt dabei, wie sie ist. " +
                    "Ein anderer Zug auf dem Brett öffnet einen Nebenstrang nur zum Anschauen; „↩ Zurück zur Variante“" +
                    (if (touch) "" else " oder Strg+B Enter") + " führt zurück, „Züge bearbeiten“ wechselt ins Bearbeiten.",
            )
            Bullet("Bearbeiten", "dasselbe Brett, nur gleich bei der Endstellung. Ein anderer Zug mitten in der Variante ersetzt ab dort den Rest, „Ab hier kürzen“ wirft ihn ohne Ersatz weg, „Zug zurück“ nimmt den letzten zurück.")
            Para("Geänderte Züge sind ein neues Puzzle: der Lernstand dieser Variante fängt von vorn an.")
            Para(
                "Eine neue Variante geht auch mitten in einem Puzzle (Kürzel „Neue Variante“): sie legt sich darüber, " +
                    "nach Speichern oder Abbrechen geht es mit dem Puzzle weiter. Eine angefangene Variante wird dabei nie überschrieben.",
            )
        }
        HelpTopic.PASTE -> {
            Para("„PGN/FEN einfügen“ übernimmt eine Zugfolge oder Stellung, die du irgendwo gefunden hast. Einfach ins Feld einfügen – " + if (touch) "lange drücken, dann „Einfügen“." else "Cmd/Strg+V.")
            Bullet("PGN", "daraus wird die Hauptvariante. Kopfzeilen, Kommentare {…}, Nebenvarianten (…), !? und $1 und das Ergebnis fallen weg; „Opening“/„Variation“ aus dem Kopf füllen die Namen, und wer den letzten Zug gemacht hat, ist die Seite, die du spielst.")
            Bullet("FEN", "daraus wird die Stellung, ab der die Variante beginnt. Du spielst von dort die Züge, die du üben willst, und trainierst sie später auch ab dort.")
            Bullet("Zugliste", "„e4 c5 Nf3“ geht genauso.")
            Para("Das Eingefügte landet erst im Editor: durchgehen, mit „Ab hier kürzen“ zuschneiden, benennen, speichern.")
        }
        HelpTopic.LEARNING -> {
            Bullet("Lernen", "die heute fälligen Varianten und ein paar neue, mit Wertung.")
            Bullet("Frei üben", "alle Varianten gemischt, ohne Wertung.")
            Bullet("Üben", "bei einer Variante: nur diese.")
            Bullet("Züge üben", "die Stellungen einer Variante gemischt, jeweils nur den nächsten Zug finden.")
            Para("Wie viele neue Varianten pro Tag dazukommen, stellst du in der Konfiguration ein – gerade sind es ${settings.newPerDay}.")
            SubHeading("Während eines Puzzles")
            Bullet("Zielstellung zeigen", "blendet die gesuchte Stellung als Hilfe ein.")
            Bullet("↺ Nochmal von vorn", "setzt das Brett zurück; Fehler von vorher zählen trotzdem.")
            Bullet("Falscher Zug", "wird korrigiert, und die Variante kommt bald wieder. Ein Zug aus einer anderen gespeicherten Variante gibt nur einen Hinweis.")
            Bullet("Gut / Weiter", "am Ende: „Gut“, oder „Weiter“ nach einem Fehler." + if (touch) "" else " Am Computer gehen auch Enter und Leertaste.")
            Bullet("Variante nicht mehr abfragen", "pausiert sie; „Wieder abfragen“ auf der Startseite holt sie zurück.")
            ConfigButton(vm, "Neue pro Tag einstellen")
        }
        HelpTopic.BOARD -> {
            Bullet("Farben", "vier Farbsätze für die Felder, in der Konfiguration unter „Brett“.")
            if (!touch) Bullet("8", "dreht das Brett (nicht in einem Textfeld): im Editor bleibt es gedreht, bis du wieder 8 drückst, beim Üben bis zur nächsten Variante. Die Seite, die du spielst, bleibt dieselbe.")
            Bullet("Ziehen", "eine Figur antippen und dann das Zielfeld, oder sie hinziehen. Umwandeln wird immer zur Dame.")
            ConfigButton(vm, "Farben einstellen")
        }
        HelpTopic.PLAYER -> {
            Para("Wer sich eine Stellung oder Zugfolge nicht mehr merkt, lässt die Variante einfach ablaufen, statt jeden Zug anzuklicken.")
            Bullet("▶ Abspielen", "auf der Startseite bei einer Variante, oder im Editor direkt unter dem Brett. Es beginnt am Start (oder am Ende erneut von vorn) und geht von allein Zug um Zug weiter.")
            Bullet("⏸ Pause", "hält an der aktuellen Stellung an. Auch jeder Klick auf ein Feld, einen Pfeil oder einen Zug in der Liste hält den Player an.")
            Bullet("− / +", "weniger oder mehr Zeit pro Zug, gerade ${formatSeconds(settings.playDelayMs.toLong())} (zwischen ${formatSeconds(PLAY_DELAY_MIN.toLong())} und ${formatSeconds(PLAY_DELAY_MAX.toLong())}).")
            Bullet("Wiederholen", "beginnt nach dem letzten Zug wieder von vorn, bis du pausierst.")
            Para(
                "Als Kürzel: " + (if (touch) "ein Bereich beim Doppeltippen lässt sich auf „Abspielen“ legen." else "${settings.shortcutLabel(Command.PLAY)}.") +
                    " Das Gleiche stellst du auch in der Konfiguration ein.",
            )
            ConfigButton(vm, "Tempo einstellen")
        }
        HelpTopic.PLAYLIST -> {
            Para("Eine Playlist sind Varianten, die nacheinander drankommen, ohne dass du jede einzeln anklickst. Jede Eröffnung auf der Startseite ist schon eine – mit all ihren Varianten in der Reihenfolge der Liste. Dazu kannst du dir eigene zusammenstellen.")
            Bullet("▶ Abspielen", "zeigt die Stellungen: die Varianten laufen nacheinander mit dem Player ab. Die Endstellung bleibt kurz stehen, dann beginnt die nächste. „← Vorherige“ und „Nächste →“ springen, ⏸ hält an. Mit „Wiederholen“ fängt sie nach der letzten Variante von vorn an.")
            Bullet("Lernen", "fragt die Varianten nacheinander als Puzzle ab. Erst wenn du eins ohne Fehler gelöst hast, geht es mit „Gut – nächstes Puzzle“ weiter; mit Fehler kommt dasselbe Puzzle sofort nochmal. Oben steht, wo du bist („Puzzle 2 von 5“).")
            Para("Lernen mit Spaced Repetition bleibt davon unberührt: Playlists zählen nicht als Wiederholung.")
            SubHeading("Eigene Playlists")
            Bullet("+ Neue Playlist", "unter „Meine Playlists“: Varianten antippen – aus beliebigen Eröffnungen –, dann „Als Playlist speichern“ und einen Namen geben.")
            Bullet("Reihenfolge", "zeigt die Varianten der Playlist; ↑ und ↓ verschieben eine, ✕ nimmt sie heraus.")
            Bullet("Varianten ändern", "hakt die Varianten der Playlist an; weitere antippen oder abwählen, dann „Übernehmen“. Neue kommen ans Ende.")
            Bullet("Umbenennen, Löschen", "betreffen nur die Playlist, die Varianten bleiben.")
            SubHeading("Anpinnen")
            Bullet("Anpinnen", "holt eine Eröffnung ganz nach oben, über alle anderen. Bis zu ${Library.MAX_PINNED} Eröffnungen gehen; „Lösen“ stellt sie wieder an ihren Platz.")
            SubHeading("Auswählen und Löschen")
            Bullet("Auswählen", "bei einer Eröffnung zeigt an jeder Variante ein Kästchen; " + (if (touch) "antippen" else "anklicken") + " wählt sie aus. „Alle auswählen“ nimmt eine ganze Eröffnung dazu. Oben steht dann, was mit der Auswahl geht: als Playlist speichern, abspielen, lernen, archivieren oder löschen. „Fertig“ beendet das Auswählen.")
            Bullet("Löschen", "bei einer Eröffnung löscht sie mit allen Varianten. Wie beim Löschen einer Variante fragt die App erst nach, und der Lernstand ist danach weg.")
            SubHeading("Suchen")
            Para("Das Suchfeld unter den Zahlen auf der Startseite zeigt nur die Varianten, auf die alle Begriffe passen – auch im Archiv. Die Treffer lassen sich gleich abspielen, lernen oder auswählen.")
            Bullet("Züge", "gelten ab dem Anfang: „d4“ findet die 1.d4-Eröffnungen (nicht jeden Sizilianer, in dem später d4 kommt), „e4 c5“ findet 1.e4 c5, ein Schwarzzug allein die erste Antwort („c5“ ist 1…c5).")
            Bullet("Mit Zugnummer", "gilt ein Zug genau dort: „6.f3“, „3...e6“ oder „3. d4“. Deutsche Figuren gehen auch (Sf3 = Nf3), x, + und = darf man weglassen.")
            Bullet("Farbe", "„weiß“ oder „schwarz“: die Seite, die du in der Variante spielst.")
            Bullet("Alles andere", "wird im Namen der Eröffnung und der Variante gesucht, z. B. „najdorf“. Mehrere Begriffe: „schwarz c5 najdorf“. ✕ oder Esc leert das Feld.")
            Bullet(if (touch) "Ein- und ausblenden" else settings.shortcutLabel(Command.SEARCH), "blendet das Suchfeld aus – die Suche ist dann weg – und wieder ein, mit dem Cursor darin." + if (touch) " Steht in der Kürzel-Liste am Pferd unter „Suchen“." else "")
            Para("Solange gesucht wird, fehlen bei den Eröffnungen die Knöpfe für die ganze Eröffnung (Playlist, Archivieren, Löschen …), damit sie nicht die ausgeblendeten Varianten treffen. Im Archiv bleibt „Zurückholen“: es holt genau die angezeigten Varianten der Eröffnung zurück.")
            SubHeading("Archiv")
            Para("Zum Beiseitelegen statt Löschen, z. B. wenn du ein neues Repertoire aufbaust und das alte nicht im Weg sein soll.")
            Bullet("Archivieren", "bei einer Eröffnung, einer Variante oder einer Auswahl legt sie ins Archiv ganz unten auf der Startseite. Dort werden sie nicht abgefragt, zählen nicht bei „fällig“ und „neu“, fehlen in den Playlists, und ihre Züge gelten beim Üben nicht mehr als „andere Variante“. Eine angepinnte Eröffnung wird gelöst.")
            Bullet("Im Archiv", "„Öffnen“ zeigt alles darin. „Durchgehen“ öffnet eine Variante zum Anschauen, „Als Vorlage“ beginnt mit ihren Zügen eine neue Variante im Repertoire, „Zurückholen“ bringt sie mit ihrem alten Lernstand zurück, „Löschen“ löscht sie endgültig.")
        }
        HelpTopic.HORSE -> {
            Para("Unten rechts sitzt ein kleines Pferd. Es ersetzt am Handy die Tastenkürzel vom Computer.")
            Bullet("Antippen", "öffnet die Liste aller Kürzel; jeder Eintrag lässt sich dort auch direkt antippen.")
            Bullet("Halten", "wählt nach der Zeit. Der Ring ums Pferd füllt sich einmal pro Schritt, und daneben steht, was beim Loslassen passiert.")
            Bullet("Wegziehen", "den Finger vom Pferd wegziehen bricht ab.")
            SubHeading("Gerade eingestellt: ${formatSeconds(settings.holdStepMs.toLong())} pro Schritt")
            for (command in Command.entries.filter { (it.holdSteps ?: 0) > 0 }) {
                ShortcutRow(formatSeconds(command.holdSteps!!.toLong() * settings.holdStepMs) + " halten", command.label, null)
            }
            Spacer(Modifier.height(8.dp))
            Para("Zu schnell oder zu langsam? Die Haltezeit pro Schritt lässt sich zwischen ${formatSeconds(HOLD_STEP_MIN.toLong())} und ${formatSeconds(HOLD_STEP_MAX.toLong())} einstellen.")
            ConfigButton(vm, "Haltezeit einstellen")
        }
        HelpTopic.TAP_ZONES -> {
            Para(
                "Der Bildschirm ist in Bereiche geteilt, standardmäßig zwei Spalten und drei Reihen; wie viele es sein sollen, " +
                    "stellst du in der Konfiguration ein (1–$TAP_COLS_MAX Spalten, 1–$TAP_ROWS_MAX Reihen – zum Beispiel nur oben und unten). Zweimal schnell auf eine freie " +
                    "Stelle tippen führt aus, was diesem Bereich zugeordnet ist – auf jedem Bildschirm, auch mitten in einem Puzzle.",
            )
            Para(
                "Frei heißt: kein Knopf, kein Textfeld und nicht das Brett. Die funktionieren ganz normal weiter.",
            )
            SubHeading(if (settings.tapZonesOn) "Gerade eingestellt" else "Gerade ausgeschaltet – so wäre es eingestellt")
            ZoneGrid(settings, onPick = null)
            Spacer(Modifier.height(12.dp))
            ConfigButton(vm, "Bereiche einstellen")
        }
        HelpTopic.KEYBOARD -> {
            Para(
                "Erst ${settings.prefixLabel}, dann eine Taste – wie bei tmux. Geht überall, auch mitten in einem Puzzle: " +
                    "danach geht es dort weiter. Die Taste selbst landet nie in einem Textfeld.",
            )
            for (command in Command.entries) ShortcutRow(settings.shortcutLabel(command), command.label, null)
            SubHeading("Tasten ändern")
            Para(
                "Passt ein Buchstabe nicht, lässt er sich in der Konfiguration ändern: auf die Taste klicken, die neue drücken. " +
                    "Erlaubt sind Buchstaben, Ziffern und ?; ist die Taste schon vergeben, tauschen die beiden. " +
                    "Auch das B nach Strg lässt sich durch einen anderen Buchstaben ersetzen. " +
                    "Die Liste der Kürzel kommt immer auch mit ? oder h, falls man die eigene Belegung vergessen hat.",
            )
            Para(
                "${settings.prefixLabel} ist der Navigator zwischen den Modi: ein Buchstabe, der noch keinen Befehl hat, " +
                    "öffnet die Konfiguration bei den Tastenkürzeln, wo du ihn belegen kannst. " +
                    "Die oft gebrauchten Befehle liegen in der unteren Reihe (y x c v b n m), nah an Strg und B. " +
                    "Strg darf gedrückt bleiben: ${settings.prefixLabel} Strg+V ist dasselbe wie ${settings.prefixLabel} v, " +
                    "zweimal ${settings.prefixLabel} also ${settings.prefixLabel} ${settings.prefixKey}.",
            )
            ConfigButton(vm, "Tasten ändern")
            SubHeading("Weitere Tasten")
            for ((keys, label) in OTHER_KEYS) ShortcutRow(keys, label, null)
        }
        HelpTopic.LICHESS -> {
            val db = if (touch) "das Kürzel „Datenbank“" else settings.shortcutLabel(Command.EXPLORER)
            SubHeading("Eröffnungsdatenbank")
            Para(
                "$db öffnet neben dem Brett lichess' Eröffnungsdatenbank für die Stellung, die gerade auf dem Brett steht: " +
                    "jeder Zug, der dort gespielt wurde, wie oft, und wie die Partien ausgingen (weiß · remis · schwarz). " +
                    "„Meister“ sind Partien starker Spieler über dem Brett, „lichess“ Online-Partien ab 1600 (Blitz und langsamer). " +
                    "Das Fenster geht mit dem Brett mit, ein Klick auf einen Zug spielt ihn im Editor. Nochmal $db schließt es.",
            )
            Para(
                "lichess gibt die Datenbank nur mit einem persönlichen API-Schlüssel heraus: auf ${Explorer.TOKEN_PAGE} einen neuen anlegen " +
                    "(kein Häkchen nötig) und in der Konfiguration unter „lichess“ einfügen. Jeder nimmt seinen eigenen; er bleibt auf dem Gerät.",
            )
            ConfigButton(vm, "Schlüssel eintragen")
            if (!touch) {
                SubHeading("Engine")
                Para(
                    "${settings.shortcutLabel(Command.ENGINE)} lässt eine Engine auf diesem Computer die Stellung auf dem Brett rechnen: die besten Linien " +
                        "mit Bewertung aus Sicht von Weiß (+0,31 heißt Weiß steht etwas besser, #3 Matt in drei), immer tiefer, bis sich die Stellung ändert. " +
                        "Ein Klick auf eine Linie spielt ihren ersten Zug im Editor, Shift+Klick die ganze Linie.",
                )
                Para(
                    "${settings.opponentLabel} macht die Engine im Editor zum Gegner: wähle, welche Seite sie spielt (W / S) und wie tief sie für " +
                        "jeden Zug rechnet (↑ ↓) und ob sie den besten, zweit- oder drittbesten Zug spielt (1 2 3), Enter startet. Ab dann zieht sie für ihre Seite, sobald sie am Ende der Variante am Zug ist, und du " +
                        "spielst nur noch die andere Farbe – z. B. die ersten Züge einer Eröffnung beidseitig eingeben, dann Weiß der Engine geben und " +
                        "mit Schwarz weiterspielen. Zurückgehen lässt sie in Ruhe; ein anderer Zug dort, und sie antwortet darauf. Unter dem Brett: „Ändern“ und „Aus“.",
                )
                Para(
                    "FireHorse lädt keine Engine herunter. Unter Konfiguration → Engines gibst du den Pfad zu einer UCI-Engine an, " +
                        "die schon auf dem Computer ist (z. B. die Stockfish-Datei, die En Croissant installiert hat), oder legst sie in den Ordner " +
                        "${vm.engineFolder}. Mehrere Engines lassen sich einrichten und wählen; jede hat ihre eigenen Linien, Tiefe, Threads und Hash " +
                        "– „unbegrenzt“ rechnet immer weiter, eine feste Tiefe hält dort an.",
                )
            }
        }
        HelpTopic.DATA -> {
            Para("Alles wird nach jeder Änderung und jeder Antwort sofort gespeichert, in: ${vm.dataLocation}")
            if (vm.canTransfer) {
                Bullet("Alle exportieren", "schreibt Varianten und Lernstand in eine .json-Datei, z. B. um sie vom Computer aufs Handy zu bringen. „Exportieren“ bei einer Eröffnung auf der Startseite nimmt nur diese.")
                Bullet(
                    "Importieren",
                    "holt eine solche Datei herein. Danach fragt FireHorse: „Dazunehmen“ – neue Varianten kommen dazu, vorhandene werden aktualisiert, " +
                        "deine anderen bleiben. „Ersetzen“ – alle deine Varianten werden mit Lernstand gelöscht und nur die aus der Datei geladen.",
                )
            }
            if (touch) Para("Achtung: Deinstallieren löscht den App-Speicher und damit den Lernstand. Vorher exportieren.")
            ConfigButton(vm, "Zu Export und Import")
        }
    }
}

@Composable
private fun ConfigButton(vm: AppViewModel, text: String) {
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = vm::openConfig) { Text(text) }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

/** "• **Title** – text" */
@Composable
private fun Bullet(title: String, text: String) {
    Row(Modifier.padding(bottom = 8.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(" – ")
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
