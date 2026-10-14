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
private val MARK = Color(0xCC15781B)
private val DRAW_BAR = Color(0xE0151515)

/** The frame while drawing into something kept, so it never looks like drawing mode. */
val KEPT = Color(0xCC1450A8)

/** A drawing from memory, checked: what was right, what is missing, what was too much. */
val KEPT_RIGHT = MARK
val KEPT_MISSING = Color(0xCCE68F00)
val KEPT_EXTRA = Color(0xCC9A2020)

/** The color as it is drawn: a little see-through, as on lichess. */
val PenColor.paint: Color get() = Color(0xCC000000.toInt() or rgb)

/** The color without transparency, for the buttons that pick it. */
val PenColor.solid: Color get() = Color(0xFF000000.toInt() or rgb)

/** Black or white, whichever can be read on top of this color. */
val PenColor.onTop: Color
    get() = if ((rgb shr 16 and 0xFF) * 299 + (rgb shr 8 and 0xFF) * 587 + (rgb and 0xFF) * 114 > 150_000) Color.Black else Color.White

/** The six pens as set in the configuration. */
val LocalPens = staticCompositionLocalOf { Ink.DEFAULT }

/** What the board draws with: one of the six pens, and on a single square a filled field instead of a circle. */
data class Pen(val color: Int = 0, val field: Boolean = false) {
    /** The next pen ([by] 1) or the one before (-1), round and round. */
    fun step(by: Int) = copy(color = (color + by).mod(Ink.PENS))
}

/** A mark that stays on the board, unlike a scribble: part of a saved drawing, or of one drawn from memory; [color] overrides its own. */
data class KeptMark(val mark: Mark, val color: Color? = null)

/**
 * The board draws into something kept instead of scribbling: a tap circles a square, a drag draws an
 * arrow, each goes to [onMark] (the same one twice takes it away), and no moves are made.
 */
class Keeping(val label: String, val onMark: (Mark) -> Unit, val onClear: () -> Unit, val onDone: (() -> Unit)? = null)

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
 *
 * Holding a square (or the right mouse button) draws on the board like on lichess: dragging to
 * another square makes an arrow, letting go on the same square circles it (or fills it in, see
 * [Pen]). Shift, Alt or both draw with pen 2, 3 or 4, otherwise with the chosen pen. The same mark
 * drawn twice disappears again, as does everything once a move is played or the board is touched.
 *
 * Three quick taps switch to drawing mode, which is easier on a phone than holding a square: every
 * tap circles a square and every drag draws an arrow, and no moves are made until three taps (or
 * „Fertig“) switch it off again. Nothing drawn that way is saved.
 *
 * [kept] marks are shown in their own colors underneath, and with [keeping] the board draws into
 * them, framed in blue: a drawing saved with the variant, or one drawn from memory in a quiz.
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
    /** Passed in for the main board, so that Ctrl+B m can switch it from outside. */
    drawModeState: MutableState<Boolean> = remember { mutableStateOf(false) },
    kept: List<KeptMark> = emptyList(),
    keeping: Keeping? = null,
    /** Passed in so that the color and shape stay the same on every board. */
    penState: MutableState<Pen> = remember { mutableStateOf(Pen()) },
    /** A new value wipes the scribbles (Ctrl+B w). */
    clears: Int = 0,
    /** Everything drawn is out of sight while true (Shift+A); drawing anything shows it again. */
    hiddenState: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    val theme = LocalBoardTheme.current
    val pens = LocalPens.current
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(position) { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    // Drawn marks belong to the position they were drawn in, so a move wipes the board clean,
    // and so does starting or ending a kept drawing.
    var drawn by remember(position, keeping != null, clears) { mutableStateOf(emptyList<Mark>()) }
    var drawing by remember(position) { mutableStateOf<Mark?>(null) }
    var drawMode by drawModeState
    // Read inside the gestures, which outlive a recomposition.
    val keepingNow by rememberUpdatedState(keeping)
    var pen by penState
    var hidden by hiddenState

    // Drawing takes the keys (1–6, o, u, …), so a text field must not keep the focus and catch them.
    val focusManager = LocalFocusManager.current
    val drawingNow = drawMode || keeping != null
    // Drawing on a board whose drawings are hidden: they come back, so nothing is drawn blind.
    LaunchedEffect(drawingNow) {
        if (drawingNow) {
            focusManager.clearFocus()
            hidden = false
        }
    }

    // Counting quick taps for the triple tap that switches drawing mode on and off.
    var taps by remember { mutableStateOf(0) }
    var lastTapUp by remember { mutableStateOf(0L) }
    // What was drawn before the first of those taps: the taps themselves should leave no trace.
    var beforeTaps by remember { mutableStateOf(emptyList<Mark>()) }

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

        // Everything drawn, with its color; filled fields go under the pieces, the rest on top.
        val marked = if (hidden) emptyList<Pair<Mark, Color>>() else kept.map { it.mark to (it.color ?: pens[it.mark.color].paint) } +
            (drawn + listOfNotNull(drawing)).map { it to pens[it.color].paint }
        val fields = marked.filter { it.first.field }.associate { (mark, color) -> mark.from to color.copy(alpha = color.alpha * 0.7f) }
        val lines = marked.filterNot { it.first.field }

        Box(
            Modifier.size(squareDp * 8).pointerInput(position, interactive, whiteBottom, squarePx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val sq = squareOf(down.position) ?: return@awaitEachGesture
                    // Taps on the board are the board's, not a double tap into a screen zone,
                    // and the keys are the board's again rather than a text field's.
                    down.consume()
                    focusManager.clearFocus()
                    val before = drawn

                    /**
                     * Counts a quick tap; the third in a row switches drawing mode and undoes what
                     * the taps did on the way. Returns true if it did.
                     */
                    fun tapped(upTime: Long): Boolean {
                        taps = if (taps > 0 && down.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis) taps + 1 else 1
                        lastTapUp = upTime
                        if (taps == 1) beforeTaps = before
                        if (taps < 3) return false
                        taps = 0
                        drawMode = !drawMode
                        drawn = beforeTaps
                        selected = null
                        dragFrom = null
                        return true
                    }

                    /**
                     * Follows the pointer until it is lifted and keeps the mark it drew. Returns when
                     * it was lifted if that was a quick tap on the same square, otherwise null.
                     */
                    suspend fun AwaitPointerEventScope.drawMark(): Long? {
                        selected = null
                        dragFrom = null
                        var to = sq
                        var upTime = 0L
                        // Shift, Alt or both pick pen 2, 3 or 4 as on lichess (red, blue, orange there); without them the chosen pen.
                        val keys = currentEvent.keyboardModifiers
                        val color = when {
                            keys.isShiftPressed && keys.isAltPressed -> 3
                            keys.isShiftPressed -> 1
                            keys.isAltPressed -> 2
                            else -> pen.color
                        }
                        fun markTo(to: Int) = Mark(sq, to, color, field = pen.field && to == sq)
                        hidden = false
                        drawing = markTo(sq)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            squareOf(change.position)?.let { to = it }
                            drawing = markTo(to)
                            change.consume()
                            if (!change.pressed) {
                                upTime = change.uptimeMillis
                                break
                            }
                        }
                        drawing = null
                        val mark = markTo(to)
                        val into = keepingNow
                        if (into != null) into.onMark(mark) else drawn = drawn.toggle(mark)
                        return upTime.takeIf { to == sq && it - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis }
                    }

                    // Right mouse button: straight to drawing, as on lichess.
                    if (currentEvent.buttons.isSecondaryPressed) {
                        drawMark()
                        return@awaitEachGesture
                    }

                    // Into a kept drawing: the same, but without the triple tap, which would leave three circles.
                    if (keepingNow != null) {
                        drawMark()
                        return@awaitEachGesture
                    }

                    // Drawing mode: a tap circles, a drag draws an arrow, nothing moves.
                    if (drawMode) {
                        drawMark()?.let(::tapped)
                        return@awaitEachGesture
                    }

                    val current = selected
                    if (interactive && current != null && current != sq && tryMove(current, sq)) return@awaitEachGesture
                    val piece = position.pieceAt(sq)
                    val movable = interactive && piece != null && piece.isUpperCase() == position.whiteToMove
                    if (movable) selected = sq

                    // Held on the same square long enough? Then this is a mark and not a move.
                    // The piece is only picked up once the finger really moves, so holding it
                    // still to draw does not drag it around first.
                    var release: Offset? = null
                    var releaseTime = 0L
                    val heldDown = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                            if (!change.pressed) {
                                release = change.position
                                releaseTime = change.uptimeMillis
                                return@withTimeoutOrNull
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                if (movable) {
                                    dragFrom = sq
                                    dragPos = change.position
                                }
                                change.consume()
                                return@withTimeoutOrNull
                            }
                        }
                    } == null
                    if (heldDown) {
                        drawMark()
                        return@awaitEachGesture
                    }

                    drawn = emptyList()
                    if (release != null && tapped(releaseTime)) return@awaitEachGesture
                    if (!movable) {
                        selected = null
                        return@awaitEachGesture
                    }
                    val lifted = release
                    if (lifted != null) {
                        // A short tap: the piece stays selected, a tap on the target follows.
                        dragFrom = null
                        squareOf(lifted)?.takeIf { it != sq }?.let { tryMove(sq, it) }
                        return@awaitEachGesture
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (!change.pressed) {
                            val target = squareOf(change.position)
                            dragFrom = null
                            if (target != null && target != sq) tryMove(sq, target)
                            break
                        }
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
                    fields[sq]?.let { Box(Modifier.fillMaxSize().background(it)) }

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

            // Drawing into something kept comes first; drawing mode waits underneath until it is done.
            val frameColor = when {
                keeping != null -> KEPT
                drawMode -> MARK
                else -> null
            }
            if (lines.isNotEmpty() || frameColor != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = { sq: Int -> topLeft(sq) + Offset(squarePx / 2, squarePx / 2) }
                    for ((mark, color) in lines) drawMark(mark, color, centre, squarePx)
                    if (frameColor != null) {
                        val frame = squarePx * 0.06f
                        drawRect(frameColor, Offset(frame / 2, frame / 2), Size(boardPx - frame, boardPx - frame), style = Stroke(frame))
                    }
                }
            }
            if (keeping != null) {
                DrawModeBar(squareDp.value, keeping.label, keeping.onClear, keeping.onDone)
            } else if (drawMode) {
                DrawModeBar(squareDp.value, "✎ Zeichnen", onClear = { drawn = emptyList() }, onDone = { drawMode = false })
            }
            if (frameColor != null) PenBar(squareDp.value, pen, pens) { pen = it }

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

/** Says that drawing mode is on and how to leave it; small enough to cover little of the top rank. */
@Composable
private fun BoxScope.DrawModeBar(square: Float, label: String, onClear: () -> Unit, onDone: (() -> Unit)?) {
    val size = (square * 0.2f).coerceIn(10f, 15f).sp
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = (square * 0.1f).dp)
            .background(DRAW_BAR, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = size, modifier = Modifier.padding(start = 12.dp, end = 6.dp))
        Text(
            "Löschen", color = Color(0xFFB0B0B0), fontSize = size,
            modifier = Modifier.clickable(onClick = onClear)
                .padding(start = 8.dp, end = if (onDone == null) 12.dp else 8.dp, top = 6.dp, bottom = 6.dp),
        )
        if (onDone != null) {
            Text(
                "Fertig", color = Color(0xFF8BD18E), fontSize = size,
                modifier = Modifier.clickable(onClick = onDone).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

/** The pen: six colors and circle or field, at the bottom of the board while drawing. ← → switch the color on a keyboard. */
@Composable
private fun BoxScope.PenBar(square: Float, pen: Pen, pens: List<PenColor>, onPen: (Pen) -> Unit) {
    val dot = (square * 0.4f).coerceIn(18f, 26f).dp
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = (square * 0.1f).dp)
            .background(DRAW_BAR, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pens.forEachIndexed { i, ink ->
            val chosen = i == pen.color
            Box(
                Modifier.padding(horizontal = 3.dp).size(dot)
                    .border(2.dp, if (chosen) Color.White else Color.Transparent, CircleShape)
                    .padding(3.dp)
                    .background(ink.solid, CircleShape)
                    .clickable { onPen(pen.copy(color = i)) },
            )
        }
        // Circle or field: shown as what a tap on a square will draw.
        for (field in listOf(false, true)) {
            val chosen = pen.field == field
            Box(
                Modifier.padding(start = if (field) 4.dp else 12.dp, end = 4.dp).size(dot)
                    .border(2.dp, if (chosen) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { onPen(pen.copy(field = field)) }
                    .padding(5.dp),
            ) {
                val paint = pens[pen.color].solid
                if (field) {
                    Box(Modifier.fillMaxSize().background(paint))
                } else {
                    Box(Modifier.fillMaxSize().border(2.dp, paint, CircleShape))
                }
            }
        }
    }
}

/** One arrow or circle, as on lichess; fields are filled in with the squares. */
private fun DrawScope.drawMark(mark: Mark, color: Color, centre: (Int) -> Offset, square: Float) {
    val from = centre(mark.from)
    if (mark.from == mark.to) {
        drawCircle(color, radius = square * 0.45f, center = from, style = Stroke(width = square * 0.08f))
        return
    }
    val to = centre(mark.to)
    val direction = (to - from) / (to - from).getDistance()
    val head = square * 0.4f
    // The shaft starts outside the piece it comes from and stops where the head begins.
    val shaftStart = from + direction * (square * 0.25f)
    val shaftEnd = to - direction * head
    drawLine(color, shaftStart, shaftEnd, strokeWidth = square * 0.16f, cap = StrokeCap.Round)
    val side = Offset(-direction.y, direction.x) * (head * 0.45f)
    drawPath(
        Path().apply {
            moveTo(to.x, to.y)
            lineTo(shaftEnd.x + side.x, shaftEnd.y + side.y)
            lineTo(shaftEnd.x - side.x, shaftEnd.y - side.y)
            close()
        },
        color,
    )
}
