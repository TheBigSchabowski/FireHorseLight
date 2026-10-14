package repertoire.core

/**
 * Says moves out loud in English ("Knight f 3"). [say] returns at once; what is said queues up and
 * is spoken one after the other.
 */
interface Speaker {
    fun say(text: String)

    /** Stops what is being said and drops what is waiting. */
    fun stop()
}

expect fun createSpeaker(): Speaker

/**
 * Speech to text: records from the microphone and writes down what was said. On a computer with
 * voxtype; on a phone the keyboard's dictation types into the field instead, so there is none.
 */
interface VoiceInput {
    /** Why it does not work here (no voxtype, no microphone), null if it does. */
    fun problem(): String?

    /** Starts recording; false if it cannot. */
    fun start(): Boolean

    /** Stops recording; the recording as a WAV file (16 kHz, mono), null if there was none. */
    fun stop(): ByteArray?

    /** Stops recording and throws it away. */
    fun cancel()

    /** What was said in [wav]. Takes a moment: call it away from the UI thread. [prompt] tells the recognizer what to expect. */
    fun transcribe(wav: ByteArray, prompt: String): String

    /** What was kept as [name] before, null if nothing. */
    fun read(name: String): ByteArray?

    /** Everything kept under the folder [dir], as names to [read] ("Spracherkennung/knight f3/1.wav"). */
    fun list(dir: String): List<String>

    /** Throws away what was kept as [name]. */
    fun delete(name: String)

    /** Keeps [data] as [name] in the data folder (e.g. "Spracherkennung/07.wav"); returns where it went. */
    fun keep(data: ByteArray, name: String): String
}

/** What the recognizer is told to expect, so "knight f three" is written as "knight f3" and not "9-3". */
const val CHESS_PROMPT =
    "Chess moves: e4, d4, knight f3, bishop c4, rook e1, queen d1, king e2, pawn e4, e takes d5, " +
        "castles kingside, castles queenside, promotes to queen. Commands: board, repeat, hint, next, restart, undo."
