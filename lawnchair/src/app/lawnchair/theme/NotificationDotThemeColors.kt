package app.lawnchair.theme

import android.content.Context
import app.lawnchair.theme.color.ColorOption

/**
 * Bridge for Launcher3's Java DotRenderer consumers.
 *
 * Preserve the existing preference semantics (including Default = 0, which
 * allows DotRenderer to choose its automatic color) until a Prime override exists.
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
            ColorOption.Default -> 0
            is ColorOption.CustomColor -> legacyOption.color
            else -> if (variant == ThemeVariant.DARK) {
                legacyOption.colorPreferenceEntry.darkColor(context)
            } else {
                legacyOption.colorPreferenceEntry.lightColor(context)
            }
        }
    }
}
