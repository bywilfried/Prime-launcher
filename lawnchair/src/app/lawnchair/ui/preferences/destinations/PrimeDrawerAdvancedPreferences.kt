package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.prime.drawer.PrimeDrawerFolderVisualOverrides
import app.lawnchair.prime.drawer.PrimeDrawerMode
import app.lawnchair.prime.drawer.PrimeDrawerModePreferences
import com.android.launcher3.InvariantDeviceProfile
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
        PreferenceGroup {
            ClickablePreference(
                label = "Tout remettre par défaut",
                subtitle = "Supprime toutes les personnalisations de cette catégorie et rétablit l’héritage.",
                confirmationText = "Tout remettre par défaut pour cette catégorie ?",
                onClick = { update(PrimeDrawerVisualOverrides()) },
            )
        }
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
        PreferenceGroup {
            ClickablePreference(
                label = "Tout remettre par défaut",
                subtitle = "Supprime toutes les personnalisations de ce dossier et rétablit l’héritage.",
                confirmationText = "Tout remettre par défaut pour ce dossier ?",
                onClick = { update(PrimeDrawerFolderVisualOverrides()) },
            )
        }
    }
}

@Composable
fun PrimeHomeFolderAdvancedPreference(folderId: Int, drawer: Boolean = false) {
    val context = LocalContext.current
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val overrides = remember(folderId) {
        mutableStateOf(
            if (drawer) repository.getDrawerFolderVisualOverrides(folderId)
            else repository.getHomeFolderVisualOverrides(folderId),
        )
    }
    fun update(value: PrimeDrawerFolderVisualOverrides) {
        overrides.value = value
        if (drawer) {
            repository.setDrawerFolderVisualOverrides(folderId, value)
            PrimeFolderLongPressHelper.refreshDefaultDrawerFolderVisualOverrides(folderId)
        } else {
            repository.setHomeFolderVisualOverrides(folderId, value)
            PrimeFolderLongPressHelper.refreshHomeFolderVisualOverrides(folderId)
        }
    }
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val value = overrides.value
    val defaultTextColor = if (drawer) {
        val grid = InvariantDeviceProfile.INSTANCE.get(context).closestProfile
        PrimeDrawerModePreferences(context).get(grid, PrimeDrawerMode.DEFAULT).defaultDrawerTextColor
            ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
    }
    val defaultFolderColor = app.lawnchair.util.resolveFolderBackgroundColor(context)

    PreferenceLayout(label = stringResource(id = R.string.folders_label), backArrowVisible = true) {
        PreferenceGroup(heading = "Dossier fermé") {
            HomeFolderShapePreference(stringResource(id = R.string.folder_shape_label), value.shape, prefs2.folderShape.getAdapter().state.value, folderId, "folderShape", drawer)
            HomeFolderColorPreference("Couleur du dossier fermé", value.color, app.lawnchair.util.resolveFolderPreviewColor(context, drawer), folderId, "folderColor", drawer)
            NullableFloatSlider(stringResource(id = R.string.folder_preview_bg_opacity_label), value.previewOpacity, prefs2.folderPreviewBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, true) { update(value.copy(previewOpacity = it)) }
            NullableSwitch("Afficher le nom du dossier fermé", value.showFolderLabel, true) { update(value.copy(showFolderLabel = it)) }
            HomeFolderColorPreference("Couleur du nom du dossier fermé", value.closedLabelColor, defaultTextColor, folderId, "folderClosedText", drawer)
        }
        PreferenceGroup(heading = "Dossier ouvert") {
            HomeFolderColorPreference("Couleur du dossier ouvert", value.openColor, defaultFolderColor, folderId, "folderOpenColor", drawer)
            NullableFloatSlider(stringResource(id = R.string.folder_bg_opacity_label), value.backgroundOpacity, prefs2.folderBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, true) { update(value.copy(backgroundOpacity = it)) }
            HomeFolderColorPreference("Couleur du texte dans le dossier ouvert", value.textColor, defaultTextColor, folderId, "folderText", drawer)
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            NullableIntSlider(stringResource(id = R.string.max_folder_columns), value.columns, prefs2.folderColumns.getAdapter().state.value, 2..5) { update(value.copy(columns = it)) }
            NullableIntSlider(stringResource(id = R.string.max_folder_rows), value.rows, prefs.folderRows.getAdapter().state.value, 2..5) { update(value.copy(rows = it)) }
        }
        PreferenceGroup(heading = "Icônes dans le dossier ouvert") {
            HomeFolderShapePreference("Forme des icônes dans les dossiers", value.childIconShape, prefs2.iconShape.getAdapter().state.value, folderId, "folderChildIcon", drawer)
            NullableSwitch(stringResource(id = R.string.show_labels), value.showLabels, prefs2.showIconLabelsOnHomeScreenFolder.getAdapter().state.value) { update(value.copy(showLabels = it)) }
            NullableFloatSlider(stringResource(id = R.string.label_size), value.labelSize, prefs2.homeIconLabelFolderSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, true) { update(value.copy(labelSize = it)) }
        }
        PreferenceGroup {
            ClickablePreference(
                label = "Tout remettre par défaut",
                subtitle = "Supprime toutes les personnalisations de ce dossier et rétablit l’héritage.",
                confirmationText = "Tout remettre par défaut pour ce dossier ?",
                onClick = { update(PrimeDrawerFolderVisualOverrides()) },
            )
        }
    }
}

@Composable
private fun HomeFolderShapePreference(label: String, value: String?, inherited: IconShape, folderId: Int, shapeKey: String, drawer: Boolean = false) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val shape = value?.let { runCatching { IconShape.fromString(it, context) }.getOrNull() }
    val effective = shape ?: inherited
    PreferenceTemplate(
        title = { Text(label) },
        modifier = Modifier.alpha(if (value == null) 0.55f else 1f),
        description = if (value == null) ({ Text("Valeur parente") }) else ({ Text("Personnalisé") }),
        endWidget = { IconShapePreview(iconShape = effective) },
        onClick = { navController.navigate(PrimeHomeFolderShape(folderId, shapeKey, label, drawer)) },
    )
}

@Composable
private fun HomeFolderColorPreference(label: String, value: Int?, inherited: Int, folderId: Int, colorKey: String, drawer: Boolean = false) {
    val navController = LocalNavController.current
    ColorPreference(
        label = label,
        selectedColor = value?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
        modifier = Modifier.alpha(if (value == null) 0.55f else 1f),
        description = if (value == null) "Valeur parente" else "Personnalisé",
        previewColor = ColorOption.CustomColor(value ?: inherited),
        onClick = { navController.navigate(PrimeHomeFolderColor(folderId, label, colorKey, drawer)) },
    )
}

@Composable
private fun PrimeCategoryDrawerOptions(
    tabId: String,
    value: PrimeDrawerVisualOverrides,
    update: (PrimeDrawerVisualOverrides) -> Unit,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val configuration = repository.getConfiguration()
    val defaultTextColor = configuration.defaultDrawerTextColor ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundOption = prefs2.appDrawerBackgroundColor.getAdapter().state.value
    val darkTheme = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val defaultBackgroundColor = configuration.defaultDrawerBackgroundColor ?: when (backgroundOption) {
        ColorOption.Default -> androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
        is ColorOption.CustomColor -> backgroundOption.color
        else -> if (darkTheme) {
            backgroundOption.colorPreferenceEntry.darkColor(context)
        } else {
            backgroundOption.colorPreferenceEntry.lightColor(context)
        }
    }
    val modeTabColor = PrimeDrawerModePreferences(context)
        .get(com.android.launcher3.InvariantDeviceProfile.INSTANCE.get(context).closestProfile, PrimeDrawerMode.TABS)
        .defaultTabsColor
    val masterTabOption = prefs2.tabsColor.getAdapter().state.value
    val masterTabColor = when (masterTabOption) {
        ColorOption.Default -> app.lawnchair.theme.color.tokens.ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context)
        is ColorOption.CustomColor -> masterTabOption.color
        ColorOption.WallpaperPrimary -> android.app.WallpaperManager.getInstance(context)
            .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor?.toArgb()
            ?: 0xFF007FFF.toInt()
        else -> if (darkTheme) {
            masterTabOption.colorPreferenceEntry.darkColor(context)
        } else {
            masterTabOption.colorPreferenceEntry.lightColor(context)
        }
    }
    val defaultTabColor = modeTabColor ?: masterTabColor
    PreferenceGroup(heading = stringResource(id = R.string.style)) {
        NullableColorPreference("Couleur de l’onglet de cette catégorie", value.tabColor, defaultTabColor, tabId, "tab")
        NullableColorPreference("Couleur d’arrière-plan", value.drawerBackgroundColor, defaultBackgroundColor, tabId, "background")
        NullableColorPreference("Couleur du texte", value.drawerTextColor, defaultTextColor, tabId, "drawerText")
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
        NullableShapePreference("Forme des icônes", value.drawerIconShape, prefs2.iconShape.getAdapter().state.value, tabId, "drawerIcon")
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
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val repository = remember { PrimeDrawerTabsRepository(context) }
    val defaultTextColor = repository.getConfiguration().defaultDrawerTextColor ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
    val defaultFolderColor = app.lawnchair.util.resolveFolderBackgroundColor(context)
    PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
        NullableShapePreference(stringResource(id = R.string.folder_shape_label), value.folderShape, prefs2.folderShape.getAdapter().state.value, tabId, "folderShape")
        NullableColorPreference("Couleur du dossier fermé", value.folderColor, app.lawnchair.util.resolveFolderPreviewColor(context, true), tabId, "folderColor")
        NullableColorPreference("Couleur du dossier ouvert", value.folderOpenColor, defaultFolderColor, tabId, "folderOpenColor")
        NullableColorPreference("Couleur du texte dans les dossiers", value.folderTextColor, defaultTextColor, tabId, "folderText")
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
        NullableShapePreference("Forme des icônes dans les dossiers", value.folderChildIconShape, prefs2.iconShape.getAdapter().state.value, tabId, "folderChildIcon")
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
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceGroup(heading = "Dossier fermé") {
        NullableShapePreference(stringResource(id = R.string.folder_shape_label), value.shape, resolveInheritedShape(context, inherited.shape, prefs2.folderShape.getAdapter().state.value), tabId, "folderShape", folderId)
        NullableColorPreference("Couleur du dossier fermé", value.color, inherited.color ?: app.lawnchair.util.resolveFolderPreviewColor(context, true), tabId, "folderColor", folderId)
        NullableFloatSlider(stringResource(id = R.string.folder_preview_bg_opacity_label), value.previewOpacity, inherited.previewOpacity ?: prefs2.folderPreviewBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(previewOpacity = it))
        }
        NullableSwitch("Afficher le nom du dossier fermé", value.showFolderLabel, inherited.showFolderLabel ?: true) {
            update(value.copy(showFolderLabel = it))
        }
        NullableColorPreference("Couleur du nom du dossier fermé", value.closedLabelColor, inherited.closedLabelColor ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb(), tabId, "folderClosedText", folderId)
    }

    PreferenceGroup(heading = "Dossier ouvert") {
        NullableColorPreference("Couleur du dossier ouvert", value.openColor, inherited.openColor ?: app.lawnchair.util.resolveFolderBackgroundColor(context, true), tabId, "folderOpenColor", folderId)
        NullableFloatSlider(stringResource(id = R.string.folder_bg_opacity_label), value.backgroundOpacity, inherited.backgroundOpacity ?: prefs2.folderBackgroundOpacity.getAdapter().state.value, 0f..1f, 0.1f, showAsPercentage = true) {
            update(value.copy(backgroundOpacity = it))
        }
        NullableColorPreference("Couleur du texte dans le dossier ouvert", value.textColor, inherited.textColor ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb(), tabId, "folderText", folderId)
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
        NullableShapePreference("Forme des icônes dans les dossiers", value.childIconShape, resolveInheritedShape(context, inherited.childIconShape, prefs2.iconShape.getAdapter().state.value), tabId, "folderChildIcon", folderId)
        NullableSwitch(stringResource(id = R.string.show_labels), value.showLabels, inherited.showLabels ?: prefs2.showIconLabelsOnHomeScreenFolder.getAdapter().state.value) {
            update(value.copy(showLabels = it))
        }
        NullableFloatSlider(stringResource(id = R.string.label_size), value.labelSize, inherited.labelSize ?: prefs2.homeIconLabelFolderSizeFactor.getAdapter().state.value, 0.5f..1.5f, 0.1f, showAsPercentage = true) {
            update(value.copy(labelSize = it))
        }
    }
}

private fun resolveInheritedShape(context: android.content.Context, value: String?, fallback: IconShape): IconShape =
    value?.let { runCatching { IconShape.fromString(it, context) }.getOrNull() } ?: fallback

@Composable
private fun NullableShapePreference(
    label: String,
    value: String?,
    inherited: IconShape,
    tabId: String,
    shapeKey: String,
    folderId: String? = null,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val shape = value?.let { runCatching { IconShape.fromString(it, context) }.getOrNull() }
    val effective = shape ?: inherited
    PreferenceTemplate(
        title = { Text(label) },
        modifier = Modifier.alpha(if (value == null) 0.55f else 1f),
        description = if (value == null) ({ Text("Valeur parente") }) else ({ Text("Personnalisé") }),
        endWidget = { IconShapePreview(iconShape = effective) },
        onClick = { navController.navigate(PrimeDrawerShape(tabId, shapeKey, label, folderId)) },
    )
}

@Composable
private fun NullableColorPreference(
    label: String,
    value: Int?,
    inherited: Int,
    tabId: String,
    colorKey: String,
    folderId: String? = null,
) {
    val navController = LocalNavController.current
    ColorPreference(
        label = label,
        selectedColor = value?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
        modifier = Modifier.alpha(if (value == null) 0.55f else 1f),
        description = if (value == null) "Valeur parente" else "Personnalisé",
        previewColor = ColorOption.CustomColor(value ?: inherited),
        onClick = { navController.navigate(PrimeDrawerCategoryColor(tabId, colorKey, label, folderId)) },
    )
}

@Composable
private fun NullableSwitch(label: String, value: Boolean?, inherited: Boolean, update: (Boolean?) -> Unit) {
    val isInherited = value == null
    val effective = value ?: inherited
    SwitchPreference(
        checked = effective,
        onCheckedChange = { update(it) },
        label = label,
        modifier = Modifier.alpha(if (isInherited) 0.55f else 1f),
        description = if (isInherited) {
            "Valeur parente • ${if (inherited) "Activé" else "Désactivé"}"
        } else {
            "Personnalisé • ${if (effective) "Activé" else "Désactivé"} • ↶ Valeur parente : ${if (inherited) "Activé" else "Désactivé"}"
        },
        onClick = if (isInherited) null else ({ update(null) }),
    )
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
    val isInherited = value == null
    SliderPreference(
        label = label,
        value = value ?: inherited,
        onValueChangeFinished = { update(it) },
        valueRange = range,
        step = step,
        showAsPercentage = showAsPercentage,
        modifier = Modifier.alpha(if (isInherited) 0.55f else 1f),
        status = if (isInherited) {
            "Valeur parente • ${formatAdvancedValue(inherited, showAsPercentage)}"
        } else {
            "Personnalisé • ↶ Valeur parente : ${formatAdvancedValue(inherited, showAsPercentage)}"
        },
        onReset = if (isInherited) null else ({ update(null) }),
    )
}

@Composable
private fun NullableIntSlider(label: String, value: Int?, inherited: Int, range: ClosedRange<Int>, update: (Int?) -> Unit) {
    val isInherited = value == null
    SliderPreference(
        label = label,
        value = (value ?: inherited).toFloat(),
        onValueChangeFinished = { update(it.toInt()) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        step = 1f,
        modifier = Modifier.alpha(if (isInherited) 0.55f else 1f),
        status = if (isInherited) "Valeur parente • $inherited" else "Personnalisé • ↶ Valeur parente : $inherited",
        onReset = if (isInherited) null else ({ update(null) }),
    )
}

private fun formatAdvancedValue(value: Float, asPercentage: Boolean): String =
    if (asPercentage) "${kotlin.math.round(value * 100).toInt()} %" else {
        val rounded = kotlin.math.round(value * 100) / 100f
        if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

@Composable
private fun SliderPreference(
    label: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    showAsPercentage: Boolean = false,
    modifier: Modifier = Modifier,
    status: String? = null,
    onReset: (() -> Unit)? = null,
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
        modifier = modifier,
    )
    if (status != null) {
        Text(
            text = status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .then(if (onReset != null) Modifier.clickable(onClick = onReset) else Modifier),
        )
    }
}

@Composable
private fun AdvancedPlaceholder(label: String, subtitle: String) {
    ClickablePreference(label = label + NOT_IMPLEMENTED, subtitle = subtitle, onClick = {})
}
