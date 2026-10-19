package app.lawnchair.smartspace

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import app.lawnchair.smartspace.provider.SmartspaceProvider
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SmartspaceAppWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        observe(context.applicationContext)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        if (ids.isNotEmpty()) observe(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        observer?.cancel()
        observer = null
        super.onDisabled(context)
    }

    companion object {
        @JvmField val componentName =
            ComponentName(BuildConfig.APPLICATION_ID, SmartspaceAppWidgetProvider::class.java.name)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var observer: Job? = null

        private fun observe(context: Context) {
            if (observer?.isActive == true) return
            observer = scope.launch {
                SmartspaceProvider.INSTANCE.get(context).targets.collectLatest { targets ->
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(componentName)
                    if (ids.isEmpty()) return@collectLatest

                    val card = targets
                        .filter { it.headerAction != null && it.id != "smartspaceSetup" }
                        .maxByOrNull { it.score }
                        ?.headerAction
                    val info = listOfNotNull(
                        card?.title?.toString()?.takeIf { it.isNotBlank() },
                        card?.subtitle?.toString()?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")

                    val views = RemoteViews(context.packageName, R.layout.smartspace_widget_placeholder)
                    views.setTextViewText(R.id.smartspace_live_info, info)
                    views.setViewVisibility(
                        R.id.smartspace_live_info,
                        if (info.isEmpty()) View.GONE else View.VISIBLE,
                    )
                    manager.updateAppWidget(ids, views)
                }
            }
        }
    }
}
