package app.lawnchair.theme

import android.content.Context
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
        if (override != ColorOption.Default) {
            return ThemeColors.resolve(context, profile, role, variant)
        }
        return when (legacyOption) {
            ColorOption.Default -> ThemeColors.official(context, profile, role, variant)
            is ColorOption.CustomColor -> legacyOption.color
            else -> if (variant == ThemeVariant.DARK) {
                legacyOption.colorPreferenceEntry.darkColor(context)
            } else {
                legacyOption.colorPreferenceEntry.lightColor(context)
            }
        }
    }
}
