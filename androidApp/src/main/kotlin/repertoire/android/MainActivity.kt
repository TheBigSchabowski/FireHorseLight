package repertoire.android

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.appContext
import repertoire.ui.App
import repertoire.ui.AppViewModel

class MainActivity : ComponentActivity() {
    // Survives rotation only as long as the process does; the progress itself is saved after every answer.
    private val viewModel by lazy { AppViewModel(Store(DirectoryStore(filesDir)), picker) }

    private var pendingSave: Pair<String, (String?) -> Unit>? = null
    private var pendingOpen: ((String?) -> Unit)? = null

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val (text, onDone) = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        if (uri == null) return@registerForActivityResult onDone(null)
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        onDone(displayName(uri))
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onDone = pendingOpen ?: return@registerForActivityResult
        pendingOpen = null
        onDone(uri?.let { contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } })
    }

    private val picker = object : FilePicker {
        override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) {
            pendingSave = text to onDone
            createDocument.launch(suggestedName)
        }

        override fun open(onDone: (String?) -> Unit) {
            pendingOpen = onDone
            // Some file managers label .json as octet-stream or text.
            openDocument.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Datei"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the view model, whose speaker needs it.
        appContext = applicationContext
        enableEdgeToEdge()
        setContent {
            // Back leaves the preferences, the editor (which may sit on top of a puzzle), then the puzzle,
            // instead of closing the app.
            BackHandler(enabled = viewModel.voiceLab != null || viewModel.prefs != null || viewModel.session != null || viewModel.editor != null) {
                when {
                    viewModel.voiceLab != null -> viewModel.closeVoiceLab()
                    viewModel.prefs != null -> viewModel.prefsBack()
                    viewModel.editor != null -> viewModel.cancelEditor()
                    else -> viewModel.endSession()
                }
            }
            App(viewModel, Modifier.safeDrawingPadding(), touch = true)
        }
    }
}
