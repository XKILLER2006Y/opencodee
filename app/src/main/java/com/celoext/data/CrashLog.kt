package com.celoext.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Failures on the app side: a coroutine that throws, a thread that dies, a crash that takes the
 * process down. Kept in memory for Settings → Local server → App log and on disk, so the next
 * start still has the stack trace when the user files a bug report.
 *
 * The server log only covers the runtime; without this, anything thrown by the client itself was
 * invisible and a bug report showed a healthy server next to a broken screen.
 */
object CrashLog {
    private const val FILE_NAME = "app-log.txt"
    private const val MAX_ENTRIES = 60
    private const val MAX_STACK_CHARS = 4000
    /** When the file grows past this, it is rewritten from the in-memory entries. */
    private const val MAX_FILE_BYTES = 128_000

    /** Lines shown in the App log sheet; also read by the bug report. */
    val lines = MutableStateFlow<List<String>>(emptyList())

    private val lock = Any()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    @Volatile private var file: File? = null

    /** Called once from [com.celoext.OcApp] with the app's files directory. */
    fun attach(dir: File) {
        if (file != null) return
        val f = File(dir, FILE_NAME)
        file = f
        val old = runCatching { if (f.isFile) f.readLines() else emptyList() }.getOrDefault(emptyList())
        if (old.isNotEmpty()) synchronized(lock) { lines.value = (lines.value + old).takeLast(MAX_ENTRIES) }
    }

    /** Records [e] with a short [where] label ("background", "fatal main", a screen name). */
    fun record(where: String, e: Throwable) {
        runCatching { Log.e("CeloApp", "$where: ${e.javaClass.simpleName}: ${e.message}", e) }
        val stack = runCatching { e.stackTraceToString().take(MAX_STACK_CHARS).trimEnd() }.getOrDefault("(no stack trace)")
        synchronized(lock) {
            val text = buildString {
                append(stamp.format(Date()))
                append(' ').append(where).append(": ").append(e.javaClass.simpleName)
                e.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                append('\n').append(stack)
            }
            lines.value = (lines.value + text).takeLast(MAX_ENTRIES)
            write(text)
        }
    }

    /**
     * Records something that went wrong but was handled, like a dropped connection that is being
     * retried. No stack trace: the point is what happened, not where it was thrown.
     */
    fun note(where: String, message: String) {
        runCatching { Log.w("CeloApp", "$where: $message") }
        synchronized(lock) {
            val text = "${stamp.format(Date())} $where: $message"
            lines.value = (lines.value + text).takeLast(MAX_ENTRIES)
            write(text)
        }
    }

    private fun write(text: String) {
        val f = file ?: return
        runCatching {
            f.appendText(text + "\n")
            if (f.length() > MAX_FILE_BYTES) f.writeText(lines.value.joinToString("\n") + "\n")
        }
    }
}
