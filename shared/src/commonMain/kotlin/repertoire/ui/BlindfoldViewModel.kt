package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import repertoire.core.Exchange
import repertoire.core.Explorer
import repertoire.core.ExplorerSource
import repertoire.core.FilePicker
import repertoire.core.Library
import repertoire.core.LineReply
import repertoire.core.LineResponder
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.ReplyStrategy
import repertoire.core.ResponseSource
import repertoire.core.Speaker
import repertoire.core.Store
import repertoire.core.VoiceMove
import repertoire.core.createSpeaker

data class BlindMoveEntry(
    val ply: Int,
    val san: String,
    val spokenEnglish: String,
    val byUser: Boolean,
    val info: String = "",
)

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

    /** Aktuelle Stellung auf dem (standardmäßig versteckten) Brett. */
    var position by mutableStateOf(Position.START)
        private set

    /** Letzter gespielter Zug (für die Hervorhebung, wenn das Brett per Doppeltipp gezeigt wird). */
    var lastMove by mutableStateOf<Move?>(null)
        private set

    /** Ist das Brett sichtbar? (Doppeltipp schaltet es jederzeit an/aus). */
    var showBoard by mutableStateOf(false)
        private set

    /** Spielst du Weiß oder Schwarz? */
    var playWhite by mutableStateOf(true)
        private set

    /** Optionaler Filter auf eine bestimmte Eröffnung aus deiner FireHorse-Library (null = Alle). */
    var selectedOpening by mutableStateOf<String?>(null)

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

    /** Doppeltipp auf die Fläche: Schachbrett ein- oder ausblenden. */
    fun toggleBoard() {
        showBoard = !showBoard
    }

    /**
     * 1x Tippen auf die Fläche oder Enter nach Voxtype-Diktat:
     * Liest `voxtypeBuffer` (z.B. "bishop c4" oder auch mehrere Züge auf einmal "e4 e5 knight f3"),
     * führt den Zug aus und lässt den Computer aus deinen Linien / Lichess antworten.
     */
    fun submitVoxtypeBuffer() {
        val text = voxtypeBuffer.trim()
        if (text.isEmpty()) return
        voxtypeBuffer = ""
        onSpokenEnglish(text)
    }

    /**
     * Verarbeitet deine englische Ansage ("bishop c4", "knight c3", "castles" oder "show board").
     */
    fun onSpokenEnglish(transcript: String) {
        val lower = transcript.lowercase().trim()
        if (lower in listOf("board", "show board", "hide board", "peek")) {
            toggleBoard()
            return
        }
        if (lower in listOf("reset", "new game", "restart")) {
            resetSession()
            return
        }
        if (lower in listOf("repeat", "again")) {
            history.lastOrNull()?.let { scope.launch { speaker.speakEnglish(it.spokenEnglish) } }
            return
        }

        val move = VoiceMove.parseSpokenEnglish(transcript, position)
        if (move == null) {
            status = "❌ Not legal here: \"$transcript\""
            scope.launch { speaker.speakEnglish("Illegal move") }
            return
        }

        playOnBoard(move, byUser = true, info = "Heard: \"$transcript\"")

        // Computer antwortet automatisch mit einem Zug aus deinen FireHorse-Linien oder Lichess!
        scope.launch {
            triggerBookReply()
        }
    }

    /** Erlaubt es auch, direkt auf dem aufgeklappten Brett zu ziehen. */
    fun onBoardMove(move: Move) {
        if (!position.isLegal(move)) return
        playOnBoard(move, byUser = true, info = "Board move")
        scope.launch { triggerBookReply() }
    }

    private fun playOnBoard(move: Move, byUser: Boolean, info: String): BlindMoveEntry {
        val san = position.san(move)
        val spoken = VoiceMove.toSpokenEnglish(position, move)
        position = position.play(move)
        lastMove = move
        val entry = BlindMoveEntry(history.size + 1, san, spoken, byUser, info)
        history = history + entry
        return entry
    }

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
            status = "🏁 End of line reached after ${history.size} plies!"
            speaker.speakEnglish("End of line")
            return
        }

        val step = playOnBoard(reply.move, byUser = false, info = reply.label)
        status = "Reply: ${step.san} (${step.spokenEnglish}) · ${reply.label}"
        speaker.speakEnglish(step.spokenEnglish)
    }

    fun resetSession(newPlayWhite: Boolean = playWhite) {
        playWhite = newPlayWhite
        position = Position.START
        lastMove = null
        history = emptyList()
        showBoard = false
        status = "New blindfold line (${if (playWhite) "White" else "Black"})."
        if (!playWhite) {
            scope.launch { triggerBookReply() }
        }
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
                val res = Exchange.import(text, library, progress, replace = false)
                library = res.library
                progress = res.progress
                store.saveLibrary(library)
                store.saveProgress(progress)
                status = "✅ FireHorse JSON importiert: ${res.summary()} (Gesamt:${library.active.size} Varianten)"
            }.onFailure { err ->
                status = "Import-Fehler: ${err.message}"
            }
        }
    }
}
