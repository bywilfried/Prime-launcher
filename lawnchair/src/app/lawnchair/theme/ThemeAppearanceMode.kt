package app.lawnchair.theme

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.util.Themes

/**
 * Controls how Prime Launcher selects the effective light/dark variant of a theme profile.
 *
 * SYSTEM is an appearance policy, not a third color variant.
 */
enum class ThemeAppearanceMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStoredValue(value: String): ThemeAppearanceMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM

        fun current(context: Context): ThemeAppearanceMode =
            fromStoredValue(PreferenceManager.getInstance(context).launcherTheme.get())
    }
}

/**
 * Returns the color variant actually applied by the launcher.
 *
 * We intentionally use the launcher's themed context rather than Android's raw night-mode
 * configuration. This keeps forced LIGHT/DARK appearance modes correct while SYSTEM continues to
 * follow the platform through the existing launcher theme machinery.
 */
fun Context.effectiveThemeVariant(): ThemeVariant =
    if (Themes.isDarkTheme(this)) ThemeVariant.DARK else ThemeVariant.LIGHT
