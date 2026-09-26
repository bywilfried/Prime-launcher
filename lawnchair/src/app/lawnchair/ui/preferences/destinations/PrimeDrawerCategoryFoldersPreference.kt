package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.prime.drawer.PrimeDrawerFolder
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderApps
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderAdvanced
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R

@Composable
fun PrimeDrawerCategoryFoldersPreference(tabId: String) {
    val context = LocalContext.current
    val repository = remember(context) { PrimeDrawerTabsRepository(context) }
    val navController = LocalNavController.current
    val tab = repository.getConfiguration().tabs.firstOrNull { it.id == tabId } ?: return
    if (tab.isSystem) return
    var folders by remember { mutableStateOf(tab.folders) }
    val bottomSheetHandler = bottomSheetHandler

    fun refresh() {
        folders = repository.getConfiguration().tabs.firstOrNull { it.id == tabId }?.folders.orEmpty()
    }

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_folder),
        backArrowVisible = true,
    ) {
        PreferenceGroup(heading = stringResource(R.string.folders_label)) {
            PreferenceTemplate(
                title = { Text(stringResource(R.string.add_folder)) },
                startWidget = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    bottomSheetHandler.show {
                        FolderEditSheet(
                            folderId = 0,
                            initialTitle = stringResource(R.string.my_folder_label),
                            itemCount = 0,
                            onRename = { _, title ->
                                repository.createFolder(tabId, title)
                                refresh()
                            },
                            onNavigate = {},
                            onDismiss = { bottomSheetHandler.hide() },
                            hideAppPicker = true,
                        )
                    }
                },
            )
        }
        ReorderablePreferenceGroup(
            label = null,
            items = folders,
            defaultList = folders,
            onOrderChange = { reordered ->
                folders = reordered
                repository.reorderFolders(tabId, reordered.map { it.id })
            },
        ) { folder, _, _ ->
            val interactionSource = remember { MutableInteractionSource() }
            PrimeFolderItem(
                folder = folder,
                onRename = { title ->
                    repository.renameFolder(tabId, folder.id, title)
                    refresh()
                },
                onDelete = {
                    repository.deleteFolder(tabId, folder.id)
                    refresh()
                },
                onManageApps = {
                    bottomSheetHandler.hide()
                    navController.navigate(PrimeDrawerFolderApps(tabId, folder.id))
                },
                interactionSource = interactionSource,
                dragIndicator = {
                    ReorderableDragHandle(
                        interactionSource = interactionSource,
                        scope = this,
                    )
                },
            )
        }
    }
}

@Composable
private fun PrimeFolderItem(
    folder: PrimeDrawerFolder,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onManageApps: () -> Unit,
    interactionSource: MutableInteractionSource,
    dragIndicator: @Composable () -> Unit,
) {
    val bottomSheetHandler = bottomSheetHandler
    var deleteOpen by remember(folder.id) { mutableStateOf(false) }
    PreferenceTemplate(
        title = { Text(folder.title) },
        description = {
            Text(
                LocalContext.current.resources.getQuantityString(
                    R.plurals.apps_count,
                    folder.apps.size,
                    folder.apps.size,
                ),
            )
        },
        startWidget = dragIndicator,
        endWidget = {
            IconButton(
                onClick = { deleteOpen = true },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = {
            bottomSheetHandler.show {
                FolderEditSheet(
                    folderId = 0,
                    initialTitle = folder.title,
                    itemCount = folder.apps.size,
                    onRename = { _, title -> onRename(title) },
                    onNavigate = { onManageApps() },
                    onDismiss = { bottomSheetHandler.hide() },
                    onAdvanced = {
                        bottomSheetHandler.hide()
                        navController.navigate(PrimeDrawerFolderAdvanced(tabId, folder.id))
                    },
                    onDelete = onDelete,
                )
            }
        },
        interactionSource = interactionSource,
    )
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Supprimer") },
            text = { Text("Supprimer ce dossier ?") },
            confirmButton = { TextButton(onClick = { deleteOpen = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
