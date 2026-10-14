package repertoire

import repertoire.core.Position
import repertoire.core.SpeechTest
import repertoire.core.VoiceCommand
import repertoire.core.VoiceMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceMoveTest {

    private fun after(vararg san: String): Position = san.fold(Position.START) { p, m -> p.play(p.parseSan(m)!!) }

    private fun uci(said: String, position: Position = Position.START): String? = VoiceMove.parseSpokenEnglish(said, position)?.uci

    @Test
    fun whatVoxtypeWroteDownForTheComputerVoice() {
        // Real transcripts of `say -v Daniel` through voxtype (whisper base).
        assertEquals("g1f3", uci("knight f3."))
        assertEquals("g1f3", uci("Knight F3."))
        assertEquals("e2e4", uci("Go on to E4."))
        val italian = after("e4", "e5", "Nf3", "Nc6", "d3", "d6")
        assertEquals("f1e2", uci("Bishop E2.", italian))
        val castle = after("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        assertEquals("e1g1", uci("castle's king side.", castle))
        assertEquals("e1g1", uci("Castles came side.", castle))
    }

    @Test
    fun movesInWordsAndHomophones() {
        assertEquals("e2e4", uci("pawn to e four"))
        assertEquals("e2e4", uci("e4"))
        assertEquals("g1f3", uci("night ef three"))
        assertEquals("b1c3", uci("knight see three"))
        val p = after("e4", "e5", "Nf3", "Nc6")
        assertEquals("f1c4", uci("bishop sea for", p))
        assertEquals("f1b5", uci("bishop b5", p))
        // "b" alone is the b-pawn, not the bishop.
        assertEquals("b2b4", uci("b4", p))
    }

    @Test
    fun capturesWithAndWithoutTakes() {
        val p = after("e4", "d5")
        assertEquals("e4d5", uci("e takes d5", p))
        assertEquals("e4d5", uci("exd5", p))
        assertEquals("e4d5", uci("takes d5", p))
        val q = after("e4", "d5", "exd5", "Qxd5", "Nc3")
        assertEquals("d5a5", uci("queen a5", q))
        assertEquals("d5d8", uci("queen to d8", q))
    }

    @Test
    fun castlingKnowsItsSide() {
        // Both are legal here: kingside unless queenside is said.
        val both = after("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "d3", "d6", "Bg5", "Bg4", "Nc3", "Qd7", "Qd2", "Nf6")
        assertEquals("e1g1", uci("castles", both))
        assertEquals("e1g1", uci("short castle", both))
        assertEquals("e1c1", uci("castles queenside", both))
        assertEquals("e1c1", uci("castle's queen side.", both))
        assertEquals("e1c1", uci("long castle", both))
        assertEquals("e1c1", uci("O-O-O", both))
    }

    @Test
    fun aMoveThatFitsTwoPiecesNeedsMore() {
        val p = Position.fromFen("4k3/8/8/8/8/8/8/1N1K1N2 w - - 0 1")
        assertNull(uci("knight d2", p))
        assertEquals("b1d2", uci("knight b d2", p))
        assertEquals("f1d2", uci("knight f1 d2", p))
    }

    @Test
    fun promotions() {
        val p = Position.fromFen("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("a7a8q", uci("a8", p))
        assertEquals("a7a8n", uci("a8 knight", p))
        assertEquals("a7b8q", uci("a takes b8 queen", p))
        assertEquals("a7b8r", uci("a takes b8 promotes to rook", p))
    }

    @Test
    fun typedMovesWork() {
        assertEquals("g1f3", uci("Nf3"))
        assertEquals("g1f3", uci("g1f3"))
        assertNull(uci("Nf4"))
        assertNull(uci("hello"))
    }

    @Test
    fun commandsAreNotMoves() {
        assertEquals(VoiceCommand.BOARD, VoiceMove.read("Board.").command)
        assertEquals(VoiceCommand.HINT, VoiceMove.read("hint please").command)
        assertEquals(VoiceCommand.UNDO, VoiceMove.read("Take back!").command)
        assertNull(VoiceMove.read("king e2").command)
        assertNull(uci("board"))
    }

    @Test
    fun movesAreSaidSoThatTextToSpeechGetsThemRight() {
        val p = Position.START
        assertEquals("e 4", VoiceMove.toSpokenEnglish(p, p.parseSan("e4")!!))
        assertEquals("Knight f 3", VoiceMove.toSpokenEnglish(p, p.parseSan("Nf3")!!))
        val q = after("e4", "d5")
        assertEquals("e takes d 5", VoiceMove.toSpokenEnglish(q, q.parseSan("exd5")!!))
        val castle = after("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        assertEquals("Castles kingside", VoiceMove.toSpokenEnglish(castle, castle.parseSan("O-O")!!))
    }

    @Test
    fun everyPhraseOfTheSpeechTestMeansSomething() {
        for (sample in SpeechTest.SAMPLES) {
            val h = sample.expected
            assertTrue(h.command != null || h.castle != null || h.target != null, "„${sample.say}“ wird als nichts gelesen")
            assertTrue(SpeechTest.isRight(sample, sample.say))
        }
        // Mishearings that still mean the same count, others do not.
        val e4 = SpeechTest.SAMPLES.first { it.say == "pawn to e4" }
        assertTrue(SpeechTest.isRight(e4, "Go on to E4."))
        assertTrue(!SpeechTest.isRight(e4, "G1 d4"))
        val castles = SpeechTest.SAMPLES.first { it.say == "castles kingside" }
        assertTrue(SpeechTest.isRight(castles, "castle's king side."))
        assertNotNull(SpeechTest.SAMPLES.firstOrNull { it.expected.promotion == 'n' })
    }
}
