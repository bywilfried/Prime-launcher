package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.PrimeDiagnosticLogs
import app.lawnchair.ui.preferences.navigation.PrimeHapticTuning
import com.android.launcher3.R

@Composable
fun PrimeDevelopmentOptionsPreference() {
    val prefs = preferenceManager()
    val navController = LocalNavController.current
    val extendedGrid = prefs.workspaceIncreaseMaxGridSize.getAdapter()
    val gridExceedsDefault = prefs.workspaceColumns.get() > 10 || prefs.workspaceRows.get() > 10

    PreferenceLayout(
        label = stringResource(R.string.prime_development_options),
        backArrowVisible = true,
    ) {
        PreferenceGroup {
            SwitchPreference(
                adapter = prefs.primeShowEmptyFolders.getAdapter(),
                label = stringResource(R.string.prime_show_empty_folders),
                description = stringResource(R.string.prime_show_empty_folders_description),
            )
            SwitchPreference(
                checked = extendedGrid.state.value,
                onCheckedChange = { enabled ->
                    if (enabled || !gridExceedsDefault) extendedGrid.onChange(enabled)
                },
                label = stringResource(R.string.workspace_increase_max_grid_size_label),
                description = if (gridExceedsDefault) {
                    stringResource(R.string.prime_extended_grid_active_description)
                } else {
                    stringResource(R.string.workspace_increase_max_grid_size_description)
                },
                enabled = !gridExceedsDefault || !extendedGrid.state.value,
            )
            ClickablePreference(
                label = stringResource(R.string.prime_diagnostic_logs),
                subtitle = stringResource(R.string.prime_diagnostic_logs_description),
                onClick = { navController.navigate(PrimeDiagnosticLogs) },
            )
            ClickablePreference(
                label = stringResource(R.string.prime_haptic_tuning),
                subtitle = stringResource(R.string.prime_haptic_tuning_description),
                onClick = { navController.navigate(PrimeHapticTuning) },
            )
        }
    }
}
