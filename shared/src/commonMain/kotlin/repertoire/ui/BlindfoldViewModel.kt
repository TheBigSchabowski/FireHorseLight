package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import repertoire.core.BlindDrill
import repertoire.core.Exchange
import repertoire.core.Explorer
import repertoire.core.ExplorerSource
import repertoire.core.FilePicker
import repertoire.core.Library
import repertoire.core.LineReply
import repertoire.core.LineResponder
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.QuizBuilder
import repertoire.core.ReplyStrategy
import repertoire.core.ResponseSource
import repertoire.core.Speaker
import repertoire.core.Store
import repertoire.core.Variant
import repertoire.core.VoiceMove
import repertoire.core.compactMoves
import repertoire.core.createSpeaker
import repertoire.core.newId
import repertoire.core.numberedSan

data class BlindMoveEntry(
    val ply: Int,
    val san: String,
    val spokenEnglish: String,
    val byUser: Boolean,
    val info: String = "",
    val uci: String = "",
)

/** Die drei Bereiche der App. */
enum class Mode(val label: String) {
    TRAIN("🎯 Blind üben"),
    FREE("♟ Frei spielen"),
    LIBRARY("📚 Meine Varianten"),
}

/** Eine Variante, die gerade angelegt oder bearbeitet wird. */
data class VariantDraft(
    /** null: eine neue Variante. */
    val editingId: String? = null,
    val opening: String = "",
    val name: String = "",
    val playWhite: Boolean = true,
    val startFen: String = Position.START_FEN,
    val moves: List<Move> = emptyList(),
) {
    val positions: List<Position> by lazy {
        moves.runningFold(Position.fromFen(startFen)) { p, m -> p.play(m) }
    }
    val position: Position get() = positions.last()
    val canSave: Boolean get() = name.isNotBlank() && moves.isNotEmpty()

    /** "1. e4 c5 2. Nf3" */
    fun movesText(): String = compactMoves(moves.mapIndexed { i, m -> numberedSan(positions[i], m) })
}

class BlindfoldViewModel(
    private val store: Store,
    private val files: FilePicker? = null,
    private val speaker: Speaker = createSpeaker(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    /** Deine komplette FireHorse-Library (geladen aus `library.json` oder per JSON-Import). */
    var library by mutableStateOf(store.loadLibrary())
        private set

    var progress by mutableStateOf(store.loadProgress())
        private set

    var mode by mutableStateOf(if (library.active.isEmpty()) Mode.LIBRARY else Mode.TRAIN)
        private set

    /** Aktuelle Stellung auf dem (standardmäßig versteckten) Brett. */
    var position by mutableStateOf(Position.START)
        private set

    /** Letzter gespielter Zug (für die Hervorhebung, wenn das Brett per Doppeltipp gezeigt wird). */
    var lastMove by mutableStateOf<Move?>(null)
        private set

    /** Ist das Brett sichtbar? (Doppeltipp schaltet es jederzeit an/aus). */
    var showBoard by mutableStateOf(false)
        private set

    /** Spielst du Weiß oder Schwarz? (Frei spielen; beim Üben bestimmt die Variante die Seite.) */
    var playWhite by mutableStateOf(true)
        private set

    /** Optionaler Filter auf eine bestimmte Eröffnung aus deiner FireHorse-Library (null = Alle). */
    var selectedOpening by mutableStateOf<String?>(null)
        private set

    var source by mutableStateOf(ResponseSource.FIREHORSE_LIBRARY)
    var strategy by mutableStateOf(ReplyStrategy.MAIN)

    /** Textfeld, in das Voxtype (per Hotkey) direkt deine englischen Züge ("bishop c4", "knight c3") tippt. */
    var voxtypeBuffer by mutableStateOf("")

    var status by mutableStateOf("Voxtype bereit · Sag z.B. „e4“, „knight f3“, „bishop c4“ · Doppeltipp = Brett")
    var history by mutableStateOf<List<BlindMoveEntry>>(emptyList())
        private set

    /** Alle Eröffnungsnamen aus deiner geladenen FireHorse-Library. */
    val openings: List<String>
        get() = library.groups().map { it.opening }

    // ---- Blind üben ----

    /** Die Variante, die gerade blind nachgespielt wird. */
    var drill by mutableStateOf<BlindDrill?>(null)
        private set

    /** Die Varianten, die nach der aktuellen noch drankommen (Ids). */
    private var queue: List<String> = emptyList()

    /** Fehlversuche in der aktuellen Variante. */
    var mistakes by mutableStateOf(0)
        private set

    /** Die aktuelle Variante ist zu Ende gespielt. */
    var drillDone by mutableStateOf(false)
        private set

    /** Wie viele Varianten in dieser Runde schon fertig sind, und wie viele es insgesamt sind. */
    var roundDone by mutableStateOf(0)
        private set
    var roundSize by mutableStateOf(0)
        private set

    // ---- Meine Varianten ----

    /** Die Variante im Editor; null: die Liste wird gezeigt. */
    var draft by mutableStateOf<VariantDraft?>(null)
        private set

    /** Id der Variante, deren Löschen noch bestätigt werden muss. */
    var pendingDelete by mutableStateOf<String?>(null)

    init {
        if (mode == Mode.TRAIN) startRound()
    }

    fun switchMode(to: Mode) {
        if (to == mode) return
        reloadLibrary()
        mode = to
        pendingDelete = null
        showBoard = false
        when (to) {
            Mode.TRAIN -> startRound()
            Mode.FREE -> resetSession()
            Mode.LIBRARY -> status = ""
        }
    }

    /** Doppeltipp auf die Fläche: Schachbrett ein- oder ausblenden. */
    fun toggleBoard() {
        showBoard = !showBoard
    }

    /**
     * 1x Tippen auf die Fläche oder Enter nach Voxtype-Diktat:
     * Liest `voxtypeBuffer` (z.B. "bishop c4"), führt den Zug aus und lässt die Gegenseite antworten.
     */
    fun submitVoxtypeBuffer() {
        val text = voxtypeBuffer.trim()
        if (text.isEmpty()) return
        voxtypeBuffer = ""
        onSpokenEnglish(text)
    }

    /**
     * Verarbeitet deine englische Ansage ("bishop c4", "knight c3", "castles") oder ein Kommando
     * ("board", "repeat", "hint", "next", "restart", "undo").
     */
    fun onSpokenEnglish(transcript: String) {
        val lower = transcript.lowercase().trim().trimEnd('.', '!', '?')
        if (lower in listOf("board", "show board", "hide board", "peek")) {
            toggleBoard()
            return
        }
        if (lower in listOf("repeat", "say again")) {
            history.lastOrNull()?.let { say(it.spokenEnglish) }
            return
        }
        if (handleCommand(lower)) return

        val before = if (mode == Mode.LIBRARY) draft?.position ?: return else position
        val move = VoiceMove.parseSpokenEnglish(transcript, before)
        if (move == null) {
            status = "❌ Nicht erkannt oder hier nicht möglich: „$transcript“"
            say("Illegal move")
            return
        }
        playUserMove(move, "„$transcript“")
    }

    /** Erlaubt es auch, direkt auf dem aufgeklappten Brett zu ziehen. */
    fun onBoardMove(move: Move) {
        val before = if (mode == Mode.LIBRARY) draft?.position ?: return else position
        if (before.isLegal(move)) playUserMove(move, "Brett")
    }

    private fun handleCommand(lower: String): Boolean {
        when (mode) {
            Mode.TRAIN -> when (lower) {
                "hint", "solution", "help" -> hint()
                "next", "skip", "next line" -> nextDrill()
                "reset", "restart", "again", "start over" -> restartDrill()
                else -> return false
            }
            Mode.FREE -> when (lower) {
                "reset", "restart", "new game", "start over" -> resetSession()
                "undo", "back", "take back" -> undoFree()
                else -> return false
            }
            Mode.LIBRARY -> when (lower) {
                "undo", "back", "take back" -> undoDraftMove()
                else -> return false
            }
        }
        return true
    }

    private fun playUserMove(move: Move, heard: String) {
        when (mode) {
            Mode.TRAIN -> drillMove(move, heard)
            Mode.FREE -> {
                playOnBoard(move, byUser = true, info = heard)
                scope.launch { triggerBookReply() }
            }
            Mode.LIBRARY -> addDraftMove(move)
        }
    }

    private fun playOnBoard(move: Move, byUser: Boolean, info: String): BlindMoveEntry {
        val san = position.san(move)
        val spoken = VoiceMove.toSpokenEnglish(position, move)
        position = position.play(move)
        lastMove = move
        val entry = BlindMoveEntry(history.size + 1, san, spoken, byUser, info, move.uci)
        history = history + entry
        return entry
    }

    private fun say(text: String) {
        scope.launch { speaker.speakEnglish(text) }
    }

    // ---------------------------------------------------------------- Blind üben

    /** Die Varianten der gewählten Eröffnung (oder alle), in zufälliger Reihenfolge. */
    fun chooseTrainingOpening(opening: String?) {
        selectedOpening = opening
        if (mode == Mode.TRAIN) startRound()
    }

    /** Neue Runde über alle Varianten der gewählten Eröffnung, oder nur über [onlyId]. */
    private fun startRound(onlyId: String? = null) {
        val pool = library.active.filter { v ->
            v.moves.isNotEmpty() && (onlyId?.let { it == v.id } ?: (selectedOpening == null || Library.openingOf(v) == selectedOpening))
        }
        queue = pool.map { it.id }.shuffled()
        roundSize = queue.size
        roundDone = 0
        nextDrill()
    }

    /** Aus der Liste heraus genau diese eine Variante blind üben. */
    fun trainVariant(id: String) {
        reloadLibrary()
        mode = Mode.TRAIN
        draft = null
        pendingDelete = null
        selectedOpening = library.variants.firstOrNull { it.id == id }?.let(Library::openingOf)
        startRound(onlyId = id)
    }

    /** Alle Varianten einer Eröffnung blind üben. */
    fun trainOpening(opening: String) {
        reloadLibrary()
        mode = Mode.TRAIN
        draft = null
        selectedOpening = opening
        startRound()
    }

    fun nextDrill() {
        if (drill != null && drillDone) roundDone += 1
        val id = queue.firstOrNull()
        if (id == null) {
            drill = null
            drillDone = false
            position = Position.START
            lastMove = null
            history = emptyList()
            status = if (roundSize == 0) {
                "Keine Varianten zum Üben. Leg unter „Meine Varianten“ welche an."
            } else {
                "Runde geschafft: $roundSize Varianten. „Neue Runde“ oder eine andere Eröffnung wählen."
            }
            if (roundSize > 0) say("Round complete")
            return
        }
        queue = queue.drop(1)
        drill = BlindDrill.forVariants(library.active.filter { it.id == id }, library.active).firstOrNull()
        restartDrill()
    }

    /** Die aktuelle Variante von vorn. */
    fun restartDrill() {
        val d = drill ?: return startRound()
        position = d.start
        lastMove = null
        history = emptyList()
        mistakes = 0
        drillDone = false
        showBoard = false
        playWhite = d.variant.playWhite
        status = "Du spielst ${if (d.variant.playWhite) "Weiß" else "Schwarz"}. " +
            if (d.isUsersTurn(position)) "Dein Zug." else "Die Variante beginnt."
        scope.launch { continueDrill(first = true) }
    }

    /** Neue Runde mit derselben Auswahl. */
    fun newRound() = startRound()

    private fun drillMove(move: Move, heard: String) {
        val d = drill ?: return
        if (drillDone) {
            status = "Variante fertig. „next“ für die nächste, „restart“ für diese noch einmal."
            return
        }
        if (!d.isUsersTurn(position)) return
        when (d.judge(position, move)) {
            BlindDrill.Verdict.RIGHT -> {
                playOnBoard(move, byUser = true, info = heard)
                scope.launch { continueDrill(first = false) }
            }
            BlindDrill.Verdict.OTHER_VARIANT -> {
                mistakes += 1
                status = "↪ ${position.san(move)} ist eine andere Variante, nicht ${d.title}. Noch einmal."
                say("Other variant. Try again")
            }
            BlindDrill.Verdict.WRONG -> {
                mistakes += 1
                status = "✗ ${position.san(move)} steht nicht in deinen Varianten. Noch einmal (oder „hint“)."
                say("Wrong. Try again")
            }
        }
    }

    /** Spielt den Zug der Variante, wenn die Gegenseite dran ist, und prüft auf das Ende. */
    private suspend fun continueDrill(first: Boolean) {
        val d = drill ?: return
        if (finishIfDone(d)) return
        if (!d.isUsersTurn(position)) {
            val reply = d.expected(position) ?: return
            val step = playOnBoard(reply, byUser = false, info = d.title)
            if (finishIfDone(d)) return
            status = "Antwort: ${step.san} · dein Zug"
            speaker.speakEnglish(step.spokenEnglish)
        } else if (first) {
            speaker.speakEnglish("Your move")
        } else {
            status = "✓ Richtig · dein Zug"
        }
    }

    private suspend fun finishIfDone(d: BlindDrill): Boolean {
        if (!d.isDone(position)) return false
        drillDone = true
        showBoard = true
        val last = history.lastOrNull()
        status = "🏁 ${d.title} geschafft" +
            (if (mistakes == 0) " – fehlerfrei!" else " – $mistakes Fehlversuch${if (mistakes == 1) "" else "e"}.") +
            " Weiter mit „next“."
        if (last != null && !last.byUser) speaker.speakEnglish(last.spokenEnglish)
        speaker.speakEnglish(if (mistakes == 0) "Line complete. Perfect" else "Line complete")
        return true
    }

    /** Sagt den richtigen Zug an (zählt als Fehlversuch). */
    fun hint() {
        val d = drill ?: return
        if (drillDone || !d.isUsersTurn(position)) return
        val move = d.expected(position) ?: return
        mistakes += 1
        val spoken = VoiceMove.toSpokenEnglish(position, move)
        status = "💡 ${position.san(move)}"
        say(spoken)
    }

    // ---------------------------------------------------------------- Frei spielen

    private suspend fun triggerBookReply() {
        val reply: LineReply? = when (source) {
            ResponseSource.FIREHORSE_LIBRARY -> LineResponder.replyFromVariants(
                position = position,
                variants = library.active,
                openingFilter = selectedOpening,
                strategy = strategy,
            )
            ResponseSource.LICHESS_MASTERS, ResponseSource.LICHESS_PLAYERS -> {
                val token = library.settings.lichessToken
                if (token.isBlank()) {
                    status = "Kein Lichess-Token in library.json – nutze eigene FireHorse-Varianten."
                    LineResponder.replyFromVariants(position, library.active, selectedOpening, strategy)
                } else {
                    val explorerSrc = if (source == ResponseSource.LICHESS_MASTERS) ExplorerSource.MASTERS else ExplorerSource.LICHESS
                    runCatching {
                        val res = Explorer.fetch(explorerSrc, position.fen, token)
                        LineResponder.replyFromExplorer(position, res, strategy)
                    }.getOrNull()
                }
            }
        }

        if (reply == null) {
            status = "🏁 Ende der Linie nach ${history.size} Halbzügen. „Als Variante speichern“ hält sie fest."
            speaker.speakEnglish("End of line")
            return
        }

        val step = playOnBoard(reply.move, byUser = false, info = reply.label)
        status = "Antwort: ${step.san} (${step.spokenEnglish}) · ${reply.label}"
        speaker.speakEnglish(step.spokenEnglish)
    }

    fun setFreeOpening(opening: String?) {
        selectedOpening = opening
    }

    fun resetSession(newPlayWhite: Boolean = playWhite) {
        playWhite = newPlayWhite
        position = Position.START
        lastMove = null
        history = emptyList()
        showBoard = false
        status = "Neue Partie, du spielst ${if (playWhite) "Weiß" else "Schwarz"}."
        if (!playWhite) {
            scope.launch { triggerBookReply() }
        }
    }

    /** Nimmt deinen letzten Zug zurück (samt Antwort darauf). */
    private fun undoFree() {
        val lastUser = history.indexOfLast { it.byUser }
        if (lastUser < 0) return
        replayFree(history.take(lastUser))
        status = "Zurückgenommen. Dein Zug."
    }

    private fun replayFree(entries: List<BlindMoveEntry>) {
        position = Position.START
        lastMove = null
        history = emptyList()
        for (e in entries) playOnBoard(Move.fromUci(e.uci), e.byUser, e.info)
    }

    /** Die frei gespielte Partie als neue Variante in den Editor übernehmen. */
    fun saveFreeAsVariant() {
        if (history.isEmpty()) return
        mode = Mode.LIBRARY
        showBoard = false
        val opening = history.lastOrNull { !it.byUser }?.info
            ?.takeIf { source == ResponseSource.FIREHORSE_LIBRARY }
            ?.substringBefore(" › ")?.substringBefore(" / ")
            ?: selectedOpening.orEmpty()
        draft = VariantDraft(
            opening = opening,
            playWhite = playWhite,
            moves = history.map { Move.fromUci(it.uci) },
        )
        status = "Gib der Variante einen Namen und speichere sie."
    }

    // ---------------------------------------------------------------- Meine Varianten

    fun newVariant() {
        reloadLibrary()
        draft = VariantDraft(opening = selectedOpening.orEmpty())
        status = "Spiel die Züge auf dem Brett oder sag sie an („e4“, „knight f3“). „undo“ nimmt zurück."
    }

    fun editVariant(id: String) {
        reloadLibrary()
        val v = library.variants.firstOrNull { it.id == id } ?: return
        draft = VariantDraft(
            editingId = v.id,
            opening = v.opening,
            name = v.name,
            playWhite = v.playWhite,
            startFen = v.startFen,
            moves = v.moves.map(Move::fromUci),
        )
        status = ""
    }

    fun updateDraft(change: (VariantDraft) -> VariantDraft) {
        draft = draft?.let(change)
    }

    private fun addDraftMove(move: Move) {
        val d = draft ?: return
        val spoken = VoiceMove.toSpokenEnglish(d.position, move)
        draft = d.copy(moves = d.moves + move)
        status = "${draft?.movesText()}"
        say(spoken)
    }

    fun undoDraftMove() {
        val d = draft ?: return
        if (d.moves.isEmpty()) return
        draft = d.copy(moves = d.moves.dropLast(1))
        status = draft?.movesText().orEmpty()
    }

    fun clearDraftMoves() {
        draft = draft?.copy(moves = emptyList())
        status = ""
    }

    fun cancelDraft() {
        draft = null
        status = ""
    }

    fun saveDraft() {
        val d = draft ?: return
        if (!d.canSave) return
        val id = d.editingId ?: newId()
        val moves = d.moves.map { it.uci }
        var oldLine: Variant? = null
        updateLibrary { lib ->
            val old = lib.variants.firstOrNull { it.id == id }
            oldLine = old
            // Theorie, Zeichnungen und Archiv-Status aus FireHorse bleiben beim Bearbeiten erhalten.
            val variant = old?.copy(opening = d.opening.trim(), name = d.name.trim(), moves = moves, playWhite = d.playWhite)
                ?: Variant(id, d.opening.trim(), d.name.trim(), moves, d.playWhite, startFen = d.startFen)
            lib.copy(variants = if (old != null) lib.variants.map { if (it.id == id) variant else it } else lib.variants + variant)
        }
        // Andere Züge sind ein anderes Puzzle: der Lernstand in FireHorse beginnt von vorn.
        val old = oldLine
        if (old != null && (old.moves != moves || old.startFen != d.startFen)) {
            updateProgress { it.copy(cards = it.cards - id) }
        }
        val title = QuizBuilder.title(library.variants.first { it.id == id })
        draft = null
        status = "✅ Gespeichert: $title"
    }

    fun deleteVariant(id: String) {
        val title = library.variants.firstOrNull { it.id == id }?.let(QuizBuilder::title) ?: return
        updateLibrary { lib ->
            val variants = lib.variants.filterNot { it.id == id }
            val left = variants.map(Library::openingOf).toSet()
            lib.copy(
                variants = variants,
                pinned = lib.pinned.filter { it in left },
                playlists = lib.playlists.map { it.copy(variantIds = it.variantIds - id) },
            )
        }
        updateProgress { it.copy(cards = it.cards - id) }
        pendingDelete = null
        status = "🗑 Gelöscht: $title"
    }

    /**
     * Lädt eine aus FireHorse exportierte `.json`-Datei (`"format": "eroeffnungs-puzzle"`) ein
     * und fügt alle darin enthaltenen Varianten zum lokalen Datenbestand hinzu.
     */
    fun importFireHorseJson() {
        val picker = files ?: return
        picker.open { text ->
            if (text == null) return@open
            runCatching {
                reloadLibrary()
                val res = Exchange.import(text, library, progress, replace = false)
                library = res.library
                progress = res.progress
                store.saveLibrary(library)
                store.saveProgress(progress)
                status = "✅ FireHorse JSON importiert: ${res.summary()} (Gesamt: ${library.active.size} Varianten)"
            }.onFailure { err ->
                status = "Import-Fehler: ${err.message}"
            }
        }
    }

    // ---------------------------------------------------------------- Speicher

    /** FireHorse kann dieselbe library.json inzwischen geändert haben. */
    private fun reloadLibrary() {
        library = store.loadLibrary()
        progress = store.loadProgress()
    }

    /** Ändert immer den Stand auf der Platte, damit nichts überschrieben wird, was FireHorse gespeichert hat. */
    private fun updateLibrary(transform: (Library) -> Library) {
        library = transform(store.loadLibrary())
        store.saveLibrary(library)
    }

    private fun updateProgress(transform: (Progress) -> Progress) {
        progress = transform(store.loadProgress())
        store.saveProgress(progress)
    }
}
