package repertoire.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Recognition by sound, from the user's own recordings: a move said now is compared with every
 * recording made of the moves that can be played, and the one it sounds most like wins. How it is
 * said does not matter – "castles kingside", "short castle" or "kurze Rochade" recorded for the
 * same move all lead to it – only that it was said like that before.
 *
 * The sound is described the classic way, by MFCCs (the spectrum's shape on the mel scale, every
 * 10 ms), and two recordings are compared by dynamic time warping, so speaking faster or slower
 * does not count as different.
 */
object AudioMatch {
    private const val RATE = 16_000
    private const val FRAME = 400 // 25 ms
    private const val HOP = 160 // 10 ms
    private const val FFT = 512
    private const val MELS = 26
    private const val COEFFS = 13

    /** The 16-bit samples of a WAV file (16 kHz, mono), without its 44-byte header. */
    fun samples(wav: ByteArray): ShortArray {
        val start = if (wav.size > 44 && wav[0] == 'R'.code.toByte() && wav[1] == 'I'.code.toByte()) 44 else 0
        val n = (wav.size - start) / 2
        return ShortArray(n) { i -> ((wav[start + 2 * i].toInt() and 0xFF) or (wav[start + 2 * i + 1].toInt() shl 8)).toShort() }
    }

    /** The loudest sample, 0 to 32767: a few hundred at most is a microphone that hands over silence. */
    fun peak(wav: ByteArray): Int = samples(wav).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0

    /** MFCCs of [wav], frame by frame, silence at the start and end cut off; empty if there is nothing. */
    fun features(wav: ByteArray): Array<FloatArray> {
        val pcm = samples(wav)
        if (pcm.size < FRAME) return emptyArray()
        // Pre-emphasis lifts the high frequencies, where consonants live.
        val x = FloatArray(pcm.size) { i -> pcm[i] / 32768f - if (i > 0) 0.97f * pcm[i - 1] / 32768f else 0f }
        val frames = (x.size - FRAME) / HOP + 1
        val energy = FloatArray(frames)
        val mfcc = Array(frames) { f ->
            val re = DoubleArray(FFT)
            val im = DoubleArray(FFT)
            for (i in 0 until FRAME) re[i] = x[f * HOP + i] * HAMMING[i]
            fft(re, im)
            val power = DoubleArray(FFT / 2 + 1) { k -> (re[k] * re[k] + im[k] * im[k]) / FFT }
            energy[f] = power.sum().toFloat()
            val mel = DoubleArray(MELS) { m -> ln(MEL_FILTERS[m].indices.sumOf { k -> MEL_FILTERS[m][k] * power[k] } + 1e-10) }
            FloatArray(COEFFS) { c -> (0 until MELS).sumOf { m -> mel[m] * DCT[c][m] }.toFloat() }
        }
        // Speech is where the energy is: at least a hundredth of the loudest frame, give or take 50 ms.
        val loudest = energy.maxOrNull() ?: return emptyArray()
        val voiced = energy.indices.filter { energy[it] > loudest / 100 }
        if (voiced.isEmpty()) return emptyArray()
        val from = max(0, voiced.first() - 5)
        val to = min(frames, voiced.last() + 6)
        // Without c0 (loudness), each coefficient around its mean: the microphone and the distance to it count less.
        val kept = Array(to - from) { mfcc[from + it].copyOfRange(1, COEFFS) }
        for (c in 0 until COEFFS - 1) {
            val mean = kept.sumOf { it[c].toDouble() } / kept.size
            for (frame in kept) frame[c] -= mean.toFloat()
        }
        return kept
    }

    /** How different two recordings sound: the average frame distance along the best alignment. */
    fun distance(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
        val n = a.size
        val m = b.size
        var prev = FloatArray(m + 1) { Float.MAX_VALUE }
        prev[0] = 0f
        for (i in 1..n) {
            val cur = FloatArray(m + 1) { Float.MAX_VALUE }
            for (j in 1..m) {
                val best = min(prev[j - 1], min(prev[j], cur[j - 1]))
                if (best != Float.MAX_VALUE) cur[j] = best + frameDistance(a[i - 1], b[j - 1])
            }
            prev = cur
        }
        return prev[m] / (n + m)
    }

    private fun frameDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    private val HAMMING = DoubleArray(FRAME) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1)) }

    private fun hzToMel(hz: Double) = 2595 * log10(1 + hz / 700)
    private fun melToHz(mel: Double) = 700 * (10.0.pow(mel / 2595) - 1)

    /** Triangular filters on the mel scale from 20 Hz to 8 kHz, over the FFT bins. */
    private val MEL_FILTERS: Array<DoubleArray> = run {
        val low = hzToMel(20.0)
        val high = hzToMel(RATE / 2.0)
        val points = DoubleArray(MELS + 2) { melToHz(low + (high - low) * it / (MELS + 1)) }
        val bins = points.map { (it * FFT / RATE) }
        Array(MELS) { m ->
            DoubleArray(FFT / 2 + 1) { k ->
                val (l, c, r) = Triple(bins[m], bins[m + 1], bins[m + 2])
                when {
                    k < l || k > r -> 0.0
                    k <= c -> (k - l) / (c - l)
                    else -> (r - k) / (r - c)
                }
            }
        }
    }

    private val DCT: Array<DoubleArray> = Array(COEFFS) { c -> DoubleArray(MELS) { m -> cos(PI * c * (m + 0.5) / MELS) } }

    /** In-place radix-2 FFT; [re] and [im] have a power-of-two length. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(angle * k)
                    val wi = sin(angle * k)
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
    }
}

/** One recording of [phrase] (a phrase of the speech training, e.g. "knight f3"), as features. */
class VoiceTemplate(val phrase: String, val features: Array<FloatArray>)

/** What a recording sounded most like: [phrase], and how sure that is. */
data class SoundMatch(val phrase: String, val distance: Float, val runnerUp: Float) {
    /** Clearly closer to this phrase than to any other: the next one is at least a fifth further away. */
    val clear: Boolean get() = distance < runnerUp * 0.8f
}

/**
 * The phrase among [allowed] whose recordings sound most like [features]; each phrase counts with
 * its closest recording. Null without recordings of at least one allowed phrase.
 */
fun List<VoiceTemplate>.closestPhrase(features: Array<FloatArray>, allowed: Set<String>): SoundMatch? {
    if (features.isEmpty()) return null
    val byPhrase = filter { it.phrase in allowed }.groupBy { it.phrase }
        .mapValues { (_, ts) -> ts.minOf { AudioMatch.distance(features, it.features) } }
        .entries.sortedBy { it.value }
    val best = byPhrase.firstOrNull() ?: return null
    return SoundMatch(best.key, best.value, byPhrase.getOrNull(1)?.value ?: Float.MAX_VALUE)
}
