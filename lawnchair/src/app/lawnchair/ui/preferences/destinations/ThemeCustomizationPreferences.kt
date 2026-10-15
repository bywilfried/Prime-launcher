package app.lawnchair.ui.preferences.destinations

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.lawnchair.theme.ThemeShareFile
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.qsb.getHotseatBackgroundColor
import app.lawnchair.theme.ThemeColors
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.navigation.ThemeColorSelection
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.CategoryThemeColorOverrides
import app.lawnchair.theme.ThemeColorOverrides
import app.lawnchair.theme.ThemeVariant
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

/**
 * Inventory-first editor for ThemeProfile colors.
 *
 * The rows intentionally exist before every runtime consumer is connected. This makes this screen
 * the checklist used to wire preview + runtime through the same ThemeColorRole resolver.
 * Persistence/export actions are intentionally disabled until the sparse override store is added.
 */
@Composable
fun ThemeCustomizationPreferences(modifier: Modifier = Modifier, openDrawerSection: Boolean = false, categoryTabId: String? = null, folderKey: String? = null, folderSection: String? = null) {
    val context = LocalContext.current
    var bulkResetRevision by remember { mutableStateOf(0) }
    // Save stable strings rather than Serializable enums: R8 may rename/remove the
    // synthetic enum values() method used by Android Parcel restoration.
    var variantName by rememberSaveable { mutableStateOf(context.effectiveThemeVariant().name) }
    val variant = ThemeVariant.valueOf(variantName)
    var expandedName by rememberSaveable(openDrawerSection, categoryTabId, folderKey) {
        mutableStateOf(if (folderKey != null) (folderSection ?: "HOME") else if (openDrawerSection || categoryTabId != null) ThemeColorRole.Section.DRAWER.name else null)
    }
    val expanded = expandedName?.let(ThemeColorRole.Section::valueOf)
    var confirmThemeReset by rememberSaveable { mutableStateOf(false) }
    var confirmVariantReset by rememberSaveable { mutableStateOf(false) }
    val profile = ThemeProfile.current(context)
    val overrides = remember(context) { ThemeColorOverrides(context) }
    val exportTheme = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ThemeShareFile.export(context, it) }
                ?: error("Unable to open destination")
        }.onSuccess {
            Toast.makeText(context, "Thème exporté", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Log.e("ThemeCustomization", "Theme export failed", it)
            Toast.makeText(context, "Échec de l'export du thème", Toast.LENGTH_SHORT).show()
        }
    }
    val importTheme = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { ThemeShareFile.import(context, it) }
                ?: error("Unable to open theme file")
        }.onSuccess {
            bulkResetRevision++
            Toast.makeText(context, "Thème importé", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Log.e("ThemeCustomization", "Theme import failed", it)
            Toast.makeText(context, "Fichier de thème invalide", Toast.LENGTH_SHORT).show()
        }
    }


    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = if (folderKey != null) "Couleurs du dossier" else if (categoryTabId == null) "Personnaliser les couleurs du thème" else "Couleurs de la catégorie",
        modifier = modifier,
    ) {
        PreferenceGroup(heading = "Legacy") {
            PreferenceTemplate(
                title = { Text("Variante") },
                endWidget = {
                    Row {
                        val light = variant == ThemeVariant.LIGHT
                        if (light) Button(onClick = { variantName = ThemeVariant.LIGHT.name }) { Text("Clair") }
                        else OutlinedButton(onClick = { variantName = ThemeVariant.LIGHT.name }) { Text("Clair") }
                        val dark = variant == ThemeVariant.DARK
                        if (dark) Button(onClick = { variantName = ThemeVariant.DARK.name }) { Text("Sombre") }
                        else OutlinedButton(onClick = { variantName = ThemeVariant.DARK.name }) { Text("Sombre") }
                    }
                },
            )
        }

        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = "Global",
            variant = variant,
            section = ThemeColorRole.Section.GLOBAL,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = "Accueil",
            variant = variant,
            section = ThemeColorRole.Section.HOME,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = "Dock",
            variant = variant,
            section = ThemeColorRole.Section.DOCK,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (folderKey != null) ThemeSection(
            title = "Couleurs du dossier",
            categoryTabId = categoryTabId,
            folderKey = folderKey,
            variant = variant,
            section = ThemeColorRole.Section.valueOf(folderSection ?: "HOME"),
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (folderKey == null) ThemeSection(
            title = "Tiroir des applications",
            categoryTabId = categoryTabId,
            variant = variant,
            section = ThemeColorRole.Section.DRAWER,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )

        PreferenceGroup(heading = if (folderKey != null) "Gestion du dossier" else if (categoryTabId == null) "Gestion du thème" else "Gestion de la catégorie") {
            PreferenceTemplate(
                title = { Text("Réinitialiser la variante") },
                description = { Text("Supprimera les personnalisations de la variante affichée.") },
                onClick = { confirmVariantReset = true },
            )
            PreferenceTemplate(
                title = { Text("Réinitialiser le thème") },
                description = { Text("Restaurera les valeurs officielles du thème sélectionné.") },
                onClick = { confirmThemeReset = true },
            )
            PreferenceTemplate(
                title = { Text("Exporter le thème") },
                description = { Text("Partager le thème général, sa palette et ses couleurs.") },
                enabled = categoryTabId == null && folderKey == null,
                onClick = { exportTheme.launch("Prime-theme.primetheme") },
            )
            PreferenceTemplate(
                title = { Text("Importer un thème") },
                description = { Text("Importer un fichier .primetheme sans modifier l’accueil.") },
                enabled = categoryTabId == null && folderKey == null,
                onClick = { importTheme.launch(arrayOf("*/*")) },
            )
        }
    }

    if (confirmVariantReset) {
        AlertDialog(
            onDismissRequest = { confirmVariantReset = false },
            title = { Text("Réinitialiser la variante ?") },
            text = {
                Text("Toutes les personnalisations de la variante ${if (variant == ThemeVariant.LIGHT) "Clair" else "Sombre"} seront supprimées. L’autre variante sera conservée.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).resetVariant(folderKey, profile, variant)
                    else if (categoryTabId == null) overrides.resetVariant(profile, variant)
                    else CategoryThemeColorOverrides(context).resetVariant(categoryTabId, profile, variant)
                    bulkResetRevision++
                    confirmVariantReset = false
                }) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVariantReset = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    if (confirmThemeReset) {
        AlertDialog(
            onDismissRequest = { confirmThemeReset = false },
            title = { Text("Réinitialiser le thème ?") },
            text = { Text("Toutes les personnalisations des variantes Clair et Sombre seront supprimées.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).resetTheme(folderKey, profile)
                        else if (categoryTabId == null) overrides.resetTheme(profile)
                        else CategoryThemeColorOverrides(context).resetTheme(categoryTabId, profile)
                        bulkResetRevision++
                        confirmThemeReset = false
                    },
                ) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmThemeReset = false }) {
                    Text("Annuler")
                }
            },
        )
    }
}

@Composable
private fun ThemeSection(
    title: String,
    section: ThemeColorRole.Section,
    variant: ThemeVariant,
    expanded: ThemeColorRole.Section?,
    bulkResetRevision: Int,
    onToggle: (ThemeColorRole.Section) -> Unit,
    categoryTabId: String? = null,
    folderKey: String? = null,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    PreferenceGroup {
        PreferenceTemplate(
            title = { Text(title) },
            description = { Text(if (expanded == section) "Masquer les couleurs" else "Afficher les couleurs") },
            onClick = { onToggle(section) },
        )
        ExpandAndShrink(visible = expanded == section) {
            androidx.compose.foundation.layout.Column {
                rolesForUi(section).filter { role ->
                    (folderKey == null || role.id.contains("folder.")) &&
                    role != ThemeColorRole.TABS_CATEGORY_TEXT &&
                        role != ThemeColorRole.HOME_FOLDER_BORDER &&
                        role != ThemeColorRole.DOCK_FOLDER_BORDER &&
                        role != ThemeColorRole.DRAWER_FOLDER_BORDER
                }.forEach { role ->
                        val profile = ThemeProfile.current(context)
                        // Observe an immediate local reset so Compose redraws this row without
                        // requiring the user to leave and reopen the editor.
                        var resetRevision by remember(profile, variant, role, categoryTabId, folderKey) {
                            mutableStateOf(0)
                        }
                        val override = run {
                            bulkResetRevision // Recompose on variant/theme reset.
                            resetRevision // Recompose on individual reset.
                            if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).get(folderKey, profile, variant, role)
                            else if (categoryTabId == null) ThemeColorOverrides(context).get(profile, variant, role)
                            else CategoryThemeColorOverrides(context).get(categoryTabId, profile, variant, role)
                        }
                        val prefs2 = preferenceManager2()
                        val preview = when (role) {
                            // Preview the exact Dock QSB defaults, including the existing
                            // "apply accent color" switch and its separate border preference.
                            ThemeColorRole.HOME_SEARCH_BACKGROUND -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    getHotseatBackgroundColor(context, prefs2.themedHotseatQsb.asState().value)
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_SEARCH_ICON -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    ThemeColors.resolveLegacyToken(
                                        context,
                                        app.lawnchair.theme.color.tokens.ColorTokens.ColorAccent,
                                        variant,
                                    )
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_SEARCH_BORDER -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    prefs2.strokeColorStyle.asState().value.colorPreferenceEntry.lightColor(context)
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> {
                                val legacy = prefs2.notificationDotColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> {
                                val legacy = prefs2.hotseatBackgroundColor.asState().value
                                ThemeColors.resolveDockBackgroundForVariant(context, legacy, variant)
                            }
                            ThemeColorRole.HOME_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.HOME_FOLDER_OPEN_BORDER,
                            ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.DOCK_FOLDER_OPEN_BORDER,
                            ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.DRAWER_FOLDER_OPEN_BORDER -> {
                                val backgroundRole = when (role) {
                                    ThemeColorRole.HOME_FOLDER_CLOSED_BORDER -> ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND
                                    ThemeColorRole.HOME_FOLDER_OPEN_BORDER -> ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER -> ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_OPEN_BORDER -> ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER -> ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND
                                    else -> ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND
                                }
                                ThemeColors.resolveFolderBorderForVariant(
                                    context, role,
                                    ThemeColors.resolveTheme(context, profile, backgroundRole, variant),
                                    variant,
                                )
                            }
                            ThemeColorRole.HOME_FOLDER_OPEN_TEXT,
                            ThemeColorRole.DOCK_FOLDER_OPEN_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT -> {
                                val backgroundRole = when (role) {
                                    ThemeColorRole.HOME_FOLDER_OPEN_TEXT -> ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_OPEN_TEXT -> ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND
                                    else -> ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND
                                }
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    ThemeColors.resolveOpenFolderTitleColorForVariant(
                                        context, role,
                                        ThemeColors.resolveTheme(context, profile, backgroundRole, variant),
                                        variant,
                                    )
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT -> {
                                val legacy = prefs2.notificationDotTextColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_ICON_TEXT,
                            ThemeColorRole.HOME_FOLDER_ICON_TEXT,
                            ThemeColorRole.HOME_FOLDER_CLOSED_TEXT,
                            ThemeColorRole.DOCK_ICON_TEXT,
                            ThemeColorRole.DOCK_FOLDER_ICON_TEXT,
                            ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT,
                            ThemeColorRole.DRAWER_ICON_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_ICON_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT -> ThemeColors.resolveIconLabelColorForVariant(context, role,
                                if (variant == ThemeVariant.DARK) android.graphics.Color.BLACK else android.graphics.Color.WHITE, variant)
                            else -> ThemeColors.resolveTheme(context, profile, role, variant)
                        }
                        val categoryPreview = if (folderKey != null) ThemeColors.resolveForFolder(context, profile, role, variant, folderKey, categoryTabId)
                            else if (categoryTabId == null || override == ColorOption.Default) preview
                            else ThemeColors.resolveForCategory(context, profile, role, variant, categoryTabId)
                        val isInherited = override == ColorOption.Default
                        val rolePreferenceAlpha = if (isInherited) 0.58f else 1f
                        Box {
                        ColorPreference(
                            label = roleLabel(role),
                            selectedColor = override,
                            modifier = Modifier.graphicsLayer { alpha = rolePreferenceAlpha },
                            previewColor = ColorOption.CustomColor(categoryPreview),
                            iconColorPreview = (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT &&
                                (override == ColorOption.IconColor || override == ColorOption.Default || override == ColorOption.LegacyBehavior)) ||
                                (role == ThemeColorRole.HOME_SEARCH_ICON &&
                                    (override == ColorOption.Default || override == ColorOption.LegacyBehavior) &&
                                    !prefs2.themedHotseatQsb.asState().value),
                            // Always display the resolved color, not a generic "A" glyph.
                            dynamicContrastPreview = (override == ColorOption.Default || override == ColorOption.LegacyBehavior) &&
                                (role == ThemeColorRole.HOME_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.DOCK_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.HOME_FOLDER_OPEN_HINT ||
                                    role == ThemeColorRole.DOCK_FOLDER_OPEN_HINT ||
                                    role == ThemeColorRole.DRAWER_FOLDER_OPEN_HINT),
                            automaticContrastPreview = (override == ColorOption.Default || override == ColorOption.LegacyBehavior || override == ColorOption.AutomaticBlackWhite) &&
                                (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT ||
                                role == ThemeColorRole.HOME_ICON_TEXT ||
                                role == ThemeColorRole.HOME_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.HOME_FOLDER_CLOSED_TEXT ||
                                role == ThemeColorRole.DOCK_ICON_TEXT ||
                                role == ThemeColorRole.DOCK_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT ||
                                role == ThemeColorRole.DRAWER_TEXT ||
                                role == ThemeColorRole.DRAWER_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT),
                            description = if (override == ColorOption.LegacyBehavior) {
                                "Comportement Legacy (fixé)"
                            } else if (override == ColorOption.Default) {
                                if (role == ThemeColorRole.HOME_PAGE_INDICATOR_ACTIVE ||
                                    role == ThemeColorRole.HOME_PAGE_INDICATOR_INACTIVE)
                                    "Contraste automatique (noir/blanc)"
                                else if (folderKey != null) "Hérité des couleurs du parent" else if (categoryTabId != null) "Hérité du thème principal ou de sa personnalisation" else "Valeur du thème"
                            } else {
                                override.colorPreferenceEntry.label()
                            },
                            onClick = {
                                navController.navigate(
                                    ThemeColorSelection(role.id, variant.name, categoryTabId, folderKey),
                                )
                            },
                        )
                        if (!isInherited) {
                            IconButton(
                                onClick = {
                                    if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).set(
                                        folderKey, profile, variant, role, ColorOption.Default,
                                    ) else if (categoryTabId == null) ThemeColorOverrides(context).set(
                                        profile, variant, role, ColorOption.Default,
                                    ) else CategoryThemeColorOverrides(context).set(
                                        categoryTabId, profile, variant, role, ColorOption.Default,
                                    )
                                    resetRevision++
                                },
                                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                                    .padding(end = 44.dp, top = 4.dp)
                                    .size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rétablir la valeur par défaut de ${roleLabel(role)}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        }

                }
            }
        }
    }
}

/**
 * Category tabs belong to the Drawer in the public editor even though their pre-public internal IDs
 * still use the historical TABS section.
 */
private fun rolesForUi(section: ThemeColorRole.Section): List<ThemeColorRole> = when (section) {
    ThemeColorRole.Section.DRAWER -> ThemeColorRole.entries.filter {
        it.section == ThemeColorRole.Section.DRAWER || it.section == ThemeColorRole.Section.TABS
    }
    else -> ThemeColorRole.entries.filter { it.section == section }
}

private fun roleLabel(role: ThemeColorRole): String = when (role) {
    ThemeColorRole.GLOBAL_ACCENT -> "Couleur des éléments modifiables"
    ThemeColorRole.GLOBAL_SETTINGS_BACKGROUND -> "Interface — fond des menus"
    ThemeColorRole.GLOBAL_SETTINGS_CARD_BACKGROUND -> "Interface — fond des cartes"
    ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> "Pastille de notification — fond"
    ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT -> "Pastille de notification — texte / compteur"
    ThemeColorRole.HOME_ICON_TEXT -> "Texte des icônes"
    ThemeColorRole.HOME_FOLDER_ICON_TEXT -> "Dossier ouvert — texte des icônes"
    ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> "Dock — fond"
    ThemeColorRole.HOME_SEARCH_BACKGROUND -> "Recherche — fond"
    ThemeColorRole.HOME_SEARCH_TEXT -> "Recherche — texte"
    ThemeColorRole.HOME_SEARCH_HINT -> "Recherche — texte indicatif"
    ThemeColorRole.HOME_SEARCH_ICON -> "Recherche — icônes"
    ThemeColorRole.HOME_SEARCH_BORDER -> "Recherche — bordure"
    ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND -> "Dossier fermé — fond"
    ThemeColorRole.HOME_FOLDER_CLOSED_TEXT -> "Dossier fermé — texte"
    ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND -> "Dossier ouvert — fond"
    ThemeColorRole.HOME_FOLDER_OPEN_TEXT -> "Dossier ouvert — texte"
    ThemeColorRole.HOME_FOLDER_OPEN_HINT -> "Dossier ouvert — texte indicatif"
    ThemeColorRole.HOME_PAGE_INDICATOR_ACTIVE -> "Pages de l’accueil — indicateur actif"
    ThemeColorRole.HOME_PAGE_INDICATOR_INACTIVE -> "Pages de l’accueil — indicateurs inactifs"
    ThemeColorRole.HOME_FOLDER_PAGINATION -> "Dossiers — pagination"
    ThemeColorRole.HOME_FOLDER_CLOSED_BORDER -> "Dossier fermé — bordure"
    ThemeColorRole.HOME_FOLDER_OPEN_BORDER -> "Dossier ouvert — bordure"
    ThemeColorRole.HOME_FOLDER_BORDER -> "Dossiers — bordure"
    ThemeColorRole.HOME_POPUP_BACKGROUND -> "Menus contextuels — fond"
    ThemeColorRole.HOME_POPUP_TEXT -> "Menus contextuels — texte"
    ThemeColorRole.HOME_POPUP_ICON -> "Menus contextuels — icônes"
    ThemeColorRole.DOCK_ICON_TEXT -> "Texte des icônes"
    ThemeColorRole.DOCK_FOLDER_ICON_TEXT -> "Dossier ouvert — texte des icônes"
    ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND -> "Dossier fermé — fond"
    ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT -> "Dossier fermé — texte"
    ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND -> "Dossier ouvert — fond"
    ThemeColorRole.DOCK_FOLDER_OPEN_TEXT -> "Dossier ouvert — texte"
    ThemeColorRole.DOCK_FOLDER_OPEN_HINT -> "Dossier ouvert — texte indicatif"
    ThemeColorRole.DOCK_FOLDER_PAGINATION -> "Dossiers — pagination"
    ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER -> "Dossier fermé — bordure"
    ThemeColorRole.DOCK_FOLDER_OPEN_BORDER -> "Dossier ouvert — bordure"
    ThemeColorRole.DOCK_FOLDER_BORDER -> "Dossiers — bordure"
    ThemeColorRole.DRAWER_BACKGROUND -> "Général — fond"
    ThemeColorRole.DRAWER_TEXT -> "Général — texte"
    ThemeColorRole.DRAWER_ICON_TEXT -> "Texte des icônes"
    ThemeColorRole.DRAWER_FOLDER_ICON_TEXT -> "Dossier ouvert — texte des icônes"
    ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE -> "Recherche — fond inactif"
    ThemeColorRole.DRAWER_SEARCH_BACKGROUND_ACTIVE -> "Recherche — fond actif"
    ThemeColorRole.DRAWER_SEARCH_TEXT -> "Recherche — texte"
    ThemeColorRole.DRAWER_SEARCH_HINT -> "Recherche — texte indicatif"
    ThemeColorRole.DRAWER_SEARCH_ICON -> "Recherche — icônes"
    ThemeColorRole.DRAWER_SEARCH_BORDER -> "Recherche — bordure"
    ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND -> "Recherche — résultat sélectionné"
    ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND -> "Dossier fermé — fond"
    ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT -> "Dossier fermé — texte"
    ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND -> "Dossier ouvert — fond"
    ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT -> "Dossier ouvert — texte"
    ThemeColorRole.DRAWER_FOLDER_OPEN_HINT -> "Dossier ouvert — texte indicatif"
    ThemeColorRole.DRAWER_FOLDER_PAGINATION -> "Dossiers — pagination"
    ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER -> "Dossier fermé — bordure"
    ThemeColorRole.DRAWER_FOLDER_OPEN_BORDER -> "Dossier ouvert — bordure"
    ThemeColorRole.DRAWER_FOLDER_BORDER -> "Dossiers — bordure"
    ThemeColorRole.DRAWER_PROFILE_TAB_SELECTED_BACKGROUND -> "Personnel / Travail — sélectionné"
    ThemeColorRole.DRAWER_PROFILE_TAB_UNSELECTED_BACKGROUND -> "Personnel / Travail — non sélectionné"
    ThemeColorRole.DRAWER_PROFILE_TAB_TEXT -> "Personnel / Travail — texte"
    ThemeColorRole.DRAWER_POPUP_BACKGROUND -> "Menus contextuels — fond"
    ThemeColorRole.DRAWER_POPUP_TEXT -> "Menus contextuels — texte"
    ThemeColorRole.DRAWER_POPUP_ICON -> "Menus contextuels — icônes"
    ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND -> "Onglets de catégorie — actif"
    ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND -> "Onglets de catégorie — inactif"
    ThemeColorRole.TABS_CATEGORY_TEXT -> "Onglets de catégorie — texte (ancien réglage)"
    ThemeColorRole.TABS_CATEGORY_ACTIVE_TEXT -> "Onglets de catégorie — texte actif"
    ThemeColorRole.TABS_CATEGORY_INACTIVE_TEXT -> "Onglets de catégorie — texte inactif"
}
