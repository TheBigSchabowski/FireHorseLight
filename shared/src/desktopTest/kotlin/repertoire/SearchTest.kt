package repertoire

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Store
import repertoire.core.Variant
import repertoire.core.VariantSearch
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SearchMemory : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

@OptIn(InternalComposeUiApi::class)
class SearchTest {
    private val najdorf = Variant("n", "Sizilianisch", "Najdorf", uci("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6"), playWhite = false)
    private val english = Variant("e", "Sizilianisch", "English Attack", uci("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1e3 e7e5 d4b3 c8e6 f2f3"), playWhite = true)
    private val french = Variant("f", "Französisch", "Vorstoß", uci("e2e4 e7e6 d2d4 d7d5 e4e5"), playWhite = true)
    private val qgd = Variant("q", "Damengambit", "Abgelehnt", uci("d2d4 d7d5 c2c4 e7e6 g1f3 g8f6"), playWhite = true)
    private val kid = Variant("k", "Königsindisch", "Klassisch", uci("d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3 e8g8"), playWhite = false)
    private val all = listOf(najdorf, english, french, qgd, kid)

    private fun uci(moves: String) = moves.split(" ")

    private fun find(query: String): List<String> = VariantSearch(query).let { s -> all.filter(s::matches).map { it.id } }

    @Test
    fun aMoveOnItsOwnIsTheFirstMove() {
        // d4 in the Sicilian and the French comes later: they are 1.e4 openings.
        assertEquals(listOf("q", "k"), find("d4"))
        assertEquals(listOf("n", "e", "f"), find("e4"))
        // A move for Black on its own: its first answer.
        assertEquals(listOf("n", "e"), find("c5"))
        assertEquals(listOf("f"), find("e6"))
    }

    @Test
    fun movesInARowFollowEachOther() {
        assertEquals(listOf("n", "e"), find("e4 c5"))
        assertEquals(listOf("f"), find("e4 e6 d4 d5 e5"))
        assertEquals(listOf("q"), find("1.d4 d5"))
        assertEquals(emptyList(), find("e4 d5"))
    }

    @Test
    fun aNumberPutsTheMoveInItsPlace() {
        assertEquals(listOf("e"), find("8.f3"))
        assertEquals(listOf("e"), find("8. f3"))
        assertEquals(listOf("n", "e"), find("5...a6"))
        assertEquals(emptyList(), find("5.a6"))
        assertEquals(listOf("f"), find("2... d5 3. e5"))
    }

    @Test
    fun theSideAndTheNames() {
        assertEquals(listOf("n", "k"), find("schwarz"))
        assertEquals(listOf("e", "f", "q"), find("Weiß"))
        assertEquals(listOf("e", "f", "q"), find("weiss"))
        assertEquals(listOf("n"), find("najdorf"))
        assertEquals(listOf("n", "e"), find("sizil"))
        assertEquals(listOf("k"), find("d4 schwarz"))
        assertEquals(listOf("e"), find("e4 weiß attack"))
    }

    @Test
    fun movesCanBeWrittenInManyWays() {
        assertEquals("Nf3", VariantSearch.normalize("Sf3"))
        assertEquals("Nf3", VariantSearch.normalize("nf3"))
        assertEquals("Nd4", VariantSearch.normalize("Sxd4+"))
        assertEquals("cd4", VariantSearch.normalize("cxd4"))
        assertEquals("Bc4", VariantSearch.normalize("Lc4"))
        assertEquals("bc4", VariantSearch.normalize("bc4"))
        assertEquals("O-O", VariantSearch.normalize("0-0"))
        assertEquals("e8Q", VariantSearch.normalize("e8=D"))
        assertNull(VariantSearch.normalize("Najdorf"))
        assertEquals(listOf("k"), find("5...O-O"))
        assertEquals(listOf("k"), find("5... 0-0"))
        assertEquals(listOf("n", "e"), find("e4 c5 Sf3"))
        assertEquals(listOf("n", "e"), find("4.Sxd4"))
    }

    @Test
    fun nothingTypedIsNoSearch() {
        assertFalse(VariantSearch("  ").active)
    }

    @Test
    fun theHomeScreenShowsWhatItFindsAndActsOnlyOnThat() {
        val store = Store(SearchMemory())
        store.saveLibrary(Library(variants = all.map { if (it.id == "k") it.copy(archived = true) else it }))
        val vm = AppViewModel(store)
        vm.search = "d4"
        assertEquals(listOf("Damengambit"), vm.shownGroups.map { it.opening })
        assertEquals(listOf("Königsindisch"), vm.shownArchive.map { it.opening })
        assertEquals(listOf("q"), vm.searchHits.map { it.id })
        vm.search = "sizilianisch"
        vm.startSelecting()
        vm.search = "sizilianisch najdorf"
        vm.toggleSelectedOpening("Sizilianisch")
        assertEquals(setOf("n"), vm.selected)
        vm.endSelecting()
        vm.search = "c5"
        vm.selectHits()
        assertEquals(setOf("n", "e"), vm.selected)
    }

    @Test
    fun ctrlBSHidesTheSearchWithItsSearchAndBringsItBack() {
        val store = Store(SearchMemory())
        store.saveLibrary(Library(variants = all))
        val vm = AppViewModel(store)
        assertTrue(vm.library.settings.showSearch)
        vm.search = "d4"
        vm.shortcutKeys.handle(KeyEvent(Key.B, KeyEventType.KeyDown, 2, isCtrlPressed = true))
        vm.shortcutKeys.handle(KeyEvent(Key.S, KeyEventType.KeyDown, 's'.code))
        assertFalse(vm.library.settings.showSearch)
        assertEquals("", vm.search)
        assertEquals(5, vm.searchHits.size)
        vm.toggleSearch()
        assertTrue(vm.library.settings.showSearch)
        assertEquals(1, vm.searchFocus)
        // In the editor it only says where the search is.
        vm.newVariant()
        vm.toggleSearch()
        assertTrue(vm.library.settings.showSearch)
        assertNotNull(vm.flash)
    }
}
