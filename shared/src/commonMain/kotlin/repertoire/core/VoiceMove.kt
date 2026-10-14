package repertoire.core

/**
 * Englischer Sprach-zu-Schachzug-Parser für Voxtype / Whisper und Sprechtext-Generator für Qwen-TTS.
 *
 * Unterstützt natürliche englische Ansagen wie:
 * - "bishop c4", "knight c3", "queen d4", "rook e1", "king f1"
 * - Typische Whisper-Homophone: "night c3", "knight see three", "bishop sea for", "rook to a ate"
 * - Schlagzüge: "c takes d4", "knight takes d4", "bishop captures f7"
 * - Rochaden: "castles", "castle kingside", "short castle", "castle queenside", "long castle"
 */
object VoiceMove {

    /**
     * Geordnete Ersetzungsliste für englische Spracheingaben aus Voxtype / Whisper.
     * Da "knight" und "night" im Englischen identisch klingen, werden beide sicher auf "N" gemappt.
     */
    private val ENGLISH_PHONETIC_RULES = listOf(
        // 1. Rochaden zuerst abfangen (lange Rochade vor kurzer!)
        "castle queenside" to "O-O-O",
        "castles queenside" to "O-O-O",
        "queen side castle" to "O-O-O",
        "queenside castle" to "O-O-O",
        "long castle" to "O-O-O",
        "long castling" to "O-O-O",
        "triple oh" to "O-O-O",
        "zero zero zero" to "O-O-O",

        "castle kingside" to "O-O",
        "castles kingside" to "O-O",
        "king side castle" to "O-O",
        "kingside castle" to "O-O",
        "short castle" to "O-O",
        "short castling" to "O-O",
        "castles" to "O-O",
        "castle" to "O-O",
        "double oh" to "O-O",
        "zero zero" to "O-O",

        // 2. Englische Figurennamen + häufige Whisper-Hörfehler ("night" statt "knight")
        "knight" to "N",
        "night" to "N",
        "horse" to "N",
        "bishop" to "B",
        "rook" to "R",
        "castle" to "R",
        "queen" to "Q",
        "king" to "K",
        "pawn" to "",

        // 3. Englische Verbindungs- und Schlagwörter
        "captures" to "x",
        "takes" to "x",
        "take" to "x",
        "by" to "x",
        "goes to" to "",
        "moves to" to "",
        "to" to "",
        "on" to "",
        "at" to "",
        "checkmate" to "#",
        "mate" to "#",
        "check" to "+",
        "promotes to" to "=",
        "equals" to "=",

        // 4. Englische Buchstaben-Homophone (wenn Whisper Buchstaben als Wörter ausschreibt)
        "alpha" to "a",
        "ay" to "a",
        "bravo" to "b",
        "bee" to "b",
        "be" to "b",
        "charlie" to "c",
        "see" to "c",
        "sea" to "c",
        "delta" to "d",
        "dee" to "d",
        "echo" to "e",
        "ee" to "e",
        "foxtrot" to "f",
        "ef" to "f",
        "golf" to "g",
        "gee" to "g",
        "hotel" to "h",
        "aitch" to "h",

        // 5. Englische Zahlen + Homophone ("for" -> 4, "ate" -> 8, "won" -> 1)
        "one" to "1",
        "won" to "1",
        "two" to "2",
        "too" to "2",
        "three" to "3",
        "tree" to "3",
        "four" to "4",
        "for" to "4",
        "fore" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "ate" to "8",
    )

    /**
     * Parst eine englische Spracheingabe (z. B. "bishop c4", "knight c3", "e4", "c takes d4")
     * im Kontext der aktuellen [position].
     * Gibt `null` zurück, wenn der Zug in dieser Stellung nicht legal ist.
     */
    fun parseSpokenEnglish(transcript: String, position: Position): Move? {
        val raw = transcript.trim().trimEnd('.', ',', '!', '?')
        if (raw.isEmpty()) return null

        // 1. Direkter Schnelltest: Falls Voxtype bereits "Bc4", "Nc3" oder "e4" geliefert hat
        position.parseSan(raw.replace(" ", ""))?.let { return it }

        // 2. Englische Phonetik-Regeln anwenden (Wortgrenzen beachten)
        var text = raw.lowercase()
        for ((spoken, replacement) in ENGLISH_PHONETIC_RULES) {
            text = text.replace(Regex("\\b$spoken\\b"), replacement)
        }

        // Sonderfall Rochade direkt prüfen
        if ("O-O-O" in text) return position.parseSan("O-O-O")
        if ("O-O" in text) return position.parseSan("O-O")

        // Alle Leerzeichen und Bindestriche entfernen (z. B. "N c 3" -> "Nc3", "b c 4" -> "bc4")
        val compact = text.replace(Regex("[\\s\\-_]+"), "")
        if (compact.isEmpty()) return null

        // Figuren-Großbuchstaben am Anfang wiederherstellen ("nc3" -> "Nc3", "bc4" -> "Bc4", aber "b4" bleibt Bauer!)
        val candidateSan = restoreEnglishPieceCase(compact)
        position.parseSan(candidateSan)?.let { return it }

        // 3. Wenn "bc4" sowohl "Bc4" (Läufer) als auch "bxc4" (b-Bauer schlägt c4) sein könnte:
        // Falls der erste Buchstabe 'B' war und parseSan("Bc4") fehlschlug, testen wir den b-Bauern ("bc4"):
        if (candidateSan.startsWith("B")) {
            position.parseSan("b" + candidateSan.drop(1))?.let { return it }
        }

        // 4. Kontext-Fallback über alle legalen Züge der Stellung:
        // Sucht das genannte Zielfeld ("a1".."h8") am Ende und gleicht den Figurentyp ab.
        return matchLegalByContext(candidateSan, position)
    }

    /**
     * Macht aus "nc3" -> "Nc3" und aus "bc4" -> "Bc4", lässt aber reine Bauernzüge wie "b4" oder "e4" klein.
     */
    private fun restoreEnglishPieceCase(compact: String): String {
        val first = compact.first()
        val second = compact.getOrNull(1)
        // Wenn es mit n, r, q, k anfängt, ist es immer eine Figur (keine Linie heißt n, r, q, k)
        if (first in "nrqkNRQK") {
            return first.uppercaseChar() + compact.drop(1)
        }
        // 'b' ist knifflig: "b4" ist ein Bauernzug, "bc4" (ohne 'x' gesprochen: "bishop c4") ist ein Läufer!
        if (first == 'b' || first == 'B') {
            val isSinglePawnPush = second != null && second in '1'..'8' && compact.length == 2
            return if (isSinglePawnPush) {
                "b$second"
            } else {
                "B" + compact.drop(1)
            }
        }
        return compact
    }

    /**
     * Falls der Nutzer z. B. "knight to f3" mit unklarer Disambiguierung sagt, schauen wir in
     * `position.legalMoves()` nach allen Zügen, die auf dem genannten Zielfeld enden.
     */
    private fun matchLegalByContext(candidate: String, position: Position): Move? {
        val sqMatch = Regex("([a-h][1-8])([qrbnQRBN])?$").find(candidate) ?: return null
        val toSquare = parseSquare(sqMatch.groupValues[1])
        val promo = sqMatch.groupValues[2].firstOrNull()?.lowercaseChar()

        val legalToTarget = position.legalMoves().filter {
            it.to == toSquare && (promo == null || it.promotion == promo || it.promotion == 'q')
        }
        if (legalToTarget.isEmpty()) return null
        if (legalToTarget.size == 1) return legalToTarget.single()

        // Nach Figurentyp filtern
        val pieceChar = candidate.first().let { if (it in "NBRQK") it else 'P' }
        val byPiece = legalToTarget.filter { position.pieceAt(it.from)?.uppercaseChar() == pieceChar }
        if (byPiece.size == 1) return byPiece.single()

        // Nach Startlinie ('a'..'h') filtern, z. B. bei "e takes d5" vs "c takes d5"
        val fileChar = candidate.drop(if (pieceChar == 'P') 0 else 1).firstOrNull { it in 'a'..'h' }
        if (fileChar != null) {
            val byFile = byPiece.filter { (it.from % 8) == (fileChar - 'a') }
            if (byFile.size == 1) return byFile.single()
        }
        return null
    }

    /**
     * Macht aus einem legalen [move] in [position] natürliches Englisch für Qwen-TTS:
     * - `Nf3` -> `"Knight f 3"`
     * - `Bc4` -> `"Bishop c 4"`
     * - `cxd4` -> `"c takes d 4"`
     * - `O-O` -> `"Castles kingside"`
     */
    fun toSpokenEnglish(position: Position, move: Move): String {
        val san = position.san(move)
        if (san.startsWith("O-O-O")) return "Castles queenside"
        if (san.startsWith("O-O")) return "Castles kingside"

        val suffix = when {
            san.endsWith("#") -> ", checkmate"
            san.endsWith("+") -> ", check"
            else -> ""
        }
        val core = san.trimEnd('+', '#')

        val pieceWord = when (core.first()) {
            'N' -> "Knight "
            'B' -> "Bishop "
            'R' -> "Rook "
            'Q' -> "Queen "
            'K' -> "King "
            else -> ""
        }

        val rest = if (pieceWord.isNotEmpty()) core.drop(1) else core
        // Fügt Leerzeichen zwischen Linie und Reihe ein ("c4" -> "c 4"), damit TTS es klar ausspricht
        val formattedRest = rest
            .replace("x", " takes ")
            .replace("=Q", " promotes to Queen")
            .replace("=R", " promotes to Rook")
            .replace("=B", " promotes to Bishop")
            .replace("=N", " promotes to Knight")
            .replace(Regex("([a-h])([1-8])"), "$1 $2")

        return (pieceWord + formattedRest).trim().replace(Regex("\\s+"), " ") + suffix
    }
}
