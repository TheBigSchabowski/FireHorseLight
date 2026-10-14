package repertoire.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.UserDirs
import repertoire.ui.BlindfoldScreen
import repertoire.ui.BlindfoldViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val vm = remember {
        val dir = System.getProperty("repertoire.dataDir")?.let(::File)
            ?: File(UserDirs.documents(), "EroeffnungsPuzzle")
        BlindfoldViewModel(Store(DirectoryStore(dir)), DesktopJsonPicker)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "FireHorse Light",
        state = rememberWindowState(width = 960.dp, height = 720.dp),
    ) {
        BlindfoldScreen(vm)
    }
}

private object DesktopJsonPicker : FilePicker {
    override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
    override fun open(onDone: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "FireHorse JSON importieren", FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val file = dialog.file?.let { File(dialog.directory, it) }
        onDone(file?.readText())
    }
}
