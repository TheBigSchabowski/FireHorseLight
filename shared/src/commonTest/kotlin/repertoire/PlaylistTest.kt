package repertoire

import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Move
import repertoire.core.Store
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

class PlaylistTest {
    private val files = InMemoryFiles()

    private fun app() = AppViewModel(Store(files))

    private fun AppViewModel.add(opening: String, name: String, san: String) {
        newVariant()
        val e = editor!!
        e.opening = opening
        e.name = name
        for (token in san.split(" ")) editorPlay(e.position.parseSan(token)!!)
        saveEditor()
    }

    /** Four Knights with three variants of 2, 3 and 4 moves, and Sicilian with one. */
    private fun sample(): AppViewModel = app().apply {
        add("Sizilianisch", "Najdorf", "e4 c5")
        add("Four Knights", "Eins", "e4 e5")
        add("Four Knights", "Zwei", "e4 e5 Nf3")
        add("Four Knights", "Drei", "e4 e5 Nf3 Nc6")
    }

    private fun AppViewModel.openings() = library.groups().map { it.opening }

    @Test
    fun pinnedOpeningsComeFirstInTheOrderTheyWerePinned() {
        val vm = sample()
        vm.add("Caro-Kann", "Vorstoß", "e4 c6")
        assertEquals(listOf("Caro-Kann", "Four Knights", "Sizilianisch"), vm.openings())
        vm.togglePin("Sizilianisch")
        vm.togglePin("Four Knights")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Caro-Kann"), vm.openings())
        assertEquals(listOf(true, true, false), vm.library.groups().map { it.pinned })
        // Saved with the library, so the pins are still there next time.
        assertEquals(listOf("Sizilianisch", "Four Knights"), app().library.pinned)
        vm.togglePin("Sizilianisch")
        assertEquals(listOf("Four Knights", "Caro-Kann", "Sizilianisch"), vm.openings())
    }

    @Test
    fun atMostThreeOpeningsArePinned() {
        val vm = sample()
        vm.add("Caro-Kann", "Vorstoß", "e4 c6")
        vm.add("Französisch", "Vorstoß", "e4 e6")
        for (o in listOf("Sizilianisch", "Four Knights", "Caro-Kann")) vm.togglePin(o)
        vm.togglePin("Französisch")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Caro-Kann"), vm.library.pinned)
        assertNotNull(vm.flash)
        // A pinned opening without variants any more does not take a place.
        vm.deleteVariant(vm.library.variants.first { it.opening == "Caro-Kann" }.id)
        vm.togglePin("Französisch")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Französisch"), vm.library.pinned)
    }

    @Test
    fun renamingThePinnedOpeningKeepsItPinned() {
        val vm = sample()
        vm.togglePin("Sizilianisch")
        vm.editVariant(vm.library.variants.first { it.opening == "Sizilianisch" })
        vm.editor!!.opening = "Sizilianische Verteidigung"
        vm.saveEditor()
        assertEquals(listOf("Sizilianische Verteidigung"), vm.library.pinned)
        assertTrue(vm.library.groups().first().pinned)
    }

    @Test
    fun thePlaylistPlaysEveryVariantOfTheOpeningInTurn() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        assertEquals("Eins", vm.editor!!.name)
        assertEquals(0, vm.editor!!.cursor)
        assertTrue(vm.editor!!.playing)
        repeat(2) { vm.editorPlayTick() }
        assertTrue(vm.editor!!.atEnd)
        // At the end of a line comes the next variant, from its start, still playing.
        vm.editorPlayTick()
        assertEquals("Zwei", vm.editor!!.name)
        assertEquals(1, vm.editor!!.playlist!!.index)
        assertEquals(0, vm.editor!!.cursor)
        assertTrue(vm.editor!!.playing)
        repeat(4) { vm.editorPlayTick() }
        assertEquals("Drei", vm.editor!!.name)
        // After the last one it stops, nothing was saved or changed.
        repeat(5) { vm.editorPlayTick() }
        assertEquals("Drei", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        assertEquals(4, vm.library.variants.size)
        // Play once more: from the first variant again.
        vm.editorTogglePlay()
        assertEquals("Eins", vm.editor!!.name)
        assertTrue(vm.editor!!.playing)
    }

    @Test
    fun withRepeatThePlaylistStartsOverAfterTheLastVariant() {
        val vm = sample()
        vm.setPlayLoop(true)
        vm.playPlaylist("Four Knights")
        repeat(3 + 4 + 5) { vm.editorPlayTick() }
        assertEquals("Eins", vm.editor!!.name)
        assertTrue(vm.editor!!.playing)
    }

    @Test
    fun stepsBetweenVariantsKeepThePlayerAsItWas() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        vm.editorTogglePlay()
        vm.playlistStep(1)
        assertEquals("Zwei", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        vm.playlistStep(1)
        vm.playlistStep(1)
        assertEquals("Drei", vm.editor!!.name)
        vm.playlistStep(-2)
        assertEquals("Eins", vm.editor!!.name)
    }

    @Test
    fun changesStopThePlaylistInsteadOfGettingLost() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        vm.editor!!.name = "Eins, umbenannt"
        repeat(3) { vm.editorPlayTick() }
        assertEquals("Eins, umbenannt", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        vm.playlistStep(1)
        assertEquals("Eins, umbenannt", vm.editor!!.name)
        assertNotNull(vm.flash)
    }

    @Test
    fun learningThePlaylistAsksEveryVariantInOrderWithoutGrading() {
        val vm = sample()
        vm.learnPlaylist("Four Knights")
        val s = vm.session!!
        assertFalse(s.graded)
        val names = mutableListOf<String>()
        while (s.attempt != null) {
            val attempt = s.attempt!!
            names += attempt.quiz.variant.name
            for (uci in attempt.quiz.variant.moves) vm.submitMove(Move.fromUci(uci))
            vm.grade()
        }
        assertEquals(listOf("Eins", "Zwei", "Drei"), names)
        assertTrue(vm.progress.cards.isEmpty())
        assertNull(vm.editor)
    }

    @Test
    fun theOpeningWithoutANameIsAGroupOfItsOwn() {
        val vm = app()
        vm.add("", "Irgendwas", "d4 d5")
        assertEquals(listOf(Library.NO_OPENING), vm.openings())
        vm.learnPlaylist(Library.NO_OPENING)
        assertEquals("Irgendwas", vm.session!!.attempt!!.quiz.variant.name)
    }

    @Test
    fun deletingAnOpeningDeletesAllItsVariantsAndItsPin() {
        val vm = sample()
        vm.togglePin("Four Knights")
        // Every variant gets a learning state, here by pausing it.
        vm.startLearning()
        repeat(4) { vm.suspendCurrent() }
        assertEquals(4, vm.progress.cards.size)
        vm.deleteOpening("Four Knights")
        assertEquals(listOf("Sizilianisch"), vm.openings())
        assertEquals(listOf("Najdorf"), vm.library.variants.map { it.name })
        assertTrue(vm.library.pinned.isEmpty())
        assertEquals(vm.library.variants.map { it.id }.toSet(), vm.progress.cards.keys)
    }

    @Test
    fun selectedVariantsAreDeletedTogether() {
        val vm = sample()
        vm.startSelecting()
        assertEquals(emptySet(), vm.selected)
        val ids = vm.library.variants.filter { it.name in setOf("Najdorf", "Zwei") }.map { it.id }
        ids.forEach(vm::toggleSelected)
        vm.deleteSelected()
        assertEquals(listOf("Eins", "Drei"), vm.library.variants.map { it.name })
        // Still selecting, with nothing ticked.
        assertEquals(emptySet(), vm.selected)
        vm.endSelecting()
        assertNull(vm.selected)
    }

    @Test
    fun aWholeOpeningIsTickedAndUntickedAtOnce() {
        val vm = sample()
        vm.startSelecting()
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(3, vm.selected!!.size)
        vm.toggleSelected(vm.library.variants.first { it.name == "Zwei" }.id)
        // Not all of them ticked: ticks the rest.
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(3, vm.selected!!.size)
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(emptySet(), vm.selected)
    }

    @Test
    fun theSelectionPlaysInTheOrderOfTheList() {
        val vm = sample()
        vm.togglePin("Sizilianisch")
        vm.startSelecting()
        // Ticked in another order than they are shown: pinned Sicilian first, then Four Knights.
        for (name in listOf("Drei", "Najdorf", "Eins")) vm.toggleSelected(vm.library.variants.first { it.name == name }.id)
        vm.playSelected()
        val p = vm.editor!!.playlist!!
        assertEquals("Auswahl", p.title)
        assertEquals(listOf("Najdorf", "Eins", "Drei"), p.ids.map { id -> vm.library.variants.first { it.id == id }.name })
        vm.cancelEditor()
        vm.learnSelected()
        assertEquals("Najdorf", vm.session!!.attempt!!.quiz.variant.name)
        assertEquals(2, vm.session!!.queue.size)
    }

    private fun AppViewModel.id(name: String) = library.variants.first { it.name == name }.id

    /** Solves the current puzzle; with [wrongFirst] the first move is a wrong one, which the app corrects. */
    private fun AppViewModel.solve(wrongFirst: Boolean = false) {
        val v = session!!.attempt!!.quiz.variant
        if (wrongFirst) submitMove(v.start.parseSan("a3")!!)
        while (!session!!.attempt!!.finished) {
            val attempt = session!!.attempt!!
            submitMove(Move.fromUci(v.moves[attempt.played.size]))
        }
        grade()
    }

    @Test
    fun learningAPlaylistGoesOnOnlyAfterACleanSolve() {
        val vm = sample()
        vm.learnPlaylist("Four Knights")
        val s = vm.session!!
        assertTrue(s.untilClean)
        assertEquals(3, s.total)
        assertEquals("Eins", s.attempt!!.quiz.variant.name)
        vm.solve(wrongFirst = true)
        // A mistake: the same puzzle again, right away.
        assertEquals("Eins", s.attempt!!.quiz.variant.name)
        assertEquals(0, s.solved)
        vm.solve()
        assertEquals("Zwei", s.attempt!!.quiz.variant.name)
        assertEquals(1, s.solved)
        vm.solve()
        vm.solve(wrongFirst = true)
        vm.solve()
        assertNull(s.attempt)
        assertEquals(3, s.solved)
        assertTrue(vm.progress.cards.isEmpty())
    }

    @Test
    fun aPlaylistOfOnesOwnMixesOpenings() {
        val vm = sample()
        vm.newPlaylist()
        vm.toggleSelected(vm.id("Drei"))
        vm.toggleSelected(vm.id("Najdorf"))
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "  Gemischt  "
        vm.confirmNaming()
        assertNull(vm.naming)
        assertNull(vm.selected)
        val p = vm.library.playlists.single()
        assertEquals("Gemischt", p.name)
        // In the order of the home screen: Four Knights before Sizilianisch.
        assertEquals(listOf("Drei", "Najdorf"), vm.library.variantsOf(p).map { it.name })
        // Saved with the library.
        assertEquals(listOf("Gemischt"), app().library.playlists.map { it.name })

        vm.movePlaylistVariant(p.id, 1, -1)
        assertEquals(listOf("Najdorf", "Drei"), vm.library.variantsOf(vm.library.playlists.single()).map { it.name })

        vm.playSavedPlaylist(p.id)
        assertEquals("Gemischt", vm.editor!!.playlist!!.title)
        assertEquals("Najdorf", vm.editor!!.name)
        vm.cancelEditor()
        vm.learnSavedPlaylist(p.id)
        assertEquals("Najdorf", vm.session!!.attempt!!.quiz.variant.name)
        assertTrue(vm.session!!.untilClean)
    }

    @Test
    fun aBlankNameSavesNothing() {
        val vm = sample()
        vm.newPlaylist()
        vm.saveSelectionAsPlaylist()
        // Nothing ticked yet: no dialog.
        assertNull(vm.naming)
        vm.toggleSelected(vm.id("Eins"))
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "   "
        vm.confirmNaming()
        assertNotNull(vm.naming)
        assertTrue(vm.library.playlists.isEmpty())
        vm.cancelNaming()
        assertNotNull(vm.selected)
    }

    @Test
    fun changingThePlaylistsVariantsKeepsItsOrder() {
        val vm = sample()
        vm.newPlaylist()
        listOf("Eins", "Zwei", "Drei").forEach { vm.toggleSelected(vm.id(it)) }
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "FK"
        vm.confirmNaming()
        val id = vm.library.playlists.single().id
        vm.movePlaylistVariant(id, 2, -2)

        vm.changePlaylistVariants(id)
        assertEquals(id, vm.choosingFor)
        assertEquals(3, vm.selected!!.size)
        vm.toggleSelected(vm.id("Zwei"))
        vm.toggleSelected(vm.id("Najdorf"))
        vm.applyPlaylistVariants()
        assertNull(vm.selected)
        assertNull(vm.choosingFor)
        assertEquals(listOf("Drei", "Eins", "Najdorf"), vm.library.variantsOf(vm.library.playlists.single()).map { it.name })

        vm.renamePlaylist(id)
        assertEquals("FK", vm.naming!!.text)
        vm.naming!!.text = "Four Knights + Najdorf"
        vm.confirmNaming()
        assertEquals("Four Knights + Najdorf", vm.library.playlists.single().name)
    }

    @Test
    fun deletedVariantsLeaveThePlaylistsAndDeletingAPlaylistKeepsTheVariants() {
        val vm = sample()
        vm.newPlaylist()
        listOf("Eins", "Najdorf").forEach { vm.toggleSelected(vm.id(it)) }
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "P"
        vm.confirmNaming()
        vm.deleteOpening("Sizilianisch")
        assertEquals(listOf(vm.id("Eins")), vm.library.playlists.single().variantIds)
        vm.deletePlaylist(vm.library.playlists.single().id)
        assertTrue(vm.library.playlists.isEmpty())
        assertEquals(3, vm.library.variants.size)
    }
}
