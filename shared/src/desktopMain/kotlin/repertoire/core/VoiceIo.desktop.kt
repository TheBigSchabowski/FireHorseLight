package repertoire.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class DesktopQwenSpeaker : Speaker {
    private val mutex = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private fun ensureBridge() {
        if (process?.isAlive == true) return
        val script = listOf(File("tools/qwen_tts_bridge.py"), File("../tools/qwen_tts_bridge.py"))
            .firstOrNull { it.exists() } ?: return
        val p = ProcessBuilder("python3", script.absolutePath).start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        reader = BufferedReader(InputStreamReader(p.inputStream))
        reader?.readLine() // wartet auf "READY"
    }

    override suspend fun speakEnglish(text: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                ensureBridge()
                val w = writer
                val r = reader
                if (w != null && r != null) {
                    w.write("SAY $text\n")
                    w.flush()
                    r.readLine()
                } else {
                    fallbackSpeak(text)
                }
            }.onFailure {
                fallbackSpeak(text)
            }
        }
    }

    private fun fallbackSpeak(text: String) {
        val os = System.getProperty("os.name").lowercase()
        runCatching {
            if ("mac" in os) {
                ProcessBuilder("say", text).start().waitFor()
            } else {
                ProcessBuilder("espeak-ng", "-v", "en-us", text).start().waitFor()
            }
        }
    }

    override fun close() {
        runCatching {
            writer?.write("QUIT\n")
            writer?.flush()
            process?.destroy()
        }
    }
}

actual fun createSpeaker(): Speaker = DesktopQwenSpeaker()
