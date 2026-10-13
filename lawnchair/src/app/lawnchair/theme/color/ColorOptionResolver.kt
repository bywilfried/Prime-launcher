package app.lawnchair.theme.color

import android.content.Context
import app.lawnchair.theme.ThemeVariant
import app.lawnchair.theme.effectiveThemeVariant

/**
 * Resolves a non-semantic color option for a specific theme variant.
 *
 * [ColorOption.Default] deliberately remains the inheritance sentinel (0). Property-specific
 * semantic resolvers are responsible for continuing their fallback chain when they receive it.
 */
fun ColorOption.resolveColor(context: Context, variant: ThemeVariant): Int = when (variant) {
    ThemeVariant.LIGHT -> colorPreferenceEntry.lightColor.invoke(context)
    ThemeVariant.DARK -> colorPreferenceEntry.darkColor.invoke(context)
}

/** Resolves this option against the variant currently applied by Prime Launcher. */
fun ColorOption.resolveColor(context: Context): Int =
    resolveColor(context, context.effectiveThemeVariant())
