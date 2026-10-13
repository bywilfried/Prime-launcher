package app.lawnchair.theme

import android.content.Context

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
        private const val PREFS_NAME = "prime_theme_profile"
        private const val KEY_SELECTED_PROFILE = "selected_profile"

        /** Registry of selectable profiles. Add future themes here without changing consumers. */
        val entries: List<ThemeProfile>
            get() = listOf(LEGACY)

        fun byId(id: ThemeProfileId): ThemeProfile? = entries.firstOrNull { it.id == id }

        fun current(context: Context): ThemeProfile {
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED_PROFILE, null)
            return stored?.let { byId(ThemeProfileId(it)) } ?: LEGACY
        }

        fun select(context: Context, profile: ThemeProfile) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_PROFILE, profile.id.value)
                .apply()
        }

        /**
         * Compatibility theme containing the colors Prime/Lawnchair used before named theme
         * profiles were introduced. It is intentionally called Legacy: LIGHT and DARK are
         * variants of this one theme, while SYSTEM remains only an appearance policy.
         */
        val LEGACY = ThemeProfile(
            id = ThemeProfileId("legacy"),
            variants = ThemeVariantAvailability(hasLight = true, hasDark = true),
        )

        /**
         * Source-compatibility alias while the remaining theme consumers migrate to [LEGACY].
         * Do not persist this name as a second theme identity.
         */
        @Deprecated("Use LEGACY")
        val DEFAULT: ThemeProfile
            get() = LEGACY
    }
}
