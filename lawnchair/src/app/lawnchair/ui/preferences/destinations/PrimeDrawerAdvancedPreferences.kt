package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.prime.drawer.PrimeDrawerFolderVisualOverrides
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.prime.drawer.PrimeFolderLongPressHelper
import app.lawnchair.prime.drawer.PrimeDrawerVisualOverrides
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategoryColor
import app.lawnchair.ui.preferences.navigation.PrimeDrawerShape
import app.lawnchair.ui.preferences.navigation.PrimeHomeFolderColor
import app.lawnchair.ui.preferences.navigation.PrimeHomeFolderShape
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.ui.preferences.destinations.IconShapePreview
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

private const val NOT_IMPLEMENTED = " *"

@Composable
fun PrimeDrawerCategoryAdvancedPreference(tabId: String) {
    val context = LocalContext.current
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val initial = remember(tabId) {
        repository.getConfiguration().tabs.firstOrNull { it.id == tabId }?.visualOverrides
            ?: PrimeDrawerVisualOverrides()
    }
    val overrides = remember(tabId) { mutableStateOf(initial) }
    fun update(value: PrimeDrawerVisualOverrides) {
        overrides.value = value
        repository.setTabVisualOverrides(tabId, value)
    }

    PreferenceLayout(label = "Options avancées", backArrowVisible = true) {
        PrimeCategoryDrawerOptions(tabId, overrides.value, ::update)
        PrimeCategoryFolderOptions(tabId, overrides.value, ::update)
    }
}

@Composable
fun PrimeDrawerFolderAdvancedPreference(tabId: String, folderId: String) {
    val context = LocalContext.current
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val initial = remember(tabId, folderId) {
        repository.getConfiguration().tabs.firstOrNull { it.id == tabId }
            ?.folders?.firstOrNull { it.id == folderId }?.visualOverrides
            ?: PrimeDrawerFolderVisualOverrides()
    }
    val overrides = remember(tabId, folderId) { mutableStateOf(initial) }
    val inherited = remember(tabId, folderId, overrides.value) {
        repository.getResolvedFolderVisualOverrides(tabId, folderId)
            ?: PrimeDrawerFolderVisualOverrides()
    }
    fun update(value: PrimeDrawerFolderVisualOverrides) {
        overrides.value = value
        repository.setFolderVisualOverrides(tabId, folderId, value)
    }

    PreferenceLayout(label = stringResource(id = R.string.folders_label), backArrowVisible = true) {
        PrimeFolderOptions(tabId, folderId, overrides.value, inherited, ::update)
    }
}

@Composable
fun PrimeHomeFolderAdvancedPreference(folderId: Int) {
    val context = LocalContext.current
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val overrides = remember(folderId) {
        mutableStateOf(repository.getHomeFolderVisualOverrides(folderId))
    }
    fun update(value: PrimeDrawerFolderVisualOverrides) {
        overrides.value = value
        repository.setHomeFolderVisualOverrides(folderId, value)
        PrimeFolderLongPressHelper.refreshHomeFolderVisualOverrides(folderId)
    }
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val value = overrides.value

    PreferenceLayout(label = stringResource(id = R.string.folders_label), backArrowVisible = true) {
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            HomeFolderShapePreference(stringResource(id = R.string.folder_shape_label), value.shape, folderId, "folderShape")
            HomeFolderColorPreference("Couleur de l’arrière-plan des icônes", value.color, folderId, "folderColor")
            NullableFloatSlider(stringResource(id = R.string.folder_preview_bg_opacity_label), value.previewOpacity, prefs2.folderPreviewBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, true) { update(value.copy(previewOpacity = it)) }
            NullableFloatSlider(stringResource(id = R.string.folder_bg_opacity_label), value.backgroundOpacity, prefs2.folderBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, true) { update(value.copy(backgroundOpacity = it)) }
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            NullableIntSlider(stringResource(id = R.string.max_folder_columns), value.columns, prefs2.folderColumns.getAdapter().state.value, 2..5) { update(value.copy(columns = it)) }
            NullableIntSlider(stringResource(id = R.string.max_folder_rows), value.rows, prefs.folderRows.getAdapter().state.value, 2..5) { update(value.copy(rows = it)) }
        }
        PreferenceGroup(heading = stringResource(id = R.string.folders_label)) {
            NullableSwitch("Afficher le nom du dossier fermé", value.showFolderLabel, true) { update(value.copy(showFolderLabel = it)) }
        }
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            HomeFolderShapePreference("Forme des icônes dans les dossiers", value.childIconShape, folderId, "folderChildIcon")
            NullableSwitch(stringResource(id = R.string.show_labels), value.showLabels, prefs2.showIconLabelsOnHomeScreenFolder.getAdapter().state.value) { update(value.copy(showLabels = it)) }
            NullableFloatSlider(stringResource(id = R.string.label_size), value.labelSize, prefs2.homeIconLabelFolderSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, true) { update(value.copy(labelSize = it)) }
        }
    }
}

@Composable
private fun HomeFolderShapePreference(label: String, value: String?, folderId: Int, shapeKey: String) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val shape = value?.let { runCatching { IconShape.fromString(it, context) }.getOrNull() }
    PreferenceTemplate(
        title = { Text(label) },
        description = if (value == null) ({ Text("Configuration générale") }) else null,
        endWidget = shape?.let { selected -> { IconShapePreview(iconShape = selected) } },
        onClick = { navController.navigate(PrimeHomeFolderShape(folderId, shapeKey, label)) },
    )
}

@Composable
private fun HomeFolderColorPreference(label: String, value: Int?, folderId: Int, colorKey: String) {
    val navController = LocalNavController.current
    ColorPreference(
        label = label,
        selectedColor = value?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
        onClick = { navController.navigate(PrimeHomeFolderColor(folderId, label, colorKey)) },
    )
}

@Composable
private fun PrimeCategoryDrawerOptions(
    tabId: String,
    value: PrimeDrawerVisualOverrides,
    update: (PrimeDrawerVisualOverrides) -> Unit,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceGroup(heading = stringResource(id = R.string.style)) {
        NullableColorPreference("Couleur de l’onglet de cette catégorie", value.tabColor, tabId, "tab")
        NullableColorPreference("Couleur d’arrière-plan", value.drawerBackgroundColor, tabId, "background")
        NullableColorPreference("Couleur du texte", value.drawerTextColor, tabId, "drawerText")
        NullableFloatSlider(stringResource(id = R.string.background_opacity), value.drawerBackgroundOpacity, prefs.drawerOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(drawerBackgroundOpacity = it))
        }
    }
    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        NullableIntSlider(stringResource(id = R.string.app_drawer_columns), value.drawerColumns, prefs2.drawerColumns.getAdapter().state.value, 3..10) {
            update(value.copy(drawerColumns = it))
        }
        NullableFloatSlider(stringResource(id = R.string.row_height_label), value.drawerRowHeight, prefs2.drawerCellHeightFactor.getAdapter().state.value, 0.3f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(drawerRowHeight = it))
        }
        NullableFloatSlider(stringResource(id = R.string.app_drawer_indent_label), value.drawerHorizontalMargin, prefs2.drawerLeftRightMarginFactor.getAdapter().state.value, 0f..1.5f, 0.05f, showAsPercentage = true) {
            update(value.copy(drawerHorizontalMargin = it))
        }
        NullableFloatSlider(stringResource(id = R.string.top_padding_label), value.drawerTopPadding, prefs2.drawerPaddingTopFactor.getAdapter().state.value, 1f..2f, 0.05f, showAsPercentage = true) {
            update(value.copy(drawerTopPadding = it))
        }
    }
    PreferenceGroup(heading = stringResource(id = R.string.icons)) {
        NullableShapePreference("Forme des icônes", value.drawerIconShape, tabId, "drawerIcon")
        NullableFloatSlider(stringResource(id = R.string.icon_sizes), value.drawerIconSize, prefs2.drawerIconSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(drawerIconSize = it))
        }
        NullableSwitch(stringResource(id = R.string.show_labels), value.showLabels, prefs2.showIconLabelsInDrawer.getAdapter().state.value) {
            update(value.copy(showLabels = it))
        }
        NullableFloatSlider(stringResource(id = R.string.label_size), value.labelSize, prefs2.drawerIconLabelSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(labelSize = it))
        }
        NullableSwitch(stringResource(id = R.string.twoline_label), value.twoLineLabels, prefs2.twoLineAllApps.getAdapter().state.value) {
            update(value.copy(twoLineLabels = it))
        }
    }
    PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
        NullableSwitch(stringResource(id = R.string.pref_all_apps_remember_position_title), value.rememberPosition, prefs2.rememberPosition.getAdapter().state.value) {
            update(value.copy(rememberPosition = it))
        }
        NullableSwitch(stringResource(id = R.string.pref_all_apps_show_scrollbar_title), value.showScrollbar, prefs2.showScrollbar.getAdapter().state.value) {
            update(value.copy(showScrollbar = it))
        }
    }
}

@Composable
private fun PrimeCategoryFolderOptions(
    tabId: String,
    value: PrimeDrawerVisualOverrides,
    update: (PrimeDrawerVisualOverrides) -> Unit,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
        NullableShapePreference(stringResource(id = R.string.folder_shape_label), value.folderShape, tabId, "folderShape")
        NullableColorPreference("Couleur de l’arrière-plan des icônes", value.folderColor, tabId, "folderColor")
        NullableColorPreference("Couleur du texte dans les dossiers", value.folderTextColor, tabId, "folderText")
        NullableFloatSlider(stringResource(id = R.string.folder_preview_bg_opacity_label), value.folderPreviewOpacity, prefs2.folderPreviewBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(folderPreviewOpacity = it))
        }
        NullableFloatSlider(stringResource(id = R.string.folder_bg_opacity_label), value.folderBackgroundOpacity, prefs2.folderBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(folderBackgroundOpacity = it))
        }
    }
    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        NullableIntSlider(stringResource(id = R.string.max_folder_columns), value.folderColumns, prefs2.folderColumns.getAdapter().state.value, 2..5) {
            update(value.copy(folderColumns = it))
        }
        NullableIntSlider(stringResource(id = R.string.max_folder_rows), value.folderRows, prefs.folderRows.getAdapter().state.value, 2..5) {
            update(value.copy(folderRows = it))
        }
    }
    PreferenceGroup(heading = stringResource(id = R.string.icons)) {
        NullableShapePreference("Forme des icônes dans les dossiers", value.folderChildIconShape, tabId, "folderChildIcon")
        NullableSwitch(stringResource(id = R.string.show_labels), value.folderShowLabels, prefs2.showIconLabelsOnHomeScreenFolder.getAdapter().state.value) {
            update(value.copy(folderShowLabels = it))
        }
        NullableFloatSlider(stringResource(id = R.string.label_size), value.folderLabelSize, prefs2.homeIconLabelFolderSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(folderLabelSize = it))
        }
    }
}

@Composable
private fun PrimeFolderOptions(
    tabId: String,
    folderId: String,
    value: PrimeDrawerFolderVisualOverrides,
    inherited: PrimeDrawerFolderVisualOverrides,
    update: (PrimeDrawerFolderVisualOverrides) -> Unit,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceGroup(heading = "Dossier fermé") {
        NullableShapePreference(stringResource(id = R.string.folder_shape_label), value.shape, tabId, "folderShape", folderId)
        NullableColorPreference("Couleur de l’arrière-plan des icônes", value.color, tabId, "folderColor", folderId)
        NullableFloatSlider(stringResource(id = R.string.folder_preview_bg_opacity_label), value.previewOpacity, inherited.previewOpacity ?: prefs2.folderPreviewBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(previewOpacity = it))
        }
        NullableSwitch("Afficher le nom du dossier fermé", value.showFolderLabel, inherited.showFolderLabel ?: true) {
            update(value.copy(showFolderLabel = it))
        }
        NullableColorPreference("Couleur du nom du dossier fermé", value.closedLabelColor, tabId, "folderClosedText", folderId)
    }

    PreferenceGroup(heading = "Dossier ouvert") {
        NullableFloatSlider(stringResource(id = R.string.folder_bg_opacity_label), value.backgroundOpacity, inherited.backgroundOpacity ?: prefs2.folderBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(backgroundOpacity = it))
        }
        NullableColorPreference("Couleur du texte dans le dossier ouvert", value.textColor, tabId, "folderText", folderId)
    }

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        NullableIntSlider(stringResource(id = R.string.max_folder_columns), value.columns, inherited.columns ?: prefs2.folderColumns.getAdapter().state.value, 2..5) {
            update(value.copy(columns = it))
        }
        NullableIntSlider(stringResource(id = R.string.max_folder_rows), value.rows, inherited.rows ?: prefs.folderRows.getAdapter().state.value, 2..5) {
            update(value.copy(rows = it))
        }
    }

    PreferenceGroup(heading = "Icônes dans le dossier ouvert") {
        NullableShapePreference("Forme des icônes dans les dossiers", value.childIconShape, tabId, "folderChildIcon", folderId)
        NullableSwitch(stringResource(id = R.string.show_labels), value.showLabels, inherited.showLabels ?: prefs2.showIconLabelsOnHomeScreenFolder.getAdapter().state.value) {
            update(value.copy(showLabels = it))
        }
        NullableFloatSlider(stringResource(id = R.string.label_size), value.labelSize, inherited.labelSize ?: prefs2.homeIconLabelFolderSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(labelSize = it))
        }
    }
}

@Composable
private fun NullableShapePreference(
    label: String,
    value: String?,
    tabId: String,
    shapeKey: String,
    folderId: String? = null,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val shape = value?.let { runCatching { IconShape.fromString(it, context) }.getOrNull() }
    PreferenceTemplate(
        title = { Text(label) },
        description = if (value == null) ({ Text("Configuration générale") }) else null,
        endWidget = shape?.let { selected -> { IconShapePreview(iconShape = selected) } },
        onClick = { navController.navigate(PrimeDrawerShape(tabId, shapeKey, label, folderId)) },
    )
}

@Composable
private fun NullableColorPreference(
    label: String,
    value: Int?,
    tabId: String,
    colorKey: String,
    folderId: String? = null,
) {
    val navController = LocalNavController.current
    ColorPreference(
        label = label,
        selectedColor = value?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
        onClick = { navController.navigate(PrimeDrawerCategoryColor(tabId, colorKey, label, folderId)) },
    )
}

@Composable
private fun NullableSwitch(label: String, value: Boolean?, inherited: Boolean, update: (Boolean?) -> Unit) {
    if (value == null) {
        SwitchPreference(checked = inherited, onCheckedChange = { update(it) }, label = label, description = "Configuration générale")
    } else {
        SwitchPreference(
            checked = value,
            onCheckedChange = { update(it) },
            label = label,
            description = null,
        )
        ClickablePreference(label = "Utiliser la configuration générale", onClick = { update(null) })
    }
}

@Composable
private fun NullableFloatSlider(
    label: String,
    value: Float?,
    inherited: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
    update: (Float?) -> Unit,
) {
    if (value == null) {
        SliderPreference(label = label, value = inherited, onValueChangeFinished = { update(it) }, valueRange = range, step = step, showAsPercentage = showAsPercentage)
        ClickablePreference(label = "Configuration générale", subtitle = "Toucher le réglage pour le personnaliser", onClick = {})
    } else {
        SliderPreference(label = label, value = value, onValueChangeFinished = { update(it) }, valueRange = range, step = step, showAsPercentage = showAsPercentage)
        ClickablePreference(label = "Utiliser la configuration générale", onClick = { update(null) })
    }
}

@Composable
private fun NullableIntSlider(label: String, value: Int?, inherited: Int, range: ClosedRange<Int>, update: (Int?) -> Unit) {
    if (value == null) {
        SliderPreference(label = label, value = inherited.toFloat(), onValueChangeFinished = { update(it.toInt()) }, valueRange = range.start.toFloat()..range.endInclusive.toFloat(), step = 1f)
        ClickablePreference(label = "Configuration générale", subtitle = "Toucher le réglage pour le personnaliser", onClick = {})
    } else {
        SliderPreference(label = label, value = value.toFloat(), onValueChangeFinished = { update(it.toInt()) }, valueRange = range.start.toFloat()..range.endInclusive.toFloat(), step = 1f)
        ClickablePreference(label = "Utiliser la configuration générale", onClick = { update(null) })
    }
}

@Composable
private fun SliderPreference(
    label: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
) {
    val state = remember(value) { mutableStateOf(value) }
    app.lawnchair.ui.preferences.components.controls.SliderPreference(
        label = label,
        adapter = app.lawnchair.preferences.customPreferenceAdapter(state.value) {
            state.value = it
            onValueChangeFinished(it)
        },
        valueRange = valueRange,
        step = step,
        showAsPercentage = showAsPercentage,
    )
}

@Composable
private fun AdvancedPlaceholder(label: String, subtitle: String) {
    ClickablePreference(label = label + NOT_IMPLEMENTED, subtitle = subtitle, onClick = {})
}
