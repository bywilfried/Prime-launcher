package app.lawnchair.prime.drawer

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo

/** Apply Prime's existing per-app name and icon overrides to virtual drawer shortcuts. */
object PrimeWebShortcutCustomization {
    @JvmStatic
    fun apply(context: Context, app: AppInfo) {
        val key = app.toComponentKey()
        val customTitle = PreferenceManager.getInstance(context).customAppName[key]
        if (!customTitle.isNullOrBlank()) {
            app.title = customTitle
            app.sectionName = customTitle.take(1).uppercase()
        }
        val override = IconOverrideRepository.INSTANCE.get(context).overridesMap[key] ?: return
        try {
            val drawable = IconPackProvider.INSTANCE.get(context)
                .getDrawable(override.toIconEntry(), context.resources.displayMetrics.densityDpi, app.user)
                ?: return
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            app.bitmap = BitmapInfo.of(drawable.toBitmap(size, size), Color.GRAY)
        } catch (_: Exception) {
            // Keep the browser-provided icon when an icon pack is unavailable.
        }
    }
}
