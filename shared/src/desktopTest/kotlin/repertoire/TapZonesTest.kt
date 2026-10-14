package repertoire

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import repertoire.core.FileStore
import repertoire.core.Settings
import repertoire.core.Store
import repertoire.ui.AppViewModel
import repertoire.ui.Command
import repertoire.ui.HelpTopic
import repertoire.ui.Prefs
import repertoire.ui.TapZone
import repertoire.ui.commandAt
import repertoire.ui.doubleTapZones
import repertoire.ui.label
import repertoire.ui.withGrid
import repertoire.ui.withZone
import repertoire.ui.zoneOf
import repertoire.ui.zones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class Files : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

@OptIn(ExperimentalComposeUiApi::class)
class TapZonesTest {
    @Test
    fun theScreenIsSixZonesReadLikeABook() {
        val size = IntSize(400, 900)
        assertEquals(TapZone(0, 0), TapZone.at(Offset(10f, 10f), size))
        assertEquals(TapZone(1, 0), TapZone.at(Offset(390f, 299f), size))
        assertEquals(TapZone(0, 1), TapZone.at(Offset(199f, 450f), size))
        assertEquals(TapZone(1, 1), TapZone.at(Offset(200f, 301f), size))
        assertEquals(TapZone(0, 2), TapZone.at(Offset(0f, 899f), size))
        assertEquals(TapZone(1, 2), TapZone.at(Offset(400f, 900f), size))
    }

    @Test
    fun theGridCanBeResized() {
        val size = IntSize(400, 900)
        assertEquals(TapZone(0, 1), TapZone.at(Offset(300f, 600f), size, cols = 1, rows = 2))
        val grid = Settings().withGrid(1, 2)
        assertEquals(Command.SHORTCUTS, grid.commandAt(TapZone(0, 0)))
        assertEquals(Command.NEW_VARIANT, grid.commandAt(TapZone(0, 1)))
        assertEquals(listOf("oben", "unten"), grid.zones.map { it.label(1, 2) })
        assertNull(grid.commandAt(TapZone(1, 0)))
        assertEquals(1 * 2, grid.tapZones.size)
    }

    @Test
    fun eachZoneCanBeGivenAShortcut() {
        val default = Settings()
        assertEquals(Command.SHORTCUTS, default.commandAt(TapZone(0, 0)))
        assertEquals(Command.PASTE_FEN, default.commandAt(TapZone(1, 2)))
        val changed = default.withZone(TapZone(0, 0), null).withZone(TapZone(1, 1), Command.PASTE_PGN)
        assertNull(changed.commandAt(TapZone(0, 0)))
        assertEquals(TapZone(1, 1), changed.zoneOf(Command.PASTE_PGN))
        assertNull(changed.zoneOf(Command.SETTINGS))
        assertNull(changed.copy(tapZonesOn = false).zoneOf(Command.PASTE_PGN))
    }

    @Test
    fun zonesAreSavedWithTheLibrary() {
        val files = Files()
        val vm = AppViewModel(Store(files))
        vm.setTapZone(TapZone(0, 0), Command.PASTE_FEN)
        vm.setTapZonesOn(false)
        vm.setHoldStep(1500)
        val settings = AppViewModel(Store(files)).library.settings
        assertEquals(Command.PASTE_FEN, settings.commandAt(TapZone(0, 0)))
        assertEquals(false, settings.tapZonesOn)
        assertEquals(1500, settings.holdStepMs)
    }

    @Test
    fun aDoubleTapRunsTheZonesShortcut() {
        val vm = AppViewModel(Store(Files()))
        vm.tapZone(TapZone(1, 1))
        assertEquals(Prefs.Config, vm.prefs)
        // Another shortcut closes the preferences, so it happens where it can be seen.
        vm.tapZone(TapZone(0, 2))
        assertNull(vm.prefs)
        assertEquals(Command.PASTE_PGN, vm.paste?.kind)
    }

    @Test
    fun helpGoesBackToWhereItWasOpened() {
        val vm = AppViewModel(Store(Files()))
        vm.openConfig()
        vm.openHelp(HelpTopic.HORSE)
        vm.prefsBack()
        assertEquals(Prefs.Config, vm.prefs)
        vm.openHelp()
        vm.openHelp(HelpTopic.DATA)
        vm.prefsBack()
        assertEquals(Prefs.Help(), vm.prefs)
        vm.prefsBack()
        assertNull(vm.prefs)
        vm.run(Command.SETTINGS)
        assertEquals(Prefs.Config, vm.prefs)
        vm.run(Command.SETTINGS)
        assertNull(vm.prefs)
    }

    /** A 300×600 screen with the zones and a 100×100 button in the top left corner. */
    private fun scene(block: (tap: (Float, Float, Long) -> Unit, zones: List<TapZone>, clicks: () -> Int) -> Unit) {
        val zones = mutableListOf<TapZone>()
        var clicks = 0
        ImageComposeScene(300, 600, Density(1f)) {
            Box(Modifier.fillMaxSize().doubleTapZones(2, 3) { zones += it }) {
                Box(Modifier.size(100.dp).clickable { clicks++ })
            }
        }.use { scene ->
            scene.render()
            val tap: (Float, Float, Long) -> Unit = { x, y, at ->
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = at, type = PointerType.Touch)
                scene.render(at * 1_000_000)
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), timeMillis = at + 40, type = PointerType.Touch)
                scene.render((at + 40) * 1_000_000)
                Unit
            }
            block(tap, zones, { clicks })
        }
    }

    @Test
    fun twoQuickTapsOnAFreeSpotCount() = scene { tap, zones, _ ->
        tap(250f, 550f, 1000)
        tap(252f, 548f, 1150)
        assertEquals(listOf(TapZone(1, 2)), zones)
    }

    @Test
    fun tapsOnAButtonAreTheButtons() = scene { tap, zones, clicks ->
        tap(50f, 50f, 1000)
        tap(50f, 50f, 1150)
        assertEquals(2, clicks())
        assertEquals(emptyList(), zones)
    }

    @Test
    fun slowOrScatteredTapsDoNothing() = scene { tap, zones, _ ->
        tap(250f, 550f, 1000)
        tap(250f, 550f, 2000)
        tap(250f, 150f, 2150)
        assertEquals(emptyList(), zones)
    }
}
