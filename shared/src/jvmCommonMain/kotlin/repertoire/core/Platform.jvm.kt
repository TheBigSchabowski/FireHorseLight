package repertoire.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class DirectoryStore(private val dir: File) : FileStore {
    override val location: String get() = dir.absolutePath

    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    override fun write(name: String, text: String) {
        dir.mkdirs()
        val file = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun dayStart(now: Long, daysAhead: Long, hour: Int): Long {
    val zone = ZoneId.systemDefault()
    val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusHours(hour.toLong())
    return local.toLocalDate().plusDays(daysAhead).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
}

actual fun isoDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.")

actual fun shortDate(epochMillis: Long): String =
    SHORT_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

actual fun newId(): String = UUID.randomUUID().toString().take(8)
