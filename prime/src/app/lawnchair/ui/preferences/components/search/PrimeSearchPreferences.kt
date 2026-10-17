package app.lawnchair.ui.preferences.components.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.ThemeColors
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.ThemeColorSelection

/**
 * Prime Search shares the drawer's configured search algorithm and result providers.
 * Its visual colors remain independent of the drawer.
 */
@Composable
fun PrimeSearchPreference() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val variant = context.effectiveThemeVariant()
    PreferenceGroup(heading = "Recherche") {
        PreferenceTemplate(
            title = { Text("Moteur et catégories de résultats") },
            description = { Text("Prime Search utilise les mêmes sources et réglages que la recherche du tiroir.") },
        )
    }
    PreferenceGroup(heading = "Couleurs Prime Search") {
        ThemeColorRole.entries.filter { it.section == ThemeColorRole.Section.PRIME_SEARCH }.forEach { role ->
            PreferenceTemplate(
                title = { Text(role.id.substringAfterLast('.').replace('_', ' ')) },
                description = { Text("Personnaliser la couleur pour la variante ${variant.name.lowercase()}") },
                onClick = { navController.navigate(ThemeColorSelection(role.id, variant.name, null, null)) },
            )
        }
    }
}
