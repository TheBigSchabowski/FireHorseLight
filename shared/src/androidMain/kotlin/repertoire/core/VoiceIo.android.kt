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
