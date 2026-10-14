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
    fun whatVoxtypeWroteForTheUsersVoiceIsUnderstoodBySound() {
        // "knight f3" as voxtype wrote it down for the user's voice.
        assertEquals("g1f3", VoiceMove.understand("Night of free.", Position.START)?.uci)
        // Clearly nothing: no move is guessed.
        assertNull(VoiceMove.understand("banana", Position.START))
        assertNull(VoiceMove.understand("good morning everyone", Position.START))
    }

    @Test
    fun whatTheSpeechTestTaughtComesFirst() {
        val learned = SpeechTest.learned(listOf("d4" to "G1 d4", "e4" to "E4.", "knight f3" to "Night of free."))
        // Right ones teach nothing, wrong ones are remembered by how they were written.
        assertEquals(mapOf("gee one dee four" to "d4", "night of free" to "knight f3"), learned)
        assertEquals("d2d4", VoiceMove.understand("G1, D4", Position.START, learned)?.uci)
        // Heard the same for two different moves: that teaches nothing.
        assertTrue(SpeechTest.learned(listOf("d4" to "Banana.", "e4" to "banana")).isEmpty())
        // A clear move that cannot be played is not guessed into one that can.
        assertNull(VoiceMove.understand("knight f6", Position.START))
    }

    @Test
    fun theMovesToSayAreWrittenAsTheyAreSaid() {
        assertEquals("knight b d2", SpeechTest.phrase("Nbd2"))
        assertEquals("e takes d5", SpeechTest.phrase("exd5"))
        assertEquals("bishop takes f7", SpeechTest.phrase("Bxf7"))
        assertEquals("rook f e1", SpeechTest.phrase("Rfe1"))
        assertEquals("e takes d8 queen", SpeechTest.phrase("exd8=Q"))
        assertEquals("castles queenside", SpeechTest.phrase("O-O-O"))
        assertEquals("knight f 3", SpeechTest.speech("knight f3"))
        assertTrue(SpeechTest.PHRASES.size > 120)
        assertEquals(SpeechTest.PHRASES.size, SpeechTest.PHRASES.distinct().size)
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
        for (phrase in SpeechTest.PHRASES) {
            val h = VoiceMove.read(phrase)
            assertTrue(h.command != null || h.castle != null || h.target != null, "„$phrase“ wird als nichts gelesen")
            assertTrue(SpeechTest.isRight(phrase, phrase))
        }
        // Mishearings that still mean the same count, others do not.
        assertTrue(SpeechTest.isRight("pawn to e4", "Go on to E4."))
        assertTrue(!SpeechTest.isRight("pawn to e4", "G1 d4"))
        assertTrue(SpeechTest.isRight("castles kingside", "castle's king side."))
        assertNotNull(SpeechTest.PHRASES.firstOrNull { VoiceMove.read(it).promotion == 'n' })
        // The own variants' moves first, each once.
        val own = repertoire.core.Variant("x", "Sizilianisch", "Najdorf", listOf("e2e4", "c7c5", "g1f3"))
        assertEquals(listOf("e4", "c5", "knight f3", "board"), SpeechTest.phrases(listOf(own)).take(4))
        assertEquals("d4", SpeechTest.phrases(listOf(own), common = true)[3 + SpeechTest.COMMANDS.size])
    }
}
