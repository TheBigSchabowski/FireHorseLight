package repertoire.core

/**
 * Gaps in a variant's theory text, Anki style:
 *
 *   "Nach 6. f3 kommt ((e6)), und gegen g4 hilft ((Nb6|Sb6::Springer zurück))."
 *
 * "|" separates answers that all count, "::" adds a hint shown in the empty gap.
 */
sealed interface TheoryPart {
    data class Text(val text: String) : TheoryPart

    data class Gap(val index: Int, val answers: List<String>, val hint: String?) : TheoryPart {
        val answer: String get() = answers.first()
    }
}

object Cloze {
    private val GAP = Regex("""\(\((.+?)\)\)""")

    fun parse(theory: String): List<TheoryPart> {
        val parts = mutableListOf<TheoryPart>()
        var last = 0
        for ((i, m) in GAP.findAll(theory).withIndex()) {
            if (m.range.first > last) parts += TheoryPart.Text(theory.substring(last, m.range.first))
            val (answerText, hint) = m.groupValues[1].split("::", limit = 2).let { it[0] to it.getOrNull(1) }
            val answers = answerText.split("|").map(String::trim).filter(String::isNotEmpty)
            parts += if (answers.isEmpty()) TheoryPart.Text(m.value) else TheoryPart.Gap(i, answers, hint?.trim()?.ifEmpty { null })
            last = m.range.last + 1
        }
        if (last < theory.length) parts += TheoryPart.Text(theory.substring(last))
        return parts
    }

    fun gaps(theory: String): List<TheoryPart.Gap> = parse(theory).filterIsInstance<TheoryPart.Gap>()

    /** The theory with every gap filled in with its first answer. */
    fun solved(theory: String): String = parse(theory).joinToString("") {
        when (it) {
            is TheoryPart.Text -> it.text
            is TheoryPart.Gap -> it.answer
        }
    }

    /** Exactly as written between the brackets (or one of the "|" alternatives); only outer spaces do not count. */
    fun isCorrect(input: String, gap: TheoryPart.Gap): Boolean = gap.answers.any { it == input.trim() }
}
