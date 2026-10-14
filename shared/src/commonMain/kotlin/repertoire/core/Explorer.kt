package repertoire.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An answer from a web address: its HTTP status and text. */
class HttpResponse(val status: Int, val body: String)

/** GETs [url], with [token] as "Bearer" if there is one; throws on no connection. Runs off the main thread. */
expect suspend fun httpGet(url: String, token: String?): HttpResponse

/** lichess's opening database: the games of masters, or of lichess players. */
enum class ExplorerSource(val label: String, val path: String) {
    MASTERS("Meister", "masters"),
    LICHESS("lichess", "lichess"),
}

/** One move of a position in the database, with how the games went on from there. */
@Serializable
data class ExplorerMove(
    val uci: String,
    val san: String,
    val white: Long = 0,
    val draws: Long = 0,
    val black: Long = 0,
    val averageRating: Int? = null,
) {
    val games: Long get() = white + draws + black

    /**
     * This move on [position], or null if it is not legal there. Goes by the SAN first; lichess
     * writes castling in the UCI as the king taking its rook ("e1h1"), which is turned into the
     * king's two steps ("e1g1").
     */
    fun moveIn(position: Position): Move? {
        position.parseSan(san)?.let { return it }
        val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: return null
        val king = position.pieceAt(move.from)
        val rook = position.pieceAt(move.to)
        val castles = king != null && king.lowercaseChar() == 'k' && rook != null && rook.lowercaseChar() == 'r' &&
            king.isUpperCase() == rook.isUpperCase() && move.from / 8 == move.to / 8
        val played = if (castles) Move(move.from, move.from + if (move.to > move.from) 2 else -2) else move
        return played.takeIf(position::isLegal)
    }
}

@Serializable
data class ExplorerOpening(val eco: String = "", val name: String = "")

/** The database on a position: all its games, the moves played in them, and the opening's name if it has one. */
@Serializable
data class ExplorerResult(
    val white: Long = 0,
    val draws: Long = 0,
    val black: Long = 0,
    val moves: List<ExplorerMove> = emptyList(),
    val opening: ExplorerOpening? = null,
) {
    val games: Long get() = white + draws + black
}

object Explorer {
    /** As En Croissant asks it; the older explorer.lichess.ovh still answers the same. */
    private const val BASE = "https://explorer.lichess.org"

    /** Where a personal token is made: without any boxes ticked, it is enough for the database. */
    const val TOKEN_PAGE = "https://lichess.org/account/oauth/token"

    private val json = Json { ignoreUnknownKeys = true }

    /** The address that asks [source] about the position [fen]. On lichess: rated games from 1600 up, blitz and slower. */
    fun url(source: ExplorerSource, fen: String): String {
        val query = "fen=" + encode(fen) + "&moves=15&topGames=0&recentGames=0" +
            if (source == ExplorerSource.LICHESS) "&variant=standard&speeds=blitz,rapid,classical&ratings=1600,1800,2000,2200,2500" else ""
        return "$BASE/${source.path}?$query"
    }

    private fun encode(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c) else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }

    fun parse(body: String): ExplorerResult = json.decodeFromString(ExplorerResult.serializer(), body)

    /** Asks lichess; throws [IllegalStateException] with a German explanation when it does not work out. */
    suspend fun fetch(source: ExplorerSource, fen: String, token: String): ExplorerResult {
        check(token.isNotBlank()) { "Für die Datenbank braucht lichess deinen API-Schlüssel (siehe Konfiguration)." }
        val response = try {
            httpGet(url(source, fen), token.trim())
        } catch (e: CancellationException) {
            // The board moved on: not a connection problem.
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Keine Verbindung zu lichess (${e.message ?: e::class.simpleName}).")
        }
        return when (response.status) {
            200 -> try {
                parse(response.body)
            } catch (e: Exception) {
                throw IllegalStateException("lichess hat etwas Unerwartetes geschickt.")
            }
            401, 403 -> throw IllegalStateException("lichess nimmt den API-Schlüssel nicht an – ist er richtig eingetragen?")
            429 -> throw IllegalStateException("lichess bittet um eine kurze Pause (zu viele Anfragen). Gleich nochmal versuchen.")
            else -> throw IllegalStateException("lichess antwortet mit Fehler ${response.status}.")
        }
    }

    /** "12,3 Mio.", "45.678", "812": games counted the German way. */
    fun count(n: Long): String = when {
        n >= 1_000_000 -> {
            val tenths = (n + 50_000) / 100_000
            "${tenths / 10},${tenths % 10} Mio."
        }
        else -> n.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    /** White, draws, black of [white] + [draws] + [black] in whole percent, adding up to 100. */
    fun percents(white: Long, draws: Long, black: Long): Triple<Int, Int, Int> {
        val total = white + draws + black
        if (total == 0L) return Triple(0, 0, 0)
        val w = ((white * 100 + total / 2) / total).toInt()
        val b = ((black * 100 + total / 2) / total).toInt()
        return Triple(w, (100 - w - b).coerceAtLeast(0), b)
    }
}
