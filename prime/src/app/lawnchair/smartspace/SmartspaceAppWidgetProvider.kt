package app.lawnchair.smartspace

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.lawnchair.smartspace.model.SmartspaceTarget
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id == -1) return
            val prefs = context.getSharedPreferences("smartspace_widget_pages", Context.MODE_PRIVATE)
            val current = prefs.getInt("page_$id", 0)
            prefs.edit().putInt("page_$id", current + 1).apply()
            render(context.applicationContext)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("smartspace_widget_pages", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove("page_$it") }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        observer?.cancel()
        observer = null
        super.onDisabled(context)
    }

    companion object {
        private const val ACTION_NEXT = "app.lawnchair.smartspace.NEXT_WIDGET_CARD"
        @JvmField val componentName =
            ComponentName(BuildConfig.APPLICATION_ID, SmartspaceAppWidgetProvider::class.java.name)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var observer: Job? = null
        private var latestTargets: List<SmartspaceTarget> = emptyList()

        private fun observe(context: Context) {
            if (observer?.isActive == true) {
                render(context)
                return
            }
            observer = scope.launch {
                SmartspaceProvider.INSTANCE.get(context).targets.collectLatest { targets ->
                    latestTargets = targets
                    render(context)
                }
            }
        }

        private fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(componentName)
            val cards = latestTargets
                .filter { it.headerAction != null && it.id != "smartspaceSetup" }
                .sortedByDescending { it.score }
            val prefs = context.getSharedPreferences("smartspace_widget_pages", Context.MODE_PRIVATE)

            ids.forEach { id ->
                val page = if (cards.isEmpty()) 0 else
                    Math.floorMod(prefs.getInt("page_$id", 0), cards.size)
                val action = cards.getOrNull(page)?.headerAction
                val info = listOfNotNull(
                    action?.title?.toString()?.takeIf { it.isNotBlank() },
                    action?.subtitle?.toString()?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                val views = RemoteViews(context.packageName, R.layout.smartspace_widget_placeholder)
                views.setTextViewText(R.id.smartspace_live_info, info)
                views.setViewVisibility(R.id.smartspace_live_info,
                    if (info.isBlank()) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.smartspace_live_icon,
                    if (action?.icon == null) View.GONE else View.VISIBLE)
                action?.icon?.let { views.setImageViewIcon(R.id.smartspace_live_icon, it) }
                views.setViewVisibility(R.id.smartspace_next,
                    if (cards.size > 1) View.VISIBLE else View.GONE)
                val next = Intent(context, SmartspaceAppWidgetProvider::class.java).apply {
                    this.action = ACTION_NEXT
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                val pending = PendingIntent.getBroadcast(context, id, next,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.smartspace_next, pending)
                action?.pendingIntent?.let {
                    views.setOnClickPendingIntent(R.id.smartspace_live_info, it)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
