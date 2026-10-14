package repertoire.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wide windows: board on the left, [top] and [panel] in a column on the right.
 * Phones: [top] above the board and [panel] below it, all scrolling together. The board gets an
 * explicit square size there; inside a scrolling column it would otherwise have no height at all.
 */
@Composable
fun BoardWithPanel(
    board: @Composable (Modifier) -> Unit,
    top: @Composable ColumnScope.() -> Unit,
    panel: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                board(Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(20.dp))
                Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    top()
                    panel()
                    HorseClearance()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                top()
                Spacer(Modifier.height(12.dp))
                board(Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(12.dp))
                panel()
                HorseClearance()
            }
        }
    }
}
