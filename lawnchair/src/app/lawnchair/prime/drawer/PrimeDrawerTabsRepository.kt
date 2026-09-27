package app.lawnchair.prime.drawer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.model.data.FolderInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent Prime-owned drawer organization.
 *
 * This deliberately does not reuse Lawnchair drawer folders: switching between the classic
 * drawer and Prime tabs must never mutate either mode's organization.
 */
class PrimeDrawerTabsRepository(context: Context) {
    private val prefs = LauncherPrefs.getPrefs(context)
    private val primePrefs = app.lawnchair.preferences.PreferenceManager.getInstance(context)

    fun getConfiguration(): PrimeDrawerTabsConfiguration =
        decode(prefs.getString(PREF_CONFIGURATION, null))

    fun getTab(tabId: String): PrimeDrawerTab? =
        getConfiguration().tabs.firstOrNull { it.id == tabId }

    fun saveConfiguration(configuration: PrimeDrawerTabsConfiguration) {
        prefs.edit { putString(PREF_CONFIGURATION, encode(configuration.normalized())) }
    }

    fun registerConfigurationChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterConfigurationChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun createTab(title: String): PrimeDrawerTab {
        val configuration = getConfiguration()
        val tab = PrimeDrawerTab(
            id = nextTabId(configuration.tabs),
            title = title.trim(),
        )
        saveConfiguration(configuration.copy(tabs = configuration.tabs + tab))
        return tab
    }

    fun renameTab(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title.trim()) }
    }

    fun deleteTab(tabId: String) {
        if (tabId == ALL_TAB_ID || tabId == UNCLASSIFIED_TAB_ID) return
        val configuration = getConfiguration()
        val remainingTabs = configuration.tabs.filterNot { it.id == tabId }
        saveConfiguration(
            configuration.copy(
                tabs = remainingTabs,
                defaultTabId = configuration.defaultTabId.takeUnless { it == tabId } ?: ALL_TAB_ID,
                selectedTabId = configuration.selectedTabId.takeUnless { it == tabId } ?: ALL_TAB_ID,
            ),
        )
        if (remainingTabs.none { !it.isSystem } &&
            primePrefs.drawerTabsHideUnclassified.get() &&
            primePrefs.drawerTabsHideAll.get()
        ) {
            primePrefs.drawerTabsHideAll.set(false)
        }
    }

    fun reorderTabs(orderedTabIds: List<String>) {
        val configuration = getConfiguration()
        val tabsById = configuration.tabs.associateBy { it.id }
        val reordered = orderedTabIds.mapNotNull(tabsById::get) +
            configuration.tabs.filter { it.id !in orderedTabIds }
        saveConfiguration(configuration.copy(tabs = reordered))
    }

    fun setDefaultTab(tabId: String) {
        val configuration = getConfiguration()
        if (configuration.tabs.none { it.id == tabId }) return
        saveConfiguration(configuration.copy(defaultTabId = tabId))
    }

    fun setSelectedTab(tabId: String) {
        val configuration = getConfiguration()
        if (configuration.tabs.none { it.id == tabId }) return
        saveConfiguration(configuration.copy(selectedTabId = tabId))
    }

    fun setTabApps(tabId: String, apps: Set<ComponentKey>) {
        if (tabId == ALL_TAB_ID || tabId == UNCLASSIFIED_TAB_ID) return
        updateTab(tabId) { tab ->
            val keys = apps.mapTo(linkedSetOf(), ComponentKey::toString)
            val members = keys + tab.folders.map { "folder:" + it.id }
            tab.copy(
                apps = keys,
                customOrder = tab.customOrder.filter(members::contains) +
                    members.filterNot(tab.customOrder::contains),
            )
        }
    }

    fun setTabSortMode(tabId: String, mode: String) {
        updateUserTab(tabId) { it.copy(sortMode = mode) }
    }

    fun setTabFolderPlacement(tabId: String, placement: String) {
        updateUserTab(tabId) { it.copy(folderPlacement = placement) }
    }

    fun setTabVisualOverrides(tabId: String, overrides: PrimeDrawerVisualOverrides) {
        updateUserTab(tabId) { it.copy(visualOverrides = overrides) }
    }

    fun setTabCustomOrder(tabId: String, orderedKeys: List<String>) {
        updateUserTab(tabId) { tab ->
            val members = tab.apps + tab.folders.map { "folder:" + it.id }
            tab.copy(customOrder = orderedKeys.filter(members::contains) + members.filterNot(orderedKeys::contains))
        }
    }

    fun createFolder(tabId: String, title: String): PrimeDrawerFolder? {
        if (tabId == ALL_TAB_ID || tabId == UNCLASSIFIED_TAB_ID) return null
        val configuration = getConfiguration()
        val tab = configuration.tabs.firstOrNull { it.id == tabId } ?: return null
        val folder = PrimeDrawerFolder(
            id = nextFolderId(tab.folders),
            title = title.trim(),
        )
        updateTab(tabId) { it.copy(folders = it.folders + folder) }
        return folder
    }

    fun renameFolderByVisibleContents(folderInfo: FolderInfo, title: String) {
        val visibleKeys = folderInfo.getContents()
            .mapNotNull { it.componentKey?.toString() }
            .toSet()
        if (visibleKeys.isEmpty()) return
        val configuration = getConfiguration()
        val selectedTab = configuration.tabs.firstOrNull { it.id == configuration.selectedTabId }
            ?: return
        val folder = selectedTab.folders.firstOrNull { candidate ->
            visibleKeys.all { it in candidate.apps }
        } ?: return
        renameFolder(selectedTab.id, folder.id, title)
    }

    fun renameFolder(tabId: String, folderId: String, title: String) {
        updateUserTab(tabId) { tab ->
            tab.copy(
                folders = tab.folders.map { folder ->
                    if (folder.id == folderId) folder.copy(title = title.trim()) else folder
                },
            )
        }
    }

    fun deleteFolder(tabId: String, folderId: String) {
        updateUserTab(tabId) { tab ->
            tab.copy(folders = tab.folders.filterNot { it.id == folderId })
        }
    }

    fun reorderFolders(tabId: String, orderedFolderIds: List<String>) {
        updateUserTab(tabId) { tab ->
            val foldersById = tab.folders.associateBy { it.id }
            val reordered = orderedFolderIds.mapNotNull(foldersById::get) +
                tab.folders.filter { it.id !in orderedFolderIds }
            tab.copy(folders = reordered)
        }
    }

    fun removeAppFromTabAndFolders(tabId: String, componentKey: ComponentKey) {
        val key = componentKey.toString()
        updateUserTab(tabId) { tab ->
            tab.copy(
                apps = tab.apps - key,
                folders = tab.folders.map { folder ->
                    if (key in folder.apps) {
                        val apps = folder.apps - key
                        folder.copy(
                            apps = apps,
                            customOrder = folder.customOrder.filter(apps::contains),
                        )
                    } else {
                        folder
                    }
                },
                customOrder = tab.customOrder.filterNot { it == key },
            )
        }
    }

    fun setFolderApps(tabId: String, folderId: String, apps: Set<ComponentKey>) {
        updateUserTab(tabId) { tab ->
            tab.copy(
                folders = tab.folders.map { folder ->
                    if (folder.id == folderId) {
                        val keys = apps.mapTo(linkedSetOf(), ComponentKey::toString)
                        folder.copy(
                            apps = keys,
                            customOrder = folder.customOrder.filter(keys::contains) + keys.filterNot(folder.customOrder::contains),
                        )
                    } else {
                        folder
                    }
                },
            )
        }
    }

    fun setFolderVisualOverrides(tabId: String, folderId: String, overrides: PrimeDrawerFolderVisualOverrides) {
        updateUserTab(tabId) { tab ->
            tab.copy(folders = tab.folders.map { folder ->
                if (folder.id == folderId) folder.copy(visualOverrides = overrides) else folder
            })
        }
    }

    fun setFolderSortMode(tabId: String, folderId: String, mode: String) {
        updateUserTab(tabId) { tab ->
            tab.copy(folders = tab.folders.map { folder ->
                if (folder.id == folderId) folder.copy(sortMode = mode) else folder
            })
        }
    }

    fun setFolderCustomOrder(tabId: String, folderId: String, orderedKeys: List<String>) {
        updateUserTab(tabId) { tab ->
            tab.copy(folders = tab.folders.map { folder ->
                if (folder.id == folderId) {
                    folder.copy(customOrder = orderedKeys.filter(folder.apps::contains) + folder.apps.filterNot(orderedKeys::contains))
                } else folder
            })
        }
    }

    fun setAppTabs(componentKey: ComponentKey, tabIds: Set<String>) {
        val key = componentKey.toString()
        val configuration = getConfiguration()
        saveConfiguration(
            configuration.copy(
                tabs = configuration.tabs.map { tab ->
                    if (tab.isSystem) {
                        tab
                    } else if (tab.id in tabIds) {
                        tab.copy(apps = tab.apps + key)
                    } else {
                        tab.copy(apps = tab.apps - key)
                    }
                },
            ),
        )
    }

    fun getHomeFolderVisualOverrides(folderId: Int): PrimeDrawerFolderVisualOverrides {
        val root = runCatching { JSONObject(prefs.getString(PREF_HOME_FOLDER_OVERRIDES, "{}")) }
            .getOrElse { JSONObject() }
        return root.optJSONObject(folderId.toString()).toFolderVisualOverrides()
    }

    fun setHomeFolderVisualOverrides(folderId: Int, overrides: PrimeDrawerFolderVisualOverrides) {
        val root = runCatching { JSONObject(prefs.getString(PREF_HOME_FOLDER_OVERRIDES, "{}")) }
            .getOrElse { JSONObject() }
        root.put(folderId.toString(), overrides.toJson())
        prefs.edit { putString(PREF_HOME_FOLDER_OVERRIDES, root.toString()) }
    }

    fun getHomeFolderOverrideIds(): Set<Int> {
        val root = runCatching { JSONObject(prefs.getString(PREF_HOME_FOLDER_OVERRIDES, "{}")) }
            .getOrElse { JSONObject() }
        return buildSet {
            val keys = root.keys()
            while (keys.hasNext()) keys.next().toIntOrNull()?.let(::add)
        }
    }

    fun deleteHomeFolderVisualOverrides(folderId: Int) {
        val root = runCatching { JSONObject(prefs.getString(PREF_HOME_FOLDER_OVERRIDES, "{}")) }
            .getOrElse { JSONObject() }
        root.remove(folderId.toString())
        prefs.edit { putString(PREF_HOME_FOLDER_OVERRIDES, root.toString()) }
    }

    fun getResolvedFolderVisualOverrides(tabId: String, folderId: String): PrimeDrawerFolderVisualOverrides? {
        val tab = getConfiguration().tabs.firstOrNull { it.id == tabId } ?: return null
        val folder = tab.folders.firstOrNull { it.id == folderId } ?: return null
        val category = tab.visualOverrides
        val own = folder.visualOverrides
        return PrimeDrawerFolderVisualOverrides(
            previewOpacity = own.previewOpacity ?: category.folderPreviewOpacity,
            backgroundOpacity = own.backgroundOpacity ?: category.folderBackgroundOpacity,
            columns = own.columns ?: category.folderColumns,
            rows = own.rows ?: category.folderRows,
            showLabels = own.showLabels ?: category.folderShowLabels,
            showFolderLabel = own.showFolderLabel,
            labelSize = own.labelSize ?: category.folderLabelSize,
            childIconShape = own.childIconShape ?: category.folderChildIconShape,
            shape = own.shape ?: category.folderShape,
            color = own.color ?: category.folderColor,
        )
    }

    fun getSelectedTabVisualOverrides(): PrimeDrawerVisualOverrides? {
        val configuration = getConfiguration()
        return configuration.tabs.firstOrNull { it.id == configuration.selectedTabId }
            ?.takeUnless { it.isSystem }
            ?.visualOverrides
    }

    fun isAppInTab(componentKey: ComponentKey, tabId: String): Boolean {
        val configuration = getConfiguration()
        return when (tabId) {
            ALL_TAB_ID -> true
            UNCLASSIFIED_TAB_ID -> configuration.tabs
                .asSequence()
                .filterNot { it.isSystem }
                .none { tab ->
                    componentKey.toString() in tab.apps ||
                        tab.folders.any { componentKey.toString() in it.apps }
                }
            else -> configuration.tabs.firstOrNull { it.id == tabId }
                ?.let { tab ->
                    componentKey.toString() in tab.apps ||
                        tab.folders.any { componentKey.toString() in it.apps }
                } == true
        }
    }

    private fun updateUserTab(tabId: String, transform: (PrimeDrawerTab) -> PrimeDrawerTab) {
        if (tabId == ALL_TAB_ID || tabId == UNCLASSIFIED_TAB_ID) return
        updateTab(tabId, transform)
    }

    private fun updateTab(tabId: String, transform: (PrimeDrawerTab) -> PrimeDrawerTab) {
        val configuration = getConfiguration()
        saveConfiguration(
            configuration.copy(
                tabs = configuration.tabs.map { if (it.id == tabId) transform(it) else it },
            ),
        )
    }

    private fun encode(configuration: PrimeDrawerTabsConfiguration): String = JSONObject().apply {
        put("version", CONFIG_VERSION)
        put("defaultTabId", configuration.defaultTabId)
        put("selectedTabId", configuration.selectedTabId)
        put("tabs", JSONArray().apply {
            configuration.tabs.forEach { tab ->
                put(JSONObject().apply {
                    put("id", tab.id)
                    put("title", tab.title)
                    put("apps", JSONArray(tab.apps.toList()))
                    put("sortMode", tab.sortMode)
                    put("folderPlacement", tab.folderPlacement)
                    put("customOrder", JSONArray(tab.customOrder))
                    put("visualOverrides", tab.visualOverrides.toJson())
                    put("folders", JSONArray().apply {
                        tab.folders.forEach { folder ->
                            put(JSONObject().apply {
                                put("id", folder.id)
                                put("title", folder.title)
                                put("apps", JSONArray(folder.apps.toList()))
                                put("sortMode", folder.sortMode)
                                put("customOrder", JSONArray(folder.customOrder))
                                put("visualOverrides", folder.visualOverrides.toJson())
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    private fun decode(raw: String?): PrimeDrawerTabsConfiguration {
        if (raw.isNullOrBlank()) return PrimeDrawerTabsConfiguration.initial()
        return runCatching {
            val json = JSONObject(raw)
            val tabsJson = json.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (index in 0 until tabsJson.length()) {
                    val tab = tabsJson.getJSONObject(index)
                    add(
                        PrimeDrawerTab(
                            id = tab.getString("id"),
                            title = tab.optString("title"),
                            apps = tab.optJSONArray("apps").toStringSet(),
                            sortMode = tab.optString("sortMode", "alphabetical"),
                            folderPlacement = tab.optString("folderPlacement", "start"),
                            customOrder = tab.optJSONArray("customOrder").toStringList(),
                            visualOverrides = tab.optJSONObject("visualOverrides").toVisualOverrides(),
                            folders = tab.optJSONArray("folders").toFolders(),
                        ),
                    )
                }
            }
            PrimeDrawerTabsConfiguration(
                tabs = tabs,
                defaultTabId = json.optString("defaultTabId", ALL_TAB_ID),
                selectedTabId = json.optString("selectedTabId", ALL_TAB_ID),
            ).normalized()
        }.getOrElse { PrimeDrawerTabsConfiguration.initial() }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val array = this@toStringSet ?: return@buildSet
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun JSONArray?.toFolders(): List<PrimeDrawerFolder> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val folder = array.getJSONObject(index)
                add(
                    PrimeDrawerFolder(
                        id = folder.getString("id"),
                        title = folder.optString("title"),
                        apps = folder.optJSONArray("apps").toStringSet(),
                        sortMode = folder.optString("sortMode", "alphabetical"),
                        customOrder = folder.optJSONArray("customOrder").toStringList(),
                        visualOverrides = folder.optJSONObject("visualOverrides").toFolderVisualOverrides(),
                    ),
                )
            }
        }
    }

    private fun PrimeDrawerVisualOverrides.toJson() = JSONObject().apply {
        tabColor?.let { put("tabColor", it) }
        drawerBackgroundColor?.let { put("drawerBackgroundColor", it) }
        drawerBackgroundOpacity?.let { put("drawerBackgroundOpacity", it.toDouble()) }
        drawerIconSize?.let { put("drawerIconSize", it.toDouble()) }
        showLabels?.let { put("showLabels", it) }
        showFolderLabel?.let { put("showFolderLabel", it) }
        labelSize?.let { put("labelSize", it.toDouble()) }
        twoLineLabels?.let { put("twoLineLabels", it) }
        drawerColumns?.let { put("drawerColumns", it) }
        drawerRowHeight?.let { put("drawerRowHeight", it.toDouble()) }
        drawerHorizontalMargin?.let { put("drawerHorizontalMargin", it.toDouble()) }
        drawerTopPadding?.let { put("drawerTopPadding", it.toDouble()) }
        rememberPosition?.let { put("rememberPosition", it) }
        showScrollbar?.let { put("showScrollbar", it) }
        folderPreviewOpacity?.let { put("folderPreviewOpacity", it.toDouble()) }
        folderBackgroundOpacity?.let { put("folderBackgroundOpacity", it.toDouble()) }
        folderColumns?.let { put("folderColumns", it) }
        folderRows?.let { put("folderRows", it) }
        folderShowLabels?.let { put("folderShowLabels", it) }
        folderLabelSize?.let { put("folderLabelSize", it.toDouble()) }
        drawerIconShape?.let { put("drawerIconShape", it) }
        folderChildIconShape?.let { put("folderChildIconShape", it) }
        folderShape?.let { put("folderShape", it) }
        folderColor?.let { put("folderColor", it) }
    }

    private fun JSONObject?.toVisualOverrides() = PrimeDrawerVisualOverrides(
        tabColor = this.optIntOrNull("tabColor"),
        drawerBackgroundColor = this.optIntOrNull("drawerBackgroundColor"),
        drawerBackgroundOpacity = this.optFloatOrNull("drawerBackgroundOpacity"),
        drawerIconSize = this.optFloatOrNull("drawerIconSize"),
        showLabels = this.optBooleanOrNull("showLabels"),
        showFolderLabel = this.optBooleanOrNull("showFolderLabel"),
        labelSize = this.optFloatOrNull("labelSize"),
        twoLineLabels = this.optBooleanOrNull("twoLineLabels"),
        drawerColumns = this.optIntOrNull("drawerColumns"),
        drawerRowHeight = this.optFloatOrNull("drawerRowHeight"),
        drawerHorizontalMargin = this.optFloatOrNull("drawerHorizontalMargin"),
        drawerTopPadding = this.optFloatOrNull("drawerTopPadding"),
        rememberPosition = this.optBooleanOrNull("rememberPosition"),
        showScrollbar = this.optBooleanOrNull("showScrollbar"),
        folderPreviewOpacity = this.optFloatOrNull("folderPreviewOpacity"),
        folderBackgroundOpacity = this.optFloatOrNull("folderBackgroundOpacity"),
        folderColumns = this.optIntOrNull("folderColumns"),
        folderRows = this.optIntOrNull("folderRows"),
        folderShowLabels = this.optBooleanOrNull("folderShowLabels"),
        folderLabelSize = this.optFloatOrNull("folderLabelSize"),
        drawerIconShape = this.optStringOrNull("drawerIconShape"),
        folderChildIconShape = this.optStringOrNull("folderChildIconShape"),
        folderShape = this.optStringOrNull("folderShape"),
        folderColor = this.optIntOrNull("folderColor"),
    )

    private fun PrimeDrawerFolderVisualOverrides.toJson() = JSONObject().apply {
        previewOpacity?.let { put("previewOpacity", it.toDouble()) }
        backgroundOpacity?.let { put("backgroundOpacity", it.toDouble()) }
        columns?.let { put("columns", it) }
        rows?.let { put("rows", it) }
        showLabels?.let { put("showLabels", it) }
        labelSize?.let { put("labelSize", it.toDouble()) }
        childIconShape?.let { put("childIconShape", it) }
        shape?.let { put("shape", it) }
        color?.let { put("color", it) }
    }

    private fun JSONObject?.toFolderVisualOverrides() = PrimeDrawerFolderVisualOverrides(
        previewOpacity = this.optFloatOrNull("previewOpacity"),
        backgroundOpacity = this.optFloatOrNull("backgroundOpacity"),
        columns = this.optIntOrNull("columns"),
        rows = this.optIntOrNull("rows"),
        showLabels = this.optBooleanOrNull("showLabels"),
        labelSize = this.optFloatOrNull("labelSize"),
        childIconShape = this.optStringOrNull("childIconShape"),
        shape = this.optStringOrNull("shape"),
        color = this.optIntOrNull("color"),
    )

    private fun JSONObject?.optStringOrNull(key: String): String? =
        if (this != null && has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject?.optFloatOrNull(key: String): Float? =
        if (this != null && has(key) && !isNull(key)) getDouble(key).toFloat() else null
    private fun JSONObject?.optIntOrNull(key: String): Int? =
        if (this != null && has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject?.optBooleanOrNull(key: String): Boolean? =
        if (this != null && has(key) && !isNull(key)) getBoolean(key) else null

    private fun nextFolderId(folders: List<PrimeDrawerFolder>): String {
        var suffix = 1
        val ids = folders.mapTo(hashSetOf()) { it.id }
        while ("folder_$suffix" in ids) suffix++
        return "folder_$suffix"
    }

    private fun nextTabId(tabs: List<PrimeDrawerTab>): String {
        var suffix = 1
        val ids = tabs.mapTo(hashSetOf()) { it.id }
        while ("tab_$suffix" in ids) suffix++
        return "tab_$suffix"
    }

    companion object {
        const val ALL_TAB_ID = "system_all"
        const val UNCLASSIFIED_TAB_ID = "system_unclassified"
        const val PREF_CONFIGURATION = "prime_drawer_tabs_configuration"
        private const val PREF_HOME_FOLDER_OVERRIDES = "prime_home_folder_overrides"
        private const val CONFIG_VERSION = 1
    }
}

data class PrimeDrawerTabsConfiguration(
    val tabs: List<PrimeDrawerTab>,
    val defaultTabId: String,
    val selectedTabId: String,
) {
    fun normalized(): PrimeDrawerTabsConfiguration {
        val uniqueTabs = tabs.distinctBy { it.id }.toMutableList()
        if (uniqueTabs.none { it.id == PrimeDrawerTabsRepository.ALL_TAB_ID }) {
            uniqueTabs.add(0, PrimeDrawerTab(PrimeDrawerTabsRepository.ALL_TAB_ID))
        }
        if (uniqueTabs.none { it.id == PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID }) {
            val allIndex = uniqueTabs.indexOfFirst {
                it.id == PrimeDrawerTabsRepository.ALL_TAB_ID
            }
            uniqueTabs.add(allIndex + 1, PrimeDrawerTab(PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID))
        }
        val normalizedTabs = uniqueTabs.toList()
        val ids = normalizedTabs.mapTo(hashSetOf()) { it.id }
        return copy(
            tabs = normalizedTabs,
            defaultTabId = defaultTabId.takeIf(ids::contains) ?: PrimeDrawerTabsRepository.ALL_TAB_ID,
            selectedTabId = selectedTabId.takeIf(ids::contains) ?: PrimeDrawerTabsRepository.ALL_TAB_ID,
        )
    }

    companion object {
        fun initial() = PrimeDrawerTabsConfiguration(
            tabs = listOf(
                PrimeDrawerTab(PrimeDrawerTabsRepository.ALL_TAB_ID),
                PrimeDrawerTab(PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID),
            ),
            defaultTabId = PrimeDrawerTabsRepository.ALL_TAB_ID,
            selectedTabId = PrimeDrawerTabsRepository.ALL_TAB_ID,
        )
    }
}

data class PrimeDrawerTab(
    val id: String,
    val title: String = "",
    val apps: Set<String> = emptySet(),
    val sortMode: String = "alphabetical",
    val folderPlacement: String = "start",
    val customOrder: List<String> = emptyList(),
    val folders: List<PrimeDrawerFolder> = emptyList(),
    val visualOverrides: PrimeDrawerVisualOverrides = PrimeDrawerVisualOverrides(),
) {
    val isSystem: Boolean
        get() = id == PrimeDrawerTabsRepository.ALL_TAB_ID ||
            id == PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID
}

data class PrimeDrawerFolder(
    val id: String,
    val title: String,
    val apps: Set<String> = emptySet(),
    val sortMode: String = "alphabetical",
    val customOrder: List<String> = emptyList(),
    val visualOverrides: PrimeDrawerFolderVisualOverrides = PrimeDrawerFolderVisualOverrides(),
)

data class PrimeDrawerVisualOverrides(
    val tabColor: Int? = null,
    val drawerBackgroundColor: Int? = null,
    val drawerBackgroundOpacity: Float? = null,
    val drawerIconSize: Float? = null,
    val showLabels: Boolean? = null,
    val labelSize: Float? = null,
    val twoLineLabels: Boolean? = null,
    val drawerColumns: Int? = null,
    val drawerRowHeight: Float? = null,
    val drawerHorizontalMargin: Float? = null,
    val drawerTopPadding: Float? = null,
    val rememberPosition: Boolean? = null,
    val showScrollbar: Boolean? = null,
    val folderPreviewOpacity: Float? = null,
    val folderBackgroundOpacity: Float? = null,
    val folderColumns: Int? = null,
    val folderRows: Int? = null,
    val folderShowLabels: Boolean? = null,
    val folderLabelSize: Float? = null,
    val drawerIconShape: String? = null,
    val folderChildIconShape: String? = null,
    val folderShape: String? = null,
    val folderColor: Int? = null,
)

data class PrimeDrawerFolderVisualOverrides(
    val previewOpacity: Float? = null,
    val backgroundOpacity: Float? = null,
    val columns: Int? = null,
    val rows: Int? = null,
    val showLabels: Boolean? = null,
    val showFolderLabel: Boolean? = null,
    val labelSize: Float? = null,
    val childIconShape: String? = null,
    val shape: String? = null,
    val color: Int? = null,
)
