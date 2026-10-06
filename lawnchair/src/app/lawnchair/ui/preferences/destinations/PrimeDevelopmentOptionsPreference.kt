package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
    val navController = LocalNavController.current
    PreferenceLayout(
        label = stringResource(R.string.prime_development_options),
        backArrowVisible = true,
    ) {
        PreferenceGroup {
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
