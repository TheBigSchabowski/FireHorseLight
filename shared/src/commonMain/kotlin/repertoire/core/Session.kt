package repertoire.core

data class Stats(val new: Int, val due: Int, val total: Int)

object Scheduler {
    private fun active(quizzes: List<Quiz>, progress: Progress) =
        quizzes.filter { progress.cards[it.variant.id]?.suspended != true }

    fun stats(quizzes: List<Quiz>, progress: Progress, now: Long = currentTimeMillis()): Stats {
        val active = active(quizzes, progress)
        return Stats(
            new = active.count { progress.cards[it.variant.id] == null },
            due = active.count { progress.cards[it.variant.id]?.let { s -> s.due <= now } == true },
            total = active.size,
        )
    }

    /** Due reviews first (most overdue first), then new variants in the order they were created, up to today's limit. */
    fun dueQueue(quizzes: List<Quiz>, progress: Progress, settings: Settings, now: Long = currentTimeMillis()): List<Quiz> {
        val active = active(quizzes, progress)
        val due = active.mapNotNull { q -> progress.cards[q.variant.id]?.takeIf { it.due <= now }?.let { q to it.due } }
            .sortedBy { it.second }.map { it.first }
        val newToday = progress.newIntroduced[Store.learningDay(now)] ?: 0
        val fresh = active.filter { progress.cards[it.variant.id] == null }
            .take((settings.newPerDay - newToday).coerceAtLeast(0))
        return due + fresh
    }

    /** Free practice: everything, shuffled, nothing is graded. */
    fun cramQueue(quizzes: List<Quiz>, progress: Progress): List<Quiz> = active(quizzes, progress).shuffled()
}

sealed interface Outcome {
    /** Still on a way to the target; [finished] once the target position is on the board. */
    data class Correct(val move: Move, val finished: Boolean) : Outcome

    /** Leads to another saved variant, not to this one. No penalty, try again. [name] if it is exactly another variant's position. */
    data class OtherVariant(val move: Move, val name: String?) : Outcome

    /** Not on any saved way to the target; [expected] has been played instead. */
    data class Wrong(val move: Move, val expected: Move, val finished: Boolean) : Outcome
}

/**
 * Playing through one quiz: any move order that the library knows to reach the target counts.
 * With [singleMove], it starts after the variant's first [startPly] moves and asks only for the next one.
 */
class QuizAttempt(val quiz: Quiz, val startPly: Int = 0, val singleMove: Boolean = false) {
    init {
        require(startPly in 0 until quiz.variant.moves.size.coerceAtLeast(1)) { "Kein Zug nach Halbzug $startPly" }
    }

    var position: Position = quiz.variant.positions[startPly]
        private set
    var lastMove: Move? = quiz.variant.moves.getOrNull(startPly - 1)?.let(Move::fromUci)
        private set
    var mistakes = 0
        private set
    var finished = false
        private set

    /** Moves played so far, numbered like "1. e4", "1... c5". */
    val played = quiz.variant.numberedSan().take(startPly).toMutableList()

    /** Something has been played by the user, not only set up. */
    val started: Boolean get() = played.size > startPly

    fun submit(move: Move): Outcome {
        check(!finished) { "Bereits gelöst" }
        val nextKey = position.play(move).key
        return when {
            nextKey == quiz.targetKey || nextKey in quiz.guide -> {
                play(move)
                Outcome.Correct(move, finished)
            }
            nextKey in quiz.knownKeys -> Outcome.OtherVariant(move, quiz.namesByKey[nextKey])
            else -> {
                mistakes++
                val expected = quiz.guide.getValue(position.key)
                play(expected)
                Outcome.Wrong(move, expected, finished)
            }
        }
    }

    private fun play(move: Move) {
        played += numberedSan(position, move)
        position = position.play(move)
        lastMove = move
        if (position.key == quiz.targetKey || singleMove) finished = true
    }
}
