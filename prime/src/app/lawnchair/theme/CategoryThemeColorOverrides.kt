package app.lawnchair.theme

import android.content.Context
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.theme.color.ColorOption

/**
 * Sparse overrides for a single drawer category. Default is not persisted: it always
 * inherits the current parent theme color, including subsequent parent edits.
 *
 * Separate namespace from ThemeColorOverrides and the older tab visual settings.
 * Keys use stable role IDs and are scoped by theme, variant and category ID.
 */
class CategoryThemeColorOverrides(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("prime_category_theme_color_overrides", Context.MODE_PRIVATE)

    fun get(tabId: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole): ColorOption =
        prefs.getString(key(tabId, profile, variant, role), null)
            ?.let(ColorOption::fromString) ?: ColorOption.Default

    fun set(tabId: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole, option: ColorOption) {
        val editor = prefs.edit()
        val key = key(tabId, profile, variant, role)
        if (option == ColorOption.Default) editor.remove(key)
        else editor.putString(key, option.toString())
        editor.apply()
        ThemeColorInvalidation.invalidate()
        ReloadHelper(appContext).recreate()
    }

    fun resetVariant(tabId: String, profile: ThemeProfile, variant: ThemeVariant) =
        removeMatching(prefix(tabId, profile, variant))

    fun resetTheme(tabId: String, profile: ThemeProfile) =
        removeMatching(prefix(tabId, profile))

    fun hasOverrides(tabId: String, profile: ThemeProfile, variant: ThemeVariant): Boolean =
        prefs.all.keys.any { it.startsWith(prefix(tabId, profile, variant)) }

    fun resetCategory(tabId: String) =
        removeMatching("${tabId.length}:$tabId.")

    private fun removeMatching(prefix: String) {
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        editor.apply()
        ThemeColorInvalidation.invalidate()
        ReloadHelper(appContext).recreate()
    }

    private fun key(tabId: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole) =
        prefix(tabId, profile, variant) + role.id

    private fun prefix(tabId: String, profile: ThemeProfile) =
        "${tabId.length}:$tabId.${profile.id.value}."

    private fun prefix(tabId: String, profile: ThemeProfile, variant: ThemeVariant) =
        prefix(tabId, profile) + variant.name.lowercase() + "."
}
