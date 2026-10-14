package repertoire

import repertoire.core.Drawing
import repertoire.core.Exchange
import repertoire.core.Ink
import repertoire.core.Mark
import repertoire.core.PenColor
import repertoire.core.Pgn
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.Library
import repertoire.core.PastedLine
import repertoire.core.Variant
import repertoire.core.compactMoves
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What lichess puts in the clipboard for a study chapter, warts and all. */
private val LICHESS_STUDY = """
[Event "Sizilianisch: Najdorf"]
[Site "https://lichess.org/study/abcd1234/efgh5678"]
[Result "*"]
[UTCDate "2026.09.20"]
[Variant "Standard"]
[ECO "B90"]
[Opening "Sicilian Defense: Najdorf Variation"]

1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 { Der Najdorf-Zug. } 6. Be3
(6. Bg5 e6 7. f4 Be7 { Die Hauptvariante der 80er. }) 6... e5 $1 7. Nb3 Be6 *
""".trimIndent()

class PgnTest {
    private fun san(line: PastedLine): String =
        compactMoves(Variant("", "", "", line.moves, startFen = line.startFen).numberedSan())

    @Test
    fun aStudyChapterBecomesItsMainLine() {
        val line = Pgn.parse(LICHESS_STUDY)
        assertEquals(
            "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Be3 e5 7. Nb3 Be6",
            san(line),
        )
        // Split at the colon; "[Variant \"Standard\"]" is the chess variant and says nothing about the opening.
        assertEquals("Sicilian Defense", line.opening)
        assertEquals("Najdorf Variation", line.name)
        assertFalse(line.setUp)
    }

    @Test
    fun theSideThatPlayedTheLastMoveIsTheSideYouTrain() {
        // A Najdorf line that ends on Black's a6 is a Black line.
        assertFalse(Pgn.parse("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6").playWhite)
        assertTrue(Pgn.parse("1. e4 c5 2. Nf3 d6 3. d4").playWhite)
    }

    @Test
    fun aBareMoveListWorksWithAndWithoutNumbers() {
        assertEquals("1. e4 c5 2. Nf3", san(Pgn.parse("1.e4 c5 2.Nf3")))
        assertEquals("1. e4 c5 2. Nf3", san(Pgn.parse("e4 c5 Nf3")))
        assertEquals("1. e4 c5 2. Nf3", san(Pgn.parse("1. e4 1... c5 2. Nf3")))
        // Copied out of a web page, with non-breaking spaces and a result.
        assertEquals("1. e4 e5", san(Pgn.parse("1.\u00A0e4\u00A0e5\u00A01-0")))
    }

    @Test
    fun nestedSideLinesAndCommentsAreThrownAway() {
        val text = "1. d4 { Ein Kommentar (mit Klammer im Text) } d5 2. c4 (2. Nf3 Nf6 (2... c5 3. d5) 3. Bf4) 2... e6"
        assertEquals("1. d4 d5 2. c4 e6", san(Pgn.parse(text)))
    }

    @Test
    fun aBareFenBecomesAStartPositionWithoutMoves() {
        val fen = "r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7"
        val line = Pgn.parse(fen)
        assertTrue(line.setUp)
        assertEquals(fen, line.startFen)
        assertEquals(emptyList(), line.moves)
        // Black is to move, so Black is the side being trained.
        assertFalse(line.playWhite)
    }

    @Test
    fun aShortenedFenIsFilledUpAndALabelIgnored() {
        val line = Pgn.parse("FEN: rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w")
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1", line.startFen)
        assertTrue(line.setUp)
    }

    @Test
    fun movesAfterAFenCountFromThatPosition() {
        val line = Pgn.parse("r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7 7... e5 8. Nf3")
        assertTrue(line.setUp)
        assertEquals("7... e5 8. Nf3", san(line))
        // The last move was White's, so this is a White line.
        assertTrue(line.playWhite)
    }

    @Test
    fun aPgnHeaderFenIsUsedAsTheStart() {
        val text = """
            [Event "Stellungsaufgabe"]
            [SetUp "1"]
            [FEN "r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7"]

            7... e5 8. Nf3 Be7 *
        """.trimIndent()
        val line = Pgn.parse(text)
        assertTrue(line.setUp)
        assertEquals("7... e5 8. Nf3 Be7", san(line))
        assertEquals("Stellungsaufgabe", line.opening)
    }

    @Test
    fun anOpeningWithAColonIsSplitIntoOpeningAndName() {
        val text = "[Event \"?\"]\n[Opening \"Sizilianisch: Drachenvariante\"]\n\n1. e4 c5"
        val line = Pgn.parse(text)
        assertEquals("Sizilianisch", line.opening)
        assertEquals("Drachenvariante", line.name)
    }

    @Test
    fun aUselessEventNameIsNotUsed() {
        val line = Pgn.parse("[Event \"Live Chess\"]\n[White \"a\"]\n[Black \"b\"]\n\n1. e4 e5 2. Nf3 1-0")
        assertEquals("", line.opening)
        assertEquals("", line.name)
    }

    @Test
    fun onlyTheFirstOfSeveralGamesIsTakenOver() {
        val text = "[Event \"Eins\"]\n\n1. e4 e5 1-0\n\n[Event \"Zwei\"]\n\n1. d4 d5 0-1\n"
        val line = Pgn.parse(text)
        assertEquals("1. e4 e5", san(line))
        assertEquals("Eins", line.opening)
        assertEquals(1, line.otherGames)
    }

    @Test
    fun anImpossibleMoveSaysWhereItWentWrong() {
        val e = assertFailsWith<IllegalArgumentException> { Pgn.parse("1. e4 c5 2. Nf6") }
        assertEquals("„Nf6“ ist nach 1. e4 c5 nicht möglich", e.message)
        val first = assertFailsWith<IllegalArgumentException> { Pgn.parse("1. e5") }
        assertEquals("„e5“ ist nach der Ausgangsstellung nicht möglich", first.message)
    }

    @Test
    fun anImpossibleFenIsRefused() {
        // Two white kings.
        val twoKings = assertFailsWith<IllegalArgumentException> {
            Pgn.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBKR w - - 0 1")
        }
        assertTrue(twoKings.message!!.contains("König"), twoKings.message)
        // Black is to move although White is the one standing in check.
        val inCheck = assertFailsWith<IllegalArgumentException> {
            Pgn.parse("4k3/8/8/8/8/8/4q3/4K3 b - - 0 1")
        }
        assertTrue(inCheck.message!!.contains("Schach"), inCheck.message)
        // Not a position at all.
        assertFailsWith<IllegalArgumentException> { Pgn.parse("Guten Morgen") }
        assertFailsWith<IllegalArgumentException> { Pgn.parse("   ") }
    }

    @Test
    fun exportAndImportKeepAPositionTheLineStartsFrom() {
        val fen = "r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7"
        val moves = Exchange.parseMoves("7... e5 8. Nf3", Position.fromFen(fen))
        val variant = Variant("v1", "Sizilianisch", "Najdorf", moves, playWhite = true, startFen = fen)

        val text = Exchange.export(listOf(variant), Progress())
        assertTrue(text.contains("\"startFen\""), text)
        val back = Exchange.import(text, Library(), Progress()).library.variants.single()
        assertEquals(fen, back.startFen)
        assertEquals(moves, back.moves)
        assertTrue(back.setUp)

        // A normal variant does not carry the field around.
        val plain = Variant("v2", "", "Offen", Exchange.parseMoves("1. e4 e5"))
        assertFalse(Exchange.export(listOf(plain), Progress()).contains("startFen"))
    }

    @Test
    fun everythingGoesIntoThePgnAndComesBackWithParseAll() {
        val moves = Exchange.parseMoves("1. e4 c5 2. Nf3 d6")
        val d5 = 4 * 8 + 3
        val e2 = 1 * 8 + 4
        val e4 = 3 * 8 + 4
        val v = Variant(
            "v1", "Sizilianisch", "Scheveningen \"klein\"", moves, playWhite = false,
            theory = "Schwarz will ((d5)) spielen.\n@2. Nf3 {Wichtig}",
            drawings = listOf(
                Drawing(2, listOf(Mark(d5, d5, 1), Mark(e2, e4, 0)), "Wo greift Weiß an?"),
                Drawing(2, listOf(Mark(e4, e4, 2))),
                // Pen 5, set to a blue, shows blue on lichess; the field and the pen come back exactly.
                Drawing(4, listOf(Mark(d5, d5, 4, field = true))),
            ),
        )
        val pens = Ink.DEFAULT.toMutableList().also { it[4] = PenColor(0x1E40C0, "Königsblau") }
        val pgn = Pgn.exportAll(v, pens)
        assertEquals(
            """
            [Event "Sizilianisch: Scheveningen 'klein'"]
            [Opening "Sizilianisch"]
            [Variation "Scheveningen 'klein'"]
            [Result "*"]

            1. e4 c5 { Wo greift Weiß an? [%csl Rd5] [%cal Ge2e4] } { [%csl Be4] } 2. Nf3 d6 { [%csl Bd5] [%fh 5:#d5] } { Schwarz will ((d5)) spielen.
            @2. Nf3 (Wichtig) } *
            """.trimIndent(),
            pgn,
        )
        val back = Pgn.parse(pgn)
        assertEquals(moves, back.moves)
        assertEquals("Sizilianisch", back.opening)
        assertEquals("Scheveningen 'klein'", back.name)

        val all = Pgn.parseAll(pgn)
        assertEquals(moves, all.moves)
        assertEquals(v.drawings, all.drawings)
        assertEquals("Schwarz will ((d5)) spielen.\n@2. Nf3 (Wichtig)", all.theory)
    }

    @Test
    fun upToAMoveKeepsTheTheoryWholeAndTheDrawingsUpToThere() {
        val v = Variant(
            "v1", "", "", Exchange.parseMoves("1. e4 c5 2. Nf3"), theory = "Alles",
            drawings = listOf(Drawing(1, listOf(Mark(0, 0))), Drawing(3, listOf(Mark(1, 1)))),
        )
        val pgn = Pgn.exportAll(v, upTo = 2)
        assertTrue(pgn.endsWith("1. e4 { [%csl Ga1] } 1... c5 { Alles } *"), pgn)
        val back = Pgn.parseAll(pgn)
        assertEquals(listOf("e2e4", "c7c5"), back.moves)
        assertEquals(listOf(Drawing(1, listOf(Mark(0, 0)))), back.drawings)
        assertEquals("Alles", back.theory)
    }

    @Test
    fun aLichessStudyGivesDrawingsAndTheory() {
        val pgn = """
            [Event "Test"]
            { Los geht's } 1. e4 { [%clk 0:03:00] Der beste Zug [%cal Ge2e4,Rd2d4] } 1... c5 (1... e5 { [%csl Ge5] }) 2. Nf3 { [%csl Yd5] }
        """.trimIndent()
        val line = Pgn.parseAll(pgn)
        assertEquals(listOf("e2e4", "c7c5", "g1f3"), line.moves)
        assertEquals(
            listOf(
                Drawing(1, listOf(Mark(1 * 8 + 4, 3 * 8 + 4, 0), Mark(1 * 8 + 3, 3 * 8 + 3, 1)), "Der beste Zug"),
                Drawing(3, listOf(Mark(4 * 8 + 3, 4 * 8 + 3, 3))),
            ),
            line.drawings,
        )
        // The side line and its circle fall away; comments without marks are theory.
        assertEquals("Los geht's", line.theory)
    }
}
