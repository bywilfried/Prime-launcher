    private fun adaptiveTextAgainstSurface(backgroundColor: Int, variant: ThemeVariant): Int {
        return adaptiveTextAgainstSurface(backgroundColor, variant)
    }

    /** Preview and runtime deliberately share the same semantic resolution path. */
    fun preview(
        context: Context,
        profile: ThemeProfile,
        role: ThemeColorRole,
        variant: ThemeVariant,
    ): Int = resolve(context, profile, role, variant)

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


    /**
     * Resolves a Lawnchair token for an explicit Prime variant, bypassing a stale View theme.
     */
    @JvmStatic
    fun resolveLegacyToken(
        context: Context,
        token: app.lawnchair.theme.color.tokens.ColorToken,
        variant: ThemeVariant,
    ): Int = token.resolveColor(
        context,
        ThemeProvider.INSTANCE.get(context).colorScheme,
        if (variant == ThemeVariant.DARK) UiColorMode.Dark else UiColorMode.Light,
    )

    private fun legacy(context: Context, role: ThemeColorRole, variant: ThemeVariant): Int = when (role) {
        // This role historically follows the selected accent source and color style through
        // the dynamic palette. Resolve the same token used by the launcher.
        ThemeColorRole.GLOBAL_ACCENT ->
            resolveLegacyToken(context, ColorTokens.PrimeInteractiveColor, variant)
        ThemeColorRole.GLOBAL_SETTINGS_BACKGROUND ->
            resolveLegacyToken(context, ColorTokens.Surface, variant)
        ThemeColorRole.GLOBAL_SETTINGS_CARD_BACKGROUND ->
            resolveLegacyToken(context, ColorTokens.SurfaceContainerHighest, variant)
        ThemeColorRole.GLOBAL_NOTIFICATION_DOT ->
            resolveLegacyToken(context, ColorTokens.DotColor, variant)
        ThemeColorRole.HOME_FOLDER_PAGINATION,
        ThemeColorRole.DOCK_FOLDER_PAGINATION,
        ThemeColorRole.DRAWER_FOLDER_PAGINATION ->
            resolveLegacyToken(context, ColorTokens.pageIndicatorDotColor, variant)
        ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND,
        ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND,
        ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND,
        ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND,
        ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND,
        ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND,
        ThemeColorRole.DRAWER_POPUP_BACKGROUND ->
            resolveDrawerPopupBackground(context, variant)
        ThemeColorRole.HOME_POPUP_BACKGROUND ->
            resolveLegacyToken(context, ColorTokens.PopupShadeFirst, variant)
        ThemeColorRole.HOME_POPUP_TEXT,
        ThemeColorRole.DRAWER_POPUP_TEXT,
        ThemeColorRole.HOME_POPUP_ICON,
        ThemeColorRole.DRAWER_POPUP_ICON ->
            resolveLegacyToken(context, ColorTokens.TextColorPrimary, variant)
        // Mirror the historical drawer runtime branch so the default swatch matches the drawer.
        ThemeColorRole.DRAWER_BACKGROUND -> legacyDrawerBackground(context, variant)
        ThemeColorRole.HOME_HOTSEAT_BACKGROUND ->
            liveDrawerBackgroundByVariant[variant] ?: legacyDrawerBackground(context, variant)
        // Category tabs keep their historical contrast hierarchy, but derive their hue from the
        // current Lawnchair dynamic palette. Changing accent source/style therefore keeps the
        // drawer and its tabs visually coherent without storing fixed greys.
        // Search background and inactive category tabs intentionally share the same Legacy
        // dynamic recipe, not the same semantic value. They therefore evolve together with the
        // accent source/style while remaining independently overridable.
        ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE,
        ThemeColorRole.DRAWER_SEARCH_BACKGROUND_ACTIVE,
        ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND ->
            resolveLegacyToken(context, ColorTokens.AllAppsTabBackground, variant)
        ThemeColorRole.DRAWER_SEARCH_TEXT,
        ThemeColorRole.DRAWER_SEARCH_HINT,
        ThemeColorRole.DRAWER_SEARCH_ICON ->
            resolveLegacyToken(context, ColorTokens.TextColorPrimary, variant)
        ThemeColorRole.DRAWER_SEARCH_BORDER ->
            resolveLegacyToken(context, ColorTokens.ColorAccent, variant)
        ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND ->
            resolveLegacyToken(
                context,
                if (com.android.systemui.shared.system.BlurUtils.supportsBlursOnWindows()) {
                    ColorTokens.FocusHighlightBlur
                } else {
                    ColorTokens.FocusHighlight
                },
                variant,
            )
        ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND ->
            resolveLegacyToken(context, ColorTokens.AllAppsTabBackgroundSelected, variant)
        ThemeColorRole.TABS_CATEGORY_TEXT,
        ThemeColorRole.TABS_CATEGORY_ACTIVE_TEXT,
        ThemeColorRole.TABS_CATEGORY_INACTIVE_TEXT ->
            resolveLegacyToken(context, ColorTokens.TextColorPrimary, variant)
        // Roles are added to the official Legacy palette as their runtime consumers are wired.
        // Until then this fallback is preview-only and must not be treated as their final token.
        else -> context.getSystemAccent(variant == ThemeVariant.DARK)
    }

    private fun resolveDrawerPopupBackground(context: Context, variant: ThemeVariant): Int =
        resolveLegacyToken(context, ColorTokens.DrawerPopupBackground, variant)

    private fun legacyDrawerBackground(context: Context, variant: ThemeVariant): Int =
        legacyDrawerOfficialByVariant[variant] ?: resolveLegacyToken(
            context,
            ColorTokens.SurfaceDimColor,
            variant,
        )
}
