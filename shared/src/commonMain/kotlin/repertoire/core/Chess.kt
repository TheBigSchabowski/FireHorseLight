package repertoire.core

/** A move from one square to another. Squares are 0..63 with a1 = 0, h8 = 63. */
data class Move(val from: Int, val to: Int, val promotion: Char? = null) {
    /** Long algebraic form, e.g. "e2e4" or "e7e8q". */
    val uci: String get() = squareName(from) + squareName(to) + (promotion?.lowercaseChar() ?: "")

    companion object {
        fun fromUci(uci: String): Move =
            Move(parseSquare(uci.substring(0, 2)), parseSquare(uci.substring(2, 4)), uci.getOrNull(4))
    }
}

fun squareName(sq: Int): String = "${'a' + sq % 8}${sq / 8 + 1}"

fun parseSquare(name: String): Int {
    require(name.length == 2 && name[0] in 'a'..'h' && name[1] in '1'..'8') { "Ungültiges Feld: $name" }
    return (name[1] - '1') * 8 + (name[0] - 'a')
}

private fun squareAt(file: Int, rank: Int) = if (file in 0..7 && rank in 0..7) rank * 8 + file else -1

private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
private val KING_STEPS = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

private const val EMPTY = '.'

/**
 * Immutable chess position. Pieces use FEN letters: uppercase = white, lowercase = black.
 * Only what an opening trainer needs: legal move generation, SAN in both directions and FEN.
 */
class Position private constructor(
    private val board: CharArray,
    val whiteToMove: Boolean,
    /** Subset of "KQkq". */
    val castling: String,
    /** Square a pawn can be captured on en passant, or -1. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(sq: Int): Char? = board[sq].takeIf { it != EMPTY }

    val fen: String
        get() = "$placement ${if (whiteToMove) "w" else "b"} ${castling.ifEmpty { "-" }} " +
            "${if (epSquare >= 0) squareName(epSquare) else "-"} $halfmoveClock $fullmoveNumber"

    /**
     * Identity of the position for repetition purposes: move counters are dropped and the
     * en-passant square only counts when a capture there is actually possible, so
     * transpositions map to the same key.
     */
    val key: String by lazy {
        val ep = if (epSquare >= 0 && legalMoves().any { it.to == epSquare && board[it.from].uppercaseChar() == 'P' }) {
            squareName(epSquare)
        } else {
            "-"
        }
        "$placement ${if (whiteToMove) "w" else "b"} ${castling.ifEmpty { "-" }} $ep"
    }

    private val placement: String
        get() = buildString {
            for (rank in 7 downTo 0) {
                var empty = 0
                for (file in 0..7) {
                    val c = board[rank * 8 + file]
                    if (c == EMPTY) {
                        empty++
                    } else {
                        if (empty > 0) append(empty).also { empty = 0 }
                        append(c)
                    }
                }
                if (empty > 0) append(empty)
                if (rank > 0) append('/')
            }
        }

    private fun isOwn(c: Char, white: Boolean) = c != EMPTY && c.isUpperCase() == white

    private fun kingSquare(white: Boolean): Int = board.indexOf(if (white) 'K' else 'k')

    fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val file = sq % 8
        val rank = sq / 8
        fun has(f: Int, r: Int, piece: Char): Boolean {
            val s = squareAt(f, r)
            return s >= 0 && board[s] == (if (byWhite) piece.uppercaseChar() else piece.lowercaseChar())
        }

        val pawnRank = if (byWhite) rank - 1 else rank + 1
        if (has(file - 1, pawnRank, 'p') || has(file + 1, pawnRank, 'p')) return true
        if (KNIGHT_STEPS.any { (df, dr) -> has(file + df, rank + dr, 'n') }) return true
        if (KING_STEPS.any { (df, dr) -> has(file + df, rank + dr, 'k') }) return true
        return ROOK_DIRS.any { (df, dr) -> firstPieceOnRay(file, rank, df, dr).let { it.isUpperCase() == byWhite && it.lowercaseChar() in "rq" } } ||
            BISHOP_DIRS.any { (df, dr) -> firstPieceOnRay(file, rank, df, dr).let { it.isUpperCase() == byWhite && it.lowercaseChar() in "bq" } }
    }

    /** The first piece seen from (file, rank) in direction (df, dr), or EMPTY if the ray leaves the board. */
    private fun firstPieceOnRay(file: Int, rank: Int, df: Int, dr: Int): Char {
        var s = squareAt(file + df, rank + dr)
        var f = file + df
        var r = rank + dr
        while (s >= 0) {
            if (board[s] != EMPTY) return board[s]
            f += df
            r += dr
            s = squareAt(f, r)
        }
        return EMPTY
    }

    fun isCheck(): Boolean = isAttacked(kingSquare(whiteToMove), !whiteToMove)

    /**
     * Why this position cannot occur in a game, in German, or null if it is sound. Only needed for
     * positions that come from outside, e.g. a pasted FEN: move generation assumes both kings exist.
     */
    fun problem(): String? = when {
        board.count { it == 'K' } != 1 || board.count { it == 'k' } != 1 ->
            "Die Stellung braucht genau einen weißen und einen schwarzen König."
        (0..7).any { board[it].lowercaseChar() == 'p' } || (56..63).any { board[it].lowercaseChar() == 'p' } ->
            "Auf der ersten und der achten Reihe kann kein Bauer stehen."
        isAttacked(kingSquare(!whiteToMove), whiteToMove) ->
            "Da steht der König der Seite im Schach, die gerade gezogen hat – so eine Stellung gibt es nicht."
        else -> null
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = mutableListOf<Move>()
        val white = whiteToMove
        for (from in 0..63) {
            val piece = board[from]
            if (!isOwn(piece, white)) continue
            val file = from % 8
            val rank = from / 8
            fun step(steps: List<Pair<Int, Int>>) {
                for ((df, dr) in steps) {
                    val to = squareAt(file + df, rank + dr)
                    if (to >= 0 && !isOwn(board[to], white)) moves += Move(from, to)
                }
            }
            fun slide(dirs: List<Pair<Int, Int>>) {
                for ((df, dr) in dirs) {
                    var f = file + df
                    var r = rank + dr
                    while (true) {
                        val to = squareAt(f, r)
                        if (to < 0 || isOwn(board[to], white)) break
                        moves += Move(from, to)
                        if (board[to] != EMPTY) break
                        f += df
                        r += dr
                    }
                }
            }
            when (piece.lowercaseChar()) {
                'p' -> {
                    val dir = if (white) 1 else -1
                    val lastRank = if (white) 7 else 0
                    fun add(to: Int) {
                        if (to / 8 == lastRank) {
                            for (promo in "qrbn") moves += Move(from, to, promo)
                        } else {
                            moves += Move(from, to)
                        }
                    }
                    val one = squareAt(file, rank + dir)
                    if (one >= 0 && board[one] == EMPTY) {
                        add(one)
                        val startRank = if (white) 1 else 6
                        val two = squareAt(file, rank + 2 * dir)
                        if (rank == startRank && board[two] == EMPTY) moves += Move(from, two)
                    }
                    for (df in listOf(-1, 1)) {
                        val to = squareAt(file + df, rank + dir)
                        if (to < 0) continue
                        val target = board[to]
                        if ((target != EMPTY && !isOwn(target, white)) || to == epSquare) add(to)
                    }
                }
                'n' -> step(KNIGHT_STEPS)
                'b' -> slide(BISHOP_DIRS)
                'r' -> slide(ROOK_DIRS)
                'q' -> slide(ROOK_DIRS + BISHOP_DIRS)
                'k' -> {
                    step(KING_STEPS)
                    val home = if (white) 4 else 60
                    val (kingSide, queenSide) = if (white) 'K' to 'Q' else 'k' to 'q'
                    val rook = if (white) 'R' else 'r'
                    if (from == home && !isAttacked(home, !white)) {
                        if (kingSide in castling && board[home + 3] == rook &&
                            board[home + 1] == EMPTY && board[home + 2] == EMPTY &&
                            !isAttacked(home + 1, !white) && !isAttacked(home + 2, !white)
                        ) {
                            moves += Move(home, home + 2)
                        }
                        if (queenSide in castling && board[home - 4] == rook &&
                            board[home - 1] == EMPTY && board[home - 2] == EMPTY && board[home - 3] == EMPTY &&
                            !isAttacked(home - 1, !white) && !isAttacked(home - 2, !white)
                        ) {
                            moves += Move(home, home - 2)
                        }
                    }
                }
            }
        }
        return moves
    }

    private val cachedLegalMoves: List<Move> by lazy {
        pseudoLegalMoves().filter { move ->
            val after = playUnchecked(move)
            !after.isAttacked(after.kingSquare(whiteToMove), !whiteToMove)
        }
    }

    fun legalMoves(): List<Move> = cachedLegalMoves

    fun isLegal(move: Move): Boolean = move in cachedLegalMoves

    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegaler Zug ${move.uci} in $fen" }
        return playUnchecked(move)
    }

    private fun playUnchecked(move: Move): Position {
        val b = board.copyOf()
        val piece = b[move.from]
        val captured = b[move.to]
        val isPawn = piece.lowercaseChar() == 'p'
        val isKing = piece.lowercaseChar() == 'k'

        if (isPawn && move.to == epSquare && captured == EMPTY && move.from % 8 != move.to % 8) {
            b[(move.from / 8) * 8 + move.to % 8] = EMPTY
        }
        if (isKing && kotlin.math.abs(move.to - move.from) == 2) {
            val (rookFrom, rookTo) = if (move.to > move.from) move.from + 3 to move.from + 1 else move.from - 4 to move.from - 1
            b[rookTo] = b[rookFrom]
            b[rookFrom] = EMPTY
        }
        b[move.to] = if (move.promotion != null) {
            if (whiteToMove) move.promotion.uppercaseChar() else move.promotion.lowercaseChar()
        } else {
            piece
        }
        b[move.from] = EMPTY

        var rights = castling
        if (isKing) rights = rights.filterNot { if (whiteToMove) it.isUpperCase() else it.isLowerCase() }
        for (sq in listOf(move.from, move.to)) {
            when (sq) {
                0 -> rights = rights.replace("Q", "")
                7 -> rights = rights.replace("K", "")
                56 -> rights = rights.replace("q", "")
                63 -> rights = rights.replace("k", "")
            }
        }

        val newEp = if (isPawn && kotlin.math.abs(move.to - move.from) == 16) (move.from + move.to) / 2 else -1
        return Position(
            board = b,
            whiteToMove = !whiteToMove,
            castling = rights,
            epSquare = newEp,
            halfmoveClock = if (isPawn || captured != EMPTY) 0 else halfmoveClock + 1,
            fullmoveNumber = if (whiteToMove) fullmoveNumber else fullmoveNumber + 1,
        )
    }

    /** Standard algebraic notation including check/mate suffix, e.g. "Nbd7", "exd5", "O-O", "Qxf7#". */
    fun san(move: Move): String {
        val base = sanWithoutSuffix(move)
        val after = play(move)
        return when {
            !after.isCheck() -> base
            after.legalMoves().isEmpty() -> "$base#"
            else -> "$base+"
        }
    }

    private fun sanWithoutSuffix(move: Move): String {
        val piece = board[move.from].uppercaseChar()
        if (piece == 'K' && kotlin.math.abs(move.to - move.from) == 2) {
            return if (move.to > move.from) "O-O" else "O-O-O"
        }
        val isCapture = board[move.to] != EMPTY || (piece == 'P' && move.from % 8 != move.to % 8)
        return buildString {
            if (piece == 'P') {
                if (isCapture) append('a' + move.from % 8).append('x')
                append(squareName(move.to))
                if (move.promotion != null) append('=').append(move.promotion.uppercaseChar())
            } else {
                append(piece)
                val rivals = legalMoves().filter {
                    it.to == move.to && it.from != move.from && board[it.from].uppercaseChar() == piece
                }
                if (rivals.isNotEmpty()) {
                    when {
                        rivals.none { it.from % 8 == move.from % 8 } -> append('a' + move.from % 8)
                        rivals.none { it.from / 8 == move.from / 8 } -> append('1' + move.from / 8)
                        else -> append(squareName(move.from))
                    }
                }
                if (isCapture) append('x')
                append(squareName(move.to))
            }
        }
    }

    /** Parses SAN leniently (tolerates missing/extra "x", 0-0, over-disambiguation). Returns null if no legal match. */
    fun parseSan(text: String): Move? {
        val clean = text.trim()
            .replace("0-0-0", "O-O-O").replace("0-0", "O-O")
            .trimEnd('+', '#', '!', '?')
        legalMoves().firstOrNull { sanWithoutSuffix(it) == clean }?.let { return it }

        if (clean == "O-O" || clean == "O-O-O") {
            return legalMoves().firstOrNull {
                board[it.from].uppercaseChar() == 'K' && it.to - it.from == (if (clean == "O-O") 2 else -2)
            }
        }
        val match = SAN_REGEX.matchEntire(clean) ?: return null
        val (pieceLetter, fromFile, fromRank, target, promoLetter) = match.destructured
        val piece = pieceLetter.ifEmpty { "P" }[0]
        val to = parseSquare(target)
        val promo = promoLetter.lastOrNull()?.lowercaseChar()
        val candidates = legalMoves().filter {
            board[it.from].uppercaseChar() == piece && it.to == to &&
                (fromFile.isEmpty() || it.from % 8 == fromFile[0] - 'a') &&
                (fromRank.isEmpty() || it.from / 8 == fromRank[0] - '1') &&
                (promo == null || it.promotion == promo)
        }
        return when {
            candidates.size == 1 -> candidates[0]
            candidates.isNotEmpty() && candidates.all { it.promotion != null } -> candidates.first { it.promotion == 'q' }
            else -> null
        }
    }

    override fun equals(other: Any?): Boolean = other is Position && other.fen == fen

    override fun hashCode(): Int = fen.hashCode()

    override fun toString(): String = fen

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val SAN_REGEX = Regex("^([NBRQK])?([a-h])?([1-8])?[x:-]?([a-h][1-8])(=?[NBRQnbrq])?$")

        val START: Position by lazy { fromFen(START_FEN) }

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Ungültige FEN: $fen" }
            val board = CharArray(64) { EMPTY }
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "Ungültige FEN: $fen" }
            for ((i, row) in ranks.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        file += c - '0'
                    } else {
                        require(c.lowercaseChar() in "pnbrqk" && file < 8) { "Ungültige FEN: $fen" }
                        board[rank * 8 + file] = c
                        file++
                    }
                }
                require(file == 8) { "Ungültige FEN: $fen" }
            }
            return Position(
                board = board,
                whiteToMove = parts[1] == "w",
                castling = parts[2].takeIf { it != "-" }?.filter { it in "KQkq" } ?: "",
                epSquare = parts[3].takeIf { it != "-" }?.let(::parseSquare) ?: -1,
                halfmoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1,
            )
        }
    }
}
