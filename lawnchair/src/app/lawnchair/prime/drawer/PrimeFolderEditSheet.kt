package app.lawnchair.prime.drawer

import androidx.compose.runtime.getValue
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.destinations.FolderEditSheet
import app.lawnchair.ui.preferences.destinations.SelectAppsForDrawerFolder
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderAdvanced
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderApps
import app.lawnchair.ui.preferences.navigation.PrimeHomeFolderAdvanced
import app.lawnchair.util.appsState
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.folder.FolderIcon

/** Opens the same Lawnchair folder editor used by the settings UI from a launcher FolderIcon. */
object PrimeFolderEditSheet {
    @JvmStatic
    fun show(icon: FolderIcon, tabId: String?, folderId: String?) {
        val launcher = Launcher.getLauncher(icon.context)
        ComposeBottomSheet.show(launcher) {
            val sheet = this
            FolderEditSheet(
                folderId = 0,
                initialTitle = icon.mInfo.title?.toString().orEmpty(),
                itemCount = icon.mInfo.getContents().size,
                onRename = { _, title ->
                    val value = title.trim()
                    if (value.isNotEmpty()) {
                        if (tabId != null && folderId != null) {
                            PrimeDrawerTabsRepository(icon.context).renameFolder(tabId, folderId, value)
                            icon.mInfo.setTitle(value, null)
                        } else {
                            icon.mInfo.setTitle(value, launcher.modelWriter)
                        }
                        icon.onTitleChanged(value)
                        icon.getFolder().reapplyItemInfo()
                    }
                },
                onNavigate = {
                    sheet.close(false)
                    if (tabId != null && folderId != null) {
                        icon.context.startActivity(
                            PreferenceActivity.createIntent(
                                icon.context,
                                PrimeDrawerFolderApps(tabId, folderId),
                            ),
                        )
                    } else {
                        icon.post { showWorkspaceApps(icon) }
                    }
                },
                onDismiss = { sheet.close(true) },
                onAdvanced = {
                    sheet.close(false)
                    if (tabId == null || folderId == null) {
                        PrimeFolderLongPressHelper.registerHomeFolderIcon(icon)
                    }
                    icon.context.startActivity(
                        PreferenceActivity.createIntent(
                            icon.context,
                            if (tabId != null && folderId != null) {
                                PrimeDrawerFolderAdvanced(tabId, folderId)
                            } else {
                                PrimeHomeFolderAdvanced(icon.mInfo.id)
                            },
                        ),
                    )
                },
                onDelete = {
                    if (tabId != null && folderId != null) {
                        PrimeDrawerTabsRepository(icon.context).deleteFolder(tabId, folderId)
                        launcher.appsView.floatingHeaderView?.onPrimeDrawerTabSelected()
                    } else {
                        PrimeDrawerTabsRepository(icon.context).deleteHomeFolderVisualOverrides(icon.mInfo.id)
                        launcher.removeItem(icon, icon.mInfo, true)
                    }
                },
            )
        }
    }

    @JvmStatic
    fun showDrawerFolder(icon: FolderIcon, drawerFolderId: Int) {
        val launcher = Launcher.getLauncher(icon.context)
        ComposeBottomSheet.show(launcher) {
            val sheet = this
            FolderEditSheet(
                folderId = drawerFolderId,
                initialTitle = icon.mInfo.title?.toString().orEmpty(),
                itemCount = icon.mInfo.getContents().size,
                onRename = { id, title ->
                    val value = title.trim()
                    if (value.isNotEmpty()) {
                        CoroutineScope(Dispatchers.Main).launch {
                            FolderService.INSTANCE.get(icon.context).renameFolderInfo(id, value)
                            icon.mInfo.setTitle(value, null)
                            icon.onTitleChanged(value)
                            ReloadHelper(icon.context).reloadGrid()
                        }
                    }
                },
                onNavigate = {
                    sheet.close(false)
                    icon.context.startActivity(
                        PreferenceActivity.createIntent(
                            icon.context,
                            app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder(drawerFolderId),
                        ),
                    )
                },
                onDismiss = { sheet.close(true) },
                onAdvanced = {
                    sheet.close(false)
                    icon.context.startActivity(
                        PreferenceActivity.createIntent(
                            icon.context,
                            PrimeHomeFolderAdvanced(drawerFolderId, drawer = true),
                        ),
                    )
                },
                onDelete = {
                    CoroutineScope(Dispatchers.Main).launch {
                        FolderService.INSTANCE.get(icon.context).deleteFolderInfo(drawerFolderId)
                        PrimeDrawerTabsRepository(icon.context)
                            .deleteDrawerFolderVisualOverrides(drawerFolderId)
                        sheet.close(false)
                        ReloadHelper(icon.context).reloadGrid()
                    }
                },
            )
        }
    }

    private fun showWorkspaceApps(icon: FolderIcon) {
        val launcher = Launcher.getLauncher(icon.context)
        ComposeBottomSheet.show(launcher) {
            val sheet = this
            val apps by appsState()
            val selected = icon.mInfo.getContents().mapNotNull { item ->
                item.targetComponent?.let { component ->
                    com.android.launcher3.util.ComponentKey(component, item.user).toString()
                }
            }
            SelectAppsForDrawerFolder(
                folderEntry = FolderEntry(
                    id = icon.mInfo.id,
                    title = icon.mInfo.title?.toString().orEmpty(),
                    itemComponentKeys = selected,
                ),
                apps = apps,
                allFolderPackages = emptySet(),
                onUpdate = { _, componentKeys ->
                    PrimeFolderLongPressHelper.setWorkspaceAppsFromSheet(icon, componentKeys)
                },
                showDuplicateFilter = false,
                onBack = { sheet.close(true) },
                handleSystemBack = false,
            )
        }
    }
}
