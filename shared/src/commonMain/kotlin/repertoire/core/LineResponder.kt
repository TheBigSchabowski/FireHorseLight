package repertoire.core

import kotlin.random.Random

/** Woher die Antwortzüge kommen: Aus deinen FireHorse-Varianten oder live aus der Lichess-Datenbank. */
enum class ResponseSource(val label: String) {
    FIREHORSE_LIBRARY("FireHorse Varianten"),
    LICHESS_MASTERS("Lichess Masters"),
    LICHESS_PLAYERS("Lichess Players"),
}

/** Wie der Computer bei Verzweigungen zieht. */
enum class ReplyStrategy(val label: String) {
    MAIN("1. Hauptlinie"),
    WEIGHTED("Zufällig gewichtet"),
    SIDELINES("2./3. Nebenvariante"),
}

/** Antwort des Computers inkl. Herkunfts-Info (z. B. "Sizilianisch › Scheveningen"). */
data class LineReply(val move: Move, val label: String)

object LineResponder {

    /**
     * Durchsucht alle aktiven [variants] aus deiner FireHorse-Library (optional gefiltert auf eine
     * bestimmte Eröffnung [openingFilter]) nach der aktuellen Stellung (`position.key`).
     * Erkennt automatisch auch Zugumstellungen (Transpositionen), genau wie `QuizBuilder` in FireHorse!
     */
    fun replyFromVariants(
        position: Position,
        variants: List<Variant>,
        openingFilter: String? = null,
        strategy: ReplyStrategy = ReplyStrategy.MAIN,
    ): LineReply? {
        val pool = variants.filter { !it.archived && (openingFilter == null || Library.openingOf(it) == openingFilter) }
        val key = position.key

        // Alle Züge finden, die von `position.key` in deinen gespeicherten Varianten weiterführen
        val matches = mutableListOf<Pair<Move, Variant>>()
        for (v in pool) {
            val positions = v.positions
            for (i in v.moves.indices) {
                if (positions[i].key == key) {
                    val move = Move.fromUci(v.moves[i])
                    if (position.isLegal(move)) {
                        matches += move to v
                    }
                    break
                }
            }
        }

        if (matches.isEmpty()) return null

        val grouped = matches.groupBy({ it.first }, { it.second }).entries.toList()
        val chosen = when (strategy) {
            ReplyStrategy.MAIN -> grouped.first()
            ReplyStrategy.SIDELINES -> grouped.getOrElse(1) { grouped.first() }
            ReplyStrategy.WEIGHTED -> grouped.random()
        }
        val titles = chosen.value.take(2).joinToString(" / ") { QuizBuilder.title(it) }
        return LineReply(chosen.key, titles)
    }

    /** Wählt eine Antwort aus der Lichess Opening Explorer Datenbank. */
    fun replyFromExplorer(
        position: Position,
        explorerResult: ExplorerResult,
        strategy: ReplyStrategy,
    ): LineReply? {
        val total = explorerResult.games.coerceAtLeast(1)
        val viable = explorerResult.moves
            .filter { it.games * 100 >= total || it == explorerResult.moves.firstOrNull() }
            .mapNotNull { em -> em.moveIn(position)?.let { it to em } }

        if (viable.isEmpty()) return null

        val (move, em) = when (strategy) {
            ReplyStrategy.MAIN -> viable.first()
            ReplyStrategy.SIDELINES -> viable.drop(1).take(2).randomOrNull() ?: viable.first()
            ReplyStrategy.WEIGHTED -> {
                val sum = viable.sumOf { it.second.games }.coerceAtLeast(1)
                var r = Random.nextLong(sum)
                viable.firstOrNull { (_, m) -> r -= m.games; r < 0 } ?: viable.first()
            }
        }
        val pct = (em.games * 100 / total).toInt()
        val openingName = explorerResult.opening?.name?.takeIf { it.isNotBlank() } ?: "Lichess"
        return LineReply(move, "$openingName ($pct%, ${Explorer.count(em.games)} games)")
    }
}
