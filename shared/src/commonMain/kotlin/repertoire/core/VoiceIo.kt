package repertoire.core

/**
 * Plattformübergreifende Sprachausgabe für die englischen Schachzüge ("Knight c 3", "Bishop c 4"):
 * - Auf macOS & Linux: `tools/qwen_tts_bridge.py` (mit deiner geklonten Stimme über Qwen-TTS oder System-Voice).
 * - Auf Android: Native `android.speech.tts.TextToSpeech` (Locale.US).
 */
interface Speaker {
    suspend fun speakEnglish(text: String)
    fun close()
}

expect fun createSpeaker(): Speaker
