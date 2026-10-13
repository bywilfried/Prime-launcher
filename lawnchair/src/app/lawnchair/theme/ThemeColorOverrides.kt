package app.lawnchair.theme

import android.content.Context
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.theme.color.ColorOption

/**
 * Sparse per-theme/per-variant overrides.
 *
 * Only customized roles are stored. Removing a key means "theme value".
 * Keys include ThemeProfileId so switching themes never destroys another theme's customization.
 */
class ThemeColorOverrides(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole): ColorOption =
        prefs.getString(key(profile, variant, role), null)
            ?.let(ColorOption::fromString)
            ?: ColorOption.Default

    fun set(profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole, option: ColorOption) {
        val editor = prefs.edit()
        val key = key(profile, variant, role)
        if (option == ColorOption.Default) editor.remove(key) else editor.putString(key, option.toString())
        editor.apply()
        notifyThemeChanged()
        if (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT ||
            role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT) {
            // DotRenderer instances are created inside DeviceProfile. Recreating
            // the activity alone does not rebuild that profile or its renderers.
            ReloadHelper(context).reloadGrid()
            ReloadHelper(context).reloadTaskbar()
        }
    }

    fun resetVariant(profile: ThemeProfile, variant: ThemeVariant) {
        removeMatching(profile, variant)
    }

    fun resetTheme(profile: ThemeProfile) {
        removeMatching(profile, null)
    }

    fun isCustomized(profile: ThemeProfile): Boolean =
        prefs.all.keys.any { it.startsWith(prefix(profile)) }

    private fun removeMatching(profile: ThemeProfile, variant: ThemeVariant?) {
        val prefix = if (variant == null) prefix(profile) else variantPrefix(profile, variant)
        val editor = prefs.edit()
        val removedKeys = prefs.all.keys.filter { it.startsWith(prefix) }
        val removedDotOverride = removedKeys.any { key ->
            key.endsWith(ThemeColorRole.GLOBAL_NOTIFICATION_DOT.id) ||
                key.endsWith(ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT.id)
        }
        removedKeys.forEach(editor::remove)
        editor.apply()
        notifyThemeChanged()
        if (removedDotOverride) {
            // A reset must rebuild DeviceProfile's DotRenderer, just like an
            // individual dot color change; recreating Launcher alone is insufficient.
            ReloadHelper(context).reloadGrid()
            ReloadHelper(context).reloadTaskbar()
        }
    }

    private fun notifyThemeChanged() {
        ThemeColorInvalidation.invalidate()
        // Most launcher Views resolve semantic colors eagerly. Recreate the active Launcher so
        // Home, Drawer, folders, popups and search surfaces all consume the new override at once.
        ReloadHelper(context).recreate()
    }

    private fun key(profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole) =
        variantPrefix(profile, variant) + role.id

    private fun prefix(profile: ThemeProfile) = "${profile.id.value}."
    private fun variantPrefix(profile: ThemeProfile, variant: ThemeVariant) =
        prefix(profile) + variant.name.lowercase() + "."

    companion object {
        private const val PREFS_NAME = "prime_theme_color_overrides"
    }
}
