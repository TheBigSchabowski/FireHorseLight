package repertoire.core

/**
 * What a pasted text turned out to be: a line of moves, possibly starting from a set-up position.
 * A bare FEN gives no [moves] at all — the continuation is then played in the editor.
 */
data class PastedLine(
    /** Position the line starts from; [Position.START_FEN] unless the text brought its own FEN. */
    val startFen: String,
    /** The main line's moves in UCI, counted from [startFen]. */
    val moves: List<String>,
    val opening: String,
    val name: String,
    val playWhite: Boolean,
    /** Games in the text after the first one; those are not taken over. */
    val otherGames: Int = 0,
    /** Only from [Pgn.parseAll]: the comments without drawings, as theory. */
    val theory: String = "",
    /** Only from [Pgn.parseAll]: the drawings in the comments, at their moves. */
    val drawings: List<Drawing> = emptyList(),
) {
    val setUp: Boolean get() = startFen != Position.START_FEN
}

/**
 * Reads what people copy out of lichess, chess.com, a database or a book: a whole PGN with headers,
 * comments, side lines and a result, a bare move list ("1.e4 c5 2.Nf3"), a FEN, or a FEN followed by
 * moves. Only the main line is kept; side lines in brackets and comments are thrown away.
 */
object Pgn {
    private val HEADER = Regex("""\[\s*([A-Za-z0-9_]+)\s*"([^"]*)"\s*\]""")

    /** A "{ … }" comment. PGN does not nest them. Android's regex engine needs the closing brace escaped. */
    private val BRACE_COMMENT = Regex("""\{[^\}]*\}""")

    /** ";" to the end of the line, and a "%" escape taking up a whole line. */
    private val REST_OF_LINE = Regex("""(?m)(;|^[ \t]*%).*$""")

    /** A side line without another one inside it, so repeated replacing peels off nested brackets. */
    private val INNERMOST_VARIATION = Regex("""\([^()]*\)""")

    private val NAG = Regex("""\$\d+""")

    /** Each game of a multi-game PGN starts with its own [Event] tag. */
    private val GAME_START = Regex("""(?m)^[ \t]*\[Event[ \t]""")

    /** A "FEN:" label in front of a pasted position. */
    private val FEN_LABEL = Regex("""^(?i)fen\s*[:=]?\s*""")

    /** Event names that say nothing about the opening. */
    private val USELESS_EVENT = Regex("""(?i)^(live chess|(rated|casual|unrated).*|\?+|-+|)$""")

    private val FEN_FIELDS = listOf(
        Regex("^[wb]$"),
        Regex("^(-|[KQkq]{1,4})$"),
        Regex("^(-|[a-h][1-8])$"),
        Regex("^\\d+$"),
        Regex("^\\d+$"),
    )
    /**
     * What to assume for the fields a shortened FEN leaves out. Castling stays on because a pasted
     * position is usually an opening one; rights that do not fit the board are ignored anyway, since
     * castling is only generated with king and rook at home.
     */
    private val FEN_DEFAULTS = listOf("w", "KQkq", "-", "0", "1")

    /**
     * The line as PGN text, for the clipboard: the moves up to some position, numbered, and a
     * FEN header when it does not begin at the start of a game. Reads back with [parse].
     */
    fun export(startFen: String, numberedMoves: List<String>): String {
        val moves = compactMoves(numberedMoves)
        val header = if (startFen == Position.START_FEN) "" else "[SetUp \"1\"]\n[FEN \"$startFen\"]\n\n"
        return header + moves
    }

    /** lichess's four arrow colors, which are also the first four pens by default (green, red, blue, orange). */
    private val LICHESS_COLORS = listOf('G' to 0x15781B, 'R' to 0x882020, 'B' to 0x003088, 'Y' to 0xE68F00)

    /**
     * The lichess letter of pen [color]: the first four pens are lichess's four, in its order; pens 5
     * and 6 take whichever of them their color [pens] is closest to.
     */
    private fun lichessColor(color: Int, pens: List<PenColor>): Char = LICHESS_COLORS.getOrNull(color)?.first
        ?: LICHESS_COLORS.minBy { (_, rgb) ->
            val pen = pens[color].rgb
            val dr = (rgb shr 16 and 0xFF) - (pen shr 16 and 0xFF)
            val dg = (rgb shr 8 and 0xFF) - (pen shr 8 and 0xFF)
            val db = (rgb and 0xFF) - (pen and 0xFF)
            dr * dr + dg * dg + db * db
        }.first

    /** "[%csl Gd5]", "[%cal Ge2e4]" as lichess writes them, and FireHorse's own "[%fh 5:#d5]". */
    private val COMMAND = Regex("""\[%(\w+)\s+([^\]]*)\]""")

    /** A comment may not close itself early. */
    private fun commentText(text: String) = text.replace('{', '(').replace('}', ')').trim()

    /**
     * A drawing as a comment: its question, then its marks as lichess writes them. What lichess
     * cannot say (pens 5 and 6, filled fields) comes exactly once more in "[%fh …]", which [parseAll]
     * prefers; lichess shows its approximation.
     */
    private fun drawingComment(d: Drawing, pens: List<PenColor>): String {
        fun code(m: Mark) = lichessColor(m.color, pens) + squareName(m.from) + if (m.from == m.to) "" else squareName(m.to)
        val squares = d.marks.filter { it.from == it.to }.joinToString(",", transform = ::code)
        val arrows = d.marks.filter { it.from != it.to }.joinToString(",", transform = ::code)
        val exact = d.marks.any { it.color >= LICHESS_COLORS.size || it.field }
        return listOf(
            commentText(d.question),
            if (squares.isEmpty()) "" else "[%csl $squares]",
            if (arrows.isEmpty()) "" else "[%cal $arrows]",
            if (exact) "[%fh ${d.marks.joinToString(",")}]" else "",
        ).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * All of [variant] as PGN: opening and name as headers, every move, each drawing as a comment
     * after its move (arrows and circles as lichess writes them, so they show up there), and the
     * theory at the end as it is written, gaps included. With [upTo], only the first [upTo] moves and
     * the drawings up to there; the theory is always whole. [pens] are what the pens look like.
     * Reads back completely with [parseAll], and the moves and names with [parse].
     */
    fun exportAll(variant: Variant, pens: List<PenColor> = Ink.DEFAULT, upTo: Int? = null): String {
        val plies = upTo?.coerceIn(0, variant.moves.size) ?: variant.moves.size
        fun header(value: String) = value.replace('"', '\'').trim()
        val title = listOf(variant.opening, variant.name).map(::header).filter { it.isNotEmpty() }
        val headers = buildList {
            add("Event" to title.joinToString(": ").ifEmpty { "?" })
            if (variant.opening.isNotBlank()) add("Opening" to header(variant.opening))
            if (variant.name.isNotBlank()) add("Variation" to header(variant.name))
            add("Result" to "*")
            if (variant.setUp) {
                add("SetUp" to "1")
                add("FEN" to variant.startFen)
            }
        }

        val parts = mutableListOf<String>()
        // After a comment, a black move needs its number again ("3... Nf6").
        var numberNext = true
        fun comment(text: String) {
            if (text.isEmpty()) return
            parts += "{ $text }"
            numberNext = true
        }
        fun drawingsAt(ply: Int) = variant.drawings.filter { it.ply == ply }.forEach { comment(drawingComment(it, pens)) }

        drawingsAt(0)
        variant.numberedSan().take(plies).forEachIndexed { i, numbered ->
            val black = numbered.substringBefore(' ').endsWith("...")
            parts += if (black && !numberNext) numbered.substringAfter(' ') else numbered
            numberNext = false
            drawingsAt(i + 1)
        }
        comment(commentText(variant.theory))
        parts += "*"
        return headers.joinToString("\n") { (k, v) -> "[$k \"$v\"]" } + "\n\n" + parts.joinToString(" ")
    }

    /** Stands in for a comment while the moves around it are counted. */
    private val PLACE = Regex("""⟦(\d+)⟧""")

    /** The marks of a comment: FireHorse's exact ones if there, else lichess's (G, R, B, Y as pens 1 to 4). */
    private fun marksOf(comment: String): List<Mark> {
        val commands = COMMAND.findAll(comment).associate { it.groupValues[1] to it.groupValues[2] }
        commands["fh"]?.let { exact -> return exact.split(',', ' ').filter { it.isNotBlank() }.map { Mark.parse(it.trim()) } }
        return listOfNotNull(commands["csl"], commands["cal"]).flatMap { it.split(',') }.map { it.trim() }.mapNotNull { code ->
            val color = LICHESS_COLORS.indexOfFirst { it.first == code.firstOrNull() }
            val squares = code.drop(1)
            if (color < 0 || (squares.length != 2 && squares.length != 4)) return@mapNotNull null
            runCatching {
                val from = parseSquare(squares.take(2))
                Mark(from, if (squares.length == 4) parseSquare(squares.drop(2)) else from, color)
            }.getOrNull()
        }
    }

    /**
     * Like [parse], and the comments too: one with arrows or circles ([%csl], [%cal], FireHorse's
     * [%fh]) becomes a drawing at its move, with the rest of its text as the question; the others
     * become the theory, in their order. Side lines and their comments still fall away.
     */
    fun parseAll(text: String): PastedLine {
        val cleaned = text.replace('\u00A0', ' ').replace('\u202F', ' ').replace("\uFEFF", "")
        require(cleaned.isNotBlank()) { "Da ist kein Text zum Einlesen." }
        val games = split(cleaned)
        val line = parseGame(games.first()).copy(otherGames = games.size - 1)

        val comments = mutableListOf<String>()
        var body = HEADER.replace(games.first(), " ")
        body = BRACE_COMMENT.replace(body) { match ->
            comments += match.value.removePrefix("{").removeSuffix("}")
            " ⟦${comments.lastIndex}⟧ "
        }
        body = REST_OF_LINE.replace(body, " ")
        while (INNERMOST_VARIATION.containsMatchIn(body)) body = INNERMOST_VARIATION.replace(body, " ")
        body = NAG.replace(body, " ")
        val headerFen = HEADER.findAll(games.first()).any { it.groupValues[1] == "FEN" && it.groupValues[2].isNotBlank() }
        val movesText = if (headerFen) body else splitOffFen(body)?.second ?: body
        val start = Position.fromFen(line.startFen)

        val theory = mutableListOf<String>()
        val drawings = mutableListOf<Drawing>()
        for (place in PLACE.findAll(movesText)) {
            val comment = comments[place.groupValues[1].toInt()]
            val words = COMMAND.replace(comment, " ").replace(Regex("\\s+"), " ").trim()
            val marks = marksOf(comment)
            if (marks.isEmpty()) {
                // As written, lines and all; only lichess's clock and evaluation go.
                if (words.isNotEmpty()) theory += COMMAND.replace(comment, "").trim()
                continue
            }
            val before = PLACE.replace(movesText.substring(0, place.range.first), " ")
            val ply = Exchange.parseMoves(before, start).size
            drawings += Drawing(ply, marks.distinct(), words)
        }
        return line.copy(theory = theory.joinToString("\n\n"), drawings = drawings)
    }

    /** Reads [text]; throws [IllegalArgumentException] with a German explanation if it does not work out. */
    fun parse(text: String): PastedLine {
        // Web pages hand out non-breaking spaces, and some editors a byte-order mark.
        val cleaned = text.replace('\u00A0', ' ').replace('\u202F', ' ').replace("\uFEFF", "")
        require(cleaned.isNotBlank()) { "Da ist kein Text zum Einlesen." }
        val games = split(cleaned)
        return parseGame(games.first()).copy(otherGames = games.size - 1)
    }

    private fun split(text: String): List<String> {
        val starts = GAME_START.findAll(text).map { it.range.first }.toList()
        if (starts.size <= 1) return listOf(text)
        return starts.mapIndexed { i, from -> text.substring(from, starts.getOrNull(i + 1) ?: text.length) }
    }

    private fun parseGame(text: String): PastedLine {
        val headers = HEADER.findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }
        var body = HEADER.replace(text, " ")
        body = REST_OF_LINE.replace(body, " ")
        body = BRACE_COMMENT.replace(body, " ")
        while (INNERMOST_VARIATION.containsMatchIn(body)) body = INNERMOST_VARIATION.replace(body, " ")
        body = NAG.replace(body, " ")

        val header = headers["FEN"]?.takeIf { it.isNotBlank() }
        val (startFen, movesText) = when {
            header != null -> normalizeFen(header) to body
            else -> splitOffFen(body) ?: (Position.START_FEN to body)
        }
        val start = try {
            Position.fromFen(startFen)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(e.message ?: "Ungültige FEN: $startFen")
        }
        start.problem()?.let { throw IllegalArgumentException(it) }

        val moves = Exchange.parseMoves(movesText, start)
        require(moves.isNotEmpty() || start.fen != Position.START_FEN) {
            "Darin sind weder Züge noch eine Stellung zu finden. Erwartet wird eine PGN " +
                "(„1. e4 c5 2. Nf3“) oder eine FEN („rnbqkbnr/pp1ppppp/… w KQkq - 0 2“)."
        }
        val (opening, name) = namesFrom(headers)
        return PastedLine(
            startFen = start.fen,
            moves = moves,
            opening = opening,
            name = name,
            // Whoever played the last move is the side you want to be: a Najdorf line ends on Black's a6.
            playWhite = if (moves.isEmpty()) start.whiteToMove else start.whiteToMove == (moves.size % 2 == 1),
        )
    }

    /**
     * A FEN at the start of [body], with the fields it leaves out filled in, plus whatever follows it.
     * Null if it does not start with a position.
     */
    private fun splitOffFen(body: String): Pair<String, String>? {
        val tokens = FEN_LABEL.replace(body.trim(), "").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val placement = tokens.firstOrNull() ?: return null
        // The one part every FEN has and no move ever looks like: eight ranks separated by slashes.
        if (placement.count { it == '/' } != 7) return null
        val fields = mutableListOf(placement)
        for (field in FEN_FIELDS) {
            if (!field.matches(tokens.getOrNull(fields.size) ?: break)) break
            fields += tokens[fields.size]
        }
        val used = fields.size
        while (fields.size < 6) fields += FEN_DEFAULTS[fields.size - 1]
        return fields.joinToString(" ") to tokens.drop(used).joinToString(" ")
    }

    /** Fills in the fields a FEN from a PGN header may leave out. */
    private fun normalizeFen(fen: String): String {
        val fields = fen.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        while (fields.size in 1..5) fields += FEN_DEFAULTS[fields.size - 1]
        return fields.joinToString(" ")
    }

    /** "Sicilian Defense: Najdorf" → „Sicilian Defense“ and „Najdorf“; whatever the headers give away. */
    private fun namesFrom(headers: Map<String, String>): Pair<String, String> {
        val variation = headers["Variation"]?.takeUnless { it.isBlank() || it == "?" }
        val title = headers["Opening"]?.takeUnless { it.isBlank() || it == "?" }
            ?: headers["Event"]?.takeUnless { USELESS_EVENT.matches(it) }
            ?: return "" to (variation ?: "")
        if (variation != null) return title to variation
        val cut = title.indexOf(": ")
        return if (cut > 0) title.take(cut).trim() to title.substring(cut + 2).trim() else title to ""
    }
}
