package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.android.msdl.data.model.FeedbackLevel
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
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

    val sliders = listOf(
        prefs.primeHapticWorkspaceLongPress, prefs.primeHapticDragStart,
        prefs.primeHapticIconDrag, prefs.primeHapticDrawerThreshold,
        prefs.primeHapticDrawerTap, prefs.primeHapticReorderStart,
        prefs.primeHapticReorderMove, prefs.primeHapticReorderEnd,
        prefs.primeHapticReorderCancel, prefs.primeHapticOtherEvents,
        prefs.primeHapticSlider, prefs.primeHapticTaskbar,
        prefs.primeHapticContextualSearch,
    )
    val levelAdapter = prefs.vibrationFeedbackLevel.getAdapter()
    val selectedLevel = levelAdapter.state.value
    val presetValue = when (selectedLevel) {
        FeedbackLevel.NO_FEEDBACK.ordinal -> 0
        FeedbackLevel.MINIMAL.ordinal -> 50
        FeedbackLevel.DEFAULT.ordinal -> 100
        FeedbackLevel.EXPRESSIVE.ordinal -> 150
        else -> null
    }
    val isCustom = presetValue == null || sliders.any { it.get() != presetValue }

    PreferenceLayout(
        label = stringResource(R.string.prime_haptic_tuning),
        backArrowVisible = true,
    ) {
        PreferenceGroup(heading = stringResource(R.string.vibration_feedback_level_label)) {
            ListPreference(
                value = if (isCustom) -1 else selectedLevel,
                onValueChange = { level ->
                    if (level >= 0) {
                        levelAdapter.onChange(level)
                        val value = when (level) {
                            FeedbackLevel.NO_FEEDBACK.ordinal -> 0
                            FeedbackLevel.MINIMAL.ordinal -> 50
                            FeedbackLevel.DEFAULT.ordinal -> 100
                            FeedbackLevel.EXPRESSIVE.ordinal -> 150
                            else -> 100
                        }
                        sliders.forEach { it.set(value) }
                    }
                },
                entries = listOf(
                    ListPreferenceEntry(FeedbackLevel.NO_FEEDBACK.ordinal) { stringResource(R.string.vibration_feedback_no_feedback_choice) },
                    ListPreferenceEntry(FeedbackLevel.MINIMAL.ordinal) { stringResource(R.string.vibration_feedback_minimal_choice) },
                    ListPreferenceEntry(FeedbackLevel.DEFAULT.ordinal) { stringResource(R.string.vibration_feedback_default_choice) },
                    ListPreferenceEntry(FeedbackLevel.EXPRESSIVE.ordinal) { stringResource(R.string.vibration_feedback_expressive_choice) },
                    ListPreferenceEntry(-1) { stringResource(R.string.prime_haptic_custom) },
                ),
                label = if (isCustom) stringResource(R.string.prime_haptic_custom) else stringResource(R.string.vibration_feedback_level_label),
            )

        }
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
        PreferenceGroup(heading = stringResource(R.string.prime_haptic_other_group)) {
            HapticSlider(stringResource(R.string.prime_haptic_other_events), prefs.primeHapticOtherEvents)
            HapticSlider(stringResource(R.string.prime_haptic_slider_label), prefs.primeHapticSlider)
            HapticSlider(stringResource(R.string.prime_haptic_taskbar_label), prefs.primeHapticTaskbar)
            HapticSlider(stringResource(R.string.prime_haptic_contextual_label), prefs.primeHapticContextualSearch)
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
                    prefs.primeHapticOtherEvents.set(100)
                    prefs.primeHapticSlider.set(100)
                    prefs.primeHapticTaskbar.set(100)
                    prefs.primeHapticContextualSearch.set(100)
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
        valueRange = 0..150,
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
