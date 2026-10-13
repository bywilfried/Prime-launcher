package app.lawnchair.theme.color.tokens

import android.R
import android.content.res.ColorStateList

object ColorStateListTokens {

    val AllAppsTabTextLight = NewColorStateList { context, scheme, uiColorMode ->
        val states = arrayOf(
            intArrayOf(R.attr.state_selected),
            intArrayOf(),
        )
        val role = app.lawnchair.theme.ThemeColorRole.DRAWER_PROFILE_TAB_TEXT
        val variant = if (uiColorMode.isDarkTheme) app.lawnchair.theme.ThemeVariant.DARK
            else app.lawnchair.theme.ThemeVariant.LIGHT
        val customized = app.lawnchair.theme.ThemeColorOverrides(context).get(
            app.lawnchair.theme.ThemeProfile.current(context), variant, role,
        ) != app.lawnchair.theme.color.ColorOption.Default
        val colors = intArrayOf(
            if (customized) app.lawnchair.theme.ThemeColors.resolveFolderPaginationColor(context, role)
            else AllAppsTabColors.selectedText(context, scheme, uiColorMode),
            if (customized) app.lawnchair.theme.ThemeColors.resolveFolderPaginationColor(context, role)
            else ColorTokens.TextColorSecondary.resolveColor(context, scheme, uiColorMode),
        )
        ColorStateList(states, colors)
    }

    val AllAppsTabTextDark = NewColorStateList { context, scheme, uiColorMode ->
        val states = arrayOf(
            intArrayOf(R.attr.state_selected),
            intArrayOf(),
        )
        val role = app.lawnchair.theme.ThemeColorRole.DRAWER_PROFILE_TAB_TEXT
        val variant = if (uiColorMode.isDarkTheme) app.lawnchair.theme.ThemeVariant.DARK
            else app.lawnchair.theme.ThemeVariant.LIGHT
        val customized = app.lawnchair.theme.ThemeColorOverrides(context).get(
            app.lawnchair.theme.ThemeProfile.current(context), variant, role,
        ) != app.lawnchair.theme.color.ColorOption.Default
        val colors = intArrayOf(
            if (customized) app.lawnchair.theme.ThemeColors.resolveFolderPaginationColor(context, role)
            else AllAppsTabColors.selectedText(context, scheme, uiColorMode),
            if (customized) app.lawnchair.theme.ThemeColors.resolveFolderPaginationColor(context, role)
            else ColorTokens.TextColorSecondary.resolveColor(context, scheme, uiColorMode),
        )
        ColorStateList(states, colors)
    }

    @JvmField val AllAppsTabText = DayNightColorStateList(AllAppsTabTextLight, AllAppsTabTextDark)
}
