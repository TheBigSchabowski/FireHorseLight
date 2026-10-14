package repertoire.core

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Wird in `MainActivity.onCreate` gesetzt, damit Android TTS ohne Zusatz-Abhängigkeiten läuft. */
var appContext: Context? = null

class AndroidSpeaker : Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        appContext?.let { ctx ->
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ready = true
                }
            }
        }
    }

    override suspend fun speakEnglish(text: String): Unit = withContext(Dispatchers.Main) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fh_move")
        }
    }

    override fun close() {
        tts?.shutdown()
    }
}

actual fun createSpeaker(): Speaker = AndroidSpeaker()
