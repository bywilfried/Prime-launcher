package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        // Use the public accessibility global action directly on Android 11+.
        // Reflection on hidden StatusBarManager APIs can fail silently.
        GestureWithAccessibilityHandler.onTrigger(
            launcher,
            R.string.notifications_fallback_a11y_hint,
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        )
    }
}
