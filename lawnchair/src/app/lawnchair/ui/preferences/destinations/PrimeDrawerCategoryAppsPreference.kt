package app.lawnchair.ui.preferences.destinations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.prime.drawer.PrimeDrawerFolder
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.PositionalList
import app.lawnchair.ui.preferences.components.reorderable.PositionalListItem
import app.lawnchair.ui.preferences.components.reorderable.PositionalListOverflowMenu
import app.lawnchair.ui.preferences.components.reorderable.rememberPositionalListState
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.util.ComponentKey

private sealed interface PrimeCategoryListItem {
    val key: String
    val label: String

    data class AppItem(val app: App) : PrimeCategoryListItem {
        override val key = app.key.toString()
        override val label = app.label
    }

    data class FolderItem(val folder: PrimeDrawerFolder) : PrimeCategoryListItem {
        override val key = "folder:" + folder.id
        override val label = folder.title
    }

    data class FolderAppItem(
        val app: App,
        val folderTitles: List<String>,
    ) : PrimeCategoryListItem {
        override val key = app.key.toString()
        override val label = app.label
    }
}

@Composable
fun PrimeDrawerCategoryAppsPreference(tabId: String) {
    val context = LocalContext.current
    val repository = remember(context) { PrimeDrawerTabsRepository(context) }
    val configuration = remember { mutableStateOf(repository.getConfiguration()) }
    DisposableEffect(repository) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PrimeDrawerTabsRepository.PREF_CONFIGURATION) {
                configuration.value = repository.getConfiguration()
            }
        }
        repository.registerConfigurationChangeListener(listener)
        onDispose { repository.unregisterConfigurationChangeListener(listener) }
    }

    val apps = appsState().value
    val tab = configuration.value.tabs.firstOrNull { it.id == tabId } ?: return
    val hideFolderApps = PreferenceManager.getInstance(context).primeHideFolderApps.get()
    val folderAppKeys = remember(tab.folders) {
        tab.folders.flatMapTo(mutableSetOf()) { it.apps }
    }
    val visibleTabApps = remember(tab.apps, folderAppKeys, hideFolderApps) {
        if (hideFolderApps) tab.apps - folderAppKeys else tab.apps
    }

    val activeApps = apps
        .filter { it.key.toString() in visibleTabApps }
        .map { PrimeCategoryListItem.AppItem(it) }
    val folders = tab.folders.map { PrimeCategoryListItem.FolderItem(it) }
    val customIndex = tab.customOrder.withIndex().associate { it.value to it.index }

    fun <T : PrimeCategoryListItem> alphabetical(items: List<T>) =
        if (tab.sortMode == "alphabetical_desc") {
            items.sortedByDescending { it.label.lowercase() }
        } else {
            items.sortedBy { it.label.lowercase() }
        }

    val activeItems: List<PrimeCategoryListItem> = when (tab.folderPlacement) {
        "end" -> {
            val orderedApps = if (tab.sortMode == "custom") activeApps.sortedBy { customIndex[it.key] ?: Int.MAX_VALUE } else alphabetical(activeApps)
            val orderedFolders = if (tab.sortMode == "custom") folders.sortedBy { customIndex[it.key] ?: Int.MAX_VALUE } else alphabetical(folders)
            orderedApps + orderedFolders
        }
        "mixed" -> {
            val items = activeApps + folders
            if (tab.sortMode == "custom") items.sortedBy { customIndex[it.key] ?: Int.MAX_VALUE } else alphabetical(items)
        }
        else -> {
            val orderedFolders = if (tab.sortMode == "custom") folders.sortedBy { customIndex[it.key] ?: Int.MAX_VALUE } else alphabetical(folders)
            val orderedApps = if (tab.sortMode == "custom") activeApps.sortedBy { customIndex[it.key] ?: Int.MAX_VALUE } else alphabetical(activeApps)
            orderedFolders + orderedApps
        }
    }

    val activeAppKeys = activeApps.mapTo(hashSetOf()) { it.key }
    val folderApps = if (hideFolderApps) apps
        .filter { it.key.toString() in folderAppKeys }
        .sortedBy { it.label.lowercase() }
        .map { app ->
            val key = app.key.toString()
            PrimeCategoryListItem.FolderAppItem(
                app = app,
                folderTitles = tab.folders.filter { key in it.apps }.map { it.title },
            )
        }
    else emptyList()
    val inactiveItems = apps
        .filter { app ->
            val key = app.key.toString()
            key !in activeAppKeys && (!hideFolderApps || key !in folderAppKeys)
        }
        .sortedBy { it.label.lowercase() }
        .map { PrimeCategoryListItem.AppItem(it) }

    val positionalItems = (activeItems + inactiveItems).map {
        PositionalListItem(data = it, id = it.key)
    }
    val state = rememberPositionalListState(
        items = positionalItems,
        activeCount = activeItems.size,
        onOrderChange = { newList, newCount ->
            val active = newList.take(newCount)
            val visibleAppKeys = active.mapNotNull { (it.data as? PrimeCategoryListItem.AppItem)?.app?.key }
            val hiddenFolderKeys = if (hideFolderApps) {
                tab.apps.intersect(folderAppKeys).mapNotNull(ComponentKey::fromString)
            } else {
                emptyList()
            }
            repository.setTabApps(tabId, (visibleAppKeys + hiddenFolderKeys).toSet())
            if (tab.sortMode == "custom") {
                val orderedMembers = newList.map { it.id }.filter { key ->
                    key.startsWith("folder:") || key in active.map { it.id }
                }
                repository.setTabCustomOrder(tabId, orderedMembers)
            }
        },
        labelSelector = { it.label },
    )

    PreferenceScaffold(
        label = "${tab.title} (${state.activeCount + folderApps.size})",
        actions = {
            PositionalListOverflowMenu(
                state = state,
                showStandardActions = false,
                extraItems = { hideMenu ->
                    DropdownMenuItem(
                        text = { Text("Tri des applications") },
                        enabled = false,
                        onClick = {},
                    )
                    DropdownMenuItem(
                        text = { Text("Applications : A → Z") },
                        trailingIcon = { if (tab.sortMode == "alphabetical") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabSortMode(tabId, "alphabetical"); hideMenu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Applications : Z → A") },
                        trailingIcon = { if (tab.sortMode == "alphabetical_desc") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabSortMode(tabId, "alphabetical_desc"); hideMenu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Applications : ordre manuel") },
                        trailingIcon = { if (tab.sortMode == "custom") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabSortMode(tabId, "custom"); hideMenu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Position des dossiers") },
                        enabled = false,
                        onClick = {},
                    )
                    DropdownMenuItem(
                        text = { Text("Dossiers au début") },
                        trailingIcon = { if (tab.folderPlacement == "start") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabFolderPlacement(tabId, "start"); hideMenu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Dossiers à la fin") },
                        trailingIcon = { if (tab.folderPlacement == "end") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabFolderPlacement(tabId, "end"); hideMenu() },
                    )
                    DropdownMenuItem(
                        text = { Text("Dossiers mélangés aux applications") },
                        trailingIcon = { if (tab.folderPlacement == "mixed") Icon(Icons.Rounded.Check, null) },
                        onClick = { repository.setTabFolderPlacement(tabId, "mixed"); hideMenu() },
                    )
                },
            )
        },
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { contentPadding ->
        PositionalList(
            state = state,
            contentPadding = contentPadding,
            reorderEnabled = tab.sortMode == "custom",
            itemContent = { item, dragHandle, toggle ->
                when (item) {
                    is PrimeCategoryListItem.AppItem -> AppItem(
                        app = item.app,
                        onClick = {},
                        widget = dragHandle,
                        endWidget = toggle,
                    )
                    is PrimeCategoryListItem.FolderItem -> PreferenceTemplate(
                        title = { Text(item.folder.title) },
                        description = { Text("Dossier") },
                        startWidget = dragHandle,
                    )
                    is PrimeCategoryListItem.FolderAppItem -> Unit
                }
            },
            middleSectionHeading = if (folderApps.isNotEmpty()) "Éléments actifs dans des dossiers" else null,
            middleItems = folderApps,
            middleItemKey = { it.key },
            middleItemContent = { item ->
                if (item is PrimeCategoryListItem.FolderAppItem) {
                    PreferenceTemplate(
                        title = { Text(item.app.label) },
                        description = {
                            Text(
                                if (item.folderTitles.size == 1) {
                                    "Dossier : " + item.folderTitles.first()
                                } else {
                                    "Dossiers : " + item.folderTitles.joinToString(", ")
                                },
                            )
                        },
                        endWidget = {
                            IconButton(
                                onClick = {
                                    repository.removeAppFromTabAndFolders(tabId, item.app.key)
                                },
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Retirer de la catégorie")
                            }
                        },
                    )
                }
            },
        )
    }
}
