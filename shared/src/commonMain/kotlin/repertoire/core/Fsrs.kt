package repertoire.core

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Grade(val value: Int, val label: String) {
    AGAIN(1, "Nochmal"),
    HARD(2, "Schwer"),
    GOOD(3, "Gut"),
    EASY(4, "Leicht"),
}

@Serializable
data class ReviewState(
    val stability: Double,
    val difficulty: Double,
    /** Epoch millis when the card is due again. */
    val due: Long,
    val lastReview: Long,
    val reps: Int = 0,
    val lapses: Int = 0,
    val suspended: Boolean = false,
)

/**
 * FSRS-4.5 scheduler (same algorithm family as En Croissant's ts-fsrs), with default parameters
 * and 90 % target retention. A failed card comes back after [AGAIN_DELAY_MS]; everything else
 * is due at the start of a day ([DAY_START_HOUR] o'clock) so a review "tomorrow" means tomorrow.
 */
object Fsrs {
    private val w = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474,
        0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    const val AGAIN_DELAY_MS = 10 * 60 * 1000L
    const val DAY_START_HOUR = 4

    private fun retrievability(elapsedDays: Double, stability: Double) = (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    private fun initialDifficulty(g: Int) = (w[4] - (g - 3) * w[5]).coerceIn(1.0, 10.0)

    fun review(state: ReviewState?, grade: Grade, now: Long = currentTimeMillis()): ReviewState {
        val g = grade.value
        val (stability, difficulty) = if (state == null || state.reps == 0) {
            w[g - 1] to initialDifficulty(g)
        } else {
            val elapsed = ((now - state.lastReview).coerceAtLeast(0)).toDouble() / DAY_MS
            val r = retrievability(elapsed, state.stability)
            val d = state.difficulty
            val nextD = (w[7] * initialDifficulty(3) + (1 - w[7]) * (d - w[6] * (g - 3))).coerceIn(1.0, 10.0)
            val nextS = if (grade == Grade.AGAIN) {
                (w[11] * d.pow(-w[12]) * ((state.stability + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r)))
                    .coerceAtMost(state.stability)
            } else {
                val hardPenalty = if (grade == Grade.HARD) w[15] else 1.0
                val easyBonus = if (grade == Grade.EASY) w[16] else 1.0
                state.stability * (
                    exp(w[8]) * (11 - d) * state.stability.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) *
                        hardPenalty * easyBonus + 1
                    )
            }
            nextS.coerceAtLeast(0.01) to nextD
        }

        val due = if (grade == Grade.AGAIN) {
            now + AGAIN_DELAY_MS
        } else {
            startOfDay(now, stability.roundToLong().coerceIn(1, 36500))
        }
        return ReviewState(
            stability = stability,
            difficulty = difficulty,
            due = due,
            lastReview = now,
            reps = (state?.reps ?: 0) + 1,
            lapses = (state?.lapses ?: 0) + if (grade == Grade.AGAIN && state != null && state.reps > 0) 1 else 0,
            suspended = state?.suspended ?: false,
        )
    }

    /** Start of the learning day [daysAhead] days after the one containing [now]. */
    fun startOfDay(now: Long, daysAhead: Long = 0): Long = dayStart(now, daysAhead, DAY_START_HOUR)
}
