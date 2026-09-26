package app.lawnchair.prime.drawer

import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.destinations.FolderEditSheet
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderAdvanced
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderApps
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
                    }
                },
                onNavigate = {
                    if (tabId != null && folderId != null) {
                        sheet.close(false)
                        icon.context.startActivity(
                            PreferenceActivity.createIntent(
                                icon.context,
                                PrimeDrawerFolderApps(tabId, folderId),
                            ),
                        )
                    }
                },
                onDismiss = { sheet.close(true) },
                onAdvanced = if (tabId != null && folderId != null) {
                    {
                        sheet.close(false)
                        icon.context.startActivity(
                            PreferenceActivity.createIntent(
                                icon.context,
                                PrimeDrawerFolderAdvanced(tabId, folderId),
                            ),
                        )
                    }
                } else null,
                onDelete = if (tabId != null && folderId != null) {
                    {
                        PrimeDrawerTabsRepository(icon.context).deleteFolder(tabId, folderId)
                        launcher.appsView.floatingHeaderView?.onPrimeDrawerTabSelected()
                    }
                } else null,
            )
        }
    }
}
