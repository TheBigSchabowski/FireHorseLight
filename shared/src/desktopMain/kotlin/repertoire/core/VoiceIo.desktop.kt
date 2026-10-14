package repertoire.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

private val isMac = System.getProperty("os.name").startsWith("Mac")

/**
 * A program by name: GUI apps on the Mac do not get the shell's PATH, so Homebrew's folders are
 * looked in as well.
 */
internal fun findProgram(name: String): String? {
    val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparator) +
        listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", System.getProperty("user.home") + "/.local/bin", System.getProperty("user.home") + "/.cargo/bin")
    return dirs.filter { it.isNotBlank() }.map { File(it, name) }.firstOrNull { it.canExecute() }?.path
}

/**
 * The computer's own voice: `say` with an English voice on the Mac, `espeak-ng` on Linux. One
 * thing after the other on a thread of its own.
 */
class DesktopSpeaker : Speaker {
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "speaker").apply { isDaemon = true } }

    @Volatile
    private var current: Process? = null

    /** Bumped by [stop]: what was queued before is dropped. */
    @Volatile
    private var generation = 0

    private val command: List<String>? by lazy {
        when {
            isMac -> listOf("say", "-v", "Daniel")
            findProgram("espeak-ng") != null -> listOf(findProgram("espeak-ng")!!, "-v", "en-us")
            findProgram("espeak") != null -> listOf(findProgram("espeak")!!, "-v", "en-us")
            findProgram("spd-say") != null -> listOf(findProgram("spd-say")!!, "-l", "en", "-w")
            else -> null
        }
    }

    override fun say(text: String) {
        val cmd = command ?: return
        val mine = generation
        queue.execute {
            if (mine != generation) return@execute
            runCatching {
                val p = ProcessBuilder(cmd + text).redirectErrorStream(true).start()
                current = p
                p.inputStream.readAllBytes()
                p.waitFor(30, TimeUnit.SECONDS)
            }
            current = null
        }
    }

    override fun stop() {
        generation++
        current?.destroy()
    }
}

actual fun createSpeaker(): Speaker = DesktopSpeaker()

/**
 * Records from the default microphone (16 kHz, 16 bit, mono, as Whisper wants it) and writes down
 * what was said with `voxtype transcribe`, in English and told to expect chess moves.
 */
class DesktopVoiceInput(private val dataDir: File) : VoiceInput {
    private val format = AudioFormat(16_000f, 16, 1, true, false)
    private var line: TargetDataLine? = null
    private var reader: Thread? = null
    private var buffer = ByteArrayOutputStream()

    private val voxtype: String? by lazy { findProgram("voxtype") }

    override fun problem(): String? = when {
        voxtype == null -> "voxtype ist nicht installiert (am Mac: brew install --cask voxtype)."
        !AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, format)) -> "Kein Mikrofon gefunden."
        else -> null
    }

    @Synchronized
    override fun start(): Boolean {
        if (line != null) return true
        return runCatching {
            val l = AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
            l.open(format)
            l.start()
            val out = ByteArrayOutputStream()
            buffer = out
            line = l
            reader = Thread({
                val chunk = ByteArray(3200)
                while (l.isOpen) {
                    val n = l.read(chunk, 0, chunk.size)
                    if (n <= 0) break
                    synchronized(out) { out.write(chunk, 0, n) }
                }
            }, "microphone").apply {
                isDaemon = true
                start()
            }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    override fun stop(): ByteArray? {
        val l = line ?: return null
        line = null
        l.stop()
        l.close()
        reader?.join(500)
        reader = null
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        // Shorter than a fifth of a second: a tap, not speech.
        if (pcm.size < 16_000 * 2 / 5) return null
        return wav(pcm)
    }

    @Synchronized
    override fun cancel() {
        stop()
    }

    override fun transcribe(wav: ByteArray, prompt: String): String {
        val program = voxtype ?: error(problem() ?: "voxtype fehlt.")
        val file = File.createTempFile("firehorselight-", ".wav")
        try {
            file.writeBytes(wav)
            val p = ProcessBuilder(program, "-q", "--language", "en", "--initial-prompt", prompt, "transcribe", file.path)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroy()
                error("voxtype hat zu lange gebraucht.")
            }
            return out.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
        } finally {
            file.delete()
        }
    }

    override fun read(name: String): ByteArray? = File(dataDir, name).takeIf { it.isFile }?.readBytes()

    override fun list(dir: String): List<String> {
        val root = File(dataDir, dir)
        return root.walkTopDown().filter { it.isFile }.map { "$dir/" + it.relativeTo(root).invariantSeparatorsPath }.toList()
    }

    override fun delete(name: String) {
        File(dataDir, name).delete()
    }

    override fun keep(data: ByteArray, name: String): String {
        val file = File(dataDir, name)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        return file.path
    }

    /** A WAV file around the raw samples. */
    private fun wav(pcm: ByteArray): ByteArray {
        val rate = format.sampleRate.toInt()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(pcm.size)
        }
        return header.array() + pcm
    }
}
