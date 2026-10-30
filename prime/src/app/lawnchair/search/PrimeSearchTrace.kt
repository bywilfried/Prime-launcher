package app.lawnchair.search

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory, bounded search diagnostics. Never records typed query contents. */
object PrimeSearchTrace {
    private val lines = ArrayDeque<String>()
    private const val MAX_LINES = 400
    @Volatile var enabled = false

    @Synchronized fun record(event: String) {
        if (!enabled) return
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        lines.addLast("$"+"time [$"+"{SystemClock.uptimeMillis()}] $"+"event")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized fun dump(): String =
        if (lines.isEmpty()) "Aucune trace. Activez le diagnostic, puis reproduisez le problème."
        else lines.joinToString("\n")

    @Synchronized fun clear() { lines.clear() }
}
