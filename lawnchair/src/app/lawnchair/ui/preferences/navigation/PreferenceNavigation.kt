package app.lawnchair.ui.preferences.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import app.lawnchair.backup.ui.CreateBackupScreen
import app.lawnchair.backup.ui.restoreBackupGraph
import app.lawnchair.backup.ui.restoreNovaBackupGraph
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.about.About
import app.lawnchair.ui.preferences.about.acknowledgements.Acknowledgements
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceModelList
import app.lawnchair.ui.preferences.components.colorpreference.ColorSelection
import app.lawnchair.ui.preferences.components.colorpreference.PrimeColorSelection
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.prime.drawer.PrimeFolderLongPressHelper
import app.lawnchair.ui.preferences.components.search.SearchProviderId
import app.lawnchair.ui.preferences.components.search.SearchProviderPreferenceScreen
import app.lawnchair.ui.preferences.destinations.AppDrawerFoldersPreference
import app.lawnchair.ui.preferences.destinations.AppDrawerPreferences
import app.lawnchair.ui.preferences.destinations.BackupAndRestorePreference
import app.lawnchair.ui.preferences.destinations.CustomIconShapePreference
import app.lawnchair.ui.preferences.destinations.DebugMenuPreferences
import app.lawnchair.ui.preferences.destinations.DismissedPredictionAppsPreferences
import app.lawnchair.ui.preferences.destinations.DockPreferences
import app.lawnchair.ui.preferences.destinations.DummyPreference
import app.lawnchair.ui.preferences.destinations.ExperimentalFeaturesPreferences
import app.lawnchair.ui.preferences.destinations.FeatureFlagsPreference
import app.lawnchair.ui.preferences.destinations.FolderPreferences
import app.lawnchair.ui.preferences.destinations.FontSelection
import app.lawnchair.ui.preferences.destinations.GeneralPreferences
import app.lawnchair.ui.preferences.destinations.GesturePreferences
import app.lawnchair.ui.preferences.destinations.HiddenAppsPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenGridPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenPreferences
import app.lawnchair.ui.preferences.destinations.IconPackPreferences
import app.lawnchair.ui.preferences.destinations.IconPickerPreference
import app.lawnchair.ui.preferences.destinations.LauncherPopupPreference
import app.lawnchair.ui.preferences.destinations.PickAppForGesture
import app.lawnchair.ui.preferences.destinations.PredictionsPreferences
import app.lawnchair.ui.preferences.destinations.PrimeDrawerCategoriesPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerCategoryPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerCategoryAppsPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerCategoryAdvancedPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerFolderAdvancedPreference
import app.lawnchair.ui.preferences.destinations.PrimeHomeFolderAdvancedPreference
import app.lawnchair.ui.preferences.destinations.PrimeDevelopmentOptionsPreference
import app.lawnchair.ui.preferences.destinations.PrimeDiagnosticLogsPreference
import app.lawnchair.ui.preferences.destinations.PrimeHapticTuningPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerCategoryFoldersPreference
import app.lawnchair.ui.preferences.destinations.PrimeDrawerFolderAppsPreference
import app.lawnchair.ui.preferences.destinations.PreferencesDashboard
import app.lawnchair.ui.preferences.destinations.QuickstepPreferences
import app.lawnchair.ui.preferences.destinations.SearchPreferences
import app.lawnchair.ui.preferences.destinations.SearchProviderPreferences
import app.lawnchair.ui.preferences.destinations.SelectAppsForDrawerFolder
import app.lawnchair.ui.preferences.destinations.SelectIconPreference
import app.lawnchair.ui.preferences.destinations.ShapePreference
import app.lawnchair.ui.preferences.destinations.PrimeShapeSelection
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.destinations.SmartspacePreferences
import com.android.launcher3.util.ComponentKey
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

inline fun <reified T> getDeepLink(route: T) where T : PreferenceRoute, T : PreferenceDeepLink = listOf(navDeepLink<T>(basePath = route.deepLink))

@Composable
fun PreferenceNavigation(
    navController: NavHostController,
    startDestination: PreferenceRoute,
    intent: Intent? = null,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slideDistance = rememberSlideDistance()

    LaunchedEffect(intent) {
        intent?.let { navController.handleDeepLink(it) }
    }

    // TODO: navigate to nav3: https://developer.android.com/guide/navigation/navigation-3
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { materialSharedAxisXIn(!isRtl, slideDistance) },
        exitTransition = { materialSharedAxisXOut(!isRtl, slideDistance) },
        popEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
        popExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
        predictivePopEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
        predictivePopExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
    ) {
        composable<Root> {
            val isExpandedScreen = LocalIsExpandedScreen.current

            PreferencesDashboard(
                currentRoute = Root,
                onNavigate = {
                    navController.navigate(it)
                },
            )

            LaunchedEffect(isExpandedScreen) {
                if (isExpandedScreen) {
                    navController.navigate(General) {
                        launchSingleTop = true
                        popUpTo(navController.graph.id)
                    }
                }
            }
        }
        composable<Dummy> {
            DummyPreference()
        }

        composable<General>(
            deepLinks = getDeepLink(General),
        ) { GeneralPreferences() }
        composable<GeneralFontSelection> { backStackEntry ->
            val route: GeneralFontSelection = backStackEntry.toRoute()
            val pref = preferenceManager().prefsMap[route.prefKey]
                as? BasePreferenceManager.FontPref ?: return@composable
            FontSelection(pref)
        }
        composable<GeneralIconPack>(
            deepLinks = getDeepLink(GeneralIconPack),
        ) { IconPackPreferences() }
        composable<GeneralIconShape> { backStackEntry ->
            val route: GeneralIconShape = backStackEntry.toRoute()
            ShapePreference(currentTab = route.selectedId)
        }
        composable<GeneralCustomIconShapeCreator>(
            deepLinks = getDeepLink(GeneralCustomIconShapeCreator()),
        ) { backStackEntry ->
            val route: GeneralCustomIconShapeCreator = backStackEntry.toRoute()
            CustomIconShapePreference(currentTab = route.selectedId)
        }

        composable<HomeScreen>(
            deepLinks = getDeepLink(HomeScreen),
        ) { HomeScreenPreferences() }
        composable<HomeScreenGrid>(
            deepLinks = getDeepLink(HomeScreenGrid),
        ) { HomeScreenGridPreferences() }
        composable<HomeScreenPopupEditor>(
            deepLinks = getDeepLink(HomeScreenPopupEditor),
        ) { LauncherPopupPreference() }

        composable<Dock>(
            deepLinks = getDeepLink(Dock),
        ) { DockPreferences() }
        composable<DockSearchProvider>(
            deepLinks = getDeepLink(DockSearchProvider),
        ) { SearchProviderPreferences() }

        composable<Smartspace>(
            deepLinks = getDeepLink(Smartspace),
        ) { SmartspacePreferences(fromWidget = false) }
        composable<SmartspaceWidget> { SmartspacePreferences(fromWidget = true) }

        composable<AppDrawer>(
            deepLinks = getDeepLink(AppDrawer),
        ) { AppDrawerPreferences() }
        composable<PrimeDrawerCategories> { PrimeDrawerCategoriesPreference() }
        composable<PrimeDrawerCategory> { backStackEntry ->
            val route: PrimeDrawerCategory = backStackEntry.toRoute()
            PrimeDrawerCategoryPreference(route.tabId)
        }
        composable<PrimeDrawerCategoryApps> { backStackEntry ->
            val route: PrimeDrawerCategoryApps = backStackEntry.toRoute()
            PrimeDrawerCategoryAppsPreference(route.tabId)
        }
        composable<PrimeDrawerCategoryFolders> { backStackEntry ->
            val route: PrimeDrawerCategoryFolders = backStackEntry.toRoute()
            PrimeDrawerCategoryFoldersPreference(route.tabId)
        }
        composable<PrimeDrawerFolderApps> { backStackEntry ->
            val route: PrimeDrawerFolderApps = backStackEntry.toRoute()
            PrimeDrawerFolderAppsPreference(route.tabId, route.folderId)
        }
        composable<PrimeDrawerCategoryAdvanced> { backStackEntry ->
            val route: PrimeDrawerCategoryAdvanced = backStackEntry.toRoute()
            PrimeDrawerCategoryAdvancedPreference(route.tabId)
        }
        composable<PrimeDrawerFolderAdvanced> { backStackEntry ->
            val route: PrimeDrawerFolderAdvanced = backStackEntry.toRoute()
            PrimeDrawerFolderAdvancedPreference(route.tabId, route.folderId)
        }
        composable<PrimeHomeFolderAdvanced> { backStackEntry ->
            val route: PrimeHomeFolderAdvanced = backStackEntry.toRoute()
            PrimeHomeFolderAdvancedPreference(route.folderId)
        }
        composable<PrimeHomeFolderShape> { backStackEntry ->
            val route: PrimeHomeFolderShape = backStackEntry.toRoute()
            val context = LocalContext.current
            val repository = PrimeDrawerTabsRepository(context)
            val prefs2 = preferenceManager2()
            val stored = repository.getHomeFolderVisualOverrides(route.folderId)
            val current = when (route.shapeKey) {
                "folderChildIcon" -> stored.childIconShape
                else -> stored.shape
            }
            val inherited = if (route.shapeKey == "folderShape") prefs2.folderShape.firstCached() else prefs2.iconShape.firstCached()
            var selected by remember(route.folderId, route.shapeKey) {
                mutableStateOf(current?.let { IconShape.fromString(it, context) } ?: inherited)
            }
            PrimeShapeSelection(
                label = route.label,
                selectedShape = selected,
                onSelect = { shape ->
                    selected = shape ?: inherited
                    val o = repository.getHomeFolderVisualOverrides(route.folderId)
                    repository.setHomeFolderVisualOverrides(
                        route.folderId,
                        if (route.shapeKey == "folderChildIcon") o.copy(childIconShape = shape?.toString()) else o.copy(shape = shape?.toString()),
                    )
                },
            )
        }
        composable<PrimeHomeFolderColor> { backStackEntry ->
            val route: PrimeHomeFolderColor = backStackEntry.toRoute()
            val context = LocalContext.current
            val repository = PrimeDrawerTabsRepository(context)
            val stored = repository.getHomeFolderVisualOverrides(route.folderId)
            PrimeColorSelection(
                label = route.label,
                appliedColor = when (route.colorKey) {
                    "folderClosedText" -> stored.closedLabelColor
                    "folderText" -> stored.textColor
                    else -> stored.color
                }?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
                onApply = { option ->
                    val resolved = when (option) {
                        ColorOption.Default -> null
                        else -> option.colorPreferenceEntry.lightColor(context)
                    }
                    val o = repository.getHomeFolderVisualOverrides(route.folderId)
                    repository.setHomeFolderVisualOverrides(
                        route.folderId,
                        when (route.colorKey) {
                            "folderClosedText" -> o.copy(closedLabelColor = resolved)
                            "folderText" -> o.copy(textColor = resolved)
                            else -> o.copy(color = resolved)
                        },
                    )
                    PrimeFolderLongPressHelper.refreshHomeFolderVisualOverrides(route.folderId)
                },
            )
        }
        composable<PrimeDrawerShape> { backStackEntry ->
            val route: PrimeDrawerShape = backStackEntry.toRoute()
            val context = LocalContext.current
            val repository = PrimeDrawerTabsRepository(context)
            val tab = repository.getTab(route.tabId) ?: return@composable
            val folder = route.folderId?.let { id -> tab.folders.firstOrNull { it.id == id } }
            val stored = when (route.shapeKey) {
                "drawerIcon" -> tab.visualOverrides.drawerIconShape
                "folderChildIcon" -> folder?.visualOverrides?.childIconShape ?: tab.visualOverrides.folderChildIconShape
                "folderShape" -> folder?.visualOverrides?.shape ?: tab.visualOverrides.folderShape
                else -> null
            }
            val prefs2 = preferenceManager2()
            val inherited = when (route.shapeKey) {
                "folderShape" -> prefs2.folderShape.firstCached()
                else -> prefs2.iconShape.firstCached()
            }
            var selected by remember(route.tabId, route.folderId, route.shapeKey) {
                mutableStateOf(stored?.let { IconShape.fromString(it, context) } ?: inherited)
            }
            PrimeShapeSelection(
                label = route.label,
                selectedShape = selected,
                onSelect = { shape ->
                    selected = shape ?: inherited
                    val currentTab = repository.getTab(route.tabId) ?: return@PrimeShapeSelection
                    if (route.folderId == null) {
                        val o = currentTab.visualOverrides
                        repository.setTabVisualOverrides(
                            route.tabId,
                            when (route.shapeKey) {
                                "drawerIcon" -> o.copy(drawerIconShape = shape?.toString())
                                "folderChildIcon" -> o.copy(folderChildIconShape = shape?.toString())
                                "folderShape" -> o.copy(folderShape = shape?.toString())
                                else -> o
                            },
                        )
                        if (route.shapeKey == "folderChildIcon") {
                            PrimeFolderLongPressHelper.refreshDrawerTabFolderVisualOverrides(route.tabId)
                        }
                    } else {
                        val currentFolder = currentTab.folders.firstOrNull { it.id == route.folderId } ?: return@PrimeShapeSelection
                        val o = currentFolder.visualOverrides
                        repository.setFolderVisualOverrides(
                            route.tabId,
                            route.folderId,
                            when (route.shapeKey) {
                                "folderChildIcon" -> o.copy(childIconShape = shape?.toString())
                                "folderShape" -> o.copy(shape = shape?.toString())
                                else -> o
                            },
                        )
                        if (route.shapeKey == "folderChildIcon") {
                            PrimeFolderLongPressHelper.refreshDrawerFolderVisualOverrides(
                                route.tabId,
                                route.folderId,
                            )
                        }
                    }
                },
            )
        }
        composable<PrimeDrawerDefaultColor> { backStackEntry ->
            val route: PrimeDrawerDefaultColor = backStackEntry.toRoute()
            val context = LocalContext.current
            val repository = PrimeDrawerTabsRepository(context)
            val mode = app.lawnchair.prime.drawer.PrimeDrawerMode.entries
                .first { it.storageKey == route.modeKey }
            val gridOption = com.android.launcher3.InvariantDeviceProfile.INSTANCE.get(context).closestProfile
            val modePreferences = app.lawnchair.prime.drawer.PrimeDrawerModePreferences(context)
            val profile = modePreferences.get(gridOption, mode)
            val prefs2 = preferenceManager2()
            val nativeDrawerBackgroundColor = prefs2.appDrawerBackgroundColor.getAdapter()
            val current = if (route.colorKey == "text") {
                profile.defaultDrawerTextColor?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default
            } else {
                profile.appDrawerBackgroundColor
            }
            PrimeColorSelection(
                label = route.label,
                appliedColor = current,
                onApply = { option ->
                    if (route.colorKey == "text") {
                        val resolved = when (option) {
                            ColorOption.Default -> null
                            else -> option.colorPreferenceEntry.lightColor(context)
                        }
                        modePreferences.update(
                            gridOption,
                            mode,
                            { currentProfile -> currentProfile.copy(defaultDrawerTextColor = resolved) },
                            {},
                        )
                        if (mode == app.lawnchair.prime.drawer.PrimeDrawerMode.TABS) {
                            repository.setDefaultDrawerColors(textColor = resolved)
                        }
                    } else {
                        modePreferences.update(
                            gridOption,
                            mode,
                            { currentProfile -> currentProfile.copy(appDrawerBackgroundColor = option) },
                            {},
                        )
                        nativeDrawerBackgroundColor.onChange(option)
                    }
                },
            )
        }
        composable<PrimeDrawerCategoryColor> { backStackEntry ->
            val route: PrimeDrawerCategoryColor = backStackEntry.toRoute()
            val context = LocalContext.current
            val repository = PrimeDrawerTabsRepository(context)
            val tab = repository.getTab(route.tabId)
            val folder = route.folderId?.let { id -> tab?.folders?.firstOrNull { it.id == id } }
            val current = when (route.colorKey) {
                "tab" -> tab?.visualOverrides?.tabColor
                "background" -> tab?.visualOverrides?.drawerBackgroundColor
                "folderColor" -> folder?.visualOverrides?.color ?: tab?.visualOverrides?.folderColor
                "drawerText" -> tab?.visualOverrides?.drawerTextColor
                "folderText" -> folder?.visualOverrides?.textColor ?: tab?.visualOverrides?.folderTextColor
                "folderClosedText" -> folder?.visualOverrides?.closedLabelColor ?: tab?.visualOverrides?.drawerTextColor
                else -> null
            }
            PrimeColorSelection(
                label = route.label,
                appliedColor = current?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default,
                onApply = { option ->
                    val resolved = when (option) {
                        ColorOption.Default -> null
                        else -> option.colorPreferenceEntry.lightColor(context)
                    }
                    val currentTab = repository.getTab(route.tabId) ?: return@PrimeColorSelection
                    if (route.folderId != null && (route.colorKey == "folderColor" || route.colorKey == "folderText" || route.colorKey == "folderClosedText")) {
                        val currentFolder = currentTab.folders.firstOrNull { it.id == route.folderId } ?: return@PrimeColorSelection
                        repository.setFolderVisualOverrides(
                            route.tabId,
                            route.folderId,
                            when (route.colorKey) {
                                "folderText" -> currentFolder.visualOverrides.copy(textColor = resolved)
                                "folderClosedText" -> currentFolder.visualOverrides.copy(closedLabelColor = resolved)
                                else -> currentFolder.visualOverrides.copy(color = resolved)
                            },
                        )
                    } else {
                        val overrides = currentTab.visualOverrides
                        repository.setTabVisualOverrides(
                            route.tabId,
                            when (route.colorKey) {
                                "tab" -> overrides.copy(tabColor = resolved)
                                "folderColor" -> overrides.copy(folderColor = resolved)
                                "drawerText" -> overrides.copy(drawerTextColor = resolved)
                                "folderText" -> overrides.copy(folderTextColor = resolved)
                                else -> overrides.copy(drawerBackgroundColor = resolved)
                            },
                        )
                    }
                },
            )
        }
        composable<AppDrawerHiddenApps>(
            deepLinks = getDeepLink(AppDrawerHiddenApps),
        ) { HiddenAppsPreferences() }
        composable<AppDrawerAppListToFolder> { backStackEntry ->
            val args = backStackEntry.arguments!!
            val folderInfoId = args.getInt("id")
            SelectAppsForDrawerFolder(folderInfoId)
        }
        composable<AppDrawerFolder>(
            deepLinks = getDeepLink(AppDrawerFolder),
        ) { AppDrawerFoldersPreference() }

        composable<Search>(
            deepLinks = getDeepLink(Search()),
        ) { backStackEntry ->
            val route: Search = backStackEntry.toRoute()
            SearchPreferences(currentTab = route.selectedId)
        }
        composable<SearchProviderPreference>(
            deepLinks = getDeepLink(SearchProviderPreference(SearchProviderId.entries.first())),
        ) { backStackEntry ->
            val route: SearchProviderPreference = backStackEntry.toRoute()
            SearchProviderPreferenceScreen(route.id)
        }

        composable<Folders>(
            deepLinks = getDeepLink(Folders),
        ) { FolderPreferences() }

        composable<Gestures>(
            deepLinks = getDeepLink(Gestures),
        ) { GesturePreferences() }
        composable<GesturesPickApp> { PickAppForGesture() }

        composable<Quickstep>(
            deepLinks = getDeepLink(Quickstep),
        ) { QuickstepPreferences() }
        composable<BackupAndRestore>(
            deepLinks = getDeepLink(BackupAndRestore),
        ) { BackupAndRestorePreference() }

        composable<PrimeDevelopmentOptions> { PrimeDevelopmentOptionsPreference() }
        composable<PrimeDiagnosticLogs> { PrimeDiagnosticLogsPreference() }
        composable<PrimeHapticTuning> { PrimeHapticTuningPreference() }
        composable<About>(
            deepLinks = getDeepLink(About),
        ) { About() }
        composable<AboutLicenses>(
            deepLinks = getDeepLink(AboutLicenses),
        ) { Acknowledgements() }

        composable<DebugMenu> { DebugMenuPreferences() }
        composable<FeatureFlags> { FeatureFlagsPreference() }

        composable<SelectIcon> { backStackEntry ->
            val args: SelectIcon = backStackEntry.toRoute()
            val componentKey = args.componentKey
            val key = ComponentKey.fromString(componentKey)!!
            SelectIconPreference(key)
        }
        composable<IconPicker> { backStackEntry ->
            val args: IconPicker = backStackEntry.toRoute()
            IconPickerPreference(packageName = args.packageName)
        }

        composable<ExperimentalFeatures>(
            deepLinks = getDeepLink(ExperimentalFeatures),
        ) { ExperimentalFeaturesPreferences() }
        composable<Predictions>(
            deepLinks = getDeepLink(Predictions),
        ) { PredictionsPreferences() }
        composable<DismissedPredictionApps> { DismissedPredictionAppsPreferences() }
        composable<ColorSelection> { backStackEntry ->
            val screen: ColorSelection = backStackEntry.toRoute()
            val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
            val model = modelList[screen.prefKey]
            ColorSelection(
                label = stringResource(id = model.labelRes),
                preference = model.prefObject,
                dynamicEntries = model.dynamicEntries,
            )
        }

        composable<CreateBackup>(
            deepLinks = getDeepLink(CreateBackup),
        ) { CreateBackupScreen(viewModel()) }

        restoreBackupGraph()
        restoreNovaBackupGraph()
    }
}
