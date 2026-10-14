package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Explorer
import repertoire.core.ExplorerResult
import repertoire.core.ExplorerSource
import repertoire.core.Library
import repertoire.core.Position

private sealed interface Lookup {
    data object Loading : Lookup
    data class Found(val result: ExplorerResult) : Lookup
    data class Failed(val message: String) : Lookup
}

/**
 * lichess's opening database for the position on the board (Ctrl+B l), in a window at the side
 * that follows the board: every move played there, how often, and how the games ended. A click on
 * a move plays it in the editor.
 */
@Composable
fun ExplorerPanel(vm: AppViewModel, modifier: Modifier = Modifier, handle: Modifier = Modifier) {
    Surface(
        modifier.width(340.dp).heightIn(max = 520.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            // The title is the handle to drag the window by.
            Row(handle, verticalAlignment = Alignment.CenterVertically) {
                Text("⠿ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("lichess-Datenbank", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.explorerOpen = false }) { Text("✕") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (source in ExplorerSource.entries) {
                    FilterChip(
                        selected = vm.explorerSource == source,
                        onClick = { vm.explorerSource = source },
                        label = { Text(source.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val token = vm.library.settings.lichessToken
            val position = vm.boardPosition
            when {
                token.isBlank() -> {
                    Hint(
                        "lichess gibt die Datenbank nur mit einem persönlichen API-Schlüssel heraus. Den machst du auf " +
                            "${Explorer.TOKEN_PAGE} (kein Häkchen nötig) und trägst ihn unter Konfiguration → lichess ein.",
                    )
                    TextButton(onClick = vm::openConfig) { Text("Schlüssel eintragen") }
                }
                position == null -> Hint("Die Datenbank zeigt die Stellung auf dem Brett – öffne eine Variante oder ein Puzzle.")
                else -> ExplorerTable(vm, vm.explorerSource, position)
            }
        }
    }
}

@Composable
private fun ExplorerTable(vm: AppViewModel, source: ExplorerSource, position: Position) {
    val fen = position.fen
    // Per position: what was found for the last one is never shown for this one, not even for a moment.
    var lookup by remember(source, fen) { mutableStateOf<Lookup>(Lookup.Loading) }
    LaunchedEffect(source, fen) {
        lookup = Lookup.Loading
        lookup = try {
            Lookup.Found(vm.explore(source, fen))
        } catch (e: IllegalStateException) {
            Lookup.Failed(e.message ?: "Das hat nicht geklappt.")
        }
    }
    when (val l = lookup) {
        Lookup.Loading -> Hint("Frage lichess …")
        is Lookup.Failed -> Text(l.message, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
        is Lookup.Found -> {
            val r = l.result
            r.opening?.takeIf { it.name.isNotBlank() }?.let {
                Text("${it.eco} ${it.name}".trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Hint("${Explorer.count(r.games)} Partien")
            Spacer(Modifier.height(6.dp))
            if (r.moves.isEmpty()) {
                Hint("Diese Stellung kommt in der Datenbank nicht vor.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (m in r.moves) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { vm.playSuggested(m.moveIn(position)) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.san, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold)
                            Text(
                                Explorer.count(m.games),
                                modifier = Modifier.width(72.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ResultBar(m.white, m.draws, m.black, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** White, draws and black of the games as one bar, as on lichess, with the percents written in. */
@Composable
private fun ResultBar(white: Long, draws: Long, black: Long, modifier: Modifier) {
    val (w, d, b) = Explorer.percents(white, draws, black)
    Row(modifier.height(18.dp).clip(RoundedCornerShape(3.dp))) {
        for ((share, colors) in listOf(w to (Color(0xFFEEEEEE) to Color.Black), d to (Color(0xFF9E9E9E) to Color.Black), b to (Color(0xFF303030) to Color.White))) {
            if (share == 0) continue
            Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(colors.first), contentAlignment = Alignment.Center) {
                if (share >= 12) Text("$share%", color = colors.second, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
