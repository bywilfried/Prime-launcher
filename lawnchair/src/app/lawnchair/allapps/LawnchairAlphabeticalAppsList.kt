package app.lawnchair.allapps

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.prime.drawer.PrimeFolderLongPressHelper
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import app.lawnchair.util.observeOnce
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener,
    DefaultLifecycleObserver,
    SharedPreferences.OnSharedPreferenceChangeListener
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)
    private val primeTabsRepository = PrimeDrawerTabsRepository(context)
    private var primePreviewTabId: String? = null

    /**
     * Immutable description of Prime's resolved category ordering.
     *
     * It deliberately contains model identities only: no View, ViewHolder or RecyclerView state.
     * Preview and live can therefore materialize the same category projection independently while
     * sharing one deterministic ordering decision.
     */
    data class PrimePreparedContent(
        val tabId: String,
        val orderedItemKeys: List<String>,
    )

    private var primePreparedContent: PrimePreparedContent? = null

    fun getPrimePreparedContent(): PrimePreparedContent? = primePreparedContent

    fun setPrimePreparedContent(content: PrimePreparedContent?) {
        primePreparedContent = content
    }

    private val viewModel = FolderViewModel(
        (context as? ComponentActivity)?.application ?: context.launcher.application,
    )
    private val folderList = mutableListOf<FolderEntry>()
    private val filteredList = mutableListOf<AppInfo>()

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
        primeTabsRepository.registerConfigurationChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PrimeDrawerTabsRepository.PREF_CONFIGURATION) {
            onAppsUpdated()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        context.launcher.deviceProfile.inv.removeOnChangeListener(this)
        primeTabsRepository.unregisterConfigurationChangeListener(this)
    }

    fun getPrimeEffectiveTabId(): String =
        primePreviewTabId ?: primeTabsRepository.getConfiguration().selectedTabId

    fun isPrimePreview(): Boolean = primePreviewTabId != null

    fun setPrimePreviewTabId(tabId: String?) {
        primePreviewTabId = tabId
        onAppsUpdated()
    }

    /**
     * Configures a swipe preview atomically: install the target tab before rebuilding the
     * normal All Apps predicate so the preview never lays out an unfiltered intermediate dataset.
     */
    fun configurePrimePreview(tabId: String, itemFilter: Predicate<ItemInfo>?) {
        primePreviewTabId = tabId
        updateItemFilter(itemFilter)
    }

    fun disposePrimePreview() {
        context.launcher.deviceProfile.inv.removeOnChangeListener(this)
        primeTabsRepository.unregisterConfigurationChangeListener(this)
    }

    private fun observeFolders() {
        viewModel.folders.observeOnce(context as LifecycleOwner) { folders ->
            if (folders != null) {
                folderList.clear()
                folderList.addAll(folders)
                updateAdapterItems()
            }
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey()
            val isVisible = !hiddenApps.contains(componentKey.toString())
            val isInPrimeTab = !prefs.drawerTabsEnabled.get() ||
                primeTabsRepository.isAppInTab(
                    componentKey,
                    primePreviewTabId ?: primeTabsRepository.getConfiguration().selectedTabId,
                )
            (itemFilter?.test(info) != false) && isVisible && isInPrimeTab
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        filteredList.clear()

        // Prime Tabs owns a separate organization. Never project Lawnchair's classic
        // drawer folders or Caddy categories while this mode is active.
        if (prefs.drawerTabsEnabled.get()) {
            var position = startPosition
            val configuration = primeTabsRepository.getConfiguration()
            val effectiveTabId = primePreviewTabId ?: configuration.selectedTabId
            val selectedTab = configuration.tabs.firstOrNull { it.id == effectiveTabId }

            if (selectedTab == null || selectedTab.isSystem) {
                return super.addAppsWithSections(appList, position)
            }

            val visibleAppsByKey = appList
                .mapNotNull { app -> app?.let { it.toComponentKey().toString() to it } }
                .toMap()
            val folderItems = selectedTab.folders.mapNotNull { folder ->
                val folderKeys = if (folder.sortMode == "custom") {
                    folder.customOrder.filter(folder.apps::contains) +
                        folder.apps.filterNot(folder.customOrder::contains)
                } else {
                    folder.apps.sortedBy { key ->
                        visibleAppsByKey[key]?.title?.toString()?.lowercase() ?: key
                    }
                }
                val resolvedApps = folderKeys.mapNotNull(visibleAppsByKey::get)
                if (resolvedApps.size > 1 || (resolvedApps.size < 2 && prefs.primeShowEmptyFolders.get())) {
                    val folderInfo = FolderInfo().apply {
                        title = folder.title
                        // A projected Prime folder may be dragged to Workspace. Keep its transient
                        // contents workspace-ready so Launcher never persists raw AppInfo children.
                        resolvedApps.forEach { add(it.makeWorkspaceItem(context)) }
                    }
                    PrimeFolderLongPressHelper.registerPrimeFolder(
                        folderInfo,
                        selectedTab.id,
                        folder.id,
                    )
                    Triple(folder, folderInfo, resolvedApps)
                } else {
                    null
                }
            }

            if (prefs.folderApps.get()) {
                folderItems.forEach { (_, _, resolvedApps) -> filteredList.addAll(resolvedApps) }
            }
            var remainingApps = if (prefs.folderApps.get()) {
                appList.filterNot(filteredList::contains)
            } else {
                appList
            }

            val customIndex = selectedTab.customOrder.withIndex().associate { it.value to it.index }
            fun preparedIndex(key: String): Int? {
                val prepared = primePreparedContent
                if (prepared == null || prepared.tabId != effectiveTabId) return null
                val index = prepared.orderedItemKeys.indexOf(key)
                return index.takeIf { it >= 0 }
            }
            fun customOrPreparedIndex(key: String): Int =
                preparedIndex(key) ?: customIndex[key] ?: Int.MAX_VALUE
            fun addFolders(folders: List<Triple<app.lawnchair.prime.drawer.PrimeDrawerFolder, FolderInfo, List<AppInfo>>>) {
                folders.forEach { (_, folderInfo, _) ->
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    position++
                }
            }

            when (selectedTab.folderPlacement) {
                "end" -> {
                    if (selectedTab.sortMode == "custom") {
                        remainingApps = remainingApps.sortedBy { app ->
                            app?.toComponentKey()?.toString()?.let(::customOrPreparedIndex) ?: Int.MAX_VALUE
                        }
                    }
                    position = super.addAppsWithSections(remainingApps, position)
                    val orderedFolders = if (selectedTab.sortMode == "custom") {
                        folderItems.sortedBy { (folder) -> customOrPreparedIndex("folder:" + folder.id) }
                    } else {
                        folderItems.sortedBy { (folder) -> folder.title.lowercase() }
                    }
                    addFolders(orderedFolders)
                }
                "mixed" -> {
                    val appItems = remainingApps.mapNotNull { app ->
                        app?.let { Triple(it.toComponentKey().toString(), it.title?.toString().orEmpty(), AdapterItem.asApp(it)) }
                    }
                    val projectedFolders = folderItems.map { (folder, folderInfo, _) ->
                        Triple("folder:" + folder.id, folder.title, AdapterItem.asFolder(folderInfo))
                    }
                    val mixedItems = (appItems + projectedFolders).sortedWith(
                        if (selectedTab.sortMode == "custom") {
                            compareBy { item -> customOrPreparedIndex(item.first) }
                        } else {
                            compareBy(String.CASE_INSENSITIVE_ORDER) { item -> item.second }
                        },
                    )
                    mixedItems.forEach { (_, _, item) ->
                        mAdapterItems.add(item)
                        position++
                    }
                }
                else -> {
                    val orderedFolders = if (selectedTab.sortMode == "custom") {
                        folderItems.sortedBy { (folder) -> customOrPreparedIndex("folder:" + folder.id) }
                    } else {
                        folderItems.sortedBy { (folder) -> folder.title.lowercase() }
                    }
                    addFolders(orderedFolders)
                    if (selectedTab.sortMode == "custom") {
                        remainingApps = remainingApps.sortedBy { app ->
                            app?.toComponentKey()?.toString()?.let(::customOrPreparedIndex) ?: Int.MAX_VALUE
                        }
                    }
                    return super.addAppsWithSections(remainingApps, position)
                }
            }
            if (primePreparedContent == null || primePreparedContent?.tabId != effectiveTabId) {
                val resolvedOrder = mAdapterItems.drop(startPosition).mapNotNull { item ->
                    when (val info = item.itemInfo) {
                        is AppInfo -> info.toComponentKey().toString()
                        is FolderInfo -> selectedTab.folders.firstOrNull { folder ->
                            folder.title == info.title?.toString()
                        }?.let { "folder:" + it.id }
                        else -> null
                    }
                }
                primePreparedContent = PrimePreparedContent(effectiveTabId, resolvedOrder.toList())
            }
            return position
        }
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size == 1) {
                    mAdapterItems.add(AdapterItem.asApp(apps.first()))
                } else {
                    val folderInfo = FolderInfo().apply {
                        title = category
                        apps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                }
                position++
            }
        } else {
            folderList.forEach { folderEntry ->
                val resolvedApps = folderEntry.itemComponentKeys.mapNotNull { keyString ->
                    val componentKey = ComponentKey.fromString(keyString) ?: return@mapNotNull null
                    appsStore.getApp(componentKey) as? AppInfo
                }

                if (resolvedApps.size > 1) {
                    val folderInfo = FolderInfo().apply {
                        id = folderEntry.id
                        title = folderEntry.title
                        // Drawer FolderService entries are projections too. Keep their children
                        // workspace-ready just like Prime Tabs folders, otherwise Workspace's
                        // external-folder copy drops raw AppInfo children and the resulting Home
                        // folder is empty/removed immediately after the drop.
                        resolvedApps.forEach { add(it.makeWorkspaceItem(context)) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    position++

                    if (prefs.folderApps.get()) {
                        filteredList.addAll(resolvedApps)
                    }
                }
            }
            val remainingApps = appList.filterNot { app -> filteredList.contains(app) && prefs.folderApps.get() }
            position = super.addAppsWithSections(remainingApps, position)
        }

        return position
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
