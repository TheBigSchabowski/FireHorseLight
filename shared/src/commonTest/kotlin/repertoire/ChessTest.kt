package repertoire

import repertoire.core.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChessTest {
    private fun perft(p: Position, depth: Int): Long =
        if (depth == 0) 1 else p.legalMoves().sumOf { perft(p.play(it), depth - 1) }

    @Test
    fun perftStart() {
        assertEquals(20, perft(Position.START, 1))
        assertEquals(8902, perft(Position.START, 3))
        assertEquals(197281, perft(Position.START, 4))
    }

    @Test
    fun perftKiwipete() {
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48, perft(p, 1))
        assertEquals(2039, perft(p, 2))
        assertEquals(97862, perft(p, 3))
    }

    @Test
    fun perftEnPassantAndPromotion() {
        assertEquals(43238, perft(Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 4))
        assertEquals(62379, perft(Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 3))
    }

    @Test
    fun sanRoundTrip() {
        var p = Position.START
        for (san in listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6", "Be3", "e5", "Nb3", "Be6", "f3", "Nbd7", "Qd2", "Be7", "O-O-O")) {
            val move = assertNotNull(p.parseSan(san), "cannot parse $san in ${p.fen}")
            assertEquals(san, p.san(move))
            p = p.play(move)
        }
        // Lenient input
        val q = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3")
        assertEquals("Bb5", q.san(assertNotNull(q.parseSan("Bf1b5"))))
        assertEquals("Nxe5", q.san(assertNotNull(q.parseSan("Ne5"))))
    }

    @Test
    fun checkAndMateSuffix() {
        var p = Position.START
        for (san in listOf("f3", "e5", "g4")) p = p.play(p.parseSan(san)!!)
        assertEquals("Qh4#", p.san(p.parseSan("Qh4")!!))
    }

    @Test
    fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Position.fromFen(fen).fen)
    }
}
