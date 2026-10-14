package repertoire

import repertoire.core.Explorer
import repertoire.core.ExplorerSource
import repertoire.core.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplorerTest {
    /** Trimmed from a real answer of explorer.lichess.ovh/masters after 1. e4. */
    private val answer = """
        {"white":512431,"draws":641097,"black":411352,
         "moves":[{"uci":"c7c5","san":"c5","averageRating":2412,"white":180123,"draws":210456,"black":160789,"game":null},
                  {"uci":"e7e5","san":"e5","averageRating":2420,"white":120000,"draws":160000,"black":80000,"game":null}],
         "topGames":[],"opening":{"eco":"B00","name":"King's Pawn Game"}}
    """.trimIndent()

    @Test
    fun theAnswerIsRead() {
        val r = Explorer.parse(answer)
        assertEquals(listOf("c5", "e5"), r.moves.map { it.san })
        assertEquals(551368, r.moves.first().games)
        assertEquals("B00", r.opening?.eco)
        assertEquals(1564880, r.games)
    }

    @Test
    fun gamesAndPercentsAreWrittenTheGermanWay() {
        assertEquals("812", Explorer.count(812))
        assertEquals("45.678", Explorer.count(45_678))
        assertEquals("1,6 Mio.", Explorer.count(1_564_880))
        assertEquals(Triple(33, 45, 22), Explorer.percents(120000, 160000, 80000))
        assertEquals(Triple(0, 0, 0), Explorer.percents(0, 0, 0))
    }

    @Test
    fun thePositionGoesIntoTheAddressEncoded() {
        val url = Explorer.url(ExplorerSource.MASTERS, Position.START.fen)
        assertTrue(url.startsWith("https://explorer.lichess.org/masters?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR%20w%20KQkq%20-%200%201"), url)
        assertTrue(Explorer.url(ExplorerSource.LICHESS, Position.START.fen).contains("/lichess?") )
    }
}
