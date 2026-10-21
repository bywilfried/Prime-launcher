package app.lawnchair.theme

import android.content.Context
import app.lawnchair.theme.color.ColorOption

/**
 * Sparse, per-folder semantic color choices. The identity includes the folder's
 * physical location and its owning category (for category drawer folders).
 * No inherited color is persisted: parent changes remain live.
 */
class FolderThemeColorOverrides(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "prime_folder_theme_color_overrides", Context.MODE_PRIVATE,
    )

    fun get(folderKey: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole): ColorOption =
        prefs.getString(key(folderKey, profile, variant, role), null)
            ?.let(ColorOption::fromString) ?: ColorOption.Default

    fun set(folderKey: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole, option: ColorOption) {
        val editor = prefs.edit()
        val key = key(folderKey, profile, variant, role)
        if (option == ColorOption.Default) editor.remove(key)
        else editor.putString(key, option.toString())
        editor.apply()
        ThemeColorInvalidation.invalidate()
    }

    fun resetVariant(folderKey: String, profile: ThemeProfile, variant: ThemeVariant) =
        removeMatching(prefix(folderKey, profile, variant))

    fun resetTheme(folderKey: String, profile: ThemeProfile) =
        removeMatching(prefix(folderKey, profile))

    fun resetFolder(folderKey: String) =
        removeMatching("${folderKey.length}:$folderKey.")

    private fun removeMatching(prefix: String) {
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        editor.apply()
        ThemeColorInvalidation.invalidate()
    }

    private fun key(folderKey: String, profile: ThemeProfile, variant: ThemeVariant, role: ThemeColorRole) =
        prefix(folderKey, profile, variant) + role.id

    private fun prefix(folderKey: String, profile: ThemeProfile) =
        "${folderKey.length}:$folderKey.${profile.id.value}."

    private fun prefix(folderKey: String, profile: ThemeProfile, variant: ThemeVariant) =
        prefix(folderKey, profile) + variant.name.lowercase() + "."

    companion object {
        fun home(folderId: Int) = "home:$folderId"
        fun dock(folderId: Int) = "dock:$folderId"
        fun drawer(folderId: Int) = "drawer:$folderId"
        fun category(tabId: String, folderId: String) = "category:${tabId.length}:$tabId:$folderId"
    }
}
