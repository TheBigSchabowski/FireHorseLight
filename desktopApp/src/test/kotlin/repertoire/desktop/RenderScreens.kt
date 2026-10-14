package repertoire.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import repertoire.core.DirectoryStore
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel
import repertoire.ui.BoardTheme
import repertoire.ui.Command
import repertoire.ui.HelpTopic
import repertoire.ui.KeyTarget
import java.io.File
import java.nio.file.Files

/**
 * Renders the main screens off-screen at phone and desktop size, without opening a window:
 *
 *   ./gradlew :desktopApp:renderScreens     → desktopApp/build/screens/
 */
fun main() {
    val out = File("build/screens").apply { mkdirs() }
    val sizes = mapOf("phone" to (393 to 852), "desktop" to (1200 to 820))

    fun render(
        name: String,
        waitMs: Long = 0,
        only: Map<String, Pair<Int, Int>> = sizes,
        files: repertoire.core.FilePicker? = null,
        setup: (AppViewModel) -> Unit,
    ) {
        for ((label, size) in only) {
            val dir = Files.createTempDirectory("puzzle").toFile()
            val vm = AppViewModel(Store(DirectoryStore(dir)), files).also(setup)
            val (w, h) = size
            ImageComposeScene(w * 2, h * 2, Density(2f)) { App(vm, touch = label == "phone") }.use { scene ->
                // Images load on the first frames; render a few before taking the picture.
                repeat(5) { scene.render(it * 20_000_000L) }
                // Something that runs outside (an engine) gets real time to answer.
                if (waitMs > 0) repeat(10) {
                    Thread.sleep(waitMs / 10)
                    scene.render(100_000_000L + it * 10_000_000L)
                }
                val png = scene.render(200_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "$name-$label.png").writeBytes(png)
            }
        }
    }

    fun AppViewModel.addVariant(opening: String, name: String, san: String) {
        newVariant()
        val e = editor!!
        e.opening = opening
        e.name = name
        e.playWhite = false
        var p = Position.START
        for (token in san.split(" ")) {
            val move: Move = p.parseSan(token)!!
            editorPlay(move)
            p = p.play(move)
        }
        saveEditor()
    }

    fun AppViewModel.sample() {
        addVariant("Sizilianisch", "English Attack", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3")
        addVariant("Sizilianisch", "Scheveningen", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e6")
    }

    render("home") { it.sample() }
    render("home-search") { vm ->
        vm.sample()
        vm.addVariant("Caro-Kann", "Vorstoß", "e4 c6 d4 d5 e5 Bf5")
        vm.addVariant("Königsindisch", "Klassisch", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O")
        vm.archiveOpening("Caro-Kann")
        vm.flash = null
        vm.search = "e4 schwarz"
    }
    render("home-archive") { vm ->
        vm.sample()
        vm.addVariant("Caro-Kann", "Vorstoß", "e4 c6 d4 d5 e5 Bf5")
        vm.archiveOpening("Sizilianisch")
        vm.flash = null
        vm.archiveOpen = true
    }
    render("training") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        var p = Position.START
        for (san in listOf("e4", "c5", "Nc3")) {
            val move = p.parseSan(san)!!
            vm.submitMove(move)
            p = vm.session!!.attempt!!.position
        }
    }
    render("editor") { vm ->
        vm.sample()
        vm.newVariant(vm.library.variants.first())
    }
    render("editor-durchgehen") { vm ->
        vm.sample()
        vm.editVariant(vm.library.variants.last(), fromStart = true)
        repeat(5) { vm.editorStepForward() }
    }
    // Blind: the board covered, the other side played and said, a move heard.
    fun AppViewModel.blind() {
        sample()
        toggleBlind()
        practice(library.variants.last().id)
        onSpoken("Knight f3.")
    }
    render("blind-ueben") { it.blind() }
    render("blind-aufgedeckt") { vm -> vm.blind(); vm.peek() }
    render("blind-gehoert-falsch") { vm -> vm.blind(); vm.onSpoken("banana") }
    render("blind-editor") { vm ->
        vm.sample()
        vm.toggleBlind()
        vm.newVariant()
        for (said in listOf("e4", "c5", "knight f3")) vm.onSpoken(said)
    }
    render("spracherkennung") { vm ->
        vm.openSpeechTest()
        val lab = vm.voiceLab!!
        lab.heard += listOf("E4.", "d4", "Go on to C5.", "a6", "B5.", "F3", "G6.", "H4.", "9-3")
        lab.index = lab.heard.size
    }
    render("stimmprobe") { vm -> vm.openVoiceSample() }
    render("hilfe-blind") { vm ->
        vm.sample()
        vm.openHelp(HelpTopic.BLIND)
    }
    render("single-move") { vm ->
        vm.sample()
        vm.practiceMoves(vm.library.variants.last().id)
    }
    render("einfuegen") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("[Opening \"Sizilianisch: Najdorf\"]\n\n1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 *")
    }
    render("einfuegen-fehler") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("1. e4 c5 2. Nf6")
        vm.applyPaste()
    }
    render("einfuegen-pgn") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("[Opening \"Sizilianisch: Najdorf\"]\n\n1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 *")
        vm.applyPaste()
        repeat(6) { vm.editorStepForward() }
    }
    render("einfuegen-fen") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7")
        vm.applyPaste()
    }
    render("datenbank") { vm ->
        vm.sample()
        vm.editVariant(vm.library.variants.first(), fromStart = true)
        repeat(4) { vm.editorStepForward() }
        vm.run(Command.EXPLORER)
    }
    render("durchgehen-nebenstrang") { vm ->
        vm.sample()
        vm.editVariant(vm.library.variants.first(), fromStart = true)
        repeat(5) { vm.editorStepForward() }
        for (san in listOf("Nc6", "Nc3", "g6")) vm.editorPlay(vm.editor!!.position.parseSan(san)!!)
    }
    render("einfuegen-alles") { vm ->
        vm.sample()
        vm.run(Command.PASTE_ALL)
    }
    // Only with an engine to show: FIREHORSE_TEST_ENGINE=/path/to/stockfish
    System.getenv("FIREHORSE_TEST_ENGINE")?.takeIf { it.isNotBlank() }?.let { engine ->
        render("engine", waitMs = 3000) { vm ->
            vm.sample()
            kotlinx.coroutines.runBlocking { vm.addEngine(engine) }
            vm.editVariant(vm.library.variants.first(), fromStart = true)
            repeat(6) { vm.editorStepForward() }
            vm.run(Command.ENGINE)
            vm.run(Command.EXPLORER)
        }
        // Tall, so the engines further down the configuration are in the picture.
        render("einstellungen-engines", only = mapOf("desktop" to (1200 to 2300))) { vm ->
            vm.sample()
            kotlinx.coroutines.runBlocking { vm.addEngine(engine) }
            vm.stepEngineDepth(engine, 6)
            vm.openKeys(null)
            vm.showKeys = false
        }
        // The engine as opponent: the dialog, then the engine playing White after a few moves by hand.
        render("gegner-dialog", only = mapOf("desktop" to (1200 to 820))) { vm ->
            kotlinx.coroutines.runBlocking { vm.addEngine(engine) }
            vm.newVariant()
            for (m in listOf("e2e4", "c7c5", "g1f3", "d7d6")) vm.editorPlay(Move.fromUci(m))
            vm.openOpponent()
        }
        render("gegner-spielt", waitMs = 4000, only = mapOf("desktop" to (1200 to 820))) { vm ->
            kotlinx.coroutines.runBlocking { vm.addEngine(engine) }
            vm.newVariant()
            for (m in listOf("e2e4", "c7c5", "g1f3", "d7d6")) vm.editorPlay(Move.fromUci(m))
            vm.setOpponent(repertoire.ui.EngineOpponent(white = true, depth = 14))
        }
    }
    render("kuerzel") { vm ->
        vm.sample()
        vm.run(Command.SHORTCUTS)
    }
    render("praefix") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        vm.prefix = true
    }
    render("puzzle-neue-variante") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        vm.run(Command.NEW_VARIANT)
    }
    render("einstellungen") { vm ->
        vm.sample()
        vm.openConfig()
    }
    // A file opened for importing: add it, or replace everything with it.
    val exported = object : repertoire.core.FilePicker {
        override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
        override fun open(onDone: (String?) -> Unit) = onDone(
            """{"format": "eroeffnungs-puzzle", "variants": [{"name": "Alapin", "opening": "Sizilianisch", "moves": "e4 c5 c3"}]}""",
        )
    }
    render("import-auswahl", files = exported) { vm ->
        vm.sample()
        vm.openConfig()
        vm.import()
    }
    render("abspielen") { vm ->
        vm.sample()
        vm.setBoardTheme(BoardTheme.GREEN)
        vm.editVariant(vm.library.variants.last(), play = true)
        repeat(7) { vm.editorPlayTick() }
    }
    render("einstellungen-kacheln") { vm ->
        vm.sample()
        vm.setTapGrid(1, 2)
        vm.setHorseLeft(true)
        vm.openConfig()
    }
    render("einstellungen-tasten") { vm ->
        vm.sample()
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'g')
        vm.openConfig()
        vm.keyCapture = KeyTarget.Of(Command.PASTE_FEN)
    }
    render("freie-taste") { vm ->
        vm.sample()
        vm.openKeys('z')
    }
    render("hilfe") { vm ->
        vm.sample()
        vm.openHelp()
    }
    render("hilfe-pferd") { vm ->
        vm.sample()
        vm.openConfig()
        vm.openHelp(HelpTopic.HORSE)
    }
    render("hilfe-doppeltippen") { vm ->
        vm.sample()
        vm.openHelp(HelpTopic.TAP_ZONES)
    }
    fun AppViewModel.twoOpenings() {
        sample()
        addVariant("Four Knights", "Scotch Game Bb4 Main line", "e4 e5 Nf3 Nc6 Nc3 Nf6 d4 exd4 Nxd4 Bb4")
        addVariant("Four Knights", "Scotch Game Nxe5", "e4 e5 Nf3 Nc6 Nc3 Nf6 d4 Nxd4")
    }
    render("angepinnt") { vm ->
        vm.twoOpenings()
        vm.togglePin("Four Knights")
    }
    render("playlist") { vm ->
        vm.twoOpenings()
        vm.playPlaylist("Four Knights")
        repeat(11 + 3) { vm.editorPlayTick() }
    }
    render("playlist-ueben") { vm ->
        vm.twoOpenings()
        vm.learnPlaylist("Four Knights")
    }
    render("auswahl") { vm ->
        vm.twoOpenings()
        vm.togglePin("Four Knights")
        vm.startSelecting()
        vm.toggleSelectedOpening("Four Knights")
        vm.toggleSelected(vm.library.variants.first().id)
    }
    fun AppViewModel.ownPlaylist() {
        twoOpenings()
        newPlaylist()
        toggleSelected(library.variants.first { it.name == "Scotch Game Nxe5" }.id)
        toggleSelected(library.variants.first { it.name == "Scheveningen" }.id)
        saveSelectionAsPlaylist()
        naming!!.text = "Four Knights + Scheveningen"
        confirmNaming()
    }
    render("meine-playlists") { it.ownPlaylist() }
    render("playlist-name") { vm ->
        vm.twoOpenings()
        vm.newPlaylist()
        vm.toggleSelected(vm.library.variants.first().id)
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "Four Knights + Scheveningen"
    }
    render("playlist-varianten-aendern") { vm ->
        vm.ownPlaylist()
        vm.changePlaylistVariants(vm.library.playlists.single().id)
    }
    render("playlist-lernen-fehler") { vm ->
        vm.twoOpenings()
        vm.learnPlaylist("Four Knights")
        val v = vm.session!!.attempt!!.quiz.variant
        vm.submitMove(v.start.parseSan("a3")!!)
        while (!vm.session!!.attempt!!.finished) vm.submitMove(Move.fromUci(v.moves[vm.session!!.attempt!!.played.size]))
    }
    render("hilfe-playlist") { vm ->
        vm.sample()
        vm.openHelp(HelpTopic.PLAYLIST)
    }
    println("Screens in ${out.absolutePath}")
    // Compose leaves the AWT event thread running, which would keep the program alive.
    kotlin.system.exitProcess(0)
}
