package app.lawnchair.ui.preferences.components.search

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.prime.search.PrimeSearchPermissions
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TwoTargetSwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.SearchProviderPreference
import app.lawnchair.util.FileAccessManager
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun DrawerSearchPreference(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    MainSwitchPreference(
        adapter = showDrawerSearchBar,
        label = stringResource(id = R.string.show_app_search_bar),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(R.string.general_label)) {
            if (hiddenApps.isNotEmpty()) {
                HiddenAppsInSearchPreference()
            }
            SwitchPreference(
                adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                label = stringResource(id = R.string.pref_search_auto_show_keyboard),
            )
            SearchProvider(
                context = context,
            )
            SwitchPreference(
                label = stringResource(R.string.allapps_match_qsb_style_label),
                description = stringResource(R.string.allapps_match_qsb_style_description),
                adapter = prefs2.matchHotseatQsbStyle.getAdapter(),
            )
        }

        DrawerAdvancedSearchPreferences()


    }
}

@Composable
private fun ASISearchSettings(prefs: PreferenceManager) {
    SwitchPreference(
        adapter = prefs.searchResultShortcuts.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_shortcuts_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPeople.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_people_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPixelTips.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_tips_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultSettings.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
    )
}

@Composable
private fun SearchProvider(
    context: Context,
) {
    val searchAlgorithmEntries = remember {
        sequenceOf(
            ListPreferenceEntry(LawnchairSearchAlgorithm.APP_SEARCH) { stringResource(R.string.search_algorithm_app_search) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.LOCAL_SEARCH) { stringResource(R.string.search_algorithm_global_search_on_device) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.ASI_SEARCH) { stringResource(R.string.search_algorithm_global_search_via_asi) },
        ).filter {
            when (it.value) {
                LawnchairSearchAlgorithm.ASI_SEARCH -> LawnchairSearchAlgorithm.isASISearchEnabled(
                    context,
                )

                else -> true
            }
        }.toList()
    }

    ListPreference(
        adapter = preferenceManager2().searchAlgorithm.getAdapter(),
        entries = searchAlgorithmEntries,
        label = stringResource(R.string.app_search_algorithm),
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocalSearchSettings(
    prefs: PreferenceManager,
    prefs2: PreferenceManager2,
    context: Context,
) {
    val navController = LocalNavController.current
    val webSuggestionProvider =
        stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)

    TwoTargetSwitchPreference(
        adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_web_title),
        description = if (webSuggestionProvider == stringResource(CustomWebSearchProvider.label)) {
            webSuggestionProvider
        } else {
            stringResource(
                id = R.string.search_pref_result_web_provider_description,
                webSuggestionProvider,
            )
        },
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.WEB))
        },
    )
    val peopleAdapter = prefs.searchResultPeople.getAdapter()
    val peopleEnabled = rememberPermissionState(android.Manifest.permission.READ_CONTACTS).status.isGranted
    TwoTargetSwitchPreference(
        checked = peopleEnabled && peopleAdapter.state.value,
        onCheckedChange = peopleAdapter::onChange,
        switchEnabled = peopleEnabled,
        label = stringResource(id = R.string.search_pref_result_people_title),
        description = stringResource(id = R.string.search_pref_result_contacts_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.CONTACTS))
        },
    )
    val filesAdapter = prefs.searchResultFilesToggle.getAdapter()
    val filesEnabled = remember { FileAccessManager.getInstance(context) }.hasAnyPermission.collectAsStateWithLifecycle().value
    TwoTargetSwitchPreference(
        checked = filesEnabled && filesAdapter.state.value,
        onCheckedChange = filesAdapter::onChange,
        switchEnabled = filesEnabled,
        label = stringResource(R.string.search_pref_result_files_title),
        description = stringResource(R.string.search_pref_result_files_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.FILES))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResultSettingsEntry.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.SETTINGS))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResulRecentSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_history_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.HISTORY))
        },
    )
    SwitchPreference(
        adapter = prefs.searchResultCalculator.getAdapter(),
        label = stringResource(R.string.all_apps_search_result_calculator),
    )
}

@Composable
private fun DrawerAdvancedSearchPreferences() {
    val context = LocalContext.current
    var permissionRefresh by remember { mutableStateOf(0) }
    var pendingCategory by remember { mutableStateOf<PrimeSearchResultPreferences.Category?>(null) }
    var categoryStates by remember { mutableStateOf(PrimeSearchResultPreferences.order(context, PrimeSearchResultPreferences.Surface.DRAWER).associateWith { PrimeSearchResultPreferences.enabled(context, it, PrimeSearchResultPreferences.Surface.DRAWER) }) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh++
                pendingCategory?.let { category ->
                    if (PrimeSearchPermissions.granted(context, category)) {
                        PrimeSearchResultPreferences.setEnabled(context, category, true, PrimeSearchResultPreferences.Surface.DRAWER)
                        categoryStates = categoryStates + (category to true)
                        pendingCategory = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        permissionRefresh++
        pendingCategory?.let { category ->
            if (PrimeSearchPermissions.granted(context, category)) {
                PrimeSearchResultPreferences.setEnabled(context, category, true, PrimeSearchResultPreferences.Surface.DRAWER)
                pendingCategory = null
            }
        }
    }
    fun requestCategory(category: PrimeSearchResultPreferences.Category, surface: PrimeSearchResultPreferences.Surface) {
        pendingCategory = category
        val special = PrimeSearchPermissions.specialAccessIntent(category)
        if (special != null) {
            runCatching { context.startActivity(special) }
        } else {
            val missing = PrimeSearchPermissions.runtimePermissions(category).filter {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        }
    }
    PreferenceGroup(heading = "Recherche du tiroir d'applications") {
        Text("Réglages indépendants de Prime Search. Les résultats du tiroir utilisent des cartes arrondies et les couleurs définies dans Personnalisation → Tiroirs.")
        var fuzzyEnabled by remember { mutableStateOf(PrimeSearchResultPreferences.fuzzyApps(context, PrimeSearchResultPreferences.Surface.DRAWER)) }
        PreferenceTemplate(
            title = { Text("Tolérer les fautes de frappe") },
            description = { Text("Autoriser les petites erreurs dans les noms d’applications, sans modifier Prime Search") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = fuzzyEnabled,
                    onCheckedChange = {
                        fuzzyEnabled = it
                        PrimeSearchResultPreferences.setFuzzyApps(context, it, PrimeSearchResultPreferences.Surface.DRAWER)
                    },
                )
            },
            onClick = {
                fuzzyEnabled = !fuzzyEnabled
                PrimeSearchResultPreferences.setFuzzyApps(context, fuzzyEnabled, PrimeSearchResultPreferences.Surface.DRAWER)
            },
        )
        val drawerSurface = PrimeSearchResultPreferences.Surface.DRAWER
        var drawerWebCount by remember {
            mutableStateOf(PrimeSearchResultPreferences.webSuggestionCount(context, drawerSurface))
        }
        var showDrawerWebSettings by remember { mutableStateOf(false) }
        PreferenceTemplate(
            title = { Text("Suggestions Web du tiroir") },
            description = { Text("Configurer le nombre et le fournisseur, indépendamment de Prime Search") },
            onClick = { showDrawerWebSettings = true },
        )
        if (showDrawerWebSettings) {
            var provider by remember { mutableStateOf(PrimeSearchResultPreferences.suggestionProvider(context, drawerSurface)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDrawerWebSettings = false },
                title = { Text("Suggestions Web du tiroir") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text("Nombre de suggestions : $drawerWebCount")
                        androidx.compose.material3.Slider(
                            value = drawerWebCount.toFloat(),
                            onValueChange = {
                                drawerWebCount = it.roundToInt().coerceIn(0, 8)
                                PrimeSearchResultPreferences.setWebSuggestionCount(context, drawerWebCount, drawerSurface)
                            },
                            valueRange = 0f..8f,
                            steps = 7,
                        )
                        PrimeSearchResultPreferences.SuggestionProvider.entries.forEach { option ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = provider == option,
                                    onClick = {
                                        provider = option
                                        PrimeSearchResultPreferences.setSuggestionProvider(context, option, drawerSurface)
                                    },
                                )
                                Text(option.title)
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDrawerWebSettings = false }) { Text("Terminé") }
                },
            )
        }
        var showDrawerEngineSettings by remember { mutableStateOf(false) }
        PreferenceTemplate(
            title = { Text("Moteurs de recherche Web") },
            description = { Text("Choisir les moteurs proposés dans les résultats du tiroir") },
            onClick = { showDrawerEngineSettings = true },
        )
        if (showDrawerEngineSettings) {
            var engines by remember { mutableStateOf(PrimeSearchResultPreferences.webEngineOrder(context, drawerSurface)) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDrawerEngineSettings = false },
                title = { Text("Moteurs Web du tiroir") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(engines.size) { index ->
                            val engine = engines[index]
                            var enabled by remember(engine) {
                                mutableStateOf(engine in PrimeSearchResultPreferences.enabledWebEngines(context, drawerSurface))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Switch(
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        PrimeSearchResultPreferences.setWebEngineEnabled(context, engine, it, drawerSurface)
                                    },
                                )
                                Text(engine.title, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                IconButton(onClick = {
                                    if (index > 0) {
                                        val updated = engines.toMutableList()
                                        java.util.Collections.swap(updated, index, index - 1)
                                        engines = updated
                                        PrimeSearchResultPreferences.saveWebEngineOrder(context, updated, drawerSurface)
                                    }
                                }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                                }
                                IconButton(onClick = {
                                    if (index < engines.lastIndex) {
                                        val updated = engines.toMutableList()
                                        java.util.Collections.swap(updated, index, index + 1)
                                        engines = updated
                                        PrimeSearchResultPreferences.saveWebEngineOrder(context, updated, drawerSurface)
                                    }
                                }, enabled = index < engines.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDrawerEngineSettings = false }) { Text("Terminé") }
                },
            )
        }
        var drawerAutoKeyboard by remember {
            mutableStateOf(PrimeSearchResultPreferences.autoShowKeyboard(context, drawerSurface))
        }
        PreferenceTemplate(
            title = { Text("Afficher le clavier automatiquement") },
            description = { Text("Ouvrir le clavier lors de l'affichage de la recherche du tiroir") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = drawerAutoKeyboard,
                    onCheckedChange = {
                        drawerAutoKeyboard = it
                        PrimeSearchResultPreferences.setAutoShowKeyboard(context, it, drawerSurface)
                    },
                )
            },
            onClick = {
                drawerAutoKeyboard = !drawerAutoKeyboard
                PrimeSearchResultPreferences.setAutoShowKeyboard(context, drawerAutoKeyboard, drawerSurface)
            },
        )
        Text("Applications à l'ouverture")
        var drawerOpeningTab by remember {
            mutableStateOf(PrimeSearchResultPreferences.openingTab(context, drawerSurface))
        }
        var showDrawerOpeningTabs by remember { mutableStateOf(false) }
        PreferenceTemplate(
            title = { Text("Onglet affiché à l'ouverture") },
            description = { Text(drawerOpeningTab.label) },
            onClick = { showDrawerOpeningTabs = true },
        )
        if (showDrawerOpeningTabs) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDrawerOpeningTabs = false },
                title = { Text("Ouvrir le tiroir sur") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        PrimeSearchResultPreferences.OpeningTab.entries.forEach { option ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = drawerOpeningTab == option,
                                    onClick = {
                                        drawerOpeningTab = option
                                        PrimeSearchResultPreferences.setOpeningTab(context, option, drawerSurface)
                                        showDrawerOpeningTabs = false
                                    },
                                )
                                androidx.compose.material3.TextButton(onClick = {
                                    drawerOpeningTab = option
                                    PrimeSearchResultPreferences.setOpeningTab(context, option, drawerSurface)
                                    showDrawerOpeningTabs = false
                                }) { Text(option.label) }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDrawerOpeningTabs = false }) {
                        Text("Fermer")
                    }
                },
            )
        }
        Text("Apparence des applications")
        var drawerRows by remember {
            mutableStateOf(PrimeSearchResultPreferences.zeroStateRows(context, drawerSurface))
        }
        var drawerColumns by remember {
            mutableStateOf(PrimeSearchResultPreferences.appsPerRow(context, drawerSurface))
        }
        var drawerIconPercent by remember {
            mutableStateOf(PrimeSearchResultPreferences.iconSizePercent(context, drawerSurface))
        }
        var drawerTwoLines by remember {
            mutableStateOf(PrimeSearchResultPreferences.twoLineAppLabels(context, drawerSurface))
        }
        Text("Lignes au repos : $drawerRows")
        androidx.compose.material3.Slider(
            value = drawerRows.toFloat(),
            onValueChange = {
                drawerRows = it.toInt().coerceIn(1, 2)
                PrimeSearchResultPreferences.setZeroStateRows(context, drawerRows, drawerSurface)
            },
            valueRange = 1f..2f,
            steps = 0,
        )
        Text("Colonnes : $drawerColumns")
        androidx.compose.material3.Slider(
            value = drawerColumns.toFloat(),
            onValueChange = {
                drawerColumns = it.toInt().coerceIn(3, 6)
                PrimeSearchResultPreferences.setAppsPerRow(context, drawerColumns, drawerSurface)
            },
            valueRange = 3f..6f,
            steps = 2,
        )
        Text("Taille des icônes : $drawerIconPercent %")
        androidx.compose.material3.Slider(
            value = drawerIconPercent.toFloat(),
            onValueChange = {
                drawerIconPercent = it.toInt().coerceIn(60, 150)
                PrimeSearchResultPreferences.setIconSizePercent(context, drawerIconPercent, drawerSurface)
            },
            valueRange = 60f..150f,
            steps = 8,
        )
        PreferenceTemplate(
            title = { Text("Libellés sur deux lignes") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = drawerTwoLines,
                    onCheckedChange = {
                        drawerTwoLines = it
                        PrimeSearchResultPreferences.setTwoLineAppLabels(context, it, drawerSurface)
                    },
                )
            },
            onClick = {
                drawerTwoLines = !drawerTwoLines
                PrimeSearchResultPreferences.setTwoLineAppLabels(context, drawerTwoLines, drawerSurface)
            },
        )
        var drawerFollowGeneral by remember {
            mutableStateOf(PrimeSearchResultPreferences.appResultsFollowGeneral(context, drawerSurface))
        }
        PreferenceTemplate(
            title = { Text("Résultats d'applications : suivre les réglages généraux") },
            description = { Text("Sinon, définir une grille distincte pour les résultats d'une recherche") },
            endWidget = {
                androidx.compose.material3.Switch(
                    checked = drawerFollowGeneral,
                    onCheckedChange = {
                        drawerFollowGeneral = it
                        PrimeSearchResultPreferences.setAppResultsFollowGeneral(context, it, drawerSurface)
                    },
                )
            },
            onClick = {
                drawerFollowGeneral = !drawerFollowGeneral
                PrimeSearchResultPreferences.setAppResultsFollowGeneral(context, drawerFollowGeneral, drawerSurface)
            },
        )
        if (!drawerFollowGeneral) {
            var resultColumns by remember {
                mutableStateOf(PrimeSearchResultPreferences.appResultsColumns(context, drawerSurface))
            }
            var resultRows by remember {
                mutableStateOf(PrimeSearchResultPreferences.appResultsRows(context, drawerSurface))
            }
            var resultIconSize by remember {
                mutableStateOf(PrimeSearchResultPreferences.appResultsIconSizePercent(context, drawerSurface))
            }
            Text("Colonnes des résultats : $resultColumns")
            androidx.compose.material3.Slider(
                value = resultColumns.toFloat(),
                onValueChange = {
                    resultColumns = it.toInt().coerceIn(3, 5)
                    PrimeSearchResultPreferences.setAppResultsColumns(context, resultColumns, drawerSurface)
                },
                valueRange = 3f..5f,
                steps = 1,
            )
            Text("Lignes des résultats : $resultRows")
            androidx.compose.material3.Slider(
                value = resultRows.toFloat(),
                onValueChange = {
                    resultRows = it.toInt().coerceIn(1, 3)
                    PrimeSearchResultPreferences.setAppResultsRows(context, resultRows, drawerSurface)
                },
                valueRange = 1f..3f,
                steps = 1,
            )
            Text("Taille des icônes des résultats : $resultIconSize %")
            androidx.compose.material3.Slider(
                value = resultIconSize.toFloat(),
                onValueChange = {
                    resultIconSize = it.toInt().coerceIn(60, 150)
                    PrimeSearchResultPreferences.setAppResultsIconSizePercent(context, resultIconSize, drawerSurface)
                },
                valueRange = 60f..150f,
                steps = 8,
            )
            var resultAlignment by remember {
                mutableStateOf(PrimeSearchResultPreferences.appResultsAlignment(context, drawerSurface))
            }
            var showAlignmentChoices by remember { mutableStateOf(false) }
            PreferenceTemplate(
                title = { Text("Alignement des résultats") },
                description = { Text(resultAlignment.label) },
                onClick = { showAlignmentChoices = true },
            )
            if (showAlignmentChoices) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showAlignmentChoices = false },
                    title = { Text("Alignement des applications") },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            PrimeSearchResultPreferences.AppResultsAlignment.entries.forEach { option ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(
                                        selected = resultAlignment == option,
                                        onClick = {
                                            resultAlignment = option
                                            PrimeSearchResultPreferences.setAppResultsAlignment(context, option, drawerSurface)
                                            showAlignmentChoices = false
                                        },
                                    )
                                    androidx.compose.material3.TextButton(onClick = {
                                        resultAlignment = option
                                        PrimeSearchResultPreferences.setAppResultsAlignment(context, option, drawerSurface)
                                        showAlignmentChoices = false
                                    }) { Text(option.label) }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showAlignmentChoices = false }) {
                            Text("Fermer")
                        }
                    },
                )
            }
        }
        val drawerCategories = PrimeSearchResultPreferences.order(context, drawerSurface)
        var drawerOrder by remember { mutableStateOf(drawerCategories) }
        var categorySettings by remember { mutableStateOf<PrimeSearchResultPreferences.Category?>(null) }
        Text("Afficher dans les résultats de recherche")
        val sharedDrag = remember { CategoryDragState() }
        fun moveDrawerCategory(from: Int, to: Int) {
            if (from !in drawerOrder.indices || to !in drawerOrder.indices || from == to) return
            val updated = drawerOrder.toMutableList()
            updated.add(to, updated.removeAt(from))
            drawerOrder = updated
            PrimeSearchResultPreferences.saveOrder(context, updated, drawerSurface)
        }
        drawerOrder.forEachIndexed { index, category ->
            val needsPermission = PrimeSearchPermissions.required(category) &&
                !PrimeSearchPermissions.granted(context, category)
            val enabled = categoryStates[category] ?: false
            SharedCategoryCard(
                category = category,
                index = index,
                lastIndex = drawerOrder.lastIndex,
                enabled = enabled,
                needsPermission = needsPermission,
                onSettings = { categorySettings = category },
                onMove = ::moveDrawerCategory,
                onEnabled = { value ->
                    PrimeSearchResultPreferences.setEnabled(context, category, value, drawerSurface)
                    categoryStates = categoryStates + (category to PrimeSearchResultPreferences.enabled(context, category, drawerSurface))
                    permissionRefresh++
                },
                onPermission = { requestCategory(category, drawerSurface) },
                dragState = sharedDrag,
            )
        }
        androidx.compose.material3.TextButton(onClick = {
            PrimeSearchResultPreferences.resetOrder(context, drawerSurface)
            drawerOrder = PrimeSearchResultPreferences.order(context, drawerSurface)
        }) { Text("Réinitialiser l'ordre") }
        categorySettings?.let { selected ->
            SharedCategorySettingsDialog(
                category = selected,
                surface = drawerSurface,
                permissionRefresh = permissionRefresh,
                onDismiss = { categorySettings = null },
            ) {
                if (PrimeSearchPermissions.required(selected) && !PrimeSearchPermissions.granted(context, selected)) {
                    Text(PrimeSearchPermissions.explanation(selected))
                    androidx.compose.material3.TextButton(onClick = { requestCategory(selected, drawerSurface) }) {
                        Text("Autoriser l'accès")
                    }
                }
            }
        }

    }

}
