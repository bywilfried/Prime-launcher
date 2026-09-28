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
                    primeTabsRepository.getConfiguration().selectedTabId,
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
            val selectedTab = configuration.tabs.firstOrNull { it.id == configuration.selectedTabId }

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
                            app?.toComponentKey()?.toString()?.let { customIndex[it] } ?: Int.MAX_VALUE
                        }
                    }
                    position = super.addAppsWithSections(remainingApps, position)
                    val orderedFolders = if (selectedTab.sortMode == "custom") {
                        folderItems.sortedBy { (folder) -> customIndex["folder:" + folder.id] ?: Int.MAX_VALUE }
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
                            compareBy { item -> customIndex[item.first] ?: Int.MAX_VALUE }
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
                        folderItems.sortedBy { (folder) -> customIndex["folder:" + folder.id] ?: Int.MAX_VALUE }
                    } else {
                        folderItems.sortedBy { (folder) -> folder.title.lowercase() }
                    }
                    addFolders(orderedFolders)
                    if (selectedTab.sortMode == "custom") {
                        remainingApps = remainingApps.sortedBy { app ->
                            app?.toComponentKey()?.toString()?.let { customIndex[it] } ?: Int.MAX_VALUE
                        }
                    }
                    return super.addAppsWithSections(remainingApps, position)
                }
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
                        resolvedApps.forEach { add(it) }
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
