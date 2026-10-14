package repertoire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import repertoire.core.Mark
import repertoire.core.Ink
import repertoire.core.PenColor
import repertoire.core.Move
import repertoire.core.toggle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import repertoire.core.Position
import repertoire.shared.resources.Res
import repertoire.shared.resources.bb
import repertoire.shared.resources.bk
import repertoire.shared.resources.bn
import repertoire.shared.resources.bp
import repertoire.shared.resources.bq
import repertoire.shared.resources.br
import repertoire.shared.resources.wb
import repertoire.shared.resources.wk
import repertoire.shared.resources.wn
import repertoire.shared.resources.wp
import repertoire.shared.resources.wq
import repertoire.shared.resources.wr
import kotlin.math.roundToInt

/** The two square colors of the board. */
enum class BoardTheme(val label: String, val light: Color, val dark: Color) {
    CLASSIC("Klassisch", Color(0xFFF0D9B5), Color(0xFFB58863)),
    GREEN("Grün", Color(0xFFEEEED2), Color(0xFF769656)),
    BLUE("Blau", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    GRAY("Grau", Color(0xFFE0E0E0), Color(0xFF8A8A8A)),
    ;

    companion object {
        fun named(name: String) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CLASSIC
    }
}

val LocalBoardTheme = staticCompositionLocalOf { BoardTheme.CLASSIC }
private val LAST_MOVE = Color(0x80CDD26A)
private val SELECTED = Color(0x9982A55A)
private val RIGHT = Color(0x9955C255)
private val WRONG = Color(0x99E04A4A)
private val TARGET_DOT = Color(0x40000000)

/** cburnett pieces (lichess), pre-rendered to PNG because Android cannot draw SVG resources. */
private val PIECES: Map<Char, DrawableResource> = mapOf(
    'K' to Res.drawable.wk, 'Q' to Res.drawable.wq, 'R' to Res.drawable.wr,
    'B' to Res.drawable.wb, 'N' to Res.drawable.wn, 'P' to Res.drawable.wp,
    'k' to Res.drawable.bk, 'q' to Res.drawable.bq, 'r' to Res.drawable.br,
    'b' to Res.drawable.bb, 'n' to Res.drawable.bn, 'p' to Res.drawable.bp,
)

@Composable
private fun piecePainter(piece: Char): Painter = painterResource(PIECES.getValue(piece))

/**
 * Interactive board. Moves are made by clicking a piece and then its target, or by dragging.
 * Only the side to move can be moved, and only legally; promotions become a queen.
 */
@Composable
fun ChessBoard(
    position: Position,
    whiteBottom: Boolean,
    lastMove: Move?,
    marks: Map<Int, Boolean>,
    interactive: Boolean,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalBoardTheme.current
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(position) { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val focusManager = LocalFocusManager.current

    fun movesFrom(sq: Int) = position.legalMoves().filter { it.from == sq }

    fun tryMove(from: Int, to: Int): Boolean {
        val candidates = movesFrom(from).filter { it.to == to }
        val move = candidates.firstOrNull { it.promotion == null || it.promotion == 'q' } ?: return false
        selected = null
        onMove(move)
        return true
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val boardPx = constraints.maxWidth.coerceAtMost(constraints.maxHeight).toFloat()
        val squarePx = boardPx / 8
        val squareDp = with(LocalDensity.current) { squarePx.toDp() }

        fun squareOf(offset: Offset): Int? {
            val col = (offset.x / squarePx).toInt()
            val row = (offset.y / squarePx).toInt()
            if (col !in 0..7 || row !in 0..7) return null
            return if (whiteBottom) (7 - row) * 8 + col else row * 8 + (7 - col)
        }

        fun topLeft(sq: Int): Offset {
            val file = sq % 8
            val rank = sq / 8
            val col = if (whiteBottom) file else 7 - file
            val row = if (whiteBottom) 7 - rank else rank
            return Offset(col * squarePx, row * squarePx)
        }

        val targets = (dragFrom ?: selected)?.let { from -> movesFrom(from).map { it.to }.toSet() } ?: emptySet()

        Box(
            Modifier.size(squareDp * 8).pointerInput(position, interactive, whiteBottom, squarePx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val sq = squareOf(down.position) ?: return@awaitEachGesture
                    // Taps on the board are the board's, not a double tap into a screen zone,
                    // and the keys are the board's again rather than a text field's.
                    down.consume()
                    focusManager.clearFocus()

                    val current = selected
                    if (interactive && current != null && current != sq && tryMove(current, sq)) return@awaitEachGesture
                    val piece = position.pieceAt(sq)
                    val movable = interactive && piece != null && piece.isUpperCase() == position.whiteToMove
                    selected = if (movable) sq else null
                    if (!movable) return@awaitEachGesture

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val target = squareOf(change.position)
                            dragFrom = null
                            // A short tap leaves the piece selected, a tap on the target follows.
                            if (target != null && target != sq) tryMove(sq, target)
                            break
                        }
                        if (dragFrom == null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragFrom = sq
                        dragPos = change.position
                        change.consume()
                    }
                }
            },
        ) {
            for (sq in 0..63) {
                val tl = topLeft(sq)
                val isLight = (sq % 8 + sq / 8) % 2 == 1
                Box(
                    Modifier
                        .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                        .size(squareDp)
                        .background(if (isLight) theme.light else theme.dark),
                ) {
                    val overlay = when {
                        marks[sq] == true -> RIGHT
                        marks[sq] == false -> WRONG
                        sq == selected -> SELECTED
                        lastMove != null && (sq == lastMove.from || sq == lastMove.to) -> LAST_MOVE
                        else -> null
                    }
                    if (overlay != null) Box(Modifier.fillMaxSize().background(overlay))

                    val piece = position.pieceAt(sq)
                    if (piece != null && sq != dragFrom) {
                        Image(piecePainter(piece), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                    if (sq in targets) {
                        Box(
                            Modifier.align(Alignment.Center).size(squareDp * (if (piece != null) 0.9f else 0.3f))
                                .background(TARGET_DOT, CircleShape),
                        )
                    }
                    // Coordinates along the bottom and left edge.
                    val col = (tl.x / squarePx).roundToInt()
                    val row = (tl.y / squarePx).roundToInt()
                    val labelColor = if (isLight) theme.dark else theme.light
                    if (row == 7) {
                        Text(
                            "${'a' + sq % 8}", color = labelColor, fontSize = (squareDp.value * 0.18f).sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
                        )
                    }
                    if (col == 0) {
                        Text(
                            "${sq / 8 + 1}", color = labelColor, fontSize = (squareDp.value * 0.18f).sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp),
                        )
                    }
                }
            }

            val dragging = dragFrom
            val draggedPiece = dragging?.let { position.pieceAt(it) }
            if (draggedPiece != null) {
                Image(
                    piecePainter(draggedPiece), contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset((dragPos.x - squarePx / 2).roundToInt(), (dragPos.y - squarePx / 2).roundToInt()) }
                        .size(squareDp),
                )
            }
        }
    }
}
