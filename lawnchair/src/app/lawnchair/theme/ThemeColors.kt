package app.lawnchair.theme

import android.content.Context
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.theme.color.resolveColor
import app.lawnchair.ui.theme.getSystemAccent

/**
 * Official theme values and the first common semantic resolver.
 *
 * Legacy's editable-elements color deliberately starts from the turquoise accent Prime currently
 * displays. Both official variants use that value for now; they can diverge later without changing
 * the role or persisted override format.
 */
object ThemeColors {
    fun official(
        context: Context,
        profile: ThemeProfile,
        role: ThemeColorRole,
        variant: ThemeVariant,
    ): Int {
        val officialVariant = profile.variants.officialFallbackFor(variant)
        return when (profile.id) {
            ThemeProfile.LEGACY.id -> legacy(context, role, officialVariant)
            else -> legacy(context, role, officialVariant)
        }
    }

    fun resolve(
        context: Context,
        profile: ThemeProfile,
        role: ThemeColorRole,
        variant: ThemeVariant,
    ): Int {
        val official = official(context, profile, role, variant)
        return when (val override = ThemeColorOverrides(context).get(profile, variant, role)) {
            ColorOption.Default -> official
            is ColorOption.CustomColor -> override.color
            else -> if (variant == ThemeVariant.DARK) {
                override.colorPreferenceEntry.darkColor(context)
            } else {
                override.colorPreferenceEntry.lightColor(context)
            }
        }
    }

    private fun legacy(context: Context, role: ThemeColorRole, variant: ThemeVariant): Int = when (role) {
        ThemeColorRole.GLOBAL_ACCENT -> LEGACY_EDITABLE_ELEMENTS
        // Preserve the exact pre-ThemeProfile drawer surface for each Legacy variant.
        ThemeColorRole.DRAWER_BACKGROUND -> ColorTokens.AllAppsBackground.resolveColor(
            context,
            variant == ThemeVariant.DARK,
        )
        // Roles are added to the official Legacy palette as their runtime consumers are wired.
        // Until then this fallback is preview-only and must not be treated as their final token.
        else -> context.getSystemAccent(variant == ThemeVariant.DARK)
    }

    private const val LEGACY_EDITABLE_ELEMENTS: Int = 0xFF80CBC4.toInt()
}
