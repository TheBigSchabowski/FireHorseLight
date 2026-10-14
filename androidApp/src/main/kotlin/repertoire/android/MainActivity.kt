package repertoire.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.appContext
import repertoire.ui.BlindfoldScreen
import repertoire.ui.BlindfoldViewModel

class MainActivity : ComponentActivity() {
    private var pendingOpen: ((String?) -> Unit)? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingOpen ?: return@registerForActivityResult
        pendingOpen = null
        cb(uri?.let { contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } })
    }

    private val picker = object : FilePicker {
        override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
        override fun open(onDone: (String?) -> Unit) {
            pendingOpen = onDone
            openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        enableEdgeToEdge()
        val vm = BlindfoldViewModel(Store(DirectoryStore(filesDir)), picker)
        setContent {
            BlindfoldScreen(vm, Modifier.safeDrawingPadding())
        }
    }
}
