package repertoire.core

import kotlinx.serialization.Serializable

/** A named position, entered by playing the moves that lead to it from [startFen]. */
@Serializable
data class Variant(
    val id: String,
    /** e.g. "Sizilianisch" */
    val opening: String,
    /** e.g. "Scheveningen" */
    val name: String,
    /** Moves from [startFen] in UCI notation ("e2e4"). */
    val moves: List<String>,
    /** Board orientation while training this variant. */
    val playWhite: Boolean = true,
    /** Notes from FireHorse; FireHorse Light keeps them but does not show them. */
    val theory: String = "",
    /**
     * Where the line begins. Normally the start of a game; a variant imported from a FEN begins at
     * that position instead and is trained by playing on from there.
     */
    val startFen: String = Position.START_FEN,
    /** Arrows and circles kept at positions of the line, asked after the puzzle like the theory. */
    val drawings: List<Drawing> = emptyList(),
    /**
     * Put away: not trained, not on the list of openings, and its positions count for nothing in
     * the other puzzles. Its learning state is kept for when it comes back.
     */
    val archived: Boolean = false,
) {
    /** True if the line does not begin at the start of a game. */
    val setUp: Boolean get() = startFen != Position.START_FEN

    val start: Position get() = if (setUp) Position.fromFen(startFen) else Position.START

    /** Start position followed by the position after each move. */
    val positions: List<Position> by lazy {
        moves.runningFold(start) { p, uci -> p.play(Move.fromUci(uci)) }
    }

    val target: Position get() = positions.last()

    /** ["1. e4", "1... c5", ...] */
    fun numberedSan(): List<String> = moves.mapIndexed { i, uci -> numberedSan(positions[i], Move.fromUci(uci)) }

    /** The same puzzle: the same moves from the same position, so a learning state still fits. */
    fun sameLine(other: Variant): Boolean = moves == other.moves && startFen == other.startFen
}

fun numberedSan(before: Position, move: Move): String {
    val san = before.san(move)
    return if (before.whiteToMove) "${before.fullmoveNumber}. $san" else "${before.fullmoveNumber}... $san"
}

/** ["1. e4", "1... c5", "2. Nf3"] → "1. e4 c5 2. Nf3" */
fun compactMoves(numbered: List<String>): String =
    numbered.joinToString(" ").replace(Regex("(\\d+)\\. (\\S+) \\1\\.\\.\\. "), "$1. $2 ")

/**
 * The puzzle for one variant: reach [targetKey] from the start. [guide] maps every position from
 * which one of the saved variants continues to the target to that continuation, so any move order
 * stored in the library counts (transpositions included).
 */
class Quiz(
    val variant: Variant,
    val targetKey: String,
    val guide: Map<String, Move>,
    /** Every position of every saved variant, to tell "other variant" apart from "wrong". */
    val knownKeys: Set<String>,
    /** Final position → title of the variant(s) ending there. */
    val namesByKey: Map<String, String>,
)

object QuizBuilder {
    fun build(variants: List<Variant>): List<Quiz> {
        val usable = variants.filter { it.moves.isNotEmpty() }
        val knownKeys = usable.flatMap { v -> v.positions.map { it.key } }.toSet()
        val namesByKey = usable.groupBy { it.target.key }.mapValues { (_, vs) -> vs.joinToString(" / ") { title(it) } }
        return usable.map { variant ->
            val targetKey = variant.target.key
            val guide = mutableMapOf<String, Move>()
            for (v in usable) {
                val keys = v.positions.map { it.key }
                val reach = keys.indexOf(targetKey)
                for (i in 0 until reach) guide.putIfAbsent(keys[i], Move.fromUci(v.moves[i]))
            }
            Quiz(variant, targetKey, guide, knownKeys, namesByKey)
        }
    }

    fun title(v: Variant): String = listOf(v.opening, v.name).filter { it.isNotBlank() }.joinToString(" › ")
}
