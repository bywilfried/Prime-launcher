package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.lawnchair.theme.ThemeColorRole
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
    var variant by remember { mutableStateOf(ThemeVariant.LIGHT) }
    var expanded by remember { mutableStateOf<ThemeColorRole.Section?>(null) }

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
            section = ThemeColorRole.Section.GLOBAL,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )
        ThemeSection(
            title = "Accueil",
            section = ThemeColorRole.Section.HOME,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )
        ThemeSection(
            title = "Tiroir",
            section = ThemeColorRole.Section.DRAWER,
            expanded = expanded,
            onToggle = { expanded = if (expanded == it) null else it },
        )

        PreferenceGroup(heading = "Gestion du thème") {
            PreferenceTemplate(
                title = { Text("Réinitialiser la variante") },
                description = { Text("Supprimera les personnalisations de la variante affichée.") },
                enabled = false,
            )
            PreferenceTemplate(
                title = { Text("Réinitialiser le thème") },
                description = { Text("Restaurera les valeurs officielles du thème sélectionné.") },
                enabled = false,
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
}

@Composable
private fun ThemeSection(
    title: String,
    section: ThemeColorRole.Section,
    expanded: ThemeColorRole.Section?,
    onToggle: (ThemeColorRole.Section) -> Unit,
) {
    PreferenceGroup {
        PreferenceTemplate(
            title = { Text(title) },
            description = { Text(if (expanded == section) "Masquer les couleurs" else "Afficher les couleurs") },
            onClick = { onToggle(section) },
        )
        ExpandAndShrink(visible = expanded == section) {
            androidx.compose.foundation.layout.Column {
                rolesForUi(section).forEach { role ->
                    PreferenceTemplate(
                        title = { Text(roleLabel(role)) },
                        description = { Text("Valeur du thème · raccordement à valider") },
                    )
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
    ThemeColorRole.GLOBAL_ACCENT -> "Accent sémantique (interne)"
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
    ThemeColorRole.DRAWER_BACKGROUND -> "Général — fond"
    ThemeColorRole.DRAWER_TEXT -> "Général — texte"
    ThemeColorRole.DRAWER_SEARCH_BACKGROUND -> "Recherche — fond"
    ThemeColorRole.DRAWER_SEARCH_TEXT -> "Recherche — texte"
    ThemeColorRole.DRAWER_SEARCH_HINT -> "Recherche — texte indicatif"
    ThemeColorRole.DRAWER_SEARCH_ICON -> "Recherche — icônes"
    ThemeColorRole.DRAWER_SEARCH_BORDER -> "Recherche — bordure"
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
    ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND -> "Onglets de catégorie — actif"
    ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND -> "Onglets de catégorie — inactif"
    ThemeColorRole.TABS_CATEGORY_TEXT -> "Onglets de catégorie — texte"
}
