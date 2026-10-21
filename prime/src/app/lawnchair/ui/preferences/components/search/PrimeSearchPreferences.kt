package app.lawnchair.ui.preferences.components.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lawnchair.preferences.getAdapter
import app.lawnchair.prime.search.PrimeSearchPermissions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    var showSources by remember { mutableStateOf(false) }
    var showSuggestionSettings by remember { mutableStateOf(false) }
    PreferenceGroup(heading = "Suggestions et moteurs Web") {
        if (showSuggestionSettings) {
            var provider by remember { mutableStateOf(PrimeSearchResultPreferences.suggestionProvider(context)) }
            var suggestionCount by remember { mutableStateOf(PrimeSearchResultPreferences.webSuggestionCount(context)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSuggestionSettings = false },
                title = { Text("Suggestions Web") },
                text = {
                    Column {
                        Text("Nombre de suggestions : $suggestionCount")
                        androidx.compose.material3.Slider(
                            value = suggestionCount.toFloat(),
                            onValueChange = {
                                suggestionCount = it.roundToInt().coerceIn(0, 8)
                                PrimeSearchResultPreferences.setWebSuggestionCount(context, suggestionCount)
                            },
                            valueRange = 0f..8f,
                            steps = 7,
                        )
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
    PreferenceGroup(heading = "Applications à l'ouverture") {
        var openingTab by remember { mutableStateOf(PrimeSearchResultPreferences.openingTab(context)) }
        var showOpeningChoices by remember { mutableStateOf(false) }
        PreferenceTemplate(
            title = { Text("Onglet affiché à l'ouverture") },
            description = { Text(openingTab.label) },
            onClick = { showOpeningChoices = true },
        )
        if (showOpeningChoices) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showOpeningChoices = false },
                title = { Text("Ouvrir Prime Search sur") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        PrimeSearchResultPreferences.OpeningTab.entries.forEach { option ->
                            androidx.compose.foundation.layout.Row {
                                androidx.compose.material3.RadioButton(
                                    selected = openingTab == option,
                                    onClick = {
                                        openingTab = option
                                        PrimeSearchResultPreferences.setOpeningTab(context, option)
                                        showOpeningChoices = false
                                    },
                                )
                                androidx.compose.material3.TextButton(onClick = {
                                    openingTab = option
                                    PrimeSearchResultPreferences.setOpeningTab(context, option)
                                    showOpeningChoices = false
                                }) { Text(option.label) }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showOpeningChoices = false }) { Text("Fermer") }
                },
            )
        }
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
    PreferenceGroup(heading = "Apparence de Prime Search") {
        var rows by remember { mutableStateOf(PrimeSearchResultPreferences.zeroStateRows(context)) }
        var columns by remember { mutableStateOf(PrimeSearchResultPreferences.appsPerRow(context)) }
        var iconPercent by remember { mutableStateOf(PrimeSearchResultPreferences.iconSizePercent(context)) }
        var twoLines by remember { mutableStateOf(PrimeSearchResultPreferences.twoLineAppLabels(context)) }
        var previewTab by remember { mutableStateOf(0) }
        // Use installed, launchable applications rather than symbolic placeholder
        // glyphs. Render them with the same Prime mask and icon factory as the
        // real zero-state rows so icon packs, backgrounds and diamond sizing match.
        val shape = app.lawnchair.preferences2.PreferenceManager2.getInstance(context).iconShape.firstCached()
        val previewApps = remember(context) {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                .take(24)
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .toList()
        }
        val previewIcons = remember(previewApps, shape.key) {
            val pm = context.packageManager
            com.android.launcher3.icons.LauncherIcons.obtain(context).use { factory ->
                previewApps.mapNotNull { (pkg, label) ->
                    runCatching {
                        val icon = factory.createPrimeIconBitmap(
                            pm.getApplicationIcon(pkg),
                            android.os.Process.myUserHandle(),
                            shape,
                        ).icon
                        label to icon.asImageBitmap()
                    }.getOrNull()
                }
            }
        }
        val previewAccent = androidx.compose.material3.MaterialTheme.colorScheme.primary

        androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Text("  Prime Search")
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(5.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Fréquentes", "Récentes", "Nouvelles").forEachIndexed { index, name ->
                        androidx.compose.material3.TextButton(onClick = { previewTab = index }) {
                            Text(
                                name,
                                color = if (previewTab == index) previewAccent
                                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                // PrimeSearchOverlay uses 48dp * percentage, with no 64dp cap.
                val iconDp = (48f * iconPercent / 100f).dp
                val offset = previewTab * 2
                repeat(rows) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        repeat(columns) { colIndex ->
                            val index = rowIndex * columns + colIndex + offset
                            val sample = previewIcons.getOrNull(index % previewIcons.size.coerceAtLeast(1))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(iconDp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    if (sample != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = sample.second,
                                            contentDescription = null,
                                            modifier = Modifier.size(iconDp),
                                        )
                                    }
                                }
                                Text(
                                    sample?.first.orEmpty(),
                                    maxLines = if (twoLines) 2 else 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        Text("Applications par ligne : $columns")
        androidx.compose.material3.Slider(
            value = columns.toFloat(),
            onValueChange = {
                columns = it.roundToInt().coerceIn(3, 6)
                PrimeSearchResultPreferences.setAppsPerRow(context, columns)
            },
            valueRange = 3f..6f,
            steps = 2,
        )
        Text("Nombre de lignes : $rows")
        androidx.compose.material3.Slider(
            value = rows.toFloat(),
            onValueChange = {
                rows = it.roundToInt().coerceIn(1, 2)
                PrimeSearchResultPreferences.setZeroStateRows(context, rows)
            },
            valueRange = 1f..2f,
        )
        Text("Taille des icônes : $iconPercent %")
        androidx.compose.material3.Slider(
            value = iconPercent.toFloat(),
            onValueChange = {
                iconPercent = (it.roundToInt() / 5 * 5).coerceIn(60, 150)
                PrimeSearchResultPreferences.setIconSizePercent(context, iconPercent)
            },
            valueRange = 60f..150f,
        )
        PreferenceTemplate(
            title = { Text("Noms des applications sur deux lignes") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = twoLines,
                    onCheckedChange = {
                        twoLines = it
                        PrimeSearchResultPreferences.setTwoLineAppLabels(context, it)
                    },
                )
            },
            onClick = {
                twoLines = !twoLines
                PrimeSearchResultPreferences.setTwoLineAppLabels(context, twoLines)
            },
        )
        androidx.compose.material3.TextButton(onClick = {
            columns = 4
            rows = 1
            iconPercent = 100
            twoLines = false
            PrimeSearchResultPreferences.setAppsPerRow(context, columns)
            PrimeSearchResultPreferences.setZeroStateRows(context, rows)
            PrimeSearchResultPreferences.setIconSizePercent(context, iconPercent)
            PrimeSearchResultPreferences.setTwoLineAppLabels(context, twoLines)
        }) {
            Text("Réinitialiser l'apparence")
        }
    }
    var permissionRefresh by remember { mutableStateOf(0) }
    var pendingCategory by remember { mutableStateOf<Pair<PrimeSearchResultPreferences.Category, PrimeSearchResultPreferences.Surface>?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh++
                pendingCategory?.let { (category, surface) ->
                    if (PrimeSearchPermissions.granted(context, category)) {
                        PrimeSearchResultPreferences.setEnabled(context, category, true, surface)
                        pendingCategory = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionRefresh++
        pendingCategory?.let { (category, surface) ->
            if (PrimeSearchPermissions.granted(context, category)) {
                PrimeSearchResultPreferences.setEnabled(context, category, true, surface)
                pendingCategory = null
            }
        }
    }
    fun requestCategory(category: PrimeSearchResultPreferences.Category, surface: PrimeSearchResultPreferences.Surface = PrimeSearchResultPreferences.Surface.PRIME_SEARCH) {
        pendingCategory = category to surface
        val special = PrimeSearchPermissions.specialAccessIntent(category)
        if (special != null) {
            runCatching { context.startActivity(special) }
        } else {
            val missing = PrimeSearchPermissions.runtimePermissions(category).filter {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, it
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        }
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
    val sharedDrag = remember { CategoryDragState() }
    PreferenceGroup(heading = "Afficher dans les résultats de recherche") {
        categories.forEachIndexed { index, category ->
            var checked by remember(category, permissionRefresh) {
                mutableStateOf(PrimeSearchResultPreferences.enabled(context, category))
            }
            val needsPermission = PrimeSearchPermissions.required(category) &&
                !PrimeSearchPermissions.granted(context, category)
            SharedCategoryCard(
                category = category,
                index = index,
                lastIndex = categories.lastIndex,
                enabled = checked,
                needsPermission = needsPermission,
                onSettings = {
                    when (category) {
                        PrimeSearchResultPreferences.Category.WEB -> showSuggestionSettings = true
                        PrimeSearchResultPreferences.Category.WEB_SHORTCUTS -> showSources = true
                        else -> categorySettings = category
                    }
                },
                onMove = ::moveCategory,
                onEnabled = {
                    checked = it
                    PrimeSearchResultPreferences.setEnabled(context, category, it)
                },
                onPermission = { requestCategory(category) },
                dragState = sharedDrag,
            )
        }
        if (categorySettings != null) {
            val selected = categorySettings!!
            SharedCategorySettingsDialog(
                category = selected,
                surface = PrimeSearchResultPreferences.Surface.PRIME_SEARCH,
                permissionRefresh = permissionRefresh,
                onDismiss = { categorySettings = null },
            ) {
                        if (selected == PrimeSearchResultPreferences.Category.NOTIFICATIONS) {
                            val granted = PrimeSearchPermissions.granted(context, selected)
                            Text(if (granted) "Accès aux notifications autorisé" else "Accès aux notifications non autorisé")
                            TextButton(onClick = {
                                requestCategory(selected)
                            }) { Text(if (granted) "Gérer l'autorisation Android" else "Autoriser l'accès aux notifications") }
                            TextButton(onClick = { confirmNotificationClear = true }) {
                                Text("Effacer l'historique des notifications")
                            }
                            Text("L'enregistrement nécessite l'accès aux notifications Android.")
                        } else if (selected == PrimeSearchResultPreferences.Category.CALENDAR ||
                            selected == PrimeSearchResultPreferences.Category.CONTACTS ||
                            selected == PrimeSearchResultPreferences.Category.REMINDERS) {
                            if (!PrimeSearchPermissions.granted(context, selected)) {
                                Text(PrimeSearchPermissions.explanation(selected))
                                TextButton(onClick = { requestCategory(selected) }) { Text("Autoriser l'accès") }
                            }
                        } else {
                            if (PrimeSearchPermissions.required(selected)) {
                                val granted = PrimeSearchPermissions.granted(context, selected)
                                Text(if (granted) "Autorisation accordée" else PrimeSearchPermissions.explanation(selected))
                                if (!granted) TextButton(onClick = { requestCategory(selected) }) { Text("Autoriser l’accès") }
                            }
                            Text("Les résultats dépendent des fournisseurs disponibles et des autorisations Android.")
                        }

            }
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
