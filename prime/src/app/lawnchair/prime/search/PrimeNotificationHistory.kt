package app.lawnchair.prime.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

/** Local notification history; never uploads notification content. */
object PrimeNotificationHistory {
    private class Store(context: Context) : SQLiteOpenHelper(context, "prime_notification_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE notifications (key TEXT PRIMARY KEY, app TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, posted INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX notifications_posted ON notifications(posted)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    data class Entry(val app: String, val title: String, val body: String, val posted: Long)

    fun isGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val component = ComponentName(context, PrimeNotificationListener::class.java)
        if (androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)) return true
        return enabled.split(':').any { entry ->
            val granted = ComponentName.unflattenFromString(entry.trim())
            granted == component ||
                (granted?.packageName == context.packageName &&
                    granted.className == PrimeNotificationListener::class.java.name)
        }
    }

    fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun save(context: Context, sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        val app = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)
        Store(context).use { helper ->
            val values = android.content.ContentValues().apply {
                put("key", sbn.key)
                put("app", app)
                put("title", title)
                put("body", body)
                put("posted", sbn.postTime)
            }
            helper.writableDatabase.insertWithOnConflict(
                "notifications", null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
        }
        prune(context)
    }

    fun prune(context: Context) {
        val primeMonths = PrimeSearchResultPreferences.notificationRetention(context).months
        val drawerMonths = PrimeSearchResultPreferences.notificationRetention(
            context, PrimeSearchResultPreferences.Surface.DRAWER
        ).months
        val months = if (primeMonths == 0 || drawerMonths == 0) 0 else maxOf(primeMonths, drawerMonths)
        if (months == 0) return
        val cutoff = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -months) }.timeInMillis
        Store(context).use { helper ->
            helper.writableDatabase.delete("notifications", "posted < ?", arrayOf(cutoff.toString()))
        }
    }

    fun search(context: Context, query: String, limit: Int = 30): List<Entry> {
        if (query.isBlank()) return emptyList()
        prune(context)
        val pattern = "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val matches = mutableListOf<Entry>()
        Store(context).use { helper ->
            helper.readableDatabase.rawQuery(
                "SELECT app,title,body,posted FROM notifications WHERE app LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' ORDER BY posted DESC LIMIT ?",
                arrayOf(pattern, pattern, pattern, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    matches.add(Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
                }
            }
        }
        return matches
    }

    fun searchForDrawer(context: Context, query: String, limit: Int = 30): List<Entry> {
        val months = PrimeSearchResultPreferences.notificationRetention(
            context, PrimeSearchResultPreferences.Surface.DRAWER
        ).months
        val cutoff = if (months == 0) Long.MIN_VALUE else
            java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -months) }.timeInMillis
        return search(context, query, limit).filter { it.posted >= cutoff }
    }

    fun clear(context: Context) {
        Store(context).use { it.writableDatabase.delete("notifications", null, null) }
    }
}

class PrimeNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        // Capture notifications already present when permission is granted or
        // the listener reconnects; onNotificationPosted only sees future posts.
        if (PrimeSearchResultPreferences.enabled(this, PrimeSearchResultPreferences.Category.NOTIFICATIONS) ||
            PrimeSearchResultPreferences.enabled(this, PrimeSearchResultPreferences.Category.NOTIFICATIONS,
                PrimeSearchResultPreferences.Surface.DRAWER)) {
            runCatching {
                activeNotifications?.forEach { PrimeNotificationHistory.save(this, it) }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (PrimeSearchResultPreferences.enabled(this, PrimeSearchResultPreferences.Category.NOTIFICATIONS) ||
            PrimeSearchResultPreferences.enabled(this, PrimeSearchResultPreferences.Category.NOTIFICATIONS,
                PrimeSearchResultPreferences.Surface.DRAWER)) {
            runCatching { PrimeNotificationHistory.save(this, sbn) }
        }
    }
}
