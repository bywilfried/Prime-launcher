package app.lawnchair.theme.color.tokens

import android.content.Context
import androidx.core.graphics.luminance
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.ThemeVariant
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.resolveColor
import dev.kdrag0n.monet.theme.ColorScheme

/** Shared colors for Personal/Work tabs in the app drawer. */
object AllAppsTabColors {

    fun selectedBackground(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val prefs2 = PreferenceManager2.getInstance(context)
        val variant = if (uiColorMode.isDarkTheme) ThemeVariant.DARK else ThemeVariant.LIGHT

        val workColor = prefs2.workProfileTabBackgroundColor.firstCached()
            .resolveColor(context, variant)
        if (workColor != 0) return workColor

        val masterColor = prefs2.tabsColor.firstCached()
            .resolveColor(context, variant)
        return if (masterColor != 0) {
            masterColor
        } else {
            ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode)
        }
    }

    /**
     * Text color for the selected tab, chosen for contrast against [selectedBackground].
     */
    fun selectedText(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val background = selectedBackground(context, scheme, uiColorMode)
        return if (background.luminance > 0.5f) {
            ColorTokens.Neutral1_900.resolveColor(context, scheme, uiColorMode)
        } else {
            ColorTokens.Neutral1_50.resolveColor(context, scheme, uiColorMode)
        }
    }
}
