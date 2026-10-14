package repertoire

import repertoire.core.Outcome
import repertoire.core.Position
import repertoire.core.QuizAttempt
import repertoire.core.QuizBuilder
import repertoire.core.Variant
import repertoire.core.compactMoves
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QuizTest {
    private fun variant(id: String, name: String, san: String): Variant {
        var p = Position.START
        val moves = san.split(" ").filter { it.isNotBlank() && !it.endsWith(".") }.map { token ->
            val move = p.parseSan(token) ?: error("illegal $token in ${p.fen}")
            p = p.play(move)
            move.uci
        }
        return Variant(id, "Sizilianisch", name, moves, playWhite = false)
    }

    private val english = variant("en", "English Attack", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3")
    private val scheveningen = variant("sch", "Scheveningen", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e6")
    private val englishE5 = variant("e5", "English Attack mit e5", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e5")
    private val cornell = variant("co", "Cornell Attack", "e4 c5 Nf3 d6 Bb5+")

    private fun attempt(name: String, vararg variants: Variant) =
        QuizAttempt(QuizBuilder.build(variants.toList()).first { it.variant.name == name })

    private fun QuizAttempt.play(san: String) = submit(position.parseSan(san)!!)

    @Test
    fun reachesTargetPosition() {
        val a = attempt("Scheveningen", english, scheveningen, englishE5, cornell)
        for (san in "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3".split(" ")) {
            assertIs<Outcome.Correct>(a.play(san))
        }
        val otherVariant = a.play("e5")
        assertIs<Outcome.OtherVariant>(otherVariant)
        assertEquals("Sizilianisch › English Attack mit e5", otherVariant.name)
        val last = a.play("e6")
        assertIs<Outcome.Correct>(last)
        assertTrue(last.finished)
        assertEquals(0, a.mistakes)
        assertEquals("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. f3 e6", compactMoves(a.played))
    }

    @Test
    fun wrongMoveIsCorrectedAndCounted() {
        val a = attempt("English Attack", english, cornell)
        a.play("e4")
        a.play("c5")
        val wrong = a.play("Nc3")
        assertIs<Outcome.Wrong>(wrong)
        assertEquals("g1f3", wrong.expected.uci)
        assertEquals(1, a.mistakes)
        assertIs<Outcome.OtherVariant>(a.play("d6").let { a.play("Bb5+") })
    }

    @Test
    fun acceptsTranspositionKnownFromAnotherVariant() {
        // Same position after 3. d4, reached with 1. Nf3 first.
        val main = variant("a", "Offen", "e4 c5 Nf3 d6 d4")
        val transposed = variant("b", "Über Nf3", "Nf3 c5 e4 d6 d4")
        val a = attempt("Offen", main, transposed)
        for (san in "Nf3 c5 e4 d6 d4".split(" ")) assertIs<Outcome.Correct>(a.play(san))
        assertTrue(a.finished)
    }
}
