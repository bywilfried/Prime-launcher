package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Row
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
import app.lawnchair.theme.ThemeColors
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.navigation.ThemeColorSelection
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.ThemeColorOverrides
import app.lawnchair.theme.ThemeVariant
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
fun ThemeCustomizationPreferences(modifier: Modifier = Modifier) {
    var variant by rememberSaveable { mutableStateOf(ThemeVariant.LIGHT) }
    var expanded by rememberSaveable { mutableStateOf<ThemeColorRole.Section?>(null) }
    var confirmThemeReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val profile = ThemeProfile.current(context)
    val overrides = remember(context) { ThemeColorOverrides(context) }

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = "Personnaliser les couleurs du thème",
        modifier = modifier,
    ) {
        PreferenceGroup(heading = "Legacy") {
            PreferenceTemplate(
                title = { Text("Variante") },
                endWidget = {
                    Row {
                        val light = variant == ThemeVariant.LIGHT
                        if (light) Button(onClick = { variant = ThemeVariant.LIGHT }) { Text("Clair") }
                        else OutlinedButton(onClick = { variant = ThemeVariant.LIGHT }) { Text("Clair") }
                        val dark = variant == ThemeVariant.DARK
                        if (dark) Button(onClick = { variant = ThemeVariant.DARK }) { Text("Sombre") }
                        else OutlinedButton(onClick = { variant = ThemeVariant.DARK }) { Text("Sombre") }
                    }
                },
            )
        }

        ThemeSection(
            title = "Global",
            variant = variant,
            section = ThemeColorRole.Section.GLOBAL,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )
        ThemeSection(
            title = "Accueil",
            variant = variant,
            section = ThemeColorRole.Section.HOME,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )
        ThemeSection(
            title = "Tiroir",
            variant = variant,
            section = ThemeColorRole.Section.DRAWER,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )

        PreferenceGroup(heading = "Gestion du thème") {
            PreferenceTemplate(
                title = { Text("Réinitialiser la variante") },
                description = { Text("Supprimera les personnalisations de la variante affichée.") },
                onClick = { overrides.resetVariant(profile, variant) },
            )
            PreferenceTemplate(
                title = { Text("Réinitialiser le thème") },
                description = { Text("Restaurera les valeurs officielles du thème sélectionné.") },
                onClick = { confirmThemeReset = true },
            )
            PreferenceTemplate(
                title = { Text("Exporter le thème") },
                description = { Text("Sera activé après stabilisation des identifiants et du format d’export.") },
                enabled = false,
            )
            PreferenceTemplate(
                title = { Text("Importer un thème") },
                description = { Text("Sera activé avec le format d’import/export versionné.") },
                enabled = false,
            )
        }
    }

    if (confirmThemeReset) {
        AlertDialog(
            onDismissRequest = { confirmThemeReset = false },
            title = { Text("Réinitialiser le thème ?") },
            text = { Text("Toutes les personnalisations des variantes Clair et Sombre seront supprimées.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        overrides.resetTheme(profile)
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
    onToggle: (ThemeColorRole.Section) -> Unit,
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
                rolesForUi(section).forEach { role ->
                    if (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT ||
                        role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT ||
                        role == ThemeColorRole.GLOBAL_ACCENT ||
                        role == ThemeColorRole.GLOBAL_SETTINGS_BACKGROUND ||
                        role == ThemeColorRole.GLOBAL_SETTINGS_CARD_BACKGROUND ||
                        role == ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND ||
                        role == ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND ||
                        role == ThemeColorRole.HOME_POPUP_BACKGROUND ||
                        role == ThemeColorRole.HOME_POPUP_TEXT ||
                        role == ThemeColorRole.HOME_POPUP_ICON ||
                        role == ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND ||
                        role == ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND ||
                        role == ThemeColorRole.DRAWER_POPUP_BACKGROUND ||
                        role == ThemeColorRole.DRAWER_POPUP_TEXT ||
                        role == ThemeColorRole.DRAWER_POPUP_ICON ||
                        role == ThemeColorRole.DRAWER_BACKGROUND ||
                        role == ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE ||
                        role == ThemeColorRole.DRAWER_SEARCH_BACKGROUND_ACTIVE ||
                        role == ThemeColorRole.DRAWER_SEARCH_TEXT ||
                        role == ThemeColorRole.DRAWER_SEARCH_HINT ||
                        role == ThemeColorRole.DRAWER_SEARCH_ICON ||
                        role == ThemeColorRole.DRAWER_SEARCH_BORDER ||
                        role == ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND ||
                        role == ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND ||
                        role == ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND ||
                        role == ThemeColorRole.TABS_CATEGORY_TEXT
                    ) {
                        val profile = ThemeProfile.current(context)
                        val override = ThemeColorOverrides(context).get(profile, variant, role)
                        val prefs2 = preferenceManager2()
                        val preview = when (role) {
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> {
                                val legacy = prefs2.notificationDotColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT -> {
                                val legacy = prefs2.notificationDotTextColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            else -> ThemeColors.resolve(context, profile, role, variant)
                        }
                        ColorPreference(
                            label = roleLabel(role),
                            selectedColor = override,
                            previewColor = ColorOption.CustomColor(preview),
                            description = if (override == ColorOption.Default) {
                                "Valeur du thème"
                            } else {
                                "Personnalisée"
                            },
                            onClick = {
                                navController.navigate(
                                    ThemeColorSelection(role.id, variant.name),
                                )
                            },
                        )
                    } else {
                        PreferenceTemplate(
                            title = { Text(roleLabel(role)) },
                            description = { Text("Valeur du thème · raccordement à valider") },
                        )
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
    ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> "Hotseat — fond"
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
    ThemeColorRole.HOME_FOLDER_PAGINATION -> "Dossiers — pagination"
    ThemeColorRole.HOME_FOLDER_BORDER -> "Dossiers — bordure"
    ThemeColorRole.HOME_POPUP_BACKGROUND -> "Menus contextuels — fond"
    ThemeColorRole.HOME_POPUP_TEXT -> "Menus contextuels — texte"
    ThemeColorRole.HOME_POPUP_ICON -> "Menus contextuels — icônes"
    ThemeColorRole.DRAWER_BACKGROUND -> "Général — fond"
    ThemeColorRole.DRAWER_TEXT -> "Général — texte"
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
    ThemeColorRole.DRAWER_FOLDER_BORDER -> "Dossiers — bordure"
    ThemeColorRole.DRAWER_PROFILE_TAB_SELECTED_BACKGROUND -> "Personnel / Travail — sélectionné"
    ThemeColorRole.DRAWER_PROFILE_TAB_UNSELECTED_BACKGROUND -> "Personnel / Travail — non sélectionné"
    ThemeColorRole.DRAWER_PROFILE_TAB_TEXT -> "Personnel / Travail — texte"
    ThemeColorRole.DRAWER_POPUP_BACKGROUND -> "Menus contextuels — fond"
    ThemeColorRole.DRAWER_POPUP_TEXT -> "Menus contextuels — texte"
    ThemeColorRole.DRAWER_POPUP_ICON -> "Menus contextuels — icônes"
    ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND -> "Onglets de catégorie — actif"
    ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND -> "Onglets de catégorie — inactif"
    ThemeColorRole.TABS_CATEGORY_TEXT -> "Onglets de catégorie — texte"
}
