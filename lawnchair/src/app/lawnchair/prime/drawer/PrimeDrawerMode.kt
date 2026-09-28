package app.lawnchair.prime.drawer

import app.lawnchair.preferences.PreferenceManager

/**
 * Stable Prime identity for each app-drawer implementation.
 *
 * Keep this outside the preferences UI: runtime rendering, folders and settings all use the
 * same mode namespace. The serialized key is intentionally stable because Prime-owned data is
 * persisted under it.
 */
enum class PrimeDrawerMode(val storageKey: String) {
    DEFAULT("default"),
    TABS("tabs"),
    CADDY("caddy");

    companion object {
        @JvmStatic
        fun current(prefs: PreferenceManager): PrimeDrawerMode = when {
            prefs.drawerTabsEnabled.get() -> TABS
            prefs.drawerList.get() -> DEFAULT
            else -> CADDY
        }
    }
}

/**
 * Prime identity of a drawer folder without reusing the backing store's raw id globally.
 *
 * Tabs folders are scoped by their tab id; Lawnchair folders keep their native Room integer id
 * as a string and are scoped by drawer mode. This lets Prime attach overrides without changing
 * Lawnchair's primary keys or allowing ids from different modes to collide.
 */
data class PrimeDrawerFolderKey(
    val mode: PrimeDrawerMode,
    val folderId: String,
    val ownerId: String? = null,
) {
    init {
        require(folderId.isNotBlank())
        if (mode == PrimeDrawerMode.TABS) require(!ownerId.isNullOrBlank())
    }

    fun storageKey(): String = buildString {
        append(mode.storageKey)
        append('/')
        ownerId?.let {
            append(escape(it))
            append('/')
        }
        append(escape(folderId))
    }

    companion object {
        @JvmStatic
        fun tabs(tabId: String, folderId: String) =
            PrimeDrawerFolderKey(PrimeDrawerMode.TABS, folderId, tabId)

        @JvmStatic
        fun default(folderId: Int) =
            PrimeDrawerFolderKey(PrimeDrawerMode.DEFAULT, folderId.toString())

        @JvmStatic
        fun caddy(folderId: Int) =
            PrimeDrawerFolderKey(PrimeDrawerMode.CADDY, folderId.toString())

        private fun escape(value: String) =
            value.replace("%", "%25").replace("/", "%2F")
    }
}
