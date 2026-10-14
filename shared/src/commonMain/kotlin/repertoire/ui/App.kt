package repertoire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier

/**
 * The whole app, shared by the desktop window and the Android activity. [touch] shows the horse
 * button for the shortcuts and turns on the double-tap zones; the keyboard reaches them with Ctrl+B.
 */
@Composable
fun App(vm: AppViewModel, modifier: Modifier = Modifier, touch: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    SideEffect {
        vm.copyToClipboard = { clipboard.setText(AnnotatedString(it)) }
        vm.uiScope = scope
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(
            LocalTouch provides touch,
            LocalBoardTheme provides BoardTheme.named(vm.library.settings.boardTheme),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                // Double taps on a free spot run the shortcut of that part of the screen.
                val zones = if (touch && vm.library.settings.tapZonesOn) Modifier.doubleTapZones(vm.library.settings.tapCols, vm.library.settings.tapRows, vm::tapZone) else Modifier
                Box(modifier.fillMaxSize().then(zones)) {
                    val session = vm.session
                    val editor = vm.editor
                    val prefs = vm.prefs
                    val lab = vm.voiceLab
                    vm.paste?.let { PasteDialog(vm, it) }
                    vm.naming?.let { PlaylistNameDialog(vm, it) }
                    if (vm.opponentDialog) OpponentDialog(vm)
                    when {
                        // The speech test and the voice sample take the whole screen; Space records there.
                        lab != null -> VoiceLabScreen(vm, lab)
                        // Preferences and help go on top of everything, which is still there afterwards.
                        prefs != null -> PreferencesScreen(vm, prefs)
                        // A variant started from inside a puzzle (Ctrl+B c) goes on top of it.
                        editor != null -> EditorScreen(vm, editor)
                        session != null -> TrainingScreen(vm, session)
                        else -> HomeScreen(vm)
                    }
                    // Beside the board rather than over the whole screen, so moves can still be made.
                    if ((vm.explorerOpen || vm.engineOpen) && prefs == null) FloatingPanels(vm, touch)
                    ShortcutOverlay(vm)
                }
            }
        }
    }
}
