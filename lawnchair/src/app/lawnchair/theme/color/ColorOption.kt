package app.lawnchair.theme.color

import android.graphics.Color
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.tokens.Swatch
import app.lawnchair.theme.color.tokens.SwatchColorToken
import app.lawnchair.theme.color.tokens.Shade
import app.lawnchair.theme.color.tokens.setLStar
import com.android.launcher3.R
import com.android.launcher3.Utilities


private fun resolveDynamicColor(context: android.content.Context, recipe: ColorOption.DynamicColor, dark: Boolean): Int {
    val swatch = when (recipe.swatch.lowercase()) {
        "neutral1" -> Swatch.Neutral1
        "neutral2" -> Swatch.Neutral2
        "accent2" -> Swatch.Accent2
        "accent3" -> Swatch.Accent3
        else -> Swatch.Accent1
    }
    val shade = when (recipe.shade) {
        0 -> Shade.S0
        10 -> Shade.S10
        20 -> Shade.S20
        50 -> Shade.S50
        100 -> Shade.S100
        200 -> Shade.S200
        300 -> Shade.S300
        400 -> Shade.S400
        500 -> Shade.S500
        600 -> Shade.S600
        650 -> Shade.S650
        700 -> Shade.S700
        800 -> Shade.S800
        900 -> Shade.S900
        950 -> Shade.S950
        1000 -> Shade.S1000
        else -> Shade.S500
    }
    var token: app.lawnchair.theme.color.tokens.ColorToken = SwatchColorToken(swatch, shade)
    recipe.lStar?.let { token = token.setLStar(it.toDouble()) }
    return token.resolveColor(
        context,
        ThemeProvider.INSTANCE.get(context).colorScheme,
        if (dark) UiColorMode.Dark else UiColorMode.Light,
    )
}

sealed class ColorOption {

    abstract val isSupported: Boolean
    abstract val colorPreferenceEntry: ColorPreferenceEntry<ColorOption>

    object SystemAccent : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.system) },
            { context -> context.getSystemAccent(false) },
            { context -> context.getSystemAccent(true) },
        )

        override fun toString() = "system_accent"
    }

    object WallpaperPrimary : ColorOption() {
        override val isSupported = Utilities.ATLEAST_O_MR1

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.wallpaper) },
            { context ->
                val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
                val primaryColor = wallpaperManager.wallpaperColors?.primaryColor
                primaryColor ?: LawnchairBlue.color
            },
        )

        override fun toString() = "wallpaper_primary"
    }

    data class DynamicColor(
        val swatch: String,
        val shade: Int,
        val lStar: Int? = null,
    ) : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { "Dynamique · $swatch $shade" },
            { context -> resolveDynamicColor(context, this, false) },
            { context -> resolveDynamicColor(context, this, true) },
        )

        override fun toString() = buildString {
            append("dynamic|").append(swatch).append('|').append(shade)
            lStar?.let { append('|').append(it) }
        }
    }

    class CustomColor(val color: Int) : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.custom) },
            { color },
            { color },
        )

        constructor(color: Long) : this(color.toInt())

        override fun equals(other: Any?) = other is CustomColor && other.color == color

        override fun hashCode() = color

        override fun toString() = "custom|#${String.format("%08x", color)}"
    }

    /** Explicit black/white contrast option, independent from the theme default. */
    object AutomaticBlackWhite : ColorOption() {
        override val isSupported = true
        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this, { "Noir/blanc automatique" }, { Color.WHITE }, { Color.WHITE },
        )
        override fun toString() = "automatic_black_white"
    }

    /** Contrast-aware theme-hued icon and folder labels, resolved against their actual surface. */
    object AdaptiveThemeText : ColorOption() {
        override val isSupported = true
        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { "Contraste automatique" },
            { 0 },
            { 0 },
        )
        override fun toString() = "adaptive_theme_text"
    }

    /** Per-app notification dot background, resolved only when drawing an icon. */
    object IconColor : ColorOption() {
        override val isSupported = true
        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { "Selon la couleur de l'icône" },
            { 0 },
            { 0 },
        )
        override fun toString() = "icon_color"
    }

    object Default : ColorOption() {
        override val isSupported = false

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.managed_by_lawnchair) },
            { 0 },
        )

        override fun toString() = "default"
    }

    companion object {
        val LawnchairBlue = CustomColor(0xFF007FFF)

        fun fromString(stringValue: String) = when (stringValue) {
            "system_accent" -> SystemAccent
            "wallpaper_primary" -> WallpaperPrimary
            "default" -> Default
            "icon_color" -> IconColor
            "adaptive_theme_text" -> AdaptiveThemeText
            "automatic_black_white" -> AutomaticBlackWhite
            else -> if (stringValue.startsWith("dynamic|")) instantiateDynamicColor(stringValue)
            else instantiateCustomColor(stringValue)
        }

        private fun instantiateDynamicColor(stringValue: String): ColorOption {
            val parts = stringValue.split('|')
            if (parts.size < 3) return Default
            val shade = parts[2].toIntOrNull() ?: return Default
            val lStar = parts.getOrNull(3)?.toIntOrNull()
            return DynamicColor(parts[1], shade, lStar)
        }

        private fun instantiateCustomColor(stringValue: String): ColorOption {
            try {
                if (stringValue.startsWith("custom")) {
                    val color = Color.parseColor(stringValue.substring(7))
                    return CustomColor(color)
                }
            } catch (_: IllegalArgumentException) {
            }
            return when {
                Utilities.ATLEAST_S -> SystemAccent
                Utilities.ATLEAST_O_MR1 -> WallpaperPrimary
                else -> LawnchairBlue
            }
        }
    }
}
