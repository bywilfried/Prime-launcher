package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.prime.drawer.PrimeDrawerTab
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategory
import com.android.launcher3.R

@Composable
fun PrimeDrawerCategoriesPreference() {
    val context = LocalContext.current
    val repository = remember(context) { PrimeDrawerTabsRepository(context) }
    val prefs = preferenceManager()
    val hideAll by prefs.drawerTabsHideAll.getAdapter().state
    val hideUnclassified by prefs.drawerTabsHideUnclassified.getAdapter().state
    var tabs by remember { mutableStateOf(repository.getConfiguration().tabs) }
    val navController = LocalNavController.current
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    PreferenceLayout(
        label = stringResource(id = R.string.prime_categories_manage),
        backArrowVisible = true,
    ) {
        PreferenceGroup {
            ClickablePreference(
                label = "Ajouter une catégorie",
                subtitle = "Créer un nouvel onglet dans le tiroir",
                onClick = { newCategoryName = ""; showAddDialog = true },
            )
        }
        ReorderablePreferenceGroup(
            label = null,
            items = tabs,
            defaultList = tabs,
            onOrderChange = { reordered ->
                tabs = reordered
                repository.reorderTabs(reordered.map { it.id })
            },
        ) { tab, _, _ ->
            val interactionSource = remember { MutableInteractionSource() }
            val hidden = when (tab.id) {
                PrimeDrawerTabsRepository.ALL_TAB_ID -> hideAll
                PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID -> hideUnclassified
                else -> false
            }
            PrimeCategoryItem(
                tab = tab,
                hidden = hidden,
                interactionSource = interactionSource,
                onClick = { navController.navigate(PrimeDrawerCategory(tab.id)) },
                dragIndicator = {
                    ReorderableDragHandle(
                        interactionSource = interactionSource,
                        scope = this,
                    )
                },
            )
        }
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nouvelle catégorie") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Nom de la catégorie") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategoryName.isNotBlank(),
                    onClick = {
                        val created = repository.createTab(newCategoryName.trim())
                        tabs = repository.getConfiguration().tabs
                        showAddDialog = false
                        navController.navigate(PrimeDrawerCategory(created.id))
                    },
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun PrimeCategoryItem(
    tab: PrimeDrawerTab,
    hidden: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    dragIndicator: @Composable () -> Unit,
) {
    val title = when (tab.id) {
        PrimeDrawerTabsRepository.ALL_TAB_ID -> stringResource(id = R.string.prime_tab_all)
        PrimeDrawerTabsRepository.UNCLASSIFIED_TAB_ID -> stringResource(id = R.string.prime_tab_unclassified)
        else -> tab.title
    }
    PreferenceTemplate(
        title = {
            Text(
                text = title,
                color = if (hidden) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        description = if (hidden) {
            {
                Text(
                    text = stringResource(id = R.string.prime_category_hidden),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        } else {
            null
        },
        startWidget = dragIndicator,
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
