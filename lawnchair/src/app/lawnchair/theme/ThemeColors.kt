package app.lawnchair.theme

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.theme.getSystemAccent

/**
 * Official theme values and the first common semantic resolver.
 *
 * Legacy official values are resolved from the same dynamic palette used by the launcher.
 * Fixed values are only used when the historical role itself was fixed.
 */
object ThemeColors {
    // The drawer can choose between several historical base colors depending on the live blur
    // state. Keep the last base actually selected by All Apps so settings can preview that exact
    // official value instead of guessing from a Context that has no launcher blur state.
    private val legacyDrawerOfficialByVariant = mutableMapOf<ThemeVariant, Int>()
    private val liveDrawerBackgroundByVariant = mutableMapOf<ThemeVariant, Int>()

    @JvmStatic
    fun recordLiveDrawerBackground(variant: ThemeVariant, color: Int) {
        liveDrawerBackgroundByVariant[variant] = color
    }

    @JvmStatic
    fun recordLegacyDrawerOfficial(variant: ThemeVariant, color: Int) {
        legacyDrawerOfficialByVariant[variant] = color
    }

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

    /**
     * Editable dynamic recipe equivalent to the official Legacy semantic value.
     * This is used only to seed the dynamic editor; runtime still resolves the real tokens above.
     */
    fun officialDynamicRecipe(role: ThemeColorRole, variant: ThemeVariant): ColorOption.DynamicColor? {
        fun recipe(swatch: String, shade: Int, lStar: Int? = null) =
            ColorOption.DynamicColor(swatch, shade, lStar)
        val dark = variant == ThemeVariant.DARK
        return when (role) {
            ThemeColorRole.GLOBAL_ACCENT -> if (dark) recipe("Accent1", 100, 72) else recipe("Accent1", 600, 42)
            ThemeColorRole.GLOBAL_SETTINGS_BACKGROUND -> if (dark) recipe("Neutral1", 500, 6) else recipe("Neutral1", 500, 98)
            ThemeColorRole.GLOBAL_SETTINGS_CARD_BACKGROUND -> if (dark) recipe("Neutral1", 500, 22) else recipe("Neutral1", 500, 90)
            // Match ColorTokens.DotColor in light and dark variants.
            ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> if (dark) recipe("Accent1", 200) else recipe("Accent2", 600)
            ThemeColorRole.HOME_POPUP_BACKGROUND -> if (dark) recipe("Accent2", 800, 20) else recipe("Accent2", 200, 98)
            ThemeColorRole.DRAWER_POPUP_BACKGROUND -> if (dark) recipe("Accent2", 800, 28) else recipe("Accent2", 200, 92)
            ThemeColorRole.HOME_POPUP_TEXT,
            ThemeColorRole.DRAWER_POPUP_TEXT,
            ThemeColorRole.HOME_POPUP_ICON,
            ThemeColorRole.DRAWER_POPUP_ICON,
            ThemeColorRole.DRAWER_SEARCH_TEXT,
            ThemeColorRole.DRAWER_SEARCH_HINT,
            ThemeColorRole.DRAWER_SEARCH_ICON,
            ThemeColorRole.TABS_CATEGORY_TEXT -> if (dark) recipe("Neutral1", 50) else recipe("Neutral1", 900)
            ThemeColorRole.DRAWER_BACKGROUND,
            ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> if (dark) recipe("Neutral2", 600, 6) else recipe("Neutral2", 600, 87)
            ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE,
            ThemeColorRole.DRAWER_SEARCH_BACKGROUND_ACTIVE,
            ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND ->
                if (dark) recipe("Accent2", 600, 34) else recipe("Accent2", 200, 72)
            ThemeColorRole.DRAWER_SEARCH_BORDER -> if (dark) recipe("Accent1", 100) else recipe("Accent1", 600)
            ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND ->
                if (dark) recipe("Accent2", 800, 14) else recipe("Accent2", 300, 58)
            // Blur highlight includes alpha, which the dynamic recipe editor cannot represent yet.
            ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND ->
                if (dark) recipe("Neutral1", 700) else recipe("Neutral1", 0)
            ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND,
            ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND,
            ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND,
            ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND,
            ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND,
            ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND ->
                if (dark) recipe("Accent2", 800, 28) else recipe("Accent2", 200, 92)
            else -> null
        }
    }

    /** Dock background: Prime override wins; otherwise preserve the existing Dock preference. */
    @JvmStatic
    fun resolveDockBackground(context: Context, legacyOption: ColorOption): Int {
        return resolveDockBackgroundForVariant(context, legacyOption, context.effectiveThemeVariant())
    }

    fun resolveDockBackgroundForVariant(context: Context, legacyOption: ColorOption, variant: ThemeVariant): Int {
        val role = ThemeColorRole.HOME_HOTSEAT_BACKGROUND
        val profile = ThemeProfile.current(context)
        if (ThemeColorOverrides(context).get(profile, variant, role) != ColorOption.Default) {
            return resolve(context, profile, role, variant)
        }
        return when (legacyOption) {
            ColorOption.Default -> official(context, profile, role, variant)
            is ColorOption.CustomColor -> legacyOption.color
            else -> if (variant == ThemeVariant.DARK) legacyOption.colorPreferenceEntry.darkColor(context)
                else legacyOption.colorPreferenceEntry.lightColor(context)
        }
    }

    /**
     * The Dock's uncustomized background follows the actual drawer renderer.
     * Keep the Dock's legacy preference and Prime overrides higher priority.
     * The caller supplies the live drawer color; this resolver never changes the drawer.
     */
    @JvmStatic
    fun resolveDockBackgroundFromDrawer(
        context: Context,
        legacyOption: ColorOption,
        drawerColor: Int?,
    ): Int {
        val profile = ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        val dockOverride = ThemeColorOverrides(context).get(
            profile, variant, ThemeColorRole.HOME_HOTSEAT_BACKGROUND,
        )
        return if (legacyOption == ColorOption.Default &&
            dockOverride == ColorOption.Default && drawerColor != null
        ) drawerColor else resolveDockBackgroundForVariant(context, legacyOption, variant)
    }

    /**
     * Harmonized open-folder title. The folder supplies its effective background, including
     * per-folder overrides. A user-selected text color always takes precedence.
     */
    @JvmStatic
    fun resolveOpenFolderTitleColor(
        context: Context,
        role: ThemeColorRole,
        backgroundColor: Int,
    ): Int = resolveOpenFolderTitleColorForVariant(
        context, role, backgroundColor, context.effectiveThemeVariant()
    )

    @JvmStatic
    fun resolveOpenFolderTitleColorForVariant(
        context: Context,
        role: ThemeColorRole,
        backgroundColor: Int,
        variant: ThemeVariant,
    ): Int {
        val profile = ThemeProfile.current(context)
        if (ThemeColorOverrides(context).get(profile, variant, role) != ColorOption.Default) {
            return resolve(context, profile, role, variant)
        }
        val backdrop = if (variant == ThemeVariant.DARK) Color.BLACK else Color.WHITE
        val opaqueBackground = ColorUtils.compositeColors(backgroundColor, backdrop)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(opaqueBackground, hsl)
        // Contrast depends on the actual surface, not merely on the theme variant.
        val lighten = ColorUtils.calculateLuminance(opaqueBackground) < 0.179
        var low = if (lighten) hsl[2] else 0f
        var high = if (lighten) 1f else hsl[2]
        repeat(20) {
            val mid = (low + high) / 2f
            hsl[2] = mid
            val candidate = ColorUtils.HSLToColor(hsl)
            if (ColorUtils.calculateContrast(candidate, opaqueBackground) >= 7.0) {
                if (lighten) high = mid else low = mid
            } else {
                if (lighten) low = mid else high = mid
            }
        }
        hsl[2] = if (lighten) high else low
        return ColorUtils.HSLToColor(hsl)
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
        ThemeColorRole.TABS_CATEGORY_TEXT ->
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
