package app.lawnchair.theme

/**
 * Stable identity for a Prime Launcher color theme.
 *
 * A theme is independent from the launcher appearance mode. The same profile can therefore expose
 * a light and a dark variant while SYSTEM simply selects the effective variant at runtime.
 */
@JvmInline
value class ThemeProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Theme profile id must not be blank" }
    }
}

/** The two editable color variants owned by a theme profile. */
enum class ThemeVariant {
    LIGHT,
    DARK,
}

/**
 * Describes which official variants a theme ships with.
 *
 * Missing official variants inherit from the available variant. User overrides can still target
 * either [ThemeVariant], so a dark-only (or light-only) theme can become adaptive after
 * customization without creating a second theme profile.
 */
data class ThemeVariantAvailability(
    val hasLight: Boolean,
    val hasDark: Boolean,
) {
    init {
        require(hasLight || hasDark) { "A theme must provide at least one official variant" }
    }

    fun officialFallbackFor(variant: ThemeVariant): ThemeVariant = when (variant) {
        ThemeVariant.LIGHT -> if (hasLight) ThemeVariant.LIGHT else ThemeVariant.DARK
        ThemeVariant.DARK -> if (hasDark) ThemeVariant.DARK else ThemeVariant.LIGHT
    }
}

/**
 * Metadata for a selectable Prime Launcher theme.
 *
 * Color role values deliberately live outside this class: this object is the stable identity used
 * to key official values, per-theme user overrides and future import/export data.
 */
data class ThemeProfile(
    val id: ThemeProfileId,
    val variants: ThemeVariantAvailability,
) {
    companion object {
        /** Compatibility profile for the current Lawnchair/Prime generated color scheme. */
        val DEFAULT = ThemeProfile(
            id = ThemeProfileId("prime_default"),
            variants = ThemeVariantAvailability(hasLight = true, hasDark = true),
        )
    }
}
