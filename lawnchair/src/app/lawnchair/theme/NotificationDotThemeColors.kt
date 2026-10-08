package app.lawnchair.theme

import android.content.Context
import android.graphics.Color
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption

/**
 * Bridge for Launcher3's Java DotRenderer consumers.
 *
 * Prime's theme default uses the same semantic color as its settings preview.
 * Explicit Legacy colors remain supported when no Prime override exists.
 */
object NotificationDotThemeColors {
    @JvmStatic
    fun resolve(context: Context, role: ThemeColorRole, legacyOption: ColorOption): Int {
        require(role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT ||
            role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT)
        val variant = context.effectiveThemeVariant()
        val profile = ThemeProfile.current(context)
        val override = ThemeColorOverrides(context).get(profile, variant, role)
        if (override == ColorOption.IconColor || (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT && override == ColorOption.Default && legacyOption == ColorOption.Default)) {
            // The real color is per icon; DeviceProfile uses this only as a fallback.
            return ThemeColors.official(context, profile, role, variant)
        }
        if (override != ColorOption.Default) {
            return ThemeColors.resolve(context, profile, role, variant)
        }
        return when (legacyOption) {
            ColorOption.Default -> {
                if (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT) {
                    val dotOption = PreferenceManager2.getInstance(context).notificationDotColor.firstCached()
                    val background = resolve(context, ThemeColorRole.GLOBAL_NOTIFICATION_DOT, dotOption)
                    contrastTextColor(background)
                } else {
                    ThemeColors.official(context, profile, role, variant)
                }
            }
            is ColorOption.CustomColor -> legacyOption.color
            else -> if (variant == ThemeVariant.DARK) {
                legacyOption.colorPreferenceEntry.darkColor(context)
            } else {
                legacyOption.colorPreferenceEntry.lightColor(context)
            }
        }
    }

    @JvmStatic
    fun isPerIconColor(context: Context): Boolean =
        ThemeColorOverrides(context).get(ThemeProfile.current(context), context.effectiveThemeVariant(),
            ThemeColorRole.GLOBAL_NOTIFICATION_DOT).let { it == ColorOption.IconColor || (it == ColorOption.Default && PreferenceManager2.getInstance(context).notificationDotColor.firstCached() == ColorOption.Default) }

    @JvmStatic
    fun isAutomaticText(context: Context): Boolean =
        ThemeColorOverrides(context).get(ThemeProfile.current(context), context.effectiveThemeVariant(),
            ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT) == ColorOption.Default &&
            PreferenceManager2.getInstance(context).notificationDotTextColor.firstCached() == ColorOption.Default

    @JvmStatic
    fun contrastForBackground(background: Int): Int = contrastTextColor(background)

    /** Choose the higher WCAG contrast ratio against the actual dot background. */
    private fun contrastTextColor(background: Int): Int {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(Color.red(background)) +
            0.7152 * linear(Color.green(background)) +
            0.0722 * linear(Color.blue(background))
        // Contrast(black) >= Contrast(white) when L >= sqrt(0.0525) - 0.05.
        return if (luminance >= 0.1791287847) Color.BLACK else Color.WHITE
    }
}
