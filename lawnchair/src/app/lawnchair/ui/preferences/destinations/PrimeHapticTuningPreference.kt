package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun PrimeHapticTuningPreference() {
    val prefs = preferenceManager()

    PreferenceLayout(
        label = stringResource(R.string.prime_haptic_tuning),
        backArrowVisible = true,
    ) {
        PreferenceGroup(heading = stringResource(R.string.prime_haptic_workspace)) {
            HapticSlider(stringResource(R.string.prime_haptic_workspace_long_press), prefs.primeHapticWorkspaceLongPress)
            HapticSlider(stringResource(R.string.prime_haptic_drag_start), prefs.primeHapticDragStart)
            HapticSlider(stringResource(R.string.prime_haptic_icon_drag), prefs.primeHapticIconDrag)
        }
        PreferenceGroup(heading = stringResource(R.string.prime_haptic_drawer)) {
            HapticSlider(stringResource(R.string.prime_haptic_drawer_threshold), prefs.primeHapticDrawerThreshold)
            HapticSlider(stringResource(R.string.prime_haptic_drawer_tap), prefs.primeHapticDrawerTap)
        }
        PreferenceGroup(heading = stringResource(R.string.prime_haptic_reorder)) {
            HapticSlider(stringResource(R.string.prime_haptic_reorder_start), prefs.primeHapticReorderStart)
            HapticSlider(stringResource(R.string.prime_haptic_reorder_move), prefs.primeHapticReorderMove)
            HapticSlider(stringResource(R.string.prime_haptic_reorder_end), prefs.primeHapticReorderEnd)
            HapticSlider(stringResource(R.string.prime_haptic_reorder_cancel), prefs.primeHapticReorderCancel)
        }
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.prime_haptic_reset_all),
                onClick = {
                    prefs.primeHapticWorkspaceLongPress.set(100)
                    prefs.primeHapticDragStart.set(100)
                    prefs.primeHapticIconDrag.set(100)
                    prefs.primeHapticDrawerThreshold.set(100)
                    prefs.primeHapticDrawerTap.set(100)
                    prefs.primeHapticReorderStart.set(100)
                    prefs.primeHapticReorderMove.set(100)
                    prefs.primeHapticReorderEnd.set(100)
                    prefs.primeHapticReorderCancel.set(100)
                },
            )
        }
    }
}

@Composable
private fun HapticSlider(label: String, pref: BasePreferenceManager.IntPref) {
    SliderPreference(
        label = label,
        adapter = pref.getAdapter(),
        valueRange = 0..100,
        step = 1,
        showUnit = "%",
    )
    if (pref.get() != 100) {
        ClickablePreference(
            label = stringResource(R.string.prime_haptic_reset),
            onClick = { pref.set(100) },
        )
    }
}
