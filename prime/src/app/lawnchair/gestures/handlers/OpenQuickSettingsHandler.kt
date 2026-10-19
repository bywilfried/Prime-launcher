package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenQuickSettingsHandler(
    context: Context,
) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        // Use the public accessibility global action directly on Android 11+.
        // Reflection on hidden StatusBarManager APIs can fail silently.
        GestureWithAccessibilityHandler.onTrigger(
            launcher,
            R.string.quick_settings_a11y_hint,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        )
    }
}    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        // On Android 11, use the public accessibility action first. Reflection
        // against StatusBarManager can fail silently on Samsung firmware.
        val app = launcher.lawnchairApp
        if (app.isAccessibilityServiceBound() &&
            app.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        ) {
            return
        }
        GestureWithAccessibilityHandler.onTrigger(
            launcher,
            R.string.quick_settings_a11y_hint,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        )
    }
}
