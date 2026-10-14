package repertoire

import repertoire.core.AudioMatch
import repertoire.core.VoiceTemplate
import repertoire.core.closestPhrase
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioMatchTest {
    /** A WAV of tones one after the other, each [ms] long, with a little silence around them. */
    private fun tones(vararg hz: Int, ms: Int = 200): ByteArray {
        val samples = mutableListOf<Short>()
        repeat(1600) { samples += 0 }
        for (f in hz) repeat(16 * ms) { i -> samples += (8000 * sin(2 * PI * f * i / 16_000)).toInt().toShort() }
        repeat(1600) { samples += 0 }
        val out = ByteArray(44 + samples.size * 2)
        "RIFF".encodeToByteArray().copyInto(out)
        samples.forEachIndexed { i, s ->
            out[44 + 2 * i] = (s.toInt() and 0xFF).toByte()
            out[45 + 2 * i] = (s.toInt() shr 8).toByte()
        }
        return out
    }

    private val templates = listOf(
        VoiceTemplate("rising", AudioMatch.features(tones(300, 900, 2000))),
        VoiceTemplate("falling", AudioMatch.features(tones(2000, 900, 300))),
        VoiceTemplate("low", AudioMatch.features(tones(250, 250, 400))),
    )

    @Test
    fun theSameSoundSaidSlowerIsRecognized() {
        val slow = templates.closestPhrase(AudioMatch.features(tones(300, 900, 2000, ms = 320)), setOf("rising", "falling", "low"))!!
        assertEquals("rising", slow.phrase)
        assertTrue(slow.clear)
        val fast = templates.closestPhrase(AudioMatch.features(tones(2000, 900, 300, ms = 140)), setOf("rising", "falling", "low"))!!
        assertEquals("falling", fast.phrase)
    }

    @Test
    fun onlyWhatCanBePlayedIsConsidered() {
        val match = templates.closestPhrase(AudioMatch.features(tones(300, 900, 2000)), setOf("falling", "low"))!!
        assertTrue(match.phrase != "rising")
        assertNull(templates.closestPhrase(AudioMatch.features(tones(300)), emptySet()))
    }

    @Test
    fun silenceHasNoFeaturesAndNoPeak() {
        val silence = ByteArray(44 + 32_000)
        assertEquals(0, AudioMatch.peak(silence))
        assertTrue(AudioMatch.features(silence).isEmpty())
        assertTrue(AudioMatch.peak(tones(440)) > 7000)
    }
}
