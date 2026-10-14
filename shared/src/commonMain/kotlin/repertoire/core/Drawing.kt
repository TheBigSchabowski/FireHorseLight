package repertoire.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The colors the six pens can be set to; [rgb] as 0xRRGGBB. */
enum class Ink(val label: String, val rgb: Int) {
    GREEN("Grün", 0x15781B),
    RED("Rot", 0x882020),
    BLUE("Blau", 0x003088),
    ORANGE("Orange", 0xE68F00),
    PURPLE("Lila", 0x6A1B9A),
    TEAL("Türkis", 0x00838F),
    PINK("Pink", 0xC2185B),
    LIME("Hellgrün", 0x7CB342),
    SKY("Hellblau", 0x1E88E5),
    YELLOW("Gelb", 0xFBC02D),
    CORAL("Koralle", 0xF4511E),
    CRIMSON("Dunkelrot", 0x5D0F0F),
    BROWN("Braun", 0x6D4C41),
    GREY("Grau", 0x757575),
    BLACK("Schwarz", 0x212121),
    WHITE("Weiß", 0xFAFAFA),
    ;

    val pen: PenColor get() = PenColor(rgb, label)

    companion object {
        const val PENS = 6

        /** The first four as on lichess, then two more. */
        val DEFAULT: List<PenColor> = listOf(GREEN, RED, BLUE, ORANGE, PURPLE, TEAL).map { it.pen }

        /** The six pens as saved; a missing or unreadable one keeps its default. */
        fun pens(saved: List<String>): List<PenColor> =
            DEFAULT.mapIndexed { i, d -> saved.getOrNull(i)?.let(PenColor::parse) ?: d }
    }
}

/** The color of a pen: one of the [Ink]s, or any color given as "#7FFFD4", with the name feedback calls it by. */
data class PenColor(val rgb: Int, val label: String) {
    /** "#7FFFD4" */
    val hex: String get() = "#" + rgb.toString(16).uppercase().padStart(6, '0')

    /** How it is saved: the name of an [Ink] ("GREEN"), or "#7FFFD4 Aquamarin". */
    val saved: String get() = Ink.entries.firstOrNull { it.pen == this }?.name ?: "$hex $label"

    companion object {
        private val HEX = Regex("""#?([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})""")

        /** "GREEN", "#7FFFD4 Aquamarin", "#7FFFD4", "7fffd4" or "#7FD"; null if it is none of these. */
        fun parse(text: String): PenColor? {
            val t = text.trim()
            Ink.entries.firstOrNull { it.name == t }?.let { return it.pen }
            val code = t.substringBefore(' ')
            val digits = HEX.matchEntire(code)?.groupValues?.get(1) ?: return null
            val six = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
            val rgb = six.toInt(16)
            val name = t.substringAfter(' ', "").trim()
            // Without a name it is called by its code, or by the ink that has exactly this color.
            return PenColor(rgb, name.ifEmpty { Ink.entries.firstOrNull { it.rgb == rgb }?.label ?: "#" + six.uppercase() })
        }
    }
}

/**
 * Something drawn on the board: an arrow from [from] to [to], or on a single square (the same
 * [from] and [to]) a circle, or with [field] the whole square filled in. [color] is one of the six
 * pens (0 to 5), whose colors are set in the configuration.
 */
@Serializable(with = MarkSerializer::class)
data class Mark(val from: Int, val to: Int, val color: Int = 0, val field: Boolean = false) {
    init {
        require(!field || from == to) { "Ein Feld ist kein Pfeil" }
        require(color in 0 until Ink.PENS) { "Farbe ${color + 1} gibt es nicht" }
    }

    val circle: Boolean get() = from == to && !this.field

    /** Where it is and what it is, without the color: drawing the same shape in another color replaces it. */
    val shape: Mark get() = copy(color = 0)

    /** "d5" for a circle, "#d5" for a field, "e2e4" for an arrow, "2:" in front for pen 2 to 6, as it is saved. */
    override fun toString(): String {
        val body = when {
            field -> "#" + squareName(from)
            from == to -> squareName(from)
            else -> squareName(from) + squareName(to)
        }
        return if (color == 0) body else "${color + 1}:$body"
    }

    /** "Kreis d5 (Grün)", "Pfeil e2→e4 (Rot)", for feedback; [pens] says what the pens are set to. */
    fun describe(pens: List<PenColor> = Ink.DEFAULT): String {
        val noun = when {
            field -> "Feld"
            from == to -> "Kreis"
            else -> "Pfeil"
        }
        val where = if (from == to) squareName(from) else "${squareName(from)}→${squareName(to)}"
        return "$noun $where (${pens[color].label})"
    }

    companion object {
        /** How version 1.12.0 named the four pens it had. */
        private val OLD_NAMES = listOf("green", "red", "blue", "yellow")

        fun parse(text: String): Mark {
            val (prefix, rest) = if (':' in text) text.split(':', limit = 2).let { it[0] to it[1] } else null to text
            val color = when (prefix) {
                null -> 0
                in OLD_NAMES -> OLD_NAMES.indexOf(prefix)
                else -> prefix.toIntOrNull()?.minus(1)?.takeIf { it in 0 until Ink.PENS }
                    ?: throw IllegalArgumentException("Unbekannte Farbe „$prefix“")
            }
            val field = rest.startsWith("#")
            val squares = rest.removePrefix("#")
            require(squares.length == 2 || (squares.length == 4 && !field)) { "„$text“ ist weder ein Feld noch ein Pfeil" }
            val from = parseSquare(squares.substring(0, 2))
            return Mark(from, if (squares.length == 4) parseSquare(squares.substring(2)) else from, color, field)
        }
    }
}

object MarkSerializer : KSerializer<Mark> {
    override val descriptor = PrimitiveSerialDescriptor("repertoire.Mark", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Mark) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Mark = Mark.parse(decoder.decodeString())
}

/**
 * Arrows and circles kept with a variant, at the position after its first [ply] moves. They are
 * seen when going through the variant and, after the puzzle is solved, drawn from memory.
 */
@Serializable
data class Drawing(
    val ply: Int,
    val marks: List<Mark>,
    /** What is asked, e.g. "Wo sind die schwachen Felder?"; may be empty. */
    val question: String = "",
)

/** How a drawing from memory compares to the saved one. */
data class DrawingCheck(val right: Set<Mark>, val missing: Set<Mark>, val extra: Set<Mark>) {
    val correct: Boolean get() = missing.isEmpty() && extra.isEmpty()

    companion object {
        /** The color counts, and an arrow only in its direction; the order in which things are drawn does not. */
        fun of(saved: Drawing, drawn: Collection<Mark>): DrawingCheck {
            val want = saved.marks.toSet()
            val got = drawn.toSet()
            return DrawingCheck(want intersect got, want - got, got - want)
        }
    }
}

/**
 * The marks after drawing [mark], as on lichess: the same thing twice takes it away, the same shape
 * in another color takes the new color, anything else is added.
 */
fun List<Mark>.toggle(mark: Mark): List<Mark> = when {
    mark in this -> this - mark
    any { it.shape == mark.shape } -> map { if (it.shape == mark.shape) mark else it }
    else -> this + mark
}
