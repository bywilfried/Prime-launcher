package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/**
 * Widget settings formerly displayed at the bottom of Home Screen preferences.
 * Existing widget preferences keep their original DataStore keys.
 */
@Composable
fun WidgetsPreferences(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val pickerPrefs = remember(context) {
        context.getSharedPreferences("prime_widget_picker_preferences", Context.MODE_PRIVATE)
    }
    var showSuggestions by remember(pickerPrefs) {
        mutableStateOf(pickerPrefs.getBoolean("show_widget_recommendations", true))
    }

    PreferenceLayout(
        label = stringResource(id = R.string.widgets_settings_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            SwitchPreference(
                checked = showSuggestions,
                onCheckedChange = { enabled ->
                    pickerPrefs.edit().putBoolean("show_widget_recommendations", enabled).apply()
                    showSuggestions = enabled
                },
                label = stringResource(id = R.string.show_widget_suggestions_label),
                description = stringResource(id = R.string.show_widget_suggestions_description),
            )
            SwitchPreference(
                adapter = prefs2.roundedWidgets.getAdapter(),
                label = stringResource(id = R.string.force_rounded_widgets),
            )
            SwitchPreference(
                adapter = prefs2.allowWidgetOverlap.getAdapter(),
                label = stringResource(id = R.string.allow_widget_overlap),
            )
            SwitchPreference(
                adapter = prefs2.widgetUnlimitedSize.getAdapter(),
                label = stringResource(id = R.string.widget_unlimited_size_label),
                description = stringResource(id = R.string.widget_unlimited_size_description),
            )
            SwitchPreference(
                adapter = prefs2.forceWidgetResize.getAdapter(),
                label = stringResource(id = R.string.force_widget_resize_label),
                description = stringResource(id = R.string.force_widget_resize_description),
            )
            SliderPreference(
                label = stringResource(id = R.string.widget_padding_label),
                adapter = prefs2.widgetPaddingFactor.getAdapter(),
                step = 0.05f,
                valueRange = 0F..2F,
                showAsPercentage = true,
            )
        }
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(id = R.string.configure_prime_preview_widget),
                description = stringResource(id = R.string.smartspace_widget_description),
                destination = Smartspace,
            )
        }
    }
}
