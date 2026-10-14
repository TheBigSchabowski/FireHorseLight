package repertoire.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The export file: readable and editable by hand, e.g.
 *
 * ```json
 * {
 *   "format": "eroeffnungs-puzzle",
 *   "version": 1,
 *   "variants": [
 *     {
 *       "opening": "Sizilianisch",
 *       "name": "Scheveningen",
 *       "playWhite": false,
 *       "moves": "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. f3 e6",
 *       "theory": "Gegen g4 hilft ((Nfd7))."
 *     }
 *   ]
 * }
 * ```
 *
 * "id" and "progress" are optional; without an id a variant is always added as new. "startFen" only
 * appears for a variant that does not begin at the start of a game, and "moves" then counts from there.
 * "drawings" are the saved arrows and circles after "ply" moves, e.g.
 * `{"ply": 12, "marks": ["d5", "2:f3f5", "#e4"], "question": "Plan?"}`: a circle, an arrow with pen 2,
 * a filled field; without a number it is pen 1.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String = "",
    val variants: List<ExportedVariant> = emptyList(),
) {
    companion object {
        const val FORMAT = "eroeffnungs-puzzle"
    }
}

@Serializable
data class ExportedVariant(
    val id: String? = null,
    val opening: String = "",
    val name: String,
    val playWhite: Boolean = true,
    /** SAN with or without move numbers ("1. e4 c5 2. Nf3"); UCI ("e2e4") works as well. */
    val moves: String,
    val theory: String = "",
    /** Left out for a variant that starts at the beginning of a game, which is the normal case. */
    val startFen: String? = null,
    /** Arrows ("e2e4") and circles ("d5") after `ply` moves, each with its question. */
    val drawings: List<Drawing> = emptyList(),
    /** Learning state; left out, the variant starts as new. */
    val progress: ReviewState? = null,
    /** In the archive: kept, but not trained. */
    val archived: Boolean = false,
)

data class ImportResult(
    val library: Library,
    val progress: Progress,
    val added: Int,
    val updated: Int,
    val errors: List<String>,
    /** Variants that were there before and are gone, when the file replaced everything. */
    val removed: Int = 0,
    val replaced: Boolean = false,
) {
    fun summary(): String = buildString {
        append(if (replaced) "$added geladen, $removed alte gelöscht" else "$added neu, $updated aktualisiert")
        if (errors.isNotEmpty()) append(", ${errors.size} übersprungen:\n" + errors.joinToString("\n"))
    }
}

object Exchange {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(variants: List<Variant>, progress: Progress, withProgress: Boolean = true): String =
        json.encodeToString(
            ExportFile(
                exportedAt = isoDate(currentTimeMillis()),
                variants = variants.map { v ->
                    ExportedVariant(
                        id = v.id,
                        opening = v.opening,
                        name = v.name,
                        playWhite = v.playWhite,
                        moves = compactMoves(v.numberedSan()),
                        theory = v.theory,
                        startFen = v.startFen.takeIf { v.setUp },
                        drawings = v.drawings,
                        progress = if (withProgress) progress.cards[v.id] else null,
                        archived = v.archived,
                    )
                },
            ),
        )

    /** How many variants the export file [text] holds; throws [IllegalArgumentException] if it is none. */
    fun variantsIn(text: String): Int = read(text).variants.size

    private fun read(text: String): ExportFile {
        val file = try {
            json.decodeFromString<ExportFile>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Keine gültige Export-Datei: ${e.message?.lineSequence()?.first()}")
        }
        require(file.format == ExportFile.FORMAT) { "Unbekanntes Format „${file.format}“" }
        return file
    }

    /**
     * Adds the file's variants to [library]. A variant whose id already exists replaces it; its
     * learning state is taken from the file, or dropped if the file has none and the moves changed.
     *
     * With [replace], the variants there were are deleted with their learning state, and only the
     * file's are left (pins and playlists keep what is still there; notes and settings stay). A file
     * without a single usable variant replaces nothing.
     */
    fun import(text: String, library: Library, progress: Progress, replace: Boolean = false): ImportResult {
        val file = read(text)

        var variants = if (replace) emptyList() else library.variants
        var cards = if (replace) emptyMap() else progress.cards
        var added = 0
        var updated = 0
        val errors = mutableListOf<String>()
        for (e in file.variants) {
            val label = listOf(e.opening, e.name).filter(String::isNotBlank).joinToString(" › ")
            // Read back and written out again, so a hand-written FEN ends up in the usual shape.
            val start = try {
                Position.fromFen(e.startFen?.takeIf(String::isNotBlank) ?: Position.START_FEN)
                    .also { p -> p.problem()?.let { throw IllegalArgumentException(it) } }
            } catch (ex: IllegalArgumentException) {
                errors += "$label: ${ex.message}"
                continue
            }
            val moves = try {
                parseMoves(e.moves, start)
            } catch (ex: IllegalArgumentException) {
                errors += "$label: ${ex.message}"
                continue
            }
            if (moves.isEmpty() || e.name.isBlank()) {
                errors += "${label.ifEmpty { "(ohne Namen)" }}: Name oder Züge fehlen"
                continue
            }
            val id = e.id?.takeIf(String::isNotBlank) ?: newId()
            // A drawing after a move the line does not have would show up nowhere.
            val drawings = e.drawings.filter { it.ply in 0..moves.size && it.marks.isNotEmpty() }
            val variant = Variant(id, e.opening.trim(), e.name.trim(), moves, e.playWhite, e.theory, start.fen, drawings, e.archived)
            val old = variants.firstOrNull { it.id == id }
            if (old != null) {
                variants = variants.map { if (it.id == id) variant else it }
                updated++
            } else {
                variants = variants + variant
                added++
            }
            cards = when {
                e.progress != null -> cards + (id to e.progress)
                old != null && !old.sameLine(variant) -> cards - id
                else -> cards
            }
        }
        if (!replace) return ImportResult(library.copy(variants = variants), progress.copy(cards = cards), added, updated, errors)
        require(variants.isNotEmpty()) {
            "Die Datei hat keine brauchbare Variante – es wurde nichts ersetzt." + errors.joinToString("") { "\n$it" }
        }
        val ids = variants.map { it.id }.toSet()
        val openings = variants.map(Library::openingOf).toSet()
        val replaced = library.copy(
            variants = variants,
            pinned = library.pinned.filter { it in openings },
            playlists = library.playlists.map { p -> p.copy(variantIds = p.variantIds.filter { it in ids }) },
        )
        val removed = library.variants.count { it.id !in ids }
        return ImportResult(replaced, progress.copy(cards = cards), added, updated, errors, removed, replaced = true)
    }

    /** "1. e4 c5 2. Nf3", "1.e4 c5", "e2e4 c7c5" → UCI moves, counted from [from]. */
    fun parseMoves(text: String, from: Position = Position.START): List<String> {
        var position = from
        val result = mutableListOf<String>()
        val tokens = text.split(Regex("\\s+"))
            .map { it.replace(Regex("^\\d+\\.+"), "") }
            .filter { it.isNotEmpty() && it !in RESULTS }
        for (token in tokens) {
            val move = position.parseSan(token)
                ?: runCatching { Move.fromUci(token) }.getOrNull()?.takeIf(position::isLegal)
                ?: throw IllegalArgumentException("„$token“ ist nach ${played(from, result)} nicht möglich")
            result += move.uci
            position = position.play(move)
        }
        return result
    }

    private val RESULTS = setOf("*", "1-0", "0-1", "1/2-1/2", "½-½", "1/2")

    /** The moves read so far, to say where a move that does not fit went wrong. */
    private fun played(from: Position, uci: List<String>): String =
        if (uci.isEmpty()) {
            "der Ausgangsstellung"
        } else {
            compactMoves(Variant("", "", "", uci, startFen = from.fen).numberedSan())
        }
}
