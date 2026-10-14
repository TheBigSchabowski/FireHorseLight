package repertoire.core

import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * The engine as a process of its own. It runs in its own folder, so a network or book file lying
 * next to it is found; everything it writes goes through one reader thread into a channel.
 */
actual fun launchEngine(path: String): UciIo {
    val file = File(path.trim())
    check(file.isFile) { "Unter „$path“ liegt keine Datei." }
    check(file.canExecute()) { "„${file.name}“ ist nicht ausführbar (im Terminal: chmod +x \"${file.absolutePath}\")." }
    val process = try {
        ProcessBuilder(file.absolutePath).directory(file.parentFile).redirectErrorStream(true).start()
    } catch (e: IOException) {
        throw IllegalStateException("„${file.name}“ lässt sich nicht starten: ${e.message}")
    }
    val lines = Channel<String>(Channel.UNLIMITED)
    thread(isDaemon = true, name = "engine ${file.name}") {
        try {
            process.inputStream.bufferedReader().forEachLine { lines.trySend(it) }
        } catch (_: IOException) {
            // Closed from our side.
        } finally {
            lines.close()
        }
    }
    val writer = process.outputStream.bufferedWriter()
    return object : UciIo {
        override fun send(line: String) {
            try {
                writer.write(line)
                writer.newLine()
                writer.flush()
            } catch (_: IOException) {
                // The engine is gone; the reader says so with null.
            }
        }

        override suspend fun readLine(): String? = lines.receiveCatching().getOrNull()

        override fun close() {
            thread(isDaemon = true) {
                // A moment to quit by itself after "quit", then it goes anyway.
                if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
    }
}

/** Data files that lie next to engines but are none themselves. */
private val NOT_ENGINES = setOf("txt", "md", "json", "nnue", "pb", "gz", "bin", "dll", "dylib", "so", "cff", "sh", "py", "html", "pdf")

actual fun engineFilesIn(dir: String): List<String> {
    val root = File(dir)
    // The folder is there from the first look on, so it can be found to put engines in.
    root.mkdirs()
    if (!root.isDirectory) return emptyList()
    val candidates = root.listFiles().orEmpty().flatMap { if (it.isDirectory) it.listFiles().orEmpty().toList() else listOf(it) }
    return candidates.filter { f ->
        f.isFile && !f.isHidden && f.canExecute() && f.extension.lowercase() !in NOT_ENGINES
    }.map { it.absolutePath }.sorted()
}

actual fun processorCount(): Int = Runtime.getRuntime().availableProcessors()
