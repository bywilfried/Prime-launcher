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
    @Volatile var keyboardTimingEnabled = false
    private var keyboardStartMs = 0L
    private var keyboardRequestMs = 0L
    private var keyboardVisibleLogged = false

    @Synchronized fun keyboardOpening() {
        if (!keyboardTimingEnabled) return
        keyboardStartMs = SystemClock.uptimeMillis()
        keyboardRequestMs = 0L
        keyboardVisibleLogged = false
        recordKeyboard("Prime Search opening")
    }

    @Synchronized fun keyboardRequested() {
        if (!keyboardTimingEnabled || keyboardStartMs == 0L) return
        keyboardRequestMs = SystemClock.uptimeMillis()
        recordKeyboard("IME request +" + (keyboardRequestMs - keyboardStartMs) + "ms")
    }

    @Synchronized fun keyboardVisibility(visible: Boolean) {
        if (!keyboardTimingEnabled || !visible || keyboardVisibleLogged || keyboardStartMs == 0L) return
        keyboardVisibleLogged = true
        val now = SystemClock.uptimeMillis()
        val requestDelay = if (keyboardRequestMs > 0L) (now - keyboardRequestMs).toString() else "unknown"
        recordKeyboard("IME visible +" + (now - keyboardStartMs) + "ms from opening, +" + requestDelay + "ms from request")
    }

    @Synchronized fun keyboardAnimation(event: String) {
        if (!keyboardTimingEnabled || keyboardStartMs == 0L) return
        val now = SystemClock.uptimeMillis()
        val requestDelay = if (keyboardRequestMs > 0L) (now - keyboardRequestMs).toString() else "unknown"
        recordKeyboard("IME animation " + event + " +" + (now - keyboardStartMs) +
            "ms from opening, +" + requestDelay + "ms from request")
    }

    @Synchronized private fun recordKeyboard(event: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        lines.addLast(time + " [" + SystemClock.uptimeMillis() + "] KEYBOARD " + event)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized fun record(event: String) {
        if (!enabled) return
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        lines.addLast("${time} [${SystemClock.uptimeMillis()}] ${event}")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized fun dump(): String =
        if (lines.isEmpty()) "Aucune trace. Activez le diagnostic, puis reproduisez le problème."
        else lines.joinToString("\n")

    @Synchronized fun clear() { lines.clear() }
}
