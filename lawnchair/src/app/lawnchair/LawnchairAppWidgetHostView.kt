package app.lawnchair

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RemoteViews
import app.lawnchair.smartspace.SmartspaceAppWidgetProvider
import com.android.launcher3.PrimeDebugLog
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherAppWidgetHostView

class LawnchairAppWidgetHostView @JvmOverloads constructor(
    context: Context,
    private var previewMode: Boolean = false,
) : LauncherAppWidgetHostView(context) {

    private var customView: ViewGroup? = null

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        PrimeDebugLog.d(
            "PrimeWidget",
            "setAppWidget id=$appWidgetId provider=${info.provider.flattenToShortString()} preview=$previewMode",
        )
        inflateCustomView(info)
        super.setAppWidget(appWidgetId, info)
    }

    fun disablePreviewMode() {
        previewMode = false
        inflateCustomView(appWidgetInfo)
    }

    private fun inflateCustomView(info: AppWidgetProviderInfo) {
        customView = inflateCustomView(context, info, previewMode)
        if (customView == null) {
            return
        }
        customView!!.setOnLongClickListener(this)
        removeAllViews()
        addView(customView, MATCH_PARENT, MATCH_PARENT)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        PrimeDebugLog.d(
            "PrimeWidget",
            "updateAppWidget id=$appWidgetId provider=${appWidgetInfo?.provider?.flattenToShortString()} remoteViews=${remoteViews != null} custom=${customView != null}",
        )
        if (customView != null) return
        super.updateAppWidget(remoteViews)
    }

    override fun getDefaultView(): View {
        PrimeDebugLog.d(
            "PrimeWidget",
            "getDefaultView id=$appWidgetId provider=${appWidgetInfo?.provider?.flattenToShortString()} custom=${customView != null}",
        )
        if (customView != null) return getEmptyView()
        return super.getDefaultView()
    }

    override fun getErrorView(): View {
        PrimeDebugLog.d(
            "PrimeWidget",
            "getErrorView id=$appWidgetId provider=${appWidgetInfo?.provider?.flattenToShortString()} custom=${customView != null}",
        )
        if (customView != null) return getEmptyView()
        return super.getErrorView()
    }

    private fun getEmptyView(): View {
        return View(context)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.smartspace_widget,
        )

        @JvmStatic
        fun inflateCustomView(context: Context, info: AppWidgetProviderInfo, previewMode: Boolean): ViewGroup? {
            val layoutId = customLayouts[info.provider] ?: return null

            val inflationContext = if (previewMode) Themes.createWidgetPreviewContext(context) else context
            return LayoutInflater.from(inflationContext)
                .inflate(layoutId, null, false) as ViewGroup
        }
    }
}
