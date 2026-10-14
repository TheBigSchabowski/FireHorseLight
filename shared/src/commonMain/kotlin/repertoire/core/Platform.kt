package repertoire.core

/** Where the library and the learning progress are kept (a folder on desktop, app storage on Android). */
interface FileStore {
    /** Shown to the user, e.g. the folder path. */
    val location: String

    fun read(name: String): String?

    /** Must replace the file atomically so a crash never leaves half-written progress behind. */
    fun write(name: String, text: String)
}

expect fun currentTimeMillis(): Long

/** Epoch millis of [hour] o'clock, [daysAhead] local days after the day containing `now - hour`. */
expect fun dayStart(now: Long, daysAhead: Long, hour: Int): Long

/** Local calendar date of [epochMillis] as "yyyy-mm-dd". */
expect fun isoDate(epochMillis: Long): String

/** Local calendar date of [epochMillis] as "dd.MM.". */
expect fun shortDate(epochMillis: Long): String

expect fun newId(): String

/** The system's save / open dialogs, for exporting and importing the library. */
interface FilePicker {
    /** Asks where to save [text]; [onDone] gets the chosen file's name, or null if cancelled. */
    fun save(suggestedName: String, text: String, onDone: (String?) -> Unit)

    /** Asks for a file; [onDone] gets its content, or null if cancelled. */
    fun open(onDone: (String?) -> Unit)
}
