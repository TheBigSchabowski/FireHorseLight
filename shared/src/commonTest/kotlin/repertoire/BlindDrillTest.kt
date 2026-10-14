package repertoire

import repertoire.core.BlindDrill
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlindDrillTest {

    private fun variant(id: String, name: String, moves: String, playWhite: Boolean = true): Variant {
        var p = Position.START
        val uci = moves.split(" ").map { san ->
            val m = p.parseSan(san)!!
            p = p.play(m)
            m.uci
        }
        return Variant(id, "Italienisch", name, uci, playWhite)
    }

    private val giuoco = variant("g", "Giuoco Piano", "e4 e5 Nf3 Nc6 Bc4 Bc5")
    private val twoKnights = variant("t", "Zweispringerspiel", "e4 e5 Nf3 Nc6 Bc4 Nf6")
    private val library = listOf(giuoco, twoKnights)

    private fun Position.san(text: String): Move = parseSan(text)!!

    @Test
    fun walksTheLineAndEndsAtTheTarget() {
        val drill = BlindDrill.forVariants(listOf(giuoco), library).single()
        var p = drill.start
        assertTrue(drill.isUsersTurn(p))

        for (san in listOf("e4", "Nf3", "Bc4")) {
            val move = p.san(san)
            assertEquals(BlindDrill.Verdict.RIGHT, drill.judge(p, move))
            p = p.play(move)
            assertFalse(drill.isUsersTurn(p))
            p = p.play(drill.expected(p)!!)
        }
        assertTrue(drill.isDone(p))
        assertNull(drill.expected(p))
    }

    @Test
    fun tellsWrongFromAnotherVariant() {
        val drill = BlindDrill.forVariants(listOf(giuoco), library).single()
        val p = Position.START
        assertEquals(BlindDrill.Verdict.WRONG, drill.judge(p, p.san("d4")))
    }

    @Test
    fun theOpponentPlaysTheMoveOfThisVariantAtABranch() {
        // Black's third move decides between the two lines: the drill for the Giuoco must answer Bc5.
        val drill = BlindDrill.forVariants(listOf(giuoco), library).single()
        var p = Position.START
        for (san in listOf("e4", "e5", "Nf3", "Nc6", "Bc4")) p = p.play(p.san(san))
        assertEquals("f8c5", drill.expected(p)!!.uci)
    }

    @Test
    fun aMoveIntoAnotherSavedLineIsOtherVariant() {
        val black = variant("b", "Sizilianisch", "e4 c5 Nf3 d6", playWhite = false)
        val french = variant("f", "Französisch", "e4 e6 d4 d5", playWhite = false)
        val drill = BlindDrill.forVariants(listOf(black), listOf(black, french)).single()
        var p = drill.start
        // As Black, the line starts with White's move.
        assertFalse(drill.isUsersTurn(p))
        p = p.play(drill.expected(p)!!)
        assertTrue(drill.isUsersTurn(p))
        assertEquals(BlindDrill.Verdict.OTHER_VARIANT, drill.judge(p, p.san("e6")))
        assertEquals(BlindDrill.Verdict.RIGHT, drill.judge(p, p.san("c5")))
    }

    @Test
    fun transpositionsFromTheLibraryCount() {
        // Saved twice: 1.Nf3 Nf6 2.d4 d5 and 1.d4 d5 2.Nf3 Nf6 reach the same position.
        val a = variant("a", "Damenbauer", "d4 d5 Nf3 Nf6")
        val b = variant("b", "Damenbauer über Nf3", "Nf3 Nf6 d4 d5")
        val drill = BlindDrill.forVariants(listOf(a), listOf(a, b)).single()
        var p = Position.START
        assertEquals(BlindDrill.Verdict.RIGHT, drill.judge(p, p.san("Nf3")))
        p = p.play(p.san("Nf3"))
        p = p.play(drill.expected(p)!!)
        p = p.play(p.san("d4"))
        p = p.play(drill.expected(p)!!)
        assertTrue(drill.isDone(p))
    }
}
