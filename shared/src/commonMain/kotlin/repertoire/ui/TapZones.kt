package repertoire.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import repertoire.core.Settings

/** Bounds of the grid of double-tap zones. */
const val TAP_COLS_MAX = 3
const val TAP_ROWS_MAX = 4

/** One area of the phone's screen for double taps: a [column] in a [row] of the grid, read like a book. */
data class TapZone(val column: Int, val row: Int) {
    companion object {
        fun at(position: Offset, size: IntSize, cols: Int = 2, rows: Int = 3): TapZone {
            val column = (position.x / size.width * cols).toInt().coerceIn(0, cols - 1)
            val row = (position.y / size.height * rows).toInt().coerceIn(0, rows - 1)
            return TapZone(column, row)
        }
    }
}

/** "oben links", "Mitte", "unten", "ganzer Bildschirm" … by where the zone lies in the grid. */
fun TapZone.label(cols: Int, rows: Int): String {
    val vertical = when {
        rows == 1 -> ""
        row == 0 -> "oben"
        row == rows - 1 -> "unten"
        rows == 3 -> "Mitte"
        else -> "Reihe ${row + 1}"
    }
    val horizontal = when {
        cols == 1 -> ""
        column == 0 -> "links"
        column == cols - 1 -> "rechts"
        else -> "Mitte"
    }
    return when {
        vertical.isEmpty() && horizontal.isEmpty() -> "ganzer Bildschirm"
        vertical == horizontal -> vertical
        else -> listOf(vertical, horizontal).filter { it.isNotEmpty() }.joinToString(" ")
    }
}

/** The label, short enough for a pill: "oben l.". */
fun TapZone.shortLabel(cols: Int, rows: Int): String = label(cols, rows).replace("links", "l.").replace("rechts", "r.")

/** Every zone of the current grid, row by row. */
val Settings.zones: List<TapZone> get() = List(tapRows * tapCols) { TapZone(it % tapCols, it / tapCols) }

/** What a double tap into [zone] runs; null if nothing. */
fun Settings.commandAt(zone: TapZone): Command? =
    if (zone.column >= tapCols || zone.row >= tapRows) null
    else tapZones.getOrNull(zone.row * tapCols + zone.column)?.let { name -> Command.entries.firstOrNull { it.name == name } }

/** The first zone that runs [command], for the list of shortcuts. */
fun Settings.zoneOf(command: Command): TapZone? =
    if (tapZonesOn) zones.firstOrNull { commandAt(it) == command } else null

/** [tapZones] with [zone] set to [command] (null: nothing). */
fun Settings.withZone(zone: TapZone, command: Command?): Settings {
    val zones = List(tapCols * tapRows) { tapZones.getOrElse(it) { "" } }.toMutableList()
    zones[zone.row * tapCols + zone.column] = command?.name ?: ""
    return copy(tapZones = zones)
}

/** A grid of [cols] × [rows]: zones that still exist keep their shortcut, new ones start empty. */
fun Settings.withGrid(cols: Int, rows: Int): Settings {
    val c = cols.coerceIn(1, TAP_COLS_MAX)
    val r = rows.coerceIn(1, TAP_ROWS_MAX)
    val kept = List(c * r) { i ->
        val column = i % c
        val row = i / c
        commandAt(TapZone(column, row))?.name ?: ""
    }
    return copy(tapCols = c, tapRows = r, tapZones = kept)
}

/**
 * Calls [onDoubleTap] with the zone of two quick taps into the same zone. Only taps that nothing
 * else used count: buttons, text fields and the board consume theirs, so they work as before and
 * a double tap only does something on a free spot. Nothing is consumed here.
 */
fun Modifier.doubleTapZones(cols: Int, rows: Int, onDoubleTap: (TapZone) -> Unit): Modifier = pointerInput(cols, rows) {
    var lastZone: TapZone? = null
    var lastUp = 0L
    awaitEachGesture {
        // The default pass comes after the children's, so what they used is consumed already.
        val down = awaitFirstDown(requireUnconsumed = false)
        // Null when the pointer was consumed, e.g. for scrolling, or left the screen.
        val up = if (down.isConsumed) null else waitForUpOrCancellation()
        if (up == null ||
            (up.position - down.position).getDistance() > viewConfiguration.touchSlop ||
            up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
        ) {
            lastZone = null
            return@awaitEachGesture
        }
        val zone = TapZone.at(down.position, size, cols, rows)
        if (zone == lastZone && down.uptimeMillis - lastUp <= viewConfiguration.doubleTapTimeoutMillis) {
            lastZone = null
            onDoubleTap(zone)
        } else {
            lastZone = zone
            lastUp = up.uptimeMillis
        }
    }
}
