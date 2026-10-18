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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

        val searchAlgorithm = preferenceManager2().searchAlgorithm.getAdapter().state.value
        val navController = LocalNavController.current
        PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
            if (searchAlgorithm != LawnchairSearchAlgorithm.ASI_SEARCH) {
                val canDisable = searchAlgorithm != LawnchairSearchAlgorithm.APP_SEARCH
                val adapter = prefs.searchResultApps.getAdapter()

                TwoTargetSwitchPreference(
                    checked = if (canDisable) adapter.state.value else true,
                    onCheckedChange = if (canDisable) adapter::onChange else ({}),
                    enabled = canDisable,
                    label = stringResource(R.string.search_pref_result_apps_and_shortcuts_title),
                    onClick = {
                        navController.navigate(SearchProviderPreference(SearchProviderId.APPS))
                    },
                )
            }
            when (searchAlgorithm) {
                LawnchairSearchAlgorithm.LOCAL_SEARCH -> {
                    LocalSearchSettings(
                        prefs = prefs,
                        prefs2 = prefs2,
                        context = context,
                    )
                }

                LawnchairSearchAlgorithm.ASI_SEARCH -> {
                    ASISearchSettings(prefs)
                }
            }
        }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh++
                pendingCategory?.let { category ->
                    if (PrimeSearchPermissions.granted(context, category)) {
                        PrimeSearchResultPreferences.setEnabled(context, category, true, PrimeSearchResultPreferences.Surface.DRAWER)
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
        Text("Réglages indépendants de Prime Search. L'apparence du tiroir et de sa barre de recherche reste inchangée.")
        val drawerSurface = PrimeSearchResultPreferences.Surface.DRAWER
        var drawerWebCount by remember {
            mutableStateOf(PrimeSearchResultPreferences.webSuggestionCount(context, drawerSurface))
        }
        Text("Suggestions Web du tiroir : $drawerWebCount")
        androidx.compose.material3.Slider(
            value = drawerWebCount.toFloat(),
            onValueChange = {
                drawerWebCount = it.roundToInt().coerceIn(0, 8)
                PrimeSearchResultPreferences.setWebSuggestionCount(context, drawerWebCount, drawerSurface)
            },
            valueRange = 0f..8f,
        )
        var drawerSuggestionProvider by remember {
            mutableStateOf(PrimeSearchResultPreferences.suggestionProvider(context, drawerSurface))
        }
        PrimeSearchResultPreferences.SuggestionProvider.entries.forEach { provider ->
            PreferenceTemplate(
                title = { Text("Suggestions : ${provider.title}") },
                endWidget = {
                    androidx.compose.material3.RadioButton(
                        selected = drawerSuggestionProvider == provider,
                        onClick = {
                            drawerSuggestionProvider = provider
                            PrimeSearchResultPreferences.setSuggestionProvider(context, provider, drawerSurface)
                        },
                    )
                },
                onClick = {
                    drawerSuggestionProvider = provider
                    PrimeSearchResultPreferences.setSuggestionProvider(context, provider, drawerSurface)
                },
            )
        }
        PrimeSearchResultPreferences.WebEngine.entries.forEach { engine ->
            var active by remember(engine) {
                mutableStateOf(engine in PrimeSearchResultPreferences.enabledWebEngines(context, drawerSurface))
            }
            PreferenceTemplate(
                title = { Text("Moteur Web : ${engine.title}") },
                endWidget = {
                    androidx.compose.material3.Switch(
                        checked = active,
                        onCheckedChange = {
                            active = it
                            PrimeSearchResultPreferences.setWebEngineEnabled(context, engine, it, drawerSurface)
                        },
                    )
                },
                onClick = {
                    active = !active
                    PrimeSearchResultPreferences.setWebEngineEnabled(context, engine, active, drawerSurface)
                },
            )
        }
        val drawerCategories = listOf(
            PrimeSearchResultPreferences.Category.APPS,
            PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
            PrimeSearchResultPreferences.Category.CONTACTS,
            PrimeSearchResultPreferences.Category.CALENDAR,
            PrimeSearchResultPreferences.Category.REMINDERS,
            PrimeSearchResultPreferences.Category.NOTIFICATIONS,
            PrimeSearchResultPreferences.Category.FILES,
            PrimeSearchResultPreferences.Category.SETTINGS,
            PrimeSearchResultPreferences.Category.WEB,
            PrimeSearchResultPreferences.Category.HISTORY,
            PrimeSearchResultPreferences.Category.CALCULATOR,
        )
        var drawerOrder by remember { mutableStateOf(PrimeSearchResultPreferences.order(context, drawerSurface)) }
        Text("Ordre des catégories du tiroir")
        drawerOrder.filter { it in drawerCategories }.forEach { category ->
            val index = drawerOrder.indexOf(category)
            PreferenceTemplate(
                title = { Text(category.title) },
                endWidget = {
                    Row {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val updated = drawerOrder.toMutableList()
                                    updated.add(index - 1, updated.removeAt(index))
                                    drawerOrder = updated
                                    PrimeSearchResultPreferences.saveOrder(context, updated, drawerSurface)
                                }
                            },
                            enabled = index > 0,
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter") }
                        IconButton(
                            onClick = {
                                if (index < drawerOrder.lastIndex) {
                                    val updated = drawerOrder.toMutableList()
                                    updated.add(index + 1, updated.removeAt(index))
                                    drawerOrder = updated
                                    PrimeSearchResultPreferences.saveOrder(context, updated, drawerSurface)
                                }
                            },
                            enabled = index < drawerOrder.lastIndex,
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre") }
                    }
                },
            )
        }
        drawerCategories.forEach { category ->
            val needsPermission = PrimeSearchPermissions.required(category) &&
                !PrimeSearchPermissions.granted(context, category)
            val enabled = PrimeSearchResultPreferences.enabled(context, category, drawerSurface)
            PreferenceTemplate(
                title = { Text(category.title) },
                description = {
                    Text(if (needsPermission) PrimeSearchPermissions.explanation(category) else category.description)
                },
                endWidget = {
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            if (value && needsPermission) {
                                requestCategory(category, drawerSurface)
                            } else {
                                PrimeSearchResultPreferences.setEnabled(context, category, value, drawerSurface)
                                permissionRefresh++
                            }
                        },
                    )
                },
                onClick = {
                    if (needsPermission) {
                        requestCategory(category, drawerSurface)
                    } else {
                        PrimeSearchResultPreferences.setEnabled(context, category, !enabled, drawerSurface)
                        permissionRefresh++
                    }
                },
            )
        }
        listOf(
            "calendars" to PrimeSearchResultPreferences.Category.CALENDAR,
            "contacts" to PrimeSearchResultPreferences.Category.CONTACTS,
        ).forEach { (kind, category) ->
            val sources = remember(kind) {
                if (kind == "calendars") app.lawnchair.prime.search.PrimeSourceSelection.calendars(context)
                else app.lawnchair.prime.search.PrimeSourceSelection.contactAccounts(context)
            }
            Text("Sources du tiroir : ${category.title}")
            sources.forEach { source ->
                var checked by remember(kind, source.id) {
                    mutableStateOf(app.lawnchair.prime.search.PrimeSourceSelection.isSelected(context, kind, source.id, drawerSurface))
                }
                PreferenceTemplate(
                    title = { Text(source.label) },
                    endWidget = {
                        androidx.compose.material3.Switch(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                app.lawnchair.prime.search.PrimeSourceSelection.setSelected(context, kind, source.id, it, sources, drawerSurface)
                            },
                        )
                    },
                    onClick = {
                        checked = !checked
                        app.lawnchair.prime.search.PrimeSourceSelection.setSelected(context, kind, source.id, checked, sources, drawerSurface)
                    },
                )
            }
        }
        var drawerMaxFiles by remember {
            mutableStateOf(
                if (PrimeSearchResultPreferences.hasCustomFileLimit(context, drawerSurface))
                    PrimeSearchResultPreferences.maxFileResults(context, drawerSurface)
                else 5
            )
        }
        Text("Limite des fichiers du tiroir : $drawerMaxFiles")
        androidx.compose.material3.Slider(
            value = drawerMaxFiles.toFloat(),
            onValueChange = {
                drawerMaxFiles = it.roundToInt().coerceIn(3, 100)
                PrimeSearchResultPreferences.setMaxFileResults(context, drawerMaxFiles, drawerSurface)
            },
            valueRange = 3f..100f,
        )
        PrimeSearchResultPreferences.FileOption.entries.forEach { option ->
            var checked by remember(option) {
                mutableStateOf(PrimeSearchResultPreferences.fileOptionEnabled(context, option, drawerSurface))
            }
            PreferenceTemplate(
                title = { Text(option.label) },
                endWidget = {
                    androidx.compose.material3.Switch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            PrimeSearchResultPreferences.setFileOptionEnabled(context, option, it, drawerSurface)
                        },
                    )
                },
                onClick = {
                    checked = !checked
                    PrimeSearchResultPreferences.setFileOptionEnabled(context, option, checked, drawerSurface)
                },
            )
        }
    }

}
