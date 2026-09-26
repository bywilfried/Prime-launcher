package app.lawnchair.prime.drawer

import android.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.navigation.PrimeDrawerFolderAdvanced
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon

object PrimeFolderEditSheet {
    @JvmStatic
    fun show(icon: FolderIcon, tabId: String?, folderId: String?) {
        val launcher = Launcher.getLauncher(icon.context)
        ComposeBottomSheet.show(launcher) {
            var title by remember { mutableStateOf(TextFieldValue(icon.mInfo.title?.toString().orEmpty())) }
            val sheet = this
            ModalBottomSheetContent(
                buttons = {
                    OutlinedButton(onClick = { sheet.close(true) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val value = title.text.trim()
                            if (value.isNotEmpty()) {
                                if (tabId != null && folderId != null) {
                                    PrimeDrawerTabsRepository(icon.context).renameFolder(tabId, folderId, value)
                                    icon.mInfo.setTitle(value, null)
                                } else {
                                    icon.mInfo.setTitle(value, launcher.modelWriter)
                                }
                                icon.onTitleChanged(value)
                                sheet.close(true)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(android.R.string.ok)) }
                },
            ) {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.label)) },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        singleLine = true,
                        isError = title.text.isEmpty(),
                    )
                    ClickablePreference(
                        label = "Manage apps",
                        subtitle = icon.mInfo.getContents().size.toString() + " apps",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        sheet.close(false)
                        PrimeFolderLongPressHelper.showAppsDialogFromSheet(icon)
                    }
                    if (tabId != null && folderId != null) {
                        ClickablePreference(
                            label = stringResource(R.string.prime_tab_advanced),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            sheet.close(false)
                            icon.context.startActivity(
                                PreferenceActivity.createIntent(
                                    icon.context,
                                    PrimeDrawerFolderAdvanced(tabId, folderId),
                                ),
                            )
                        }
                        ClickablePreference(
                            label = "Supprimer",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            AlertDialog.Builder(icon.context)
                                .setTitle("Supprimer le dossier ?")
                                .setMessage(icon.mInfo.title)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton("Supprimer") { _, _ ->
                                    PrimeDrawerTabsRepository(icon.context).deleteFolder(tabId, folderId)
                                    sheet.close(true)
                                    launcher.appsView.floatingHeaderView?.onPrimeDrawerTabSelected()
                                }
                                .show()
                        }
                    }
                }
            }
        }
    }
}
