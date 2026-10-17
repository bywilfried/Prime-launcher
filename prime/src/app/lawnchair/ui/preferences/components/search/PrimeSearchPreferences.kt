package app.lawnchair.ui.preferences.components.search

import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.lawnchair.prime.search.PrimeSearchResultPreferences
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
    PreferenceGroup(heading = "Affichage des applications") {
        var appsPerRow by remember { mutableStateOf(PrimeSearchResultPreferences.appsPerRow(context)) }
        PreferenceTemplate(
            title = { Text("Applications par ligne") },
            description = { Text("$appsPerRow applications par ligne (de 3 à 6)") },
        )
        androidx.compose.material3.Slider(
            value = appsPerRow.toFloat(),
            onValueChange = { value ->
                appsPerRow = value.toInt().coerceIn(3, 6)
                PrimeSearchResultPreferences.setAppsPerRow(context, appsPerRow)
            },
            valueRange = 3f..6f,
            steps = 2,
        )
    }
    var categories by remember {
        mutableStateOf(PrimeSearchResultPreferences.order(context))
    }
    fun moveCategory(from: Int, to: Int) {
        if (from !in categories.indices || to !in categories.indices || from == to) return
        val reordered = categories.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        categories = reordered
        PrimeSearchResultPreferences.saveOrder(context, reordered)
    }
    PreferenceGroup(heading = "Afficher dans les résultats de recherche") {
        categories.forEachIndexed { index, category ->
            var checked by remember(category) {
                mutableStateOf(PrimeSearchResultPreferences.enabled(context, category))
            }
            var dragDistance by remember(category) { mutableStateOf(0f) }
            PreferenceTemplate(
                title = { Text(category.title) },
                description = { Text(category.description) },
                endWidget = {
                    Row {
                        IconButton(
                            onClick = { moveCategory(index, index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                        }
                        IconButton(
                            onClick = { moveCategory(index, index + 1) },
                            enabled = index < categories.lastIndex,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                        }
                        androidx.compose.material3.Switch(
                            checked = checked,
                            onCheckedChange = { enabled ->
                                checked = enabled
                                PrimeSearchResultPreferences.setEnabled(context, category, enabled)
                            },
                        )
                    }
                },
                onClick = {
                    checked = !checked
                    PrimeSearchResultPreferences.setEnabled(context, category, checked)
                },
            )
        }
        TextButton(onClick = {
            PrimeSearchResultPreferences.resetOrder(context)
            categories = PrimeSearchResultPreferences.order(context)
        }) { Text("Réinitialiser l'ordre") }
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
