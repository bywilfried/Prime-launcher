package app.lawnchair.search

import android.graphics.drawable.GradientDrawable
import android.content.Context
import app.lawnchair.theme.effectiveThemeVariant

/**
 * Reusable drawer section edge component. Adjacent result rows share a single
 * visual surface: only the first row rounds the top corners and only the last
 * row rounds the bottom corners.
 */
internal class DrawerSearchSectionBackground(
    context: Context,
    first: Boolean,
    last: Boolean,
) : GradientDrawable() {
    init {
        val density = context.resources.displayMetrics.density
        val radius = 14f * density
        val topRadius = if (first) radius else 0f
        val bottomRadius = if (last) radius else 0f
        cornerRadii = floatArrayOf(
            topRadius, topRadius, topRadius, topRadius,
            bottomRadius, bottomRadius, bottomRadius, bottomRadius,
        )
        val profile = app.lawnchair.theme.ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        setColor(
            app.lawnchair.theme.ThemeColors.resolve(
                context, profile,
                app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND,
                variant,
            ),
        )
        setStroke(
            density.toInt().coerceAtLeast(1),
            app.lawnchair.theme.ThemeColors.resolve(
                context, profile,
                app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER,
                variant,
            ),
        )
    }
}
