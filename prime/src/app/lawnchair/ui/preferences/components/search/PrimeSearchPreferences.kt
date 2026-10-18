package app.lawnchair.ui.preferences.components.search

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
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
            description = { Text("Les moteurs et suggestions Web de Prime Search sont configurés ci-dessous, indépendamment du tiroir.") },
        )
    }
    var showSources by remember { mutableStateOf(false) }
    var showSuggestionSettings by remember { mutableStateOf(false) }
    PreferenceGroup(heading = "Suggestions et moteurs Web") {
        var suggestionCount by remember { mutableStateOf(PrimeSearchResultPreferences.webSuggestionCount(context)) }
        PreferenceTemplate(
            title = { Text("Suggestions Web") },
            description = { Text("$suggestionCount suggestion(s) pendant la saisie (0 pour désactiver)") },
        )
        androidx.compose.material3.Slider(
            value = suggestionCount.toFloat(),
            onValueChange = {
                suggestionCount = it.roundToInt().coerceIn(0, 8)
                PrimeSearchResultPreferences.setWebSuggestionCount(context, suggestionCount)
            },
            valueRange = 0f..8f,
            steps = 7,
        )
        if (showSuggestionSettings) {
            var provider by remember { mutableStateOf(PrimeSearchResultPreferences.suggestionProvider(context)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSuggestionSettings = false },
                title = { Text("Suggestions Web") },
                text = {
                    Column {
                        Text("Fournisseur de suggestions")
                        PrimeSearchResultPreferences.SuggestionProvider.entries.forEach { option ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = provider == option,
                                    onClick = {
                                        provider = option
                                        PrimeSearchResultPreferences.setSuggestionProvider(context, option)
                                    },
                                )
                                Text(option.title)
                            }
                        }
                        Text("Les termes saisis sont transmis au fournisseur sélectionné.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSuggestionSettings = false }) { Text("Terminé") }
                },
            )
        }
        if (showSources) {
            var engines by remember { mutableStateOf(PrimeSearchResultPreferences.webEngineOrder(context)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSources = false },
                title = { Text("Sources de recherche Web") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(engines.size) { index ->
                            val engine = engines[index]
                            var enabled by remember(engine) {
                                mutableStateOf(engine in PrimeSearchResultPreferences.enabledWebEngines(context))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Switch(
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        PrimeSearchResultPreferences.setWebEngineEnabled(context, engine, it)
                                    },
                                )
                                Text(engine.title, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                IconButton(onClick = {
                                    if (index > 0) {
                                        val reordered = engines.toMutableList()
                                        java.util.Collections.swap(reordered, index, index - 1)
                                        engines = reordered
                                        PrimeSearchResultPreferences.saveWebEngineOrder(context, reordered)
                                    }
                                }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                                }
                                IconButton(onClick = {
                                    if (index < engines.lastIndex) {
                                        val reordered = engines.toMutableList()
                                        java.util.Collections.swap(reordered, index, index + 1)
                                        engines = reordered
                                        PrimeSearchResultPreferences.saveWebEngineOrder(context, reordered)
                                    }
                                }, enabled = index < engines.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSources = false }) {
                        Text("Terminé")
                    }
                },
            )
        }
    }
    PreferenceGroup(heading = "Fenêtre Prime Search") {
        var floating by remember { mutableStateOf(PrimeSearchResultPreferences.floatingWindow(context)) }
        PreferenceTemplate(
            title = { Text("Fenêtre flottante (overlay)") },
            description = { Text("Afficher Prime Search au-dessus de l'accueil avec des marges visibles. Désactiver pour le plein écran.") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = floating,
                    onCheckedChange = {
                        floating = it
                        PrimeSearchResultPreferences.setFloatingWindow(context, it)
                    },
                )
            },
            onClick = {
                floating = !floating
                PrimeSearchResultPreferences.setFloatingWindow(context, floating)
            },
        )
    }
    PreferenceGroup(heading = "Accès aux données d’utilisation") {
        val usageGranted = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
        PreferenceTemplate(
            title = { Text("Applications fréquentes et récentes") },
            description = {
                Text(if (usageGranted) "Autorisation accordée"
                    else "Autorisation nécessaire pour afficher les applications fréquentes et récentes. Appuyer pour ouvrir les paramètres Android.")
            },
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
    }
    PreferenceGroup(heading = "Applications à l'ouverture") {
        var rows by remember { mutableStateOf(PrimeSearchResultPreferences.zeroStateRows(context)) }
        PreferenceTemplate(
            title = { Text("Lignes d'applications") },
            description = { Text("$rows ligne(s) pour Fréquentes, Récentes et Nouvelles") },
        )
        androidx.compose.material3.Slider(
            value = rows.toFloat(),
            onValueChange = {
                rows = it.roundToInt().coerceIn(1, 2)
                PrimeSearchResultPreferences.setZeroStateRows(context, rows)
            },
            valueRange = 1f..2f,
            steps = 0,
        )
    }
    PreferenceGroup(heading = "Recherche d'applications") {
        var fuzzyEnabled by remember { mutableStateOf(PrimeSearchResultPreferences.fuzzyApps(context)) }
        PreferenceTemplate(
            title = { Text("Tolérer les fautes de frappe") },
            description = { Text("Recherche approximative des noms d'applications, indépendante du tiroir") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = fuzzyEnabled,
                    onCheckedChange = {
                        fuzzyEnabled = it
                        PrimeSearchResultPreferences.setFuzzyApps(context, it)
                    },
                )
            },
            onClick = {
                fuzzyEnabled = !fuzzyEnabled
                PrimeSearchResultPreferences.setFuzzyApps(context, fuzzyEnabled)
            },
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
    var categorySettings by remember { mutableStateOf<PrimeSearchResultPreferences.Category?>(null) }
    var confirmNotificationClear by remember { mutableStateOf(false) }
    var activeDrag by remember { mutableStateOf<PrimeSearchResultPreferences.Category?>(null) }
    var dragOrigin by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(0f) }
    PreferenceGroup(heading = "Afficher dans les résultats de recherche") {
        categories.forEachIndexed { index, category ->
            var checked by remember(category) {
                mutableStateOf(PrimeSearchResultPreferences.enabled(context, category))
            }
            val dragging = activeDrag == category
            val rowPixels = with(LocalDensity.current) { 112.dp.toPx() }
            val targetIndex = (dragOrigin + (dragOffset / rowPixels).roundToInt()).coerceIn(0, categories.lastIndex)
            val displacement = if (activeDrag != null && !dragging) {
                when {
                    index > dragOrigin && index <= targetIndex -> -rowPixels
                    index < dragOrigin && index >= targetIndex -> rowPixels
                    else -> 0f
                }
            } else 0f
            val animatedDisplacement by animateFloatAsState(displacement, label = "primeCategoryShift")
            val animatedLift by animateFloatAsState(if (dragging) 1.06f else 1f, label = "primeDragLift")
            PreferenceTemplate(
                modifier = Modifier
                    .zIndex(if (dragging) 10f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else animatedDisplacement
                        scaleX = animatedLift
                        scaleY = animatedLift
                        shadowElevation = if (dragging) 12.dp.toPx() else 0f
                    },
                title = { Text(category.title) },
                description = { Text(category.description) },
                endWidget = {
                    Row {
                        IconButton(onClick = {
                            when (category) {
                                PrimeSearchResultPreferences.Category.WEB -> showSuggestionSettings = true
                                PrimeSearchResultPreferences.Category.WEB_SHORTCUTS -> showSources = true
                                else -> categorySettings = category
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Paramètres de ${category.title}")
                        }
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Maintenir puis glisser pour réorganiser",
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                .pointerInput(category, index) {
                                    var distance = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { distance = 0f; dragOrigin = index; dragOffset = 0f; activeDrag = category },
                                        onDragEnd = {
                                            val destination = (dragOrigin + (dragOffset / rowPixels).roundToInt()).coerceIn(0, categories.lastIndex)
                                            activeDrag = null
                                            dragOffset = 0f
                                            moveCategory(index, destination)
                                        },
                                        onDragCancel = { distance = 0f; dragOffset = 0f; activeDrag = null },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            distance += dragAmount.y
                                            dragOffset = distance
                                        },
                                    )
                                },
                        )
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
        if (categorySettings != null) {
            val selected = categorySettings!!
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { categorySettings = null },
                title = { Text(selected.title) },
                text = {
                    Column {
                        Text(selected.description)
                        if (selected == PrimeSearchResultPreferences.Category.NOTIFICATIONS) {
                            var retention by remember {
                                mutableStateOf(PrimeSearchResultPreferences.notificationRetention(context))
                            }
                            Text("Conserver les notifications pendant :")
                            PrimeSearchResultPreferences.NotificationRetention.entries.forEach { option ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(
                                        selected = retention == option,
                                        onClick = {
                                            retention = option
                                            PrimeSearchResultPreferences.setNotificationRetention(context, option)
                                        },
                                    )
                                    Text(option.label)
                                }
                            }
                            TextButton(onClick = { confirmNotificationClear = true }) {
                                Text("Effacer l'historique des notifications")
                            }
                            Text("L'enregistrement nécessite l'accès aux notifications Android.")
                        } else {
                            Text("Les réglages détaillés de cette catégorie seront ajoutés ici. Les résultats dépendent des fournisseurs disponibles et des autorisations Android.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { categorySettings = null }) { Text("Fermer") }
                },
            )
        }
        if (confirmNotificationClear) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmNotificationClear = false },
                title = { Text("Effacer l'historique ?") },
                text = { Text("Cette action supprimera les notifications enregistrées dans Prime Launcher.") },
                confirmButton = {
                    TextButton(onClick = {
                        PrimeSearchResultPreferences.clearNotificationHistory(context)
                        confirmNotificationClear = false
                    }) { Text("Effacer") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmNotificationClear = false }) { Text("Annuler") }
                },
            )
        }
        TextButton(onClick = {
            PrimeSearchResultPreferences.resetOrder(context)
            categories = PrimeSearchResultPreferences.order(context)
        }) { Text("Réinitialiser l'ordre") }
    }
}
