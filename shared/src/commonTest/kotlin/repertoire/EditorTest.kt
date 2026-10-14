package repertoire

import repertoire.core.FileStore
import repertoire.core.Position
import repertoire.core.Store
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Library and progress in memory, so the editor can be driven like in the app. */
private class MemoryFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

private const val LINE = "e4 c5 Nf3 d6 d4 cxd4"

class EditorTest {
    private val files = MemoryFiles()

    private fun app() = AppViewModel(Store(files))

    /** Plays the moves on the board, exactly like a click on the board does. */
    private fun AppViewModel.play(san: String) {
        for (token in san.split(" ").filter { it.isNotBlank() }) {
            val board = editor!!.position
            editorPlay(board.parseSan(token) ?: error("$token ist in ${board.fen} nicht möglich"))
        }
    }

    private fun AppViewModel.add(name: String, san: String): String {
        newVariant()
        editor!!.opening = "Sizilianisch"
        editor!!.name = name
        play(san)
        saveEditor()
        return library.variants.last().id
    }

    private fun AppViewModel.san(): String = library.variants.last().numberedSan().joinToString(" ")

    @Test
    fun thePlayerWalksThroughTheLineByItself() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id }, play = true)
        val e = vm.editor!!
        assertEquals(0, e.cursor)
        assertTrue(e.playing)
        repeat(6) { vm.editorPlayTick() }
        assertEquals(6, e.cursor)
        // At the end it stops, unless it should start over.
        vm.editorPlayTick()
        assertFalse(e.playing)
        // Play at the end starts from the beginning again.
        vm.editorTogglePlay()
        assertEquals(0, e.cursor)
        vm.setPlayLoop(true)
        repeat(7) { vm.editorPlayTick() }
        assertEquals(0, e.cursor)
        assertTrue(e.playing)
        // Stepping by hand stops it.
        vm.editorPlayTick()
        vm.editorStepForward()
        assertEquals(2, e.cursor)
        assertFalse(e.playing)
    }

    @Test
    fun steppingThroughTheLineKeepsEveryMove() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        assertEquals(6, e.cursor)

        vm.editorGoTo(2)
        assertEquals(2, e.shown.size)
        assertEquals(6, e.moves.size)
        assertEquals(Position.START.play(e.moves[0]).play(e.moves[1]).fen, e.position.fen)
        assertFalse(e.atEnd)

        vm.editorStepBack()
        vm.editorStepForward()
        vm.editorStepForward()
        assertEquals(3, e.cursor)
        vm.editorToEnd()
        assertTrue(e.atEnd)

        vm.saveEditor()
        assertEquals(6, vm.library.variants.first { it.id == id }.moves.size)
    }

    @Test
    fun goingThroughFromTheStartShowsTheStartPosition() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id }, fromStart = true)
        assertEquals(0, vm.editor!!.cursor)
        assertEquals(Position.START.fen, vm.editor!!.position.fen)
        // Walking forward through the stored line does not change it.
        repeat(6) { vm.editorStepForward() }
        assertEquals(6, vm.editor!!.moves.size)
    }

    @Test
    fun replayingTheStoredMoveOnlyWalksOn() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        vm.editorGoTo(2)
        val before = e.moves
        vm.play("Nf3")
        assertEquals(before, e.moves)
        assertEquals(3, e.cursor)
    }

    @Test
    fun anotherMoveInTheMiddleReplacesTheRestOfTheLine() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        vm.editorGoTo(2)
        vm.play("Bc4")
        assertEquals(3, vm.editor!!.moves.size)
        vm.saveEditor()
        assertEquals("1. e4 1... c5 2. Bc4", vm.san())
    }

    @Test
    fun theUpdatedPositionSurvivesARestart() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        vm.editorGoTo(4)
        vm.editorTruncate()
        vm.play("Bb5+")
        vm.editor!!.name = "Klassisch"
        vm.saveEditor()

        // Same files, fresh app: the variant is updated, not duplicated.
        val restarted = app()
        assertEquals(1, restarted.library.variants.size)
        val saved = restarted.library.variants.single()
        assertEquals("Klassisch", saved.name)
        assertEquals("1. e4 1... c5 2. Nf3 2... d6 3. Bb5+", saved.numberedSan().joinToString(" "))
    }

    @Test
    fun undoAndClearWorkFromAnywhereInTheLine() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        vm.editorUndo()
        assertEquals(5, e.moves.size)
        assertEquals(5, e.cursor)
        vm.editorGoTo(1)
        vm.editorTruncate()
        assertEquals(1, e.moves.size)
        vm.editorClear()
        assertEquals(0, e.moves.size)
        assertEquals(0, e.cursor)
    }

    @Test
    fun changedMovesAnnounceAndResetTheLearningState() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.startLearning()
        for (token in LINE.split(" ")) {
            vm.submitMove(vm.session!!.attempt!!.position.parseSan(token)!!)
        }
        vm.grade()
        vm.endSession()
        assertTrue(vm.progress.cards.containsKey(id))

        vm.editVariant(vm.library.variants.first { it.id == id })
        assertFalse(vm.editorRestartsProgress())
        vm.editorGoTo(2)
        vm.play("Bc4")
        assertTrue(vm.editorRestartsProgress())
        vm.saveEditor()
        assertNull(vm.progress.cards[id])
    }

    @Test
    fun aPastedPgnLandsInTheEditorReadyToBeShortened() {
        val vm = app()
        vm.openPaste()
        vm.setPasteText("[Opening \"Sizilianisch: Najdorf\"]\n\n1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 *")
        vm.applyPaste()
        assertNull(vm.paste)
        val e = vm.editor!!
        assertEquals("Sizilianisch", e.opening)
        assertEquals("Najdorf", e.name)
        assertEquals(10, e.moves.size)
        // The board starts at move zero so the line can be walked through and cut.
        assertEquals(0, e.cursor)
        assertFalse(e.setUp)
        // Black played the last move, so this is a Black line.
        assertFalse(e.playWhite)

        vm.editorGoTo(6)
        vm.editorTruncate()
        vm.saveEditor()
        assertEquals("1. e4 1... c5 2. Nf3 2... d6 3. d4 3... cxd4", vm.san())
    }

    @Test
    fun aPastedFenBecomesAVariantThatStartsThere() {
        val fen = "r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7"
        val vm = app()
        vm.openPaste()
        vm.setPasteText(fen)
        vm.applyPaste()
        val e = vm.editor!!
        assertTrue(e.setUp)
        assertEquals(fen, e.position.fen)
        // A position alone is not a puzzle yet: the continuation still has to be played.
        assertEquals(0, e.moves.size)
        e.name = "Aus der FEN"
        vm.saveEditor()
        assertEquals(0, vm.library.variants.size)

        vm.play("e5 Nf3")
        vm.saveEditor()
        val saved = vm.library.variants.single()
        assertEquals(fen, saved.startFen)
        assertEquals("7... e5 8. Nf3", saved.numberedSan().joinToString(" "))
        assertEquals(fen, saved.start.fen)

        // Training this variant begins at the pasted position, not at the start of a game.
        vm.practice(saved.id)
        assertEquals(fen, vm.session!!.attempt!!.position.fen)
    }

    @Test
    fun aPastedFenSurvivesARestartAndCanBeUndone() {
        val fen = "r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7"
        val vm = app()
        vm.openPaste()
        vm.setPasteText("$fen 7... e5")
        vm.applyPaste()
        vm.editor!!.name = "Najdorf-Mitte"
        vm.saveEditor()

        val restarted = app()
        val saved = restarted.library.variants.single()
        assertEquals(fen, saved.startFen)
        assertEquals("7... e5", saved.numberedSan().joinToString(" "))

        // Back to the normal start position: the moves from the pasted one no longer fit.
        restarted.editVariant(saved)
        assertTrue(restarted.editor!!.setUp)
        restarted.editorResetStart()
        assertFalse(restarted.editor!!.setUp)
        assertEquals(0, restarted.editor!!.moves.size)
        assertEquals(Position.START.fen, restarted.editor!!.position.fen)
    }

    @Test
    fun textThatIsNoPgnKeepsTheDialogOpen() {
        val vm = app()
        vm.openPaste()
        vm.setPasteText("1. e4 Qh8")
        vm.applyPaste()
        assertNull(vm.editor)
        assertEquals("„Qh8“ ist nach 1. e4 nicht möglich", vm.paste!!.error)
        // Typing again clears the complaint.
        vm.setPasteText("1. e4 e5")
        assertNull(vm.paste!!.error)
        vm.applyPaste()
        assertNull(vm.paste)
        assertEquals(2, vm.editor!!.moves.size)
    }

    @Test
    fun onlyTheEndOfTheLineCountsAsADuplicate() {
        val vm = app()
        vm.add("Offen", LINE)
        vm.add("Kürzer", "e4 c5 Nf3")
        vm.editVariant(vm.library.variants.first())
        vm.editorGoTo(3)
        // The board stands in the other variant's position, but this line still ends elsewhere.
        assertNull(vm.editorDuplicate())
        vm.editorTruncate()
        assertEquals("Sizilianisch › Kürzer", vm.editorDuplicate())
    }
}
