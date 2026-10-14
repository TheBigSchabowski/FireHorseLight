package repertoire

import repertoire.core.Exchange
import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Progress
import repertoire.core.QuizBuilder
import repertoire.core.ReviewState
import repertoire.core.SavedPlaylist
import repertoire.core.Store
import repertoire.core.Variant
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class ArchiveMemory : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

class ArchiveTest {
    private val najdorf = Variant("n", "Sizilianisch", "Najdorf", listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6"), playWhite = false)
    private val dragon = Variant("d", "Sizilianisch", "Drache", listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "g7g6"), playWhite = false)
    private val caro = Variant("c", "Caro-Kann", "Hauptvariante", listOf("e2e4", "c7c6", "d2d4", "d7d5"), playWhite = false)
    private val learned = ReviewState(stability = 5.0, difficulty = 5.0, due = 0, lastReview = 0, reps = 3)

    private fun vm(): AppViewModel {
        val store = Store(ArchiveMemory())
        store.saveLibrary(
            Library(
                variants = listOf(najdorf, dragon, caro),
                pinned = listOf("Sizilianisch"),
                playlists = listOf(SavedPlaylist("p", "Alles", listOf("n", "d", "c"))),
            ),
        )
        store.saveProgress(Progress(cards = mapOf("n" to learned)))
        return AppViewModel(store)
    }

    @Test
    fun anArchivedOpeningIsOutOfTheWayButKeepsItsLearningState() {
        val vm = vm()
        assertEquals(3, vm.stats().total)
        vm.archiveOpening("Sizilianisch")
        assertEquals(listOf("Caro-Kann"), vm.library.groups().map { it.opening })
        assertEquals(listOf("Sizilianisch"), vm.library.archivedGroups().map { it.opening })
        // Not trained, not counted, not in a playlist, no longer pinned.
        assertEquals(1, vm.stats().total)
        assertEquals(listOf("c"), vm.library.variantsOf(vm.library.playlists.single()).map { it.id })
        assertTrue(vm.library.pinned.isEmpty())
        assertNotNull(vm.flash)
        // The learning state waits for it.
        assertEquals(3, vm.reviewOf("n")?.reps)

        vm.restoreOpening("Sizilianisch")
        assertEquals(3, vm.stats().total)
        assertEquals(3, vm.library.variantsOf(vm.library.playlists.single()).size)
        assertTrue(vm.library.archivedGroups().isEmpty())
    }

    @Test
    fun oneVariantCanGoWhileItsOpeningStays() {
        val vm = vm()
        vm.archiveVariant("d")
        assertEquals(listOf("n"), vm.library.groups().first { it.opening == "Sizilianisch" }.variants.map { it.id })
        assertEquals(listOf("d"), vm.library.archivedGroups().single().variants.map { it.id })
        assertEquals(listOf("Sizilianisch"), vm.library.pinned)
        vm.restoreVariant("d")
        assertTrue(vm.library.variants.none { it.archived })
    }

    @Test
    fun theOldRepertoireDoesNotCountInTheNewOne() {
        // A new line through the old Najdorf's positions: with Najdorf archived, its moves are no "other variant".
        val lib = Library(variants = listOf(najdorf.copy(archived = true), caro))
        val quizzes = QuizBuilder.build(lib.active)
        assertEquals(listOf("c"), quizzes.map { it.variant.id })
        assertFalse(najdorf.target.key in quizzes.single().knownKeys)
    }

    @Test
    fun selectedVariantsGoIntoTheArchiveAndDeletingThereDeletesOnlyThere() {
        val vm = vm()
        vm.startSelecting()
        vm.toggleSelected("n")
        vm.toggleSelected("c")
        vm.archiveSelected()
        assertEquals(null, vm.selected)
        assertEquals(listOf("d"), vm.library.active.map { it.id })
        vm.deleteOpening("Sizilianisch", archived = true)
        assertEquals(listOf("d", "c"), vm.library.variants.map { it.id })
        assertFalse(vm.progress.cards.containsKey("n"))
    }

    @Test
    fun editingAnArchivedVariantKeepsItInTheArchive() {
        val vm = vm()
        vm.archiveVariant("c")
        vm.editVariant(vm.library.variants.first { it.id == "c" })
        vm.editor!!.name = "Klassisch"
        vm.saveEditor()
        val c = vm.library.variants.first { it.id == "c" }
        assertEquals("Klassisch", c.name)
        assertTrue(c.archived)
    }

    @Test
    fun theArchiveComesAlongInAnExport() {
        val text = Exchange.export(listOf(najdorf.copy(archived = true), caro), Progress())
        val result = Exchange.import(text, Library(), Progress())
        assertEquals(mapOf("n" to true, "c" to false), result.library.variants.associate { it.id to it.archived })
    }

    @Test
    fun whatTheSearchShowsInTheArchiveCanBeBroughtBack() {
        val vm = vm()
        vm.archiveOpening("Sizilianisch")
        vm.archiveOpening("Caro-Kann")
        vm.search = "sizilianisch"
        vm.restoreVariants(vm.shownArchive.single().variants)
        assertEquals(listOf("Sizilianisch"), vm.library.groups().map { it.opening })
        assertEquals(listOf("Caro-Kann"), vm.library.archivedGroups().map { it.opening })
    }
}
