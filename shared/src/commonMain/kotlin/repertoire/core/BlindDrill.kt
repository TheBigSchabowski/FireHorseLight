package repertoire.core

/**
 * Eine gespeicherte Variante blind nachspielen: du ziehst deine Seite, die Variante zieht die andere.
 * Wie das Puzzle in FireHorse zählt jede Zugfolge aus deiner Library, die zur Zielstellung führt
 * (auch Zugumstellungen), siehe [QuizBuilder].
 */
class BlindDrill(private val quiz: Quiz) {

    enum class Verdict {
        /** Bleibt auf dem Weg zur Zielstellung. */
        RIGHT,

        /** Führt in eine andere gespeicherte Variante, aber nicht zu dieser. */
        OTHER_VARIANT,

        /** Steht in keiner gespeicherten Variante. */
        WRONG,
    }

    val variant: Variant get() = quiz.variant

    val title: String get() = QuizBuilder.title(variant)

    /** Die Stellung, in der die Linie beginnt. */
    val start: Position get() = variant.start

    /** Anzahl Halbzüge der Variante. */
    val length: Int get() = variant.moves.size

    fun isDone(position: Position): Boolean = position.key == quiz.targetKey

    /** Ist in [position] der Spieler am Zug (und nicht die Variante)? */
    fun isUsersTurn(position: Position): Boolean = position.whiteToMove == variant.playWhite

    /** Der Zug, mit dem es in [position] Richtung Ziel weitergeht; null am Ziel oder abseits der Linie. */
    fun expected(position: Position): Move? = quiz.guide[position.key]

    fun judge(position: Position, move: Move): Verdict {
        val after = position.play(move).key
        return when {
            after == quiz.targetKey || after in quiz.guide -> Verdict.RIGHT
            after in quiz.knownKeys -> Verdict.OTHER_VARIANT
            else -> Verdict.WRONG
        }
    }

    companion object {
        /** Drills for [variants], built against the whole [library] so that its transpositions count. */
        fun forVariants(variants: List<Variant>, library: List<Variant>): List<BlindDrill> {
            val ids = variants.map { it.id }.toSet()
            return QuizBuilder.build(library).filter { it.variant.id in ids }.map(::BlindDrill)
        }
    }
}
