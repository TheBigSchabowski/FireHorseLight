package repertoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Library
import repertoire.core.OpeningGroup
import repertoire.core.QuizBuilder
import repertoire.core.VariantSearch
import repertoire.core.SavedPlaylist
import repertoire.core.Variant
import repertoire.core.compactMoves
import repertoire.core.currentTimeMillis
import repertoire.core.shortDate

private val DELETE_COLOR = Color(0xFFE57373)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val stats = vm.stats()
    Column(modifier.fillMaxSize()) {
        // Stays on top while scrolling, so ticking variants further down needs no way back up.
        vm.selected?.let { SelectionBar(vm, it) }
        Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FireHorse Light", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.openHelp() }) { Text("Hilfe") }
                TextButton(onClick = vm::openConfig) { Text("Einstellungen") }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::startLearning, enabled = stats.due + stats.new > 0) { Text("Lernen") }
                OutlinedButton(onClick = vm::startCram, enabled = stats.total > 0) { Text("Frei üben") }
                FilledTonalButton(onClick = { vm.newVariant() }) { Text("+ Neue Variante") }
                OutlinedButton(onClick = vm::openPaste) { Text("PGN/FEN einfügen") }
                FilterChip(
                    selected = vm.blind,
                    onClick = vm::toggleBlind,
                    label = { Text(if (vm.blind) "🙈 Blind an" else "Blind") },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("${stats.due} fällig · ${stats.new} neu · ${stats.total} Varianten", style = MaterialTheme.typography.bodyMedium)
            val searching = VariantSearch(vm.search).active
            if (vm.library.variants.isNotEmpty() && vm.library.settings.showSearch) SearchField(vm, searching)

            Spacer(Modifier.height(20.dp))
            if (vm.library.active.isEmpty()) {
                Text(
                    (if (vm.library.variants.isNotEmpty()) "Alles liegt im Archiv unten. " else "Noch keine Varianten. ") +
                        "Klick auf „+ Neue Variante“, spiel die Züge bis zur Stellung und gib ihr einen Namen – " +
                        "oder füg mit „PGN/FEN einfügen“ eine Zugfolge ein, die du irgendwo gefunden hast.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }
            // Picking variants is about the openings; the playlists wait above until it is done.
            if (vm.library.active.isNotEmpty() && vm.selected == null && !searching) PlaylistSection(vm)
            val groups = vm.shownGroups
            if (searching && groups.isEmpty() && vm.shownArchive.isEmpty()) {
                Text("Nichts gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
            val anyPinned = groups.any { it.pinned }
            groups.forEachIndexed { i, group ->
                // With pins, the list falls into two parts: what matters now, and the rest.
                if (i == 0 || group.pinned != groups[i - 1].pinned) {
                    Text(
                        when {
                            group.pinned -> "ANGEPINNT"
                            anyPinned -> "WEITERE ERÖFFNUNGEN"
                            else -> "ERÖFFNUNGEN"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                OpeningHeader(vm, group, searching)
                Spacer(Modifier.height(8.dp))
                for (v in group.variants) {
                    VariantRow(vm, v)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (vm.selected == null) ArchiveSection(vm, searching)

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (LocalTouch.current) {
                    "Das Pferd unten rechts und zweimal Tippen auf eine freie Stelle führen Kürzel aus. " +
                        "Wie alles funktioniert, steht unter Einstellungen → Hilfe."
                } else {
                    "${vm.library.settings.shortcutLabel(Command.SHORTCUTS)} zeigt alle Tastenkürzel. Wie alles funktioniert, steht unter Einstellungen → Hilfe."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorseClearance()
        }
    }
}

/** What can be done with the ticked variants, above the list while selecting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(vm: AppViewModel, selected: Set<String>) {
    var confirmDelete by remember { mutableStateOf(false) }
    val n = selected.size
    if (n == 0) confirmDelete = false
    val playlist = vm.choosingFor?.let { id -> vm.library.playlists.firstOrNull { it.id == id } }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (playlist != null) {
                // Changing which variants a playlist has: tick and untick, then take it over.
                Text(
                    "Playlist „${playlist.name}“: $n " + (if (n == 1) "Variante" else "Varianten"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
                FlowRow {
                    TextButton(onClick = vm::applyPlaylistVariants) { Text("Übernehmen") }
                    TextButton(onClick = vm::endSelecting) { Text("Abbrechen") }
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (n == 0) "Varianten antippen, um sie auszuwählen" else "$n ausgewählt",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = vm::endSelecting) { Text("Fertig") }
            }
            FlowRow {
                TextButton(onClick = vm::saveSelectionAsPlaylist, enabled = n > 0) { Text("Als Playlist speichern") }
                TextButton(onClick = vm::playSelected, enabled = n > 0) { Text("▶ Abspielen") }
                TextButton(onClick = vm::learnSelected, enabled = n > 0) { Text("Lernen") }
                TextButton(onClick = vm::archiveSelected, enabled = n > 0) { Text("Archivieren") }
                if (confirmDelete) {
                    TextButton(onClick = { vm.deleteSelected(); confirmDelete = false }) {
                        Text("Wirklich $n löschen", color = DELETE_COLOR)
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                } else {
                    TextButton(onClick = { confirmDelete = true }, enabled = n > 0) { Text("Löschen") }
                }
            }
        }
    }
}

/** The playlists of one's own, above the openings, and the way to make a new one. */
@Composable
private fun PlaylistSection(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "MEINE PLAYLISTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = vm::newPlaylist) { Text("+ Neue Playlist") }
    }
    if (vm.library.playlists.isEmpty()) {
        Text(
            "Stell dir Varianten aus beliebigen Eröffnungen zusammen: „+ Neue Playlist“, Varianten antippen, speichern.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    for (p in vm.library.playlists) {
        Spacer(Modifier.height(8.dp))
        PlaylistCard(vm, p)
    }
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistCard(vm: AppViewModel, p: SavedPlaylist) {
    var confirmDelete by remember(p.id) { mutableStateOf(false) }
    var showOrder by remember(p.id) { mutableStateOf(false) }
    val variants = vm.library.variantsOf(p)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${variants.size} " + (if (variants.size == 1) "Variante" else "Varianten") +
                    variants.map(Library::openingOf).distinct().joinToString(", ").let { if (it.isEmpty()) "" else "  ·  $it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showOrder) {
                Spacer(Modifier.height(6.dp))
                variants.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}.  " + QuizBuilder.title(v),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.movePlaylistVariant(p.id, i, -1) }, enabled = i > 0) { Text("↑") }
                        TextButton(onClick = { vm.movePlaylistVariant(p.id, i, 1) }, enabled = i < variants.lastIndex) { Text("↓") }
                        TextButton(onClick = { vm.removeFromPlaylist(p.id, v.id) }) { Text("✕") }
                    }
                }
            }
            FlowRow {
                TextButton(onClick = { vm.playSavedPlaylist(p.id) }, enabled = variants.isNotEmpty()) { Text("▶ Abspielen") }
                TextButton(onClick = { vm.learnSavedPlaylist(p.id) }, enabled = variants.isNotEmpty()) { Text("Lernen") }
                TextButton(onClick = { showOrder = !showOrder }) { Text(if (showOrder) "Reihenfolge schließen" else "Reihenfolge") }
                TextButton(onClick = { vm.changePlaylistVariants(p.id) }) { Text("Varianten ändern") }
                TextButton(onClick = { vm.renamePlaylist(p.id) }) { Text("Umbenennen") }
                if (confirmDelete) {
                    TextButton(onClick = { vm.deletePlaylist(p.id) }) { Text("Playlist wirklich löschen", color = DELETE_COLOR) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                }
            }
        }
    }
}

/** Name for a new playlist, or a new name for one. */
@Composable
internal fun PlaylistNameDialog(vm: AppViewModel, n: PlaylistNaming) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = vm::cancelNaming,
        title = { Text(if (n.playlistId == null) "Neue Playlist" else "Playlist umbenennen") },
        text = {
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                n.text,
                { n.text = it },
                label = { Text("Name") },
                placeholder = { Text("z. B. Four Knights + Najdorf") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.confirmNaming() }),
                modifier = Modifier.typing(vm).fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) vm.confirmNaming().let { true } else false
                },
            )
        },
        confirmButton = { Button(onClick = vm::confirmNaming, enabled = n.text.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = vm::cancelNaming) { Text("Abbrechen") } },
    )
}

/** Name of the opening and what goes for all its variants: the playlist, pinning, export. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningHeader(vm: AppViewModel, group: OpeningGroup, searching: Boolean) {
    var confirmDelete by remember(group.opening) { mutableStateOf(false) }
    val count = group.variants.size
    val all = vm.library.groups().firstOrNull { it.opening == group.opening }?.variants?.size ?: count
    Text(group.opening, style = MaterialTheme.typography.titleLarge)
    Text(
        (if (all != count) "$count von $all " else "$count ") + (if (all == 1) "Variante" else "Varianten"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val selected = vm.selected
    if (selected != null) {
        val ticked = group.variants.all { it.id in selected }
        TextButton(onClick = { vm.toggleSelectedOpening(group.opening) }) { Text(if (ticked) "Keine auswählen" else "Alle auswählen") }
        return
    }
    // What goes for the whole opening would also hit the variants the search hides.
    if (searching) return
    FlowRow {
        TextButton(onClick = { vm.playPlaylist(group.opening) }) { Text("▶ Playlist") }
        TextButton(onClick = { vm.learnPlaylist(group.opening) }) { Text("Playlist lernen") }
        TextButton(onClick = { vm.togglePin(group.opening) }) { Text(if (group.pinned) "Lösen" else "Anpinnen") }
        TextButton(onClick = { vm.startSelecting() }) { Text("Auswählen") }
        if (vm.canTransfer) TextButton(onClick = { vm.export(group.opening) }) { Text("Exportieren") }
        TextButton(onClick = { vm.archiveOpening(group.opening) }) { Text("Archivieren") }
        if (confirmDelete) {
            TextButton(onClick = { vm.deleteOpening(group.opening) }) {
                Text(if (count == 1) "Wirklich löschen" else "Wirklich alle $count löschen", color = DELETE_COLOR)
            }
            TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
        } else {
            TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantRow(vm: AppViewModel, v: Variant) {
    var confirmDelete by remember(v.id) { mutableStateOf(false) }
    val review = vm.reviewOf(v.id)
    val status = when {
        review == null -> "neu"
        review.suspended -> "pausiert"
        review.due <= currentTimeMillis() -> "fällig"
        else -> "nächste Abfrage " + shortDate(review.due)
    }
    val selected = vm.selected
    if (selected != null) {
        // While selecting, a tap anywhere on the card ticks it; the variant's own buttons make way.
        Card(Modifier.fillMaxWidth().clickable { vm.toggleSelected(v.id) }) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = v.id in selected, onCheckedChange = { vm.toggleSelected(v.id) })
                Column(Modifier.padding(end = 12.dp)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        compactMoves(v.numberedSan()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "$status  ·  ${if (v.playWhite) "Weiß" else "Schwarz"}" + if (v.setUp) "  ·  ab eigener Stellung" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                compactMoves(v.numberedSan()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wraps onto several lines on a phone instead of squeezing the text away.
            FlowRow {
                if (review?.suspended == true) {
                    TextButton(onClick = { vm.unsuspend(v.id) }) { Text("Wieder abfragen") }
                }
                TextButton(onClick = { vm.practice(v.id) }) { Text("Üben") }
                TextButton(onClick = { vm.practiceMoves(v.id) }) { Text("Züge üben") }
                TextButton(onClick = { vm.editVariant(v, play = true) }) { Text("▶ Abspielen") }
                TextButton(onClick = { vm.editVariant(v, fromStart = true) }) { Text("Durchgehen") }
                TextButton(onClick = { vm.newVariant(template = v) }) { Text("Weiterführen") }
                TextButton(onClick = { vm.editVariant(v) }) { Text("Bearbeiten") }
                TextButton(onClick = { vm.archiveVariant(v.id) }) { Text("Archivieren") }
                if (confirmDelete) {
                    TextButton(onClick = { vm.deleteVariant(v.id) }) { Text("Wirklich löschen", color = DELETE_COLOR) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                }
            }
        }
    }
}

/**
 * The search under the counts: moves ("d4", "e4 c5", "6.f3"), the side ("schwarz") and names, see
 * [VariantSearch]. While it finds something, what it found can be played, learned or selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchField(vm: AppViewModel, searching: Boolean) {
    val focus = remember { FocusRequester() }
    // Shown with Ctrl+B s: ready to type into.
    LaunchedEffect(vm.searchFocus) { if (vm.searchFocus > 0) focus.requestFocus() }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        vm.search,
        { vm.search = it },
        placeholder = { Text("Suchen: d4, e4 c5, 6.f3, schwarz, Najdorf …") },
        singleLine = true,
        trailingIcon = if (vm.search.isNotEmpty()) {
            { TextButton(onClick = { vm.search = "" }) { Text("✕") } }
        } else {
            null
        },
        modifier = Modifier.typing(vm).fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && vm.search.isNotEmpty()) true.also { vm.search = "" } else false
        },
    )
    if (searching && vm.selected == null) {
        val hits = vm.searchHits.size
        val archived = vm.shownArchive.sumOf { it.variants.size }
        FlowRow(verticalArrangement = Arrangement.Center) {
            Text(
                "$hits Treffer" + if (archived > 0) " · $archived im Archiv" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp, top = 12.dp),
            )
            TextButton(onClick = vm::playHits, enabled = hits > 0) { Text("▶ Abspielen") }
            TextButton(onClick = vm::learnHits, enabled = hits > 0) { Text("Lernen") }
            TextButton(onClick = vm::selectHits, enabled = hits > 0) { Text("Auswählen") }
        }
    }
}

/**
 * The archive at the bottom of the home screen, closed at first: openings and variants put away,
 * e.g. to build a new repertoire without the old one in the way. From here they can be gone
 * through, taken as the start of a new variant, brought back or deleted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveSection(vm: AppViewModel, searching: Boolean) {
    val groups = vm.shownArchive
    if (groups.isEmpty()) return
    val count = groups.sumOf { it.variants.size }
    // What the search finds in the archive is shown straight away.
    val open = vm.archiveOpen || searching
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ARCHIV  ·  $count " + (if (count == 1) "Variante" else "Varianten"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (!searching) TextButton(onClick = { vm.archiveOpen = !vm.archiveOpen }) { Text(if (open) "Schließen" else "Öffnen") }
    }
    if (!open) {
        Text(
            "Wird nicht abgefragt und stört beim Üben nicht; der Lernstand bleibt, bis du es zurückholst.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        return
    }
    Spacer(Modifier.height(4.dp))
    for (group in groups) {
        ArchivedOpening(vm, group, searching)
        Spacer(Modifier.height(8.dp))
        for (v in group.variants) {
            ArchivedVariantRow(vm, v)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchivedOpening(vm: AppViewModel, group: OpeningGroup, searching: Boolean) {
    var confirmDelete by remember(group.opening) { mutableStateOf(false) }
    val count = group.variants.size
    val all = vm.library.archivedGroups().firstOrNull { it.opening == group.opening }?.variants?.size ?: count
    Text(group.opening, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        (if (all != count) "$count von $all " else "$count ") + (if (all == 1) "Variante" else "Varianten") + " im Archiv",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (searching) {
        // Bringing back is harmless, so it stays while searching: what the search shows of the opening.
        TextButton(onClick = { vm.restoreVariants(group.variants) }) {
            Text(
                when {
                    count == all -> if (count == 1) "Zurückholen" else "Alle $count zurückholen"
                    else -> "Diese $count zurückholen"
                },
            )
        }
        return
    }
    FlowRow {
        TextButton(onClick = { vm.restoreOpening(group.opening) }) { Text(if (count == 1) "Zurückholen" else "Alle $count zurückholen") }
        if (vm.canTransfer) TextButton(onClick = { vm.export(group.opening, archived = true) }) { Text("Exportieren") }
        if (confirmDelete) {
            TextButton(onClick = { vm.deleteOpening(group.opening, archived = true) }) {
                Text(if (count == 1) "Wirklich löschen" else "Wirklich alle $count löschen", color = DELETE_COLOR)
            }
            TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
        } else {
            TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchivedVariantRow(vm: AppViewModel, v: Variant) {
    var confirmDelete by remember(v.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "im Archiv  ·  ${if (v.playWhite) "Weiß" else "Schwarz"}" + if (v.setUp) "  ·  ab eigener Stellung" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                compactMoves(v.numberedSan()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow {
                TextButton(onClick = { vm.restoreVariant(v.id) }) { Text("Zurückholen") }
                TextButton(onClick = { vm.editVariant(v, fromStart = true) }) { Text("Durchgehen") }
                // A new variant of the repertoire from the old moves; the archived one stays as it is.
                TextButton(onClick = { vm.newVariant(template = v) }) { Text("Als Vorlage") }
                if (confirmDelete) {
                    TextButton(onClick = { vm.deleteVariant(v.id) }) { Text("Wirklich löschen", color = DELETE_COLOR) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                }
            }
        }
    }
}

/**
 * Paste a PGN or a FEN from somewhere else. The field is focused right away, so Cmd/Strg+V is enough;
 * what comes out of the text is then opened in the editor instead of being saved straight away.
 */
@Composable
internal fun PasteDialog(vm: AppViewModel, p: PasteState) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = vm::cancelPaste,
        title = {
            Text(
                when (p.kind) {
                    Command.PASTE_PGN -> "PGN einfügen"
                    Command.PASTE_FEN -> "FEN einfügen"
                    Command.PASTE_ALL -> "PGN mit allem einfügen"
                    else -> "PGN oder FEN einfügen"
                },
            )
        },
        text = {
            Column {
                LaunchedEffect(Unit) { focus.requestFocus() }
                Text(
                    if (p.kind == Command.PASTE_ALL) {
                        "Eine mit „Alles kopieren“ kopierte Variante hier einfügen – am Computer mit Cmd/Strg+V, am Handy lange drücken. " +
                            "Züge, Eröffnung und Name kommen mit, die Theorie und die Zeichnungen an ihren Zügen. " +
                            "Geht auch mit einer PGN von lichess: Pfeile und Kreise werden Zeichnungen, die übrigen Kommentare Theorie."
                    } else {
                        "Zugfolge oder Stellung hier einfügen – am Computer mit Cmd/Strg+V, am Handy lange drücken. " +
                            "Aus einer PGN wird die Zugfolge (Kommentare und Nebenvarianten fallen weg), " +
                            "aus einer FEN die Stellung, ab der du weiterspielst."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    p.text,
                    vm::setPasteText,
                    label = { Text(if (p.kind == Command.PASTE_ALL) "PGN mit allem" else "PGN / FEN") },
                    placeholder = {
                        Text(
                            if (p.kind == Command.PASTE_FEN) {
                                "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
                            } else {
                                "1. e4 c5 2. Nf3 d6 3. d4 cxd4"
                            },
                        )
                    },
                    minLines = 4,
                    maxLines = 8,
                    isError = p.error != null,
                    // The dialog may have a window of its own, which the main window's keys never see.
                    modifier = Modifier.typing(vm).fillMaxWidth().focusRequester(focus).onPreviewKeyEvent(vm.shortcutKeys::handle),
                )
                p.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = vm::applyPaste, enabled = p.text.isNotBlank()) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = vm::cancelPaste) { Text("Abbrechen") } },
    )
}
