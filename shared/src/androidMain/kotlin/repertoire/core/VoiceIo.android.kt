package repertoire.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Set in `MainActivity.onCreate`, before the first [createSpeaker]. */
var appContext: Context? = null

/** The phone's text-to-speech in US English. Things said before it is ready wait for it. */
class AndroidSpeaker(context: Context) : Speaker {
    private var ready = false
    private val waiting = mutableListOf<String>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        synchronized(waiting) {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.US
                waiting.forEach(::speak)
            }
            waiting.clear()
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    override fun say(text: String) {
        synchronized(waiting) { if (ready) speak(text) else waiting += text }
    }

    override fun stop() {
        synchronized(waiting) { waiting.clear() }
        tts.stop()
    }
}

/** Silent when there is no context yet, e.g. in tests. */
actual fun createSpeaker(): Speaker = appContext?.let(::AndroidSpeaker) ?: object : Speaker {
    override fun say(text: String) {}
    override fun stop() {}
}

/**
 * Recording on the phone (16 kHz, mono, as on the computer). There is no Whisper here: what is said
 * is recognized only by sound, from the user's own recordings; anything else waits for the move it
 * meant. [askPermission] asks for the microphone the first time it is needed.
 */
class AndroidVoiceInput(
    private val context: Context,
    private val dir: java.io.File,
    private val askPermission: () -> Unit,
) : VoiceInput {
    private var record: android.media.AudioRecord? = null
    private var reader: Thread? = null
    private var buffer = java.io.ByteArrayOutputStream()

    private fun allowed() = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun problem(): String? = null

    override val canWriteDown: Boolean = false

    @Synchronized
    override fun start(): Boolean {
        if (record != null) return true
        if (!allowed()) {
            askPermission()
            return false
        }
        return runCatching {
            val rate = 16_000
            val size = maxOf(
                android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT),
                rate,
            )
            @Suppress("MissingPermission")
            val r = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, size,
            )
            r.startRecording()
            val out = java.io.ByteArrayOutputStream()
            buffer = out
            record = r
            reader = Thread({
                val chunk = ByteArray(3200)
                while (r.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    val n = r.read(chunk, 0, chunk.size)
                    if (n <= 0) break
                    synchronized(out) { out.write(chunk, 0, n) }
                }
            }, "microphone").apply { start() }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    override fun stop(): ByteArray? {
        val r = record ?: return null
        record = null
        runCatching { r.stop() }
        reader?.join(500)
        reader = null
        r.release()
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        if (pcm.size < 16_000 * 2 / 5) return null
        return AudioMatch.wav(pcm)
    }

    override fun cancel() {
        stop()
    }

    /** No Whisper on the phone: nothing is written down. */
    override fun transcribe(wav: ByteArray, prompt: String): String = ""

    override fun read(name: String): ByteArray? = java.io.File(dir, name).takeIf { it.isFile }?.readBytes()

    override fun list(dir: String): List<String> {
        val root = java.io.File(this.dir, dir)
        return root.walkTopDown().filter { it.isFile }.map { "$dir/" + it.relativeTo(root).invariantSeparatorsPath }.toList()
    }

    override fun play(name: String) {
        val file = java.io.File(dir, name).takeIf { it.isFile } ?: return
        runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        }
    }

    override fun delete(name: String) {
        java.io.File(dir, name).delete()
    }

    override fun keep(data: ByteArray, name: String): String {
        val file = java.io.File(dir, name)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        return file.path
    }
}
