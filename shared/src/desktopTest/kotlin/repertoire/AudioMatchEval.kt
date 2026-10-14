package repertoire

import repertoire.core.AudioMatch
import repertoire.core.VoiceTemplate
import repertoire.core.closestPhrase
import java.io.File
import kotlin.test.Test

/**
 * How well recognition by sound works on a folder of recordings, not a test that passes or fails:
 *
 *   FIREHORSE_AUDIO_EVAL=/folder ./gradlew :shared:desktopTest --tests repertoire.AudioMatchEval
 *
 * The folder has phrases.txt ("01|knight f 3" per line), the templates in tpl/ and any number of
 * other folders of recordings of the same phrases (01.wav …) to recognize.
 */
class AudioMatchEval {
    @Test
    fun evaluate() {
        val dir = System.getenv("FIREHORSE_AUDIO_EVAL")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val phrases = File(dir, "phrases.txt").readLines().filter { it.isNotBlank() }.associate { it.substringBefore('|') to it.substringAfter('|') }
        val templates = phrases.mapNotNull { (n, p) -> File(dir, "tpl/$n.wav").takeIf { it.exists() }?.let { VoiceTemplate(p, AudioMatch.features(it.readBytes())) } }
        val all = phrases.values.toSet()
        for (set in dir.listFiles()!!.filter { it.isDirectory && it.name != "tpl" }.sortedBy { it.name }) {
            var right = 0
            var clearRight = 0
            var clearWrong = 0
            val rightDistances = mutableListOf<Float>()
            val wrongDistances = mutableListOf<Float>()
            for ((n, p) in phrases) {
                val wav = File(set, "$n.wav").takeIf { it.exists() } ?: continue
                val match = templates.closestPhrase(AudioMatch.features(wav.readBytes()), all) ?: continue
                val ok = match.phrase == p
                if (ok) right++
                if (match.clear && ok) clearRight++
                if (match.clear && !ok) clearWrong++
                (if (ok) rightDistances else wrongDistances) += match.distance
                if (!ok) println("  ${set.name}: „$p“ → „${match.phrase}“ (${"%.1f".format(match.distance)} / ${"%.1f".format(match.runnerUp)})")
            }
            println(
                "${set.name}: $right/${phrases.size} richtig; eindeutig: $clearRight richtig, $clearWrong falsch; " +
                    "Abstand richtig ⌀${"%.1f".format(rightDistances.average())}, falsch ⌀${"%.1f".format(wrongDistances.average())}",
            )
        }
    }
}
