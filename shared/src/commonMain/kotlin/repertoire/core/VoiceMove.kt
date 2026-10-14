package repertoire.core

/**
 * Moves said in English, as speech recognition (voxtype / Whisper) writes them down: "knight f3",
 * "Knight F3.", "night ef three", "e takes d5", "castle's king side", "Go on to E4".
 *
 * Rather than rewriting the text until it is SAN, the transcript is read for what it names (a
 * piece, squares, a file, takes, castling, a promotion), and that is matched against the legal
 * moves of the position. Words it does not know are left out, so "pawn to e4" and "go on to e4"
 * both end up as the e-pawn to e4.
 */
object VoiceMove {

    /** What a transcript names; nothing of it has been checked against a position yet. */
    data class Heard(
        /** "N", "B", "R", "Q", "K", "P" or null when no piece was named. */
        val piece: Char? = null,
        /** The squares in the order they were said; the last one is where the move goes. */
        val squares: List<Int> = emptyList(),
        /** A file said on its own, as in "e takes d5": where the piece comes from. */
        val fromFile: Int? = null,
        val takes: Boolean = false,
        /** 'K' for kingside, 'Q' for queenside, '?' for castling without a side. */
        val castle: Char? = null,
        /** The piece a pawn becomes, lowercase like [Move.promotion]. */
        val promotion: Char? = null,
        val command: VoiceCommand? = null,
    ) {
        val target: Int? get() = squares.lastOrNull()

        /** "Springer f3", "Rochade lang", "Kommando hint": what was understood, for the speech test. */
        fun describe(): String = when {
            command != null -> "Kommando ${command.words.first()}"
            castle == 'K' -> "Rochade kurz"
            castle == 'Q' -> "Rochade lang"
            castle != null -> "Rochade"
            target == null -> "–"
            else -> buildString {
                append(PIECE_NAMES[piece ?: 'P'])
                if (fromFile != null) append(" von der ${'a' + fromFile}-Linie")
                if (squares.size > 1) append(" " + squareName(squares[squares.size - 2]))
                append(if (takes) " schlägt " else " ")
                append(squareName(target!!))
                if (promotion != null) append(" = ${PIECE_NAMES[promotion.uppercaseChar()]}")
            }
        }
    }

    private val PIECE_NAMES = mapOf('N' to "Springer", 'B' to "Läufer", 'R' to "Turm", 'Q' to "Dame", 'K' to "König", 'P' to "Bauer")

    private val PIECES = mapOf(
        "knight" to 'N', "knights" to 'N', "night" to 'N', "nights" to 'N', "nite" to 'N', "horse" to 'N',
        "bishop" to 'B', "bishops" to 'B',
        "rook" to 'R', "rooks" to 'R', "rock" to 'R', "ruk" to 'R',
        "queen" to 'Q', "queens" to 'Q',
        "king" to 'K', "kings" to 'K',
        "pawn" to 'P', "pawns" to 'P', "pond" to 'P', "porn" to 'P',
    )

    private val FILES = mapOf(
        "a" to 0, "ay" to 0, "alpha" to 0,
        "b" to 1, "be" to 1, "bee" to 1, "bravo" to 1,
        "c" to 2, "see" to 2, "sea" to 2, "si" to 2, "charlie" to 2,
        "d" to 3, "dee" to 3, "delta" to 3,
        "e" to 4, "ee" to 4, "echo" to 4,
        "f" to 5, "ef" to 5, "eff" to 5, "foxtrot" to 5,
        "g" to 6, "gee" to 6, "ji" to 6, "golf" to 6,
        "h" to 7, "aitch" to 7, "hotel" to 7,
    )

    private val RANKS = mapOf(
        "1" to 0, "one" to 0, "won" to 0,
        "2" to 1, "two" to 1, "too" to 1,
        "3" to 2, "three" to 2, "tree" to 2, "free" to 2,
        "4" to 3, "four" to 3, "for" to 3, "fore" to 3,
        "5" to 4, "five" to 4,
        "6" to 5, "six" to 5, "sicks" to 5,
        "7" to 6, "seven" to 6,
        "8" to 7, "eight" to 7, "ate" to 7,
    )

    private val TAKES = setOf("takes", "take", "taking", "captures", "capture", "x", "times", "cross")
    private val CASTLES = setOf("castle", "castles", "castling", "castled")
    private val KINGSIDE = setOf("kingside", "short")
    private val QUEENSIDE = setOf("queenside", "long")
    private val PROMOTES = setOf("promotes", "promote", "promoting", "promotion", "equals", "becomes")

    /** Reads [transcript] for what it names. */
    fun read(transcript: String): Heard {
        val text = transcript.lowercase()
            .replace("o-o-o", " castle queenside ").replace("0-0-0", " castle queenside ")
            .replace("o-o", " castle kingside ").replace("0-0", " castle kingside ")
            .replace(Regex("king\\s*side"), "kingside").replace(Regex("queen\\s*side"), "queenside")
            .replace(Regex("came\\s*side"), "kingside")
        // "castle's" → "castle"; then everything that is not a letter or digit separates words.
        val words = text.replace(Regex("'s\\b"), "").split(Regex("[^a-z0-9=]+")).filter { it.isNotEmpty() }

        VoiceCommand.entries.firstOrNull { it.matches(words) }?.let { return Heard(command = it) }

        var piece: Char? = null
        var promotion: Char? = null
        var promoting = false
        var takes = false
        var castle: Char? = null
        val squares = mutableListOf<Int>()
        var fromFile: Int? = null
        var pendingFile: Int? = null

        fun settleFile() {
            pendingFile?.let { if (squares.isEmpty()) fromFile = it }
            pendingFile = null
        }

        for (word in words.flatMap(::split)) {
            val rank = RANKS[word]
            when {
                pendingFile != null && rank != null -> {
                    squares += rank * 8 + pendingFile!!
                    pendingFile = null
                }
                word in CASTLES -> castle = castle ?: '?'
                word in KINGSIDE -> castle = 'K'
                word in QUEENSIDE -> castle = 'Q'
                word in PROMOTES || word == "=" -> {
                    settleFile()
                    promoting = true
                }
                word in TAKES -> {
                    settleFile()
                    takes = true
                }
                word in PIECES -> {
                    settleFile()
                    val p = PIECES.getValue(word)
                    when {
                        // "e8 queen", "e8 promotes to queen": a piece after the target square is what the pawn becomes.
                        (promoting || squares.isNotEmpty()) && p in "QRBN" -> promotion = p.lowercaseChar()
                        piece == null -> piece = p
                    }
                }
                word in FILES -> {
                    settleFile()
                    pendingFile = FILES.getValue(word)
                }
                else -> settleFile()
            }
        }
        settleFile()
        // "king side" alone (no "castle") is still castling when nothing else was said.
        if (castle == null && squares.isEmpty() && words.any { it in KINGSIDE || it in QUEENSIDE }) castle = '?'
        return Heard(piece, squares, fromFile, takes, castle, promotion)
    }

    /** "e4" → ["e", "4"], "nf3" → ["n", "f", "3"]; words stay as they are. */
    private fun split(word: String): List<String> = when {
        word in PIECES || word in FILES || word in RANKS || word in TAKES -> listOf(word)
        Regex("[a-h][1-8]").matches(word) -> listOf(word.substring(0, 1), word.substring(1))
        Regex("[nbrqk][a-h][1-8]").matches(word) -> listOf(LETTER_PIECES.getValue(word[0]), word.substring(1, 2), word.substring(2))
        Regex("[a-h]x?[a-h][1-8]").matches(word) -> listOf(word.substring(0, 1), "takes", word.takeLast(2).take(1), word.takeLast(1))
        Regex("[a-h][1-8][a-h][1-8][qrbn]?").matches(word) -> word.chunked(1).let { c ->
            listOf(c[0], c[1], c[2], c[3]) + listOfNotNull(c.getOrNull(4)?.let { LETTER_PIECES.getValue(it[0]) })
        }
        else -> listOf(word)
    }

    private val LETTER_PIECES = mapOf('n' to "knight", 'b' to "bishop", 'r' to "rook", 'q' to "queen", 'k' to "king")

    /**
     * The legal move in [position] that [transcript] names, or null if it names none or more than
     * one. Typed SAN ("Nf3", "O-O") and coordinates ("g1f3") work too.
     */
    fun parseSpokenEnglish(transcript: String, position: Position): Move? {
        val raw = transcript.trim().trimEnd('.', ',', '!', '?')
        if (raw.isEmpty()) return null
        // Typed rather than said: SAN with capital pieces, or coordinates.
        if (raw.none { it.isWhitespace() }) {
            position.parseSan(raw)?.let { return it }
            if (Regex("[a-h][1-8][a-h][1-8][qrbn]?").matches(raw)) {
                runCatching { Move.fromUci(raw) }.getOrNull()?.let { m ->
                    position.legalMoves().firstOrNull { it.from == m.from && it.to == m.to && (it.promotion ?: 'q') == (m.promotion ?: 'q') }
                }?.let { return it }
            }
        }
        return match(read(raw), position)
    }

    /**
     * What [transcript] means in [position]: first what the user's own recordings taught ([learned]:
     * what voxtype wrote → what was said, see [SpeechTest.learned]), then read word by word. Only
     * if that does not even name a square is it guessed by sound: a clear "knight f6" that cannot be
     * played is not turned into the "knight f3" that can.
     */
    fun understand(transcript: String, position: Position, learned: Map<String, String> = emptyMap()): Move? {
        val meant = learned[normalize(transcript)] ?: transcript
        parseSpokenEnglish(meant, position)?.let { return it }
        val heard = read(meant)
        return if (heard.target == null && heard.castle == null && heard.command == null) closest(meant, position) else null
    }

    /**
     * The legal move whose spoken form sounds most like [transcript]: "night of free" is closest to
     * "knight f three". Only when it is clearly the closest and not far off; null otherwise.
     */
    fun closest(transcript: String, position: Position): Move? {
        val heard = normalize(transcript)
        if (heard.isBlank()) return null
        val key = soundKey(heard)
        val scored = position.legalMoves().map { m ->
            val said = normalize(toSpokenEnglish(position, m))
            // How it sounds counts double; the letters themselves decide between moves that sound alike.
            m to 2 * distance(key, soundKey(said)) + distance(heard.replace(" ", ""), said.replace(" ", ""))
        }.sortedBy { it.second }
        val (best, score) = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)?.second ?: Int.MAX_VALUE
        return best.takeIf { score <= key.length + 2 && score < second }
    }

    private val LETTER_NAMES = mapOf(
        "a" to "ay", "b" to "bee", "c" to "see", "d" to "dee", "e" to "ee", "f" to "ef", "g" to "gee", "h" to "aitch",
    )
    private val DIGIT_NAMES = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")

    /** Lowercase words as they are said: "Knight F3." → "knight ef three". */
    fun normalize(text: String): String =
        text.lowercase().replace(Regex("'s\\b"), "").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            .flatMap { w -> if (Regex("[a-h][1-8]").matches(w)) listOf(w.take(1), w.drop(1)) else listOf(w) }
            .joinToString(" ") { w ->
                LETTER_NAMES[w] ?: w.singleOrNull()?.takeIf { it in '1'..'8' }?.let { DIGIT_NAMES[it - '1'] } ?: w
            }

    /**
     * A rough key for how [words] sound: "kn", "gh" and "th" as they are heard (n, nothing, f — speech
     * recognition writes "free" for "three"), every run of vowels as one, doubled letters once.
     */
    private fun soundKey(words: String): String {
        var s = words.replace(" ", "")
        for ((from, to) in listOf("kn" to "n", "gh" to "", "ph" to "f", "th" to "f", "ck" to "k", "wh" to "w", "q" to "k", "x" to "ks")) {
            s = s.replace(from, to)
        }
        s = s.replace(Regex("[aeiouy]+"), "a")
        return s.replace(Regex("(.)\\1+"), "$1")
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** The one legal move that fits what was [heard]; null if none or several do. */
    fun match(heard: Heard, position: Position): Move? {
        if (heard.command != null) return null
        val legal = position.legalMoves()
        fun pieceOf(m: Move) = position.pieceAt(m.from)?.uppercaseChar()

        heard.castle?.let { side ->
            val castles = legal.filter { pieceOf(it) == 'K' && kotlin.math.abs(it.to - it.from) == 2 }
            val wanted = when (side) {
                'K' -> castles.filter { it.to > it.from }
                'Q' -> castles.filter { it.to < it.from }
                // Only one way to castle is legal: that one; with both, "castles" means kingside.
                else -> castles.filter { it.to > it.from }.ifEmpty { castles }
            }
            return wanted.singleOrNull()
        }

        val target = heard.target ?: return null
        val from = heard.squares.dropLast(1).lastOrNull()
        var candidates = legal.filter { it.to == target }
        if (from != null) candidates.filter { it.from == from }.takeIf { it.isNotEmpty() }?.let { candidates = it }
        heard.fromFile?.let { file -> candidates.filter { it.from % 8 == file }.takeIf { it.isNotEmpty() }?.let { candidates = it } }
        candidates = when (val piece = heard.piece) {
            null -> candidates.filter { pieceOf(it) == 'P' }.ifEmpty { candidates }
            else -> candidates.filter { pieceOf(it) == piece }
        }
        // A promotion nobody named becomes a queen.
        val promo = heard.promotion ?: 'q'
        candidates.filter { it.promotion != null }.takeIf { it.isNotEmpty() }?.let { promotions ->
            return promotions.singleOrNull { it.promotion == promo } ?: promotions.firstOrNull { it.promotion == promo }
        }
        return candidates.singleOrNull()
    }

    /**
     * [move] in words for the speech output: "Knight f 3", "c takes d 4", "Castles kingside".
     * The space between file and rank makes text-to-speech say "f three", not "f-third".
     */
    fun toSpokenEnglish(position: Position, move: Move): String {
        val san = position.san(move)
        val suffix = when {
            san.endsWith("#") -> ", checkmate"
            san.endsWith("+") -> ", check"
            else -> ""
        }
        if (san.startsWith("O-O-O")) return "Castles queenside$suffix"
        if (san.startsWith("O-O")) return "Castles kingside$suffix"
        val core = san.trimEnd('+', '#')
        val pieceWord = when (core.first()) {
            'N' -> "Knight "
            'B' -> "Bishop "
            'R' -> "Rook "
            'Q' -> "Queen "
            'K' -> "King "
            else -> ""
        }
        val rest = (if (pieceWord.isNotEmpty()) core.drop(1) else core)
            .replace("x", " takes ")
            .replace("=Q", " promotes to queen")
            .replace("=R", " promotes to rook")
            .replace("=B", " promotes to bishop")
            .replace("=N", " promotes to knight")
            .replace(Regex("([a-h])([1-8])"), "$1 $2")
        return (pieceWord + rest).trim().replace(Regex("\\s+"), " ") + suffix
    }
}

/** What can be said instead of a move. */
enum class VoiceCommand(vararg val words: String) {
    BOARD("board", "show board", "hide board", "peek"),
    REPEAT("repeat", "say again", "again please"),
    HINT("hint", "help", "solution"),
    NEXT("next", "good", "okay", "ok"),
    RESTART("restart", "start over", "reset"),
    UNDO("undo", "back", "take back"),
    ;

    /** The whole transcript is this command, give or take a "please". */
    fun matches(words: List<String>): Boolean {
        val said = words.filterNot { it == "please" }.joinToString(" ")
        return this.words.any { it == said }
    }
}

/**
 * Saying moves in: the app says a move, the user says it after, and what voxtype writes down is
 * kept with the recording. The common opening moves – every piece, file and rank among them –
 * castling, promotions and the commands. What it got wrong teaches [learned], and the recordings
 * are what a recognizer of its own would be built from.
 */
object SpeechTest {
    private val MOVES = listOf(
        "e4", "d4", "c4", "f4", "b3", "g3", "a3", "h3", "b4", "g4", "a4", "h4", "e3", "d3", "c3", "f3",
        "e5", "d5", "c5", "f5", "e6", "d6", "c6", "g6", "b6", "a6", "h6", "b5", "g5", "a5", "h5",
        "Nf3", "Nc3", "Nf6", "Nc6", "Nd2", "Nd7", "Nbd2", "Nbd7", "Ne2", "Ne7", "Nge2", "Nge7",
        "Ng5", "Ng4", "Ne5", "Ne4", "Nd4", "Nd5", "Nb5", "Nb4", "Nh3", "Nh6", "Na3", "Na6",
        "Bc4", "Bc5", "Bb5", "Bb4", "Be2", "Be7", "Bd3", "Bd6", "Bg5", "Bg4", "Bf4", "Bf5", "Be3", "Be6",
        "Bg2", "Bg7", "Bb2", "Bb7", "Ba3", "Bh6",
        "Re1", "Re8", "Rd1", "Rd8", "Rc1", "Rc8", "Rb1", "Rb8", "Rfe1", "Rfd8", "Rad1", "Rac8",
        "Qd2", "Qd7", "Qe2", "Qe7", "Qc2", "Qc7", "Qb3", "Qb6", "Qa4", "Qa5", "Qh5", "Qf3", "Qg4",
        "Kg1", "Kg8", "Kh1", "Kh8", "Kf1", "Kf8", "Ke2", "Ke7", "Kd2",
        "exd5", "exd4", "cxd4", "dxe5", "dxc4", "fxe5", "bxc6", "Nxd4", "Nxe5", "Nxd5", "Bxf7", "Bxc6", "Bxf6",
        "Qxd4", "Qxd5", "Rxe8",
        "O-O", "O-O-O", "e8=Q", "a8=Q", "b1=N", "exd8=Q",
    )

    private val PIECE_WORDS = mapOf('N' to "knight", 'B' to "bishop", 'R' to "rook", 'Q' to "queen", 'K' to "king")

    /** SAN as it is said: "Nbd2" → "knight b d2", "exd5" → "e takes d5", "e8=Q" → "e8 queen". */
    fun phrase(san: String): String {
        if (san == "O-O") return "castles kingside"
        if (san == "O-O-O") return "castles queenside"
        val piece = PIECE_WORDS[san.first()]
        var rest = if (piece != null) san.drop(1) else san
        val promotion = rest.substringAfter('=', "").firstOrNull()?.let { PIECE_WORDS[it] }
        rest = rest.substringBefore('=')
        val words = mutableListOf<String>()
        piece?.let(words::add)
        val target = rest.takeLast(2)
        val before = rest.dropLast(2)
        before.replace("x", "").forEach { words += it.toString() }
        if ('x' in before) words += "takes"
        words += target
        promotion?.let(words::add)
        return words.joinToString(" ")
    }

    /** The common opening moves. */
    val PHRASES: List<String> = MOVES.map(::phrase) + listOf("pawn to e4", "knight to c3", "bishop to e2")

    /** The commands, as their first word. */
    val COMMANDS: List<String> = VoiceCommand.entries.map { it.words.first() }

    /**
     * What to say in: the moves of the user's own variants (those are the ones said when
     * practising) and the commands; with [common], the common opening moves after them.
     */
    fun phrases(variants: List<Variant>, common: Boolean = false): List<String> {
        val own = variants.flatMap { v ->
            v.moves.mapIndexed { i, uci -> phraseOf(v.positions[i], Move.fromUci(uci)) }
        }
        return (own + COMMANDS + if (common) PHRASES else emptyList()).distinct()
    }

    /** [move] in [position] as a phrase of the training: "knight f3", "e takes d5". */
    fun phraseOf(position: Position, move: Move): String = phrase(position.san(move).trimEnd('+', '#'))

    /** What the app says for [phrase]: "knight f 3", so that text-to-speech says "f three". */
    fun speech(phrase: String): String = phrase.replace(Regex("([a-h])([1-8])"), "$1 $2")

    /**
     * A recognition counts if it reads as the same thing as what was meant. A pawn need not be
     * named, "takes" need not be heard, and plain "castles" is kingside, as when playing.
     */
    fun isRight(said: String, transcript: String): Boolean = loose(VoiceMove.read(transcript)) == loose(VoiceMove.read(said))

    private fun loose(h: VoiceMove.Heard) = h.copy(piece = h.piece ?: 'P', takes = false, castle = if (h.castle == '?') 'K' else h.castle)

    /**
     * What the recordings taught: each transcript that voxtype got wrong, normalized, → what was
     * said. A transcript heard for two different things teaches nothing.
     */
    fun learned(results: List<Pair<String, String>>): Map<String, String> {
        val wrong = results.filter { (said, heard) -> !isRight(said, heard) }
        return wrong.groupBy({ VoiceMove.normalize(it.second) }, { it.first })
            .filter { (key, said) -> key.isNotBlank() && said.distinct().size == 1 }
            .mapValues { it.value.first() }
    }

    /** The table that the speech test writes: number, said, heard, ok, understood. */
    const val TABLE_HEADER = "Nr\tgesagt\tvoxtype\tok\tverstanden"

    /** (said, heard) of each line of a table written before. */
    fun readTable(text: String): List<Pair<String, String>> =
        text.lines().drop(1).mapNotNull { line -> line.split('\t').takeIf { it.size >= 3 }?.let { it[1] to it[2] } }
}

/** What to read out for the voice sample that Qwen3-TTS copies the voice from: chess words, about 15 seconds. */
const val VOICE_SAMPLE_TEXT =
    "Let's play the Sicilian. E4, c5, knight f3, d6. Then d4, c takes d4, knight takes d4, knight f6 and knight c3. " +
        "Bishop to c4, queen to d1, rook to e1, and castles kingside. Your move."
