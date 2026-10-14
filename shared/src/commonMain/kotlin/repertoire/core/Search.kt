package repertoire.core

/**
 * The search on the home screen: words that must all fit a variant.
 *
 * - Moves are read from the start of the line: "d4" finds 1.d4, "e4 c5" finds 1.e4 c5, and a
 *   move for Black on its own its first answer ("c5": 1...c5). With a number, a move counts at
 *   that place: "6.f3", "3...e6", "3. d4". German piece letters work too ("Sf3" is Nf3), and
 *   captures, checks and "=" may be left out.
 * - "weiß"/"weiss"/"white" and "schwarz"/"black": the side the variant is trained for.
 * - Anything else is looked for in the name of the opening and of the variant.
 */
class VariantSearch(query: String) {
    private sealed interface Term
    private class Side(val white: Boolean) : Term
    private class Words(val text: String) : Term

    /** Moves in a row, at [at] (number and side) or, without, from White's or Black's first move. */
    private class Moves(val at: Pair<Int, Boolean>?, val sans: List<String>) : Term

    private val terms: List<Term> = parse(query)

    /** There is something to search for. */
    val active: Boolean get() = terms.isNotEmpty()

    fun matches(v: Variant): Boolean = terms.all { term ->
        when (term) {
            is Side -> v.playWhite == term.white
            is Words -> (v.opening + " " + v.name).lowercase().contains(term.text)
            is Moves -> movesMatch(v, term)
        }
    }

    private fun movesMatch(v: Variant, term: Moves): Boolean {
        val sans = v.positions.zip(v.moves).map { (p, uci) -> normalize(p.san(Move.fromUci(uci))) }
        val starts = term.at?.let { (number, white) ->
            val start = v.start
            listOf((number - start.fullmoveNumber) * 2 + (if (white) 0 else 1) - (if (start.whiteToMove) 0 else 1))
        } ?: listOf(0, 1)
        return starts.any { i -> i >= 0 && term.sans.indices.all { k -> sans.getOrNull(i + k) == term.sans[k] } }
    }

    companion object {
        private val WHITE = setOf("weiß", "weiss", "white")
        private val BLACK = setOf("schwarz", "black")
        private val NUMBER = Regex("^(\\d+)(\\.\\.\\.|…|\\.)(.*)$")
        private val SAN = Regex("^([KQRBN]?[a-h]?[1-8]?[a-h][1-8][QRBN]?|O-O|O-O-O)$")

        /** "Sxf3+", "nf3", "exd5", "e8=D", "0-0" → "Nf3", "Nf3", "ed5", "e8Q", "O-O": one way of writing a move. */
        fun normalize(san: String): String? {
            var s = san.trim().trimEnd('+', '#', '!', '?').replace("x", "").replace(":", "").replace("=", "")
            s = s.replace("0", "O")
            if (s.isEmpty()) return null
            // Lowercase only where it cannot be a file: "nf3", "sf3"; "bc4" and "dc4" stay pawn moves.
            val first = when (val c = s[0]) {
                'S', 's', 'n' -> 'N'
                'L', 'l' -> 'B'
                'T', 't', 'r' -> 'R'
                'D', 'q' -> 'Q'
                'k' -> 'K'
                else -> c
            }
            s = first + s.substring(1)
            // A promotion to a German piece: "e8D".
            if (s.length >= 3 && s.last() in "DTLS" && s[s.length - 2] in '1'..'8') {
                s = s.dropLast(1) + mapOf('D' to 'Q', 'T' to 'R', 'L' to 'B', 'S' to 'N').getValue(s.last())
            }
            return s.takeIf { SAN.matches(it) }
        }

        private fun parse(query: String): List<Term> {
            val terms = mutableListOf<Term>()
            var run: MutableList<String>? = null
            var runAt: Pair<Int, Boolean>? = null
            // "3." or "3..." on its own: the number for the move after it.
            var pending: Pair<Int, Boolean>? = null
            fun endRun() {
                run?.let { terms += Moves(runAt, it) }
                run = null
                runAt = null
            }
            for (token in query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val lower = token.lowercase()
                when {
                    lower in WHITE -> { endRun(); terms += Side(true) }
                    lower in BLACK -> { endRun(); terms += Side(false) }
                    else -> {
                        val numbered = NUMBER.find(token)
                        val at = numbered?.let { it.groupValues[1].toInt() to (it.groupValues[2] == ".") }
                        val rest = numbered?.groupValues?.get(3) ?: token
                        if (at != null && rest.isEmpty()) {
                            endRun()
                            pending = at
                            continue
                        }
                        val san = normalize(rest)
                        if (san == null) {
                            endRun()
                            pending = null
                            terms += Words(lower)
                            continue
                        }
                        val anchor = at ?: pending
                        pending = null
                        // A number starts a new row of moves; without one, the move goes on the row there is.
                        if (anchor != null || run == null) {
                            endRun()
                            run = mutableListOf()
                            runAt = anchor
                        }
                        run!! += san
                    }
                }
            }
            endRun()
            return terms
        }
    }
}
