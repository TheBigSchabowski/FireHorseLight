package repertoire

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import repertoire.core.FileStore
import repertoire.core.Speaker
import repertoire.core.Store
import repertoire.ui.BlindfoldViewModel
import repertoire.ui.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VariantEditorTest {

    private class MemoryFiles : FileStore {
        val files = mutableMapOf<String, String>()
        override val location = "memory"
        override fun read(name: String) = files[name]
        override fun write(name: String, text: String) {
            files[name] = text
        }
    }

    private class Silent : Speaker {
        val said = mutableListOf<String>()
        override suspend fun speakEnglish(text: String) {
            said += text
        }
        override fun close() {}
    }

    private fun viewModel(files: MemoryFiles = MemoryFiles(), speaker: Silent = Silent()) =
        BlindfoldViewModel(Store(files), speaker = speaker, scope = CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun createsAVariantBySpeakingAndTrainsItBlind() {
        val files = MemoryFiles()
        val speaker = Silent()
        val vm = viewModel(files, speaker)
        assertEquals(Mode.LIBRARY, vm.mode)

        vm.newVariant()
        for (move in listOf("e4", "c5", "knight f3", "d6", "undo", "pawn d6")) vm.onSpokenEnglish(move)
        vm.updateDraft { it.copy(opening = "Sizilianisch", name = "Najdorf-Weg", playWhite = false) }
        vm.saveDraft()
        assertNull(vm.draft)
        assertTrue("Najdorf-Weg" in files.files.getValue("library.json"))
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6"), vm.library.variants.single().moves)

        // Playing Black: the line opens with White's e4, then it is our turn.
        vm.trainVariant(vm.library.variants.single().id)
        assertEquals(Mode.TRAIN, vm.mode)
        assertEquals("e2e4", vm.history.single().uci)
        vm.onSpokenEnglish("e5")
        assertEquals(1, vm.mistakes)
        vm.onSpokenEnglish("c5")
        vm.onSpokenEnglish("d6")
        assertTrue(vm.drillDone)
        assertTrue("Line complete" in speaker.said)
    }

    @Test
    fun editKeepsTheIdAndDeleteRemovesIt() {
        val files = MemoryFiles()
        val vm = viewModel(files)
        vm.newVariant()
        vm.onSpokenEnglish("d4")
        vm.updateDraft { it.copy(opening = "Damenbauer", name = "Test") }
        vm.saveDraft()
        val id = vm.library.variants.single().id

        vm.editVariant(id)
        vm.onSpokenEnglish("d5")
        vm.updateDraft { it.copy(name = "Test 2") }
        vm.saveDraft()
        val edited = vm.library.variants.single()
        assertEquals(id, edited.id)
        assertEquals("Test 2", edited.name)
        assertEquals(2, edited.moves.size)

        vm.deleteVariant(id)
        assertTrue(vm.library.variants.isEmpty())
    }

    @Test
    fun keepsWhatFireHorseSavedInTheMeantime() {
        val files = MemoryFiles()
        val vm = viewModel(files)
        // FireHorse writes a variant while FireHorseLight is open.
        files.files["library.json"] = """{"variants":[{"id":"fh","opening":"Französisch","name":"Winawer","moves":["e2e4","e7e6"]}]}"""
        vm.newVariant()
        vm.onSpokenEnglish("c4")
        vm.updateDraft { it.copy(name = "Englisch") }
        vm.saveDraft()
        assertEquals(setOf("Winawer", "Englisch"), vm.library.variants.map { it.name }.toSet())
    }

    @Test
    fun freePlayCanBeSavedAsAVariant() {
        val vm = viewModel()
        vm.switchMode(Mode.FREE)
        vm.onSpokenEnglish("e4")
        vm.onSpokenEnglish("e5")
        vm.saveFreeAsVariant()
        val draft = assertNotNull(vm.draft)
        assertEquals(2, draft.moves.size)
        assertEquals(Mode.LIBRARY, vm.mode)
    }
}
