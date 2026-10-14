package repertoire

import repertoire.core.Exchange
import repertoire.core.Library
import repertoire.core.LineResponder
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.VoiceMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VoiceMoveTest {

    @Test
    fun parsesEnglishSpokenMovesAndHomophones() {
        var pos = Position.START

        // 1. e4 ("pawn to e four")
        val m1 = VoiceMove.parseSpokenEnglish("pawn to e four", pos)
        assertNotNull(m1)
        assertEquals("e2e4", m1.uci)
        assertEquals("e 4", VoiceMove.toSpokenEnglish(pos, m1))
        pos = pos.play(m1)

        // 1... e5 ("ee five")
        val m2 = VoiceMove.parseSpokenEnglish("ee five", pos)
        assertNotNull(m2)
        assertEquals("e7e5", m2.uci)
        pos = pos.play(m2)

        // 2. Nf3 ("knight f3" oder Whisper-Homophon "night ef three")
        val m3 = VoiceMove.parseSpokenEnglish("night ef three", pos)
        assertNotNull(m3)
        assertEquals("g1f3", m3.uci)
        assertEquals("Knight f 3", VoiceMove.toSpokenEnglish(pos, m3))
        pos = pos.play(m3)

        // 2... Nc6 ("knight c6")
        val m4 = VoiceMove.parseSpokenEnglish("knight c6", pos)
        assertNotNull(m4)
        assertEquals("b8c6", m4.uci)
        pos = pos.play(m4)

        // 3. Bc4 ("bishop c4" oder "bishop sea for")
        val m5 = VoiceMove.parseSpokenEnglish("bishop c4", pos)
        assertNotNull(m5)
        assertEquals("f1c4", m5.uci)
        assertEquals("Bishop c 4", VoiceMove.toSpokenEnglish(pos, m5))
    }

    @Test
    fun importsFireHorseJsonAndRepliesAlongSavedLine() {
        val json = """
            {
              "format": "eroeffnungs-puzzle",
              "version": 1,
              "variants": [
                {
                  "opening": "Italian Game",
                  "name": "Giuoco Piano",
                  "playWhite": true,
                  "moves": "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5"
                }
              ]
            }
        """.trimIndent()

        val imported = Exchange.import(json, Library(), Progress())
        assertEquals(1, imported.added)

        // Wir spielen 1. e4 und prüfen, ob LineResponder automatisch 1... e5 aus der FireHorse-JSON antwortet
        val afterE4 = Position.START.play(VoiceMove.parseSpokenEnglish("e4", Position.START)!!)
        val reply = LineResponder.replyFromVariants(afterE4, imported.library.active)
        assertNotNull(reply)
        assertEquals("e7e5", reply.move.uci)
    }
}
