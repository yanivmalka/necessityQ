package app.necessityq.platform

import android.content.Context
import java.io.File

/**
 * Keeps the stack trace of the last crash in a file, so the next launch can show it (and the user can copy it):
 * a phone has no logcat. The default handler still runs afterwards, so the app closes exactly as before.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 6000

    fun install(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(error).take(MAX_CHARS)
                file.writeText("thread: ${thread.name}\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, if the app crashed last time. */
    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
