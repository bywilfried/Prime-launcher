package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import app.lawnchair.predictions.LawnchairPredictionManager
import app.lawnchair.predictions.LawnchairPredictor
import app.lawnchair.predictions.NoPredictor
import app.lawnchair.predictions.PredictionMode
import app.lawnchair.predictions.SystemPredictor
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.PrefEntry
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.prime.drawer.PrimeDrawerMode
import app.lawnchair.prime.drawer.PrimeDrawerModePreferences
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.PermissionDialog
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.DismissedPredictionApps
import app.lawnchair.util.lifecycleState
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities

@Composable
fun PredictionsPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.suggestion_pref_screen_title),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()
        val activeDrawerMode = when {
            prefs.drawerTabsEnabled.get() -> PrimeDrawerMode.TABS
            prefs.drawerList.get() -> PrimeDrawerMode.DEFAULT
            else -> PrimeDrawerMode.CADDY
        }
        val modePreferences = remember(context) { PrimeDrawerModePreferences(context) }
        val gridOption = InvariantDeviceProfile.INSTANCE.get(context).closestProfile
        val profile = modePreferences.get(gridOption, activeDrawerMode)

        fun <T> modePreference(
            key: String,
            read: (app.lawnchair.prime.drawer.PrimeDrawerModeProfile) -> T,
            write: (app.lawnchair.prime.drawer.PrimeDrawerModeProfile, T) -> app.lawnchair.prime.drawer.PrimeDrawerModeProfile,
        ) = modePreferences.preference(gridOption, activeDrawerMode, key, read, write, {})

        val nativeEnabled = prefs2.enableGlobalPrediction.getAdapter()
        val nativeMode = prefs2.predictionMode.getAdapter()
        val nativeWeighted = prefs2.lawnchairPredictorUseWeightedUsageStats.getAdapter()

        LaunchedEffect(activeDrawerMode) {
            if (nativeEnabled.state.value != profile.predictionsEnabled) nativeEnabled.onChange(profile.predictionsEnabled)
            val storedMode = PredictionMode.fromString(profile.predictionMode)
            if (nativeMode.state.value != storedMode) nativeMode.onChange(storedMode)
            if (nativeWeighted.state.value != profile.predictionUseWeightedUsageStats) {
                nativeWeighted.onChange(profile.predictionUseWeightedUsageStats)
            }
        }

        val enabledAdapter = predictionModeBackedAdapter(
            modePreference("predictionsEnabled", { it.predictionsEnabled }, { p, value -> p.copy(predictionsEnabled = value) }),
            nativeEnabled,
        )
        val predictionModeAdapter = predictionModeBackedAdapter(
            modePreference("predictionMode", { it.predictionMode }, { p, value -> p.copy(predictionMode = value) }),
            nativeMode,
            readNative = { PredictionMode.fromString(it) },
            writeMode = { it.toString() },
        )
        val weightedAdapter = predictionModeBackedAdapter(
            modePreference("predictionUseWeightedUsageStats", { it.predictionUseWeightedUsageStats }, { p, value -> p.copy(predictionUseWeightedUsageStats = value) }),
            nativeWeighted,
        )

        MainSwitchPreference(
            adapter = enabledAdapter,
            label = stringResource(R.string.global_predictions_label),
        ) {
            AppPredictionsFeature(context, prefs2, predictionModeAdapter, weightedAdapter)
        }
    }
}

@Composable
private fun AppPredictionsFeature(
    context: Context,
    prefs2: PreferenceManager2,
    predictionModeAdapter: PreferenceAdapter<PredictionMode>,
    weightedUsageStatsAdapter: PreferenceAdapter<Boolean>,
) {
    val resources = LocalResources.current
    val appOps = remember { context.getSystemService(AppOpsManager::class.java) }
    fun checkPermission() = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    ) == AppOpsManager.MODE_ALLOWED

    val hasUsageStatsPermission = remember { mutableStateOf(checkPermission()) }
    val resumed = lifecycleState().isAtLeast(Lifecycle.State.RESUMED)

    if (resumed) {
        DisposableEffect(context) {
            hasUsageStatsPermission.value = checkPermission()
            onDispose {}
        }
    }

    val predictionModeEntries = rememberPredictionModeEntries(context)
    val dismissedPredictionAppsCount = rememberDismissedPredictionAppsCount(context)
    val dismissedPredictionAppsSubtitle = resources.getQuantityString(
        R.plurals.apps_count,
        dismissedPredictionAppsCount,
        dismissedPredictionAppsCount,
    )

    val canUseAppPrediction = Utilities.ATLEAST_Q

    PreferenceGroup(
        heading = stringResource(R.string.app_predictions_label),
    ) {
        ListPreference(
            adapter = predictionModeAdapter,
            entries = predictionModeEntries,
            label = stringResource(R.string.prediction_mode_label),
            description = if (canUseAppPrediction) null else stringResource(R.string.app_predictions_disable_reason_pre_q_description),
            enabled = canUseAppPrediction,
        )
        when (predictionModeAdapter.state.value) {
            SystemPredictor -> SystemSuggestionsPreference()

            LawnchairPredictor -> LawnchairPredictionSettings(
                weightedUsageStatsAdapter = weightedUsageStatsAdapter,
                hasUsageStatsPermission = hasUsageStatsPermission.value,
                dismissedPredictionAppsSubtitle = dismissedPredictionAppsSubtitle,
            )

            NoPredictor -> Unit
        }
    }
}

@Composable
private fun LawnchairPredictionSettings(
    weightedUsageStatsAdapter: PreferenceAdapter<Boolean>,
    hasUsageStatsPermission: Boolean,
    dismissedPredictionAppsSubtitle: String,
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SwitchPreference(
        checked = weightedUsageStatsAdapter.state.value && hasUsageStatsPermission,
        onCheckedChange = { newValue ->
            if (hasUsageStatsPermission) {
                weightedUsageStatsAdapter.onChange(newValue)
            } else {
                showPermissionDialog = true
            }
        },
        label = stringResource(R.string.prediction_weighted_usage_stats_label),
        description = stringResource(R.string.prediction_weighted_usage_stats_description),
    )
    if (showPermissionDialog) {
        PermissionDialog(
            title = stringResource(id = R.string.missing_usage_access_label),
            text = stringResource(id = R.string.missing_usage_access_desc, stringResource(id = R.string.derived_app_name)),
            isPermanentlyDenied = true,
            onConfirm = {},
            onDismiss = { showPermissionDialog = false },
            onGoToSettings = {
                showPermissionDialog = false
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {}
                }
            },
        )
    }
    NavigationActionPreference(
        label = stringResource(R.string.dismissed_prediction_apps_label),
        destination = DismissedPredictionApps,
        subtitle = dismissedPredictionAppsSubtitle,
    )
}

@Composable
private fun rememberPredictionModeEntries(context: Context): List<ListPreferenceEntry<PredictionMode>> {
    return remember(context) {
        PredictionMode.values()
            .filter { it.isAvailable(context) }
            .map { mode ->
                ListPreferenceEntry(
                    value = mode,
                    label = { stringResource(mode.nameResourceId) },
                )
            }
    }
}

@Composable
private fun rememberDismissedPredictionAppsCount(context: Context): Int {
    val dismissedAppsStore = remember {
        LawnchairPredictionManager.getInstance(context).dismissedAppsStore
    }
    var dismissedPredictionAppsCount by remember {
        mutableIntStateOf(dismissedAppsStore.getEntries().size)
    }

    LaunchedEffect(dismissedAppsStore) {
        dismissedAppsStore.preference.get().collect {
            dismissedPredictionAppsCount = dismissedAppsStore.getEntries().size
        }
    }

    return dismissedPredictionAppsCount
}

@SuppressLint("WrongConstant")
@Composable
fun SystemSuggestionsPreference() {
    val context = LocalContext.current
    val intent = Intent("android.settings.ACTION_CONTENT_SUGGESTIONS_SETTINGS")
    val hasPkgUsagePermission = context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    val canResolveToSuggestionPreference = context.packageManager.resolveActivity(intent, 0) != null
    val suggestionSettingsAvailable = hasPkgUsagePermission && canResolveToSuggestionPreference

    if (suggestionSettingsAvailable) {
        ClickablePreference(
            label = stringResource(id = R.string.suggestion_pref_screen_title),
            onClick = {
                context.startActivity(intent)
            },
        )
    }
}


@Composable
private fun <T> predictionModeBackedAdapter(
    modePreference: PrefEntry<T>,
    nativeAdapter: PreferenceAdapter<T>,
): PreferenceAdapter<T> {
    val modeAdapter = androidx.compose.runtime.key(modePreference.key) { modePreference.getAdapter() }
    return remember(modePreference.key, modeAdapter, nativeAdapter) {
        object : PreferenceAdapter<T> {
            override val state = modeAdapter.state
            override fun onChange(newValue: T) {
                modeAdapter.onChange(newValue)
                nativeAdapter.onChange(newValue)
            }
        }
    }
}

@Composable
private fun <M, N> predictionModeBackedAdapter(
    modePreference: PrefEntry<M>,
    nativeAdapter: PreferenceAdapter<N>,
    readNative: (M) -> N,
    writeMode: (N) -> M,
): PreferenceAdapter<N> {
    val modeAdapter = androidx.compose.runtime.key(modePreference.key) { modePreference.getAdapter() }
    return remember(modePreference.key, modeAdapter, nativeAdapter) {
        object : PreferenceAdapter<N> {
            override val state = androidx.compose.runtime.derivedStateOf { readNative(modeAdapter.state.value) }
            override fun onChange(newValue: N) {
                modeAdapter.onChange(writeMode(newValue))
                nativeAdapter.onChange(newValue)
            }
        }
    }
}
