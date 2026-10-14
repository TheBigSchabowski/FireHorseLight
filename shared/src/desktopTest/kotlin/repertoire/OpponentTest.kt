package repertoire

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import repertoire.core.EngineEntry
import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Settings
import repertoire.core.Move
import repertoire.core.Store
import repertoire.ui.AppViewModel
import repertoire.ui.ENGINE_DEPTHS
import repertoire.core.EngineLine
import repertoire.core.ExplorerMove
import repertoire.core.Score
import repertoire.ui.EngineOpponent
import repertoire.ui.opponentMove
import repertoire.ui.stepIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class OpponentMemory : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

@OptIn(InternalComposeUiApi::class)
class OpponentTest {
    private fun editorWith(vararg moves: String): AppViewModel {
        val vm = AppViewModel(Store(OpponentMemory()))
        vm.newVariant()
        for (m in moves) vm.editorPlay(Move.fromUci(m))
        return vm
    }

    @Test
    fun ctrlBShiftROpensTheDialogInTheEditor() {
        val vm = editorWith("e2e4")
        vm.shortcutKeys.handle(KeyEvent(Key.B, KeyEventType.KeyDown, 2, isCtrlPressed = true))
        vm.shortcutKeys.handle(KeyEvent(Key.R, KeyEventType.KeyDown, 'R'.code, isShiftPressed = true))
        assertTrue(vm.opponentDialog)
        assertFalse(vm.engineOpen)
    }

    @Test
    fun withoutAnEditorItOnlySaysWhere() {
        val vm = AppViewModel(Store(OpponentMemory()))
        vm.openOpponent()
        assertFalse(vm.opponentDialog)
        assertNotNull(vm.flash)
    }

    @Test
    fun theEngineMovesOnlyForItsSideAtTheEndOfTheLine() {
        val vm = editorWith("e2e4", "c7c5")
        vm.setOpponent(EngineOpponent(white = true, depth = 12))
        assertTrue(vm.opponentToMove)
        vm.setOpponent(EngineOpponent(white = false, depth = 12))
        assertFalse(vm.opponentToMove)
        // Stepping back to a position where Black is to move does not wake it.
        vm.editorStepBack()
        assertFalse(vm.opponentToMove)
    }

    @Test
    fun itsMovePlaysOnlyIfTheBoardIsStillThere() {
        val vm = editorWith("e2e4", "c7c5")
        vm.setOpponent(EngineOpponent(white = true, depth = 12))
        val fen = vm.editor!!.position.fen
        vm.opponentPlay(fen, "g1f3")
        assertEquals(3, vm.editor!!.moves.size)
        assertFalse(vm.opponentToMove)
        // An answer for a position that is gone is dropped.
        vm.editorPlay(Move.fromUci("d7d6"))
        vm.opponentPlay(fen, "d2d4")
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6"), vm.editor!!.moves.map { it.uci })
    }

    @Test
    fun theDepthIsKeptAndOffTurnsItOff() {
        val vm = editorWith("e2e4")
        vm.setOpponent(EngineOpponent(white = false, depth = 22))
        assertEquals(22, vm.library.settings.opponentDepth)
        vm.setOpponent(null)
        assertNull(vm.editor!!.opponent)
        assertFalse(vm.opponentToMove)
    }

    @Test
    fun theRankIsKeptAndPicksItsLine() {
        val vm = editorWith("e2e4")
        vm.setOpponent(EngineOpponent(white = false, depth = 12, rank = 3))
        assertEquals(3, vm.library.settings.opponentRank)
        val lines = listOf(
            EngineLine(1, 12, Score(cp = 30), listOf("c7c5", "g1f3")),
            EngineLine(2, 12, Score(cp = 25), listOf("e7e5")),
            EngineLine(3, 12, Score(cp = 20), listOf("e7e6")),
        )
        assertEquals("c7c5", opponentMove(lines, 1))
        assertEquals("e7e5", opponentMove(lines, 2))
        assertEquals("e7e6", opponentMove(lines, 3))
        // Fewer moves to choose from: the worst of them.
        assertEquals("e7e5", opponentMove(lines.take(2), 3))
        assertNull(opponentMove(emptyList(), 2))
    }

    @Test
    fun shiftClickPlaysTheWholeLineAsFarAsItIsLegal() {
        val vm = editorWith("e2e4")
        vm.playSuggestedLine(listOf("c7c5", "g1f3", "d7d6", "a1a8", "d2d4"))
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6"), vm.editor!!.moves.map { it.uci })
        assertEquals(4, vm.editor!!.cursor)
    }

    @Test
    fun lichessCastlingAsKingTakesRookIsPlayedAsCastling() {
        val vm = editorWith("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        val position = vm.editor!!.position
        // As lichess sends it: the king onto its rook.
        val castles = ExplorerMove(uci = "e1h1", san = "O-O")
        assertEquals("e1g1", castles.moveIn(position)?.uci)
        // Even if the SAN were missing, the UCI alone is turned into castling.
        assertEquals("e1g1", ExplorerMove(uci = "e1h1", san = "").moveIn(position)?.uci)
        vm.playSuggested(castles.moveIn(position))
        assertEquals("e1g1", vm.editor!!.moves.last().uci)
        assertEquals('R', vm.editor!!.position.pieceAt(5))
    }

    @Test
    fun aMoveThatDoesNotFitTheBoardIsNotPlayedAndDoesNotBreakTheLine() {
        val vm = editorWith("e2e4", "e7e5")
        vm.playSuggested("e1h1")
        vm.playSuggested("zz")
        vm.playSuggested(ExplorerMove(uci = "d7d5", san = "d5").moveIn(vm.editor!!.position))
        vm.editorPlay(Move.fromUci("e2e4"))
        assertEquals(listOf("e2e4", "e7e5"), vm.editor!!.moves.map { it.uci })
        assertNotNull(vm.flash)
        // The board can still be drawn.
        assertEquals(2, vm.editor!!.position.fullmoveNumber)
    }

    @Test
    fun aTypedDepthStepsToItsNeighbours() {
        assertEquals(18, stepIn(ENGINE_DEPTHS, 17, 1))
        assertEquals(16, stepIn(ENGINE_DEPTHS, 17, -1))
        assertEquals(50, stepIn(ENGINE_DEPTHS, 77, 1))
        assertEquals(50, stepIn(ENGINE_DEPTHS, 77, -1))
        assertEquals(0, stepIn(ENGINE_DEPTHS, 0, -1))
    }

    @Test
    fun typedEngineValuesAreKeptInBounds() {
        val store = Store(OpponentMemory())
        store.saveLibrary(Library(settings = Settings(engines = listOf(EngineEntry("Test", "/x")))))
        val vm = AppViewModel(store)
        vm.setEngineDepth("/x", 33)
        vm.setEngineHash("/x", 3000)
        vm.setEngineHash("/x", 5)
        val e = vm.library.settings.engines.single()
        assertEquals(33, e.depth)
        assertEquals(16, e.hashMb)
    }
}
