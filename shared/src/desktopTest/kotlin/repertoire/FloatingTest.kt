package repertoire

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import repertoire.core.FileStore
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel
import repertoire.ui.Command
import kotlin.test.Test
import kotlin.test.assertTrue

private class PanelFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

/** The database window in a whole app of 1200×820, dragged with the mouse. */
class FloatingTest {
    private fun assertClose(expected: Offset, actual: Offset) =
        assertTrue((expected - actual).getDistance() < 1f, "erwartet $expected, war $actual")

    private fun ImageComposeScene.drag(from: Offset, by: Offset, at: Long) {
        sendPointerEvent(PointerEventType.Press, from, timeMillis = at, type = PointerType.Mouse)
        render(at * 1_000_000)
        for (step in 1..10) {
            sendPointerEvent(PointerEventType.Move, from + by * (step / 10f), timeMillis = at + step * 16, type = PointerType.Mouse)
            render((at + step * 16) * 1_000_000)
        }
        sendPointerEvent(PointerEventType.Release, from + by, timeMillis = at + 200, type = PointerType.Mouse)
        render((at + 200) * 1_000_000)
    }

    @Test
    fun theDatabaseWindowIsDraggedByItsTitleAndStaysInTheWindow() {
        val vm = AppViewModel(Store(PanelFiles()))
        vm.newVariant()
        vm.run(Command.EXPLORER)
        ImageComposeScene(1200, 820, Density(1f)) { App(vm) }.use { scene ->
            scene.render()
            // Where exactly the title lies depends on the text in the window: feel for it from the top.
            val title = (300..800 step 6).map { Offset(880f, it.toFloat()) }.firstOrNull { point ->
                scene.drag(point, Offset(-200f, -100f), 1000)
                vm.explorerAt.value != Offset.Zero
            }
            assertTrue(title != null, "die Titelzeile lässt sich greifen")
            // Give or take the bit the pointer moves before it counts as a drag.
            assertClose(Offset(-200f, -100f), vm.explorerAt.value)

            // Far to the right and down it stops at its own corner; far up and left part of it stays in.
            scene.drag(title + Offset(-200f, -100f), Offset(900f, 900f), 3000)
            assertClose(Offset.Zero, vm.explorerAt.value)
            scene.drag(title, Offset(-5000f, -5000f), 5000)
            assertClose(Offset(-(1200f - 160f), -(820f - 160f)), vm.explorerAt.value)
        }
    }
}
