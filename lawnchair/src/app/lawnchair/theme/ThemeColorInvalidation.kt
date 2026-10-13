package app.lawnchair.theme

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local invalidation bus for semantic theme colors.
 *
 * Consumers subscribe once and re-resolve every ThemeColorRole they render when the effective
 * theme changes. The event deliberately carries no role: a variant/profile/palette change can
 * potentially affect every semantic color.
 */
object ThemeColorInvalidation {
    fun interface Listener {
        fun onThemeColorsInvalidated()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun invalidate() {
        listeners.forEach(Listener::onThemeColorsInvalidated)
    }
}
