package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.getAdapter
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.gestures.handlers.SleepMode
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.GestureHandlerPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun GesturePreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager2()
    val context = LocalContext.current
    val pageCount = context.getSharedPreferences("prime_workspace_gestures", Context.MODE_PRIVATE)
        .getInt("page_count", 1)
    val multiplePages = pageCount > 1
    val feedEnabled = prefs.enableFeed.getAdapter().state.value
    PreferenceLayout(
        label = stringResource(id = R.string.gestures_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            GestureHandlerPreference(
                adapter = prefs.doubleTapGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_double_tap),
            )
            GestureHandlerPreference(
                adapter = prefs.swipeUpGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_swipe_up),
            )
            GestureHandlerPreference(
                adapter = prefs.swipeDownGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_swipe_down),
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeUpGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_two_finger_swipe_up),
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeDownGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_two_finger_swipe_down),
            )
            GestureHandlerPreference(
                adapter = prefs.swipeLeftGestureHandler.getAdapter(),
                label = "Balayage vers la gauche",
                enabled = !multiplePages,
                disabledReason = if (multiplePages) "Indisponible avec plusieurs pages d’accueil" else null,
            )
            GestureHandlerPreference(
                adapter = prefs.swipeRightGestureHandler.getAdapter(),
                label = "Balayage vers la droite",
                enabled = !multiplePages && !feedEnabled,
                disabledReason = when {
                    multiplePages -> "Indisponible avec plusieurs pages d’accueil"
                    feedEnabled -> "Indisponible lorsque le panneau de flux est activé"
                    else -> null
                },
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeLeftGestureHandler.getAdapter(),
                label = "Deux doigts vers la gauche",
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeRightGestureHandler.getAdapter(),
                label = "Deux doigts vers la droite",
            )
            GestureHandlerPreference(
                adapter = prefs.pinchInGestureHandler.getAdapter(),
                label = "Pincer deux doigts",
            )
            GestureHandlerPreference(
                adapter = prefs.pinchOutGestureHandler.getAdapter(),
                label = "Écarter deux doigts",
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerDoubleTapGestureHandler.getAdapter(),
                label = "Double appui à deux doigts",
            )
            GestureHandlerPreference(
                adapter = prefs.homePressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_home_tap),
            )
            GestureHandlerPreference(
                adapter = prefs.backPressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_back_tap),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.sleep_mode_label)) {
            ListPreference(
                adapter = prefs.sleepMode.getAdapter(),
                entries = SleepMode.entries(),
                label = stringResource(id = R.string.sleep_mode_label),
            )
        }
    }
}
