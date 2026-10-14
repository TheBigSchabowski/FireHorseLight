package repertoire

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import repertoire.core.EngineLine
import repertoire.core.EngineSession
import repertoire.core.Position
import repertoire.core.launchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Against a real engine, if one is given: FIREHORSE_TEST_ENGINE=/path/to/stockfish. Without it the
 * test has nothing to run against and passes, so the build needs no engine.
 */
class RealEngineTest {
    private val path: String? = System.getenv("FIREHORSE_TEST_ENGINE")?.takeIf { it.isNotBlank() }

    @Test
    fun aRealEngineAnalysesAPosition() = runBlocking {
        val path = path ?: return@runBlocking
        val session = EngineSession(launchEngine(path))
        try {
            session.start(mapOf("Threads" to "2", "Hash" to "64", "MultiPV" to "3"))
            assertTrue(session.name.isNotBlank())
            val afterE4 = Position.START.play(Position.START.parseSan("e4")!!)
            var seen = emptyList<EngineLine>()
            withTimeoutOrNull(2500) { session.analyse(afterE4) { seen = it } }
            assertTrue(seen.size == 3, "drei Linien: $seen")
            assertTrue(seen.all { afterE4.isLegal(repertoire.core.Move.fromUci(it.pv.first())) })
            assertTrue(seen.first().depth >= 10, "Tiefe ${seen.first().depth}")
            // And straight on to the next position, after the old search was stopped.
            seen = emptyList()
            withTimeoutOrNull(1500) { session.analyse(Position.START) { seen = it } }
            assertTrue(seen.isNotEmpty() && Position.START.isLegal(repertoire.core.Move.fromUci(seen.first().pv.first())))
        } finally {
            session.close()
        }
    }

    @Test
    fun theOpponentFindsItsSecondAndThirdBestMove() = runBlocking {
        val path = path ?: return@runBlocking
        val session = EngineSession(launchEngine(path))
        try {
            session.start(mapOf("Threads" to "2", "Hash" to "64", "MultiPV" to "3"))
            var found = emptyList<EngineLine>()
            session.analyse(Position.START, 10) { found = it }
            assertEquals(3, found.size, "drei Linien am Ende: $found")
            assertTrue(found.all { it.depth == 10 }, "alle auf Tiefe 10: $found")
            val moves = (1..3).map { repertoire.ui.opponentMove(found, it) }
            assertEquals(3, moves.toSet().size, "drei verschiedene Züge: $moves")
        } finally {
            session.close()
        }
    }

    @Test
    fun aMissingFileSaysSo() {
        val e = assertFailsWith<IllegalStateException> { launchEngine("/gibt/es/nicht/stockfish") }
        assertTrue(e.message!!.contains("keine Datei"))
    }
}
