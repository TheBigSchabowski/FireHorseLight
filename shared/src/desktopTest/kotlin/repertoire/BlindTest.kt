package repertoire

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import repertoire.core.FileStore
import repertoire.core.Position
import repertoire.core.Speaker
import repertoire.core.SpeechTest
import repertoire.core.Store
import repertoire.core.VoiceInput
import repertoire.ui.AppViewModel
import repertoire.ui.SPEECH_TEST_DIR
import repertoire.ui.SPEECH_TEST_TABLE
import repertoire.ui.TAP_MS
import repertoire.ui.VOICE_DIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlindTest {
    private class Memory : FileStore {
        val files = mutableMapOf<String, String>()
        override val location = "Speicher"
        override fun read(name: String): String? = files[name]
        override fun write(name: String, text: String) {
            files[name] = text
        }
    }

    /** Remembers what was said. */
    private class Mouth : Speaker {
        val said = mutableListOf<String>()

        override fun say(text: String) {
            said += text
        }

        override fun stop() {}
    }

    /** Writes down what it is told to and remembers what was kept. */
    private class Voice : VoiceInput {
        val kept = mutableMapOf<String, ByteArray>()
        var next = ""
        var recording = false

        override fun problem(): String? = null
        override fun start(): Boolean = true.also { recording = true }
        /** The microphone hands over silence, as when macOS does not let the app have it. */
        var silent = false

        /** What is "said" into the microphone: each text has a sound of its own, three tones picked by it. */
        var sound = ""

        /** How long each of the three tones is. */
        var toneMs = 300

        override fun stop(): ByteArray? {
            if (!recording) return null
            recording = false
            val seed = sound.lowercase().filter { it.isLetterOrDigit() }.hashCode()
            val tones = List(3) { 200 + ((seed shr (it * 5)) and 31) * 90 }
            val samples = tones.flatMap { hz -> List(16 * toneMs) { i -> if (silent) 0 else (8000 * kotlin.math.sin(2 * Math.PI * hz * i / 16_000)).toInt() } }
            val out = ByteArray(44 + samples.size * 2)
            "RIFF".encodeToByteArray().copyInto(out)
            samples.forEachIndexed { i, s ->
                out[44 + 2 * i] = (s and 0xFF).toByte()
                out[45 + 2 * i] = (s shr 8).toByte()
            }
            return out
        }
        override fun cancel() {
            recording = false
        }

        override fun transcribe(wav: ByteArray, prompt: String): String = next
        override fun read(name: String): ByteArray? = kept[name]
        override fun list(dir: String): List<String> = kept.keys.filter { it.startsWith("$dir/") }
        override fun delete(name: String) {
            kept.remove(name)
        }
        override fun keep(data: ByteArray, name: String): String = name.also { kept[it] = data }
    }

    private val mic = Voice()
    private val mouth = Mouth()

    private fun app() = AppViewModel(Store(Memory()), voice = mic, speaker = mouth).apply {
        uiScope = CoroutineScope(Dispatchers.Unconfined)
        transcribeOn = Dispatchers.Unconfined
    }

    private fun AppViewModel.addVariant(opening: String, playWhite: Boolean, vararg sans: String) {
        newVariant()
        editor!!.name = sans.joinToString(" ")
        editor!!.opening = opening
        editor!!.playWhite = playWhite
        var p = Position.START
        for (san in sans) {
            val move = p.parseSan(san)!!
            editorPlay(move)
            p = p.play(move)
        }
        saveEditor()
    }

    /**
     * Space held long enough to be speech, then let go: Whisper "writes down" [said]; the recording
     * sounds like [sound] (by default like what is written down).
     */
    private fun AppViewModel.speak(said: String, sound: String = said) {
        mic.next = said
        mic.sound = sound
        assertTrue(voiceDown())
        Thread.sleep(TAP_MS + 20L)
        assertFalse(voiceUp())
    }

    @Test
    fun blindTheOtherSideIsPlayedAndSaid() {
        val vm = app()
        vm.addVariant("Sizilianisch", false, "e4", "c5", "Nf3", "d6")
        vm.toggleBlind()
        vm.startCram()
        val attempt = vm.session!!.attempt!!
        // Playing Black: White's first move is already on the board, and said.
        assertEquals(1, attempt.played.size)
        assertEquals("e 4", mouth.said.last())
        assertTrue(vm.boardCovered)

        vm.speak("C5.")
        assertEquals(listOf("1. e4", "1... c5", "2. Nf3"), attempt.played)
        assertEquals("Knight f 3", mouth.said.last())
        assertEquals("C5.", vm.heard)

        vm.speak("pawn to d6")
        assertTrue(attempt.finished)
        assertEquals(0, vm.mistakes(vm.session!!))
        assertEquals("Correct", mouth.said.last())
        // Solved: the board is shown again.
        assertFalse(vm.boardCovered)
    }

    @Test
    fun aWrongMoveIsCorrectedAloudAndTheLineGoesOn() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5", "Nf3", "Nc6", "Bc4")
        vm.toggleBlind()
        vm.startCram()
        assertEquals("Your move", mouth.said.last())
        vm.speak("d4")
        val attempt = vm.session!!.attempt!!
        assertEquals(1, attempt.mistakes)
        // e4 was played instead, said, and Black answered.
        assertTrue("No. e 4" in mouth.said)
        assertEquals("e 5", mouth.said.last())
        vm.speak("hint")
        assertEquals("Knight f 3", mouth.said.last())
        assertEquals(2, vm.mistakes(vm.session!!))
    }

    @Test
    fun commandsAndWhatIsNotUnderstood() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5", "Nf3")
        vm.toggleBlind()
        vm.startCram()
        vm.speak("board")
        assertTrue(vm.peeking)
        assertFalse(vm.boardCovered)
        vm.speak("board")
        assertTrue(vm.boardCovered)
        vm.speak("banana")
        assertEquals("Say again", mouth.said.last())
        vm.speak("knight f6")
        assertEquals("Not possible", mouth.said.last())
        assertNotNull(vm.flash)
        assertEquals(0, vm.session!!.attempt!!.played.size)
    }

    @Test
    fun aShortTapIsNotSpeech() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.startCram()
        assertTrue(vm.voiceDown())
        assertTrue(vm.voiceUp())
        assertFalse(mic.recording)
        assertNull(vm.heard)
    }

    @Test
    fun spaceDoesNothingWithoutABoard() {
        val vm = app()
        assertFalse(vm.voiceDown())
    }

    @Test
    fun movesSaidInTheEditorAreSaidBack() {
        val vm = app()
        vm.toggleBlind()
        vm.newVariant()
        assertTrue(vm.boardCovered)
        vm.speak("e4")
        vm.speak("c5")
        vm.speak("undo")
        assertEquals(listOf("e2e4"), vm.editor!!.moves.map { it.uci })
        assertEquals("c 5", mouth.said.last())
    }

    @Test
    fun theTrainingSaysThePhraseKeepsEveryRecordingAndLearns() {
        val vm = app()
        vm.addVariant("Sizilianisch", false, "e4", "c5", "Nf3")
        vm.openSpeechTest()
        val lab = vm.voiceLab!!
        // The own variant's moves come first.
        assertEquals(listOf("e4", "c5", "knight f3"), lab.phrases.take(3))
        assertEquals("e 4", mouth.said.last())
        vm.speak("E4.")
        assertEquals("c 5", mouth.said.last())
        vm.speak("See five.")
        vm.speak("Night of free.")
        assertEquals(3, lab.index)
        assertEquals(3, vm.recordedPhrases)
        assertTrue("$SPEECH_TEST_DIR/knight f3/1.wav" in mic.kept)
        val table = mic.kept.getValue("$SPEECH_TEST_DIR/$SPEECH_TEST_TABLE").decodeToString().lines()
        assertEquals("1\te4\tE4.\tok", table[1].split("\t").take(4).joinToString("\t"))
        // Said again: one more recording of the same phrase.
        vm.speechTestStep(-1)
        vm.speak("Knight F3.")
        assertTrue("$SPEECH_TEST_DIR/knight f3/2.wav" in mic.kept)

        // Opened again, it goes on where it was left.
        vm.closeVoiceLab()
        vm.openSpeechTest()
        assertEquals(3, vm.voiceLab!!.index)
        vm.closeVoiceLab()
    }

    @Test
    fun theTrainingIsNotForcedAndHasTheOwnMovesAndTheCommands() {
        val vm = app()
        assertNull(vm.voiceLab)
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.openSpeechTest()
        assertEquals(listOf("e4", "e5", "board", "repeat", "hint", "next", "restart", "undo"), vm.voiceLab!!.phrases)
        vm.openCommonTraining()
        assertTrue(vm.voiceLab!!.phrases.size > 120)
    }

    @Test
    fun afterTheTrainingAMoveIsRecognizedBySoundEvenWhenWhisperGetsItWrong() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.openSpeechTest()
        assertEquals("e4", vm.voiceLab!!.phrases.first())
        vm.speak("You", sound = "my e4")
        vm.closeVoiceLab()
        vm.startCram()
        // The same sound again; Whisper writes nonsense, the recording decides.
        vm.speak("You", sound = "my e4")
        // Not blind: both sides are the user's, so no answer comes.
        assertEquals(listOf("1. e4"), vm.session!!.attempt!!.played)
        assertEquals("e4 (am Klang erkannt)", vm.heard)
    }

    @Test
    fun aRecognizedMoveIsLearnedAndHeardBySoundNextTime() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.startCram()
        vm.speak("e4", sound = "my e4")
        assertTrue("$SPEECH_TEST_DIR/e4/1.wav" in mic.kept)
        vm.restartCurrent()
        vm.speak("You", sound = "my e4")
        assertEquals("e4 (am Klang erkannt)", vm.heard)
        assertEquals(listOf("1. e4"), vm.session!!.attempt!!.played)
    }

    @Test
    fun whatWasNotUnderstoodIsLinkedToTheMovePlayedNext() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.toggleBlind()
        vm.startCram()
        vm.speak("Night of free banana", sound = "ee for")
        assertNotNull(vm.pendingRecording)
        assertEquals(0, vm.session!!.attempt!!.played.size)
        // Typed into the field: played, and the recording is kept as e4.
        vm.onSpoken("e4")
        assertNull(vm.pendingRecording)
        assertTrue("$SPEECH_TEST_DIR/e4/1.wav" in mic.kept)
        assertTrue(vm.flash!!.contains("e4"))
        vm.restartCurrent()
        vm.speak("banana", sound = "ee for")
        assertEquals("e4 (am Klang erkannt)", vm.heard)
    }

    @Test
    fun aMoveOnTheBoardTakesThePendingRecordingToo() {
        val vm = app()
        vm.newVariant()
        vm.speak("hmm", sound = "springer f drei")
        assertNotNull(vm.pendingRecording)
        vm.boardMove(Position.START.parseSan("Nf3")!!)
        assertTrue("$SPEECH_TEST_DIR/knight f3/1.wav" in mic.kept)
        assertEquals(1, vm.editor!!.moves.size)
    }

    @Test
    fun wronglyRecognizedIsUnlinkedAndTakenBackInTheEditor() {
        val vm = app()
        vm.newVariant()
        // Said "d4", but Whisper wrote "e4".
        vm.speak("e4", sound = "dee four")
        assertTrue("$SPEECH_TEST_DIR/e4/1.wav" in mic.kept)
        assertTrue(vm.canCorrect)
        vm.wrongRecognition()
        assertTrue("$SPEECH_TEST_DIR/e4/1.wav" !in mic.kept)
        assertTrue(vm.editor!!.moves.isEmpty())
        vm.onSpoken("d4")
        assertTrue("$SPEECH_TEST_DIR/d4/1.wav" in mic.kept)
        assertEquals(listOf("d2d4"), vm.editor!!.moves.map { it.uci })
    }

    @Test
    fun silenceIsNotSentToBeRecognized() {
        val vm = app()
        vm.addVariant("Italienisch", true, "e4", "e5")
        vm.startCram()
        mic.silent = true
        vm.speak("e4")
        assertNull(vm.heard)
        assertTrue(vm.flash!!.contains("Stille"))
    }

    @Test
    fun theVoiceSampleIsKeptWithItsText() {
        val vm = app()
        vm.openVoiceSample()
        mic.toneMs = 2000
        vm.speak("")
        assertEquals("$VOICE_DIR/referenz.wav", vm.voiceLab!!.saved)
        assertTrue("$VOICE_DIR/referenz.txt" in mic.kept)
    }
}
