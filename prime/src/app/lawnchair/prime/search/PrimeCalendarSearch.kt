package app.lawnchair.prime.search

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.text.Normalizer
import java.util.Locale

/** Search calendar events and their reminders from the Android calendar provider. */
object PrimeCalendarSearch {
    data class Result(val title: String, val detail: String, val uri: Uri)
    fun futureOnly(context: Context): Boolean =
        context.getSharedPreferences("prime_calendar_search", Context.MODE_PRIVATE).getBoolean("future_only", true)
    fun setFutureOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences("prime_calendar_search", Context.MODE_PRIVATE)
            .edit().putBoolean("future_only", enabled).apply()
    }
    fun futureOnly(context: Context, surface: PrimeSearchResultPreferences.Surface): Boolean =
        if (surface == PrimeSearchResultPreferences.Surface.PRIME_SEARCH) futureOnly(context)
        else context.getSharedPreferences(surface.store, Context.MODE_PRIVATE).getBoolean("calendar_future_only", true)

    fun setFutureOnly(context: Context, enabled: Boolean, surface: PrimeSearchResultPreferences.Surface) {
        if (surface == PrimeSearchResultPreferences.Surface.PRIME_SEARCH) setFutureOnly(context, enabled)
        else context.getSharedPreferences(surface.store, Context.MODE_PRIVATE).edit().putBoolean("calendar_future_only", enabled).apply()
    }

    fun events(context: Context, query: String): List<Result> =
        events(context, query, PrimeSearchResultPreferences.Surface.PRIME_SEARCH)

    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun events(context: Context, query: String, surface: PrimeSearchResultPreferences.Surface): List<Result> {
        if (query.isBlank() || !PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CALENDAR)) return emptyList()
        val results = mutableListOf<Result>()
        val columns = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.CALENDAR_ID)
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, columns,
                if (futureOnly(context, surface)) "${CalendarContract.Events.DTSTART} >= ?" else null,
                if (futureOnly(context, surface)) arrayOf(System.currentTimeMillis().toString()) else null,
                "${CalendarContract.Events.DTSTART} DESC"
            )?.use { cursor ->
                val words = fold(query.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
                var scanned = 0
                while (cursor.moveToNext() && results.size < 25 && scanned++ < 5000) {
                    val titleText = cursor.getString(1).orEmpty()
                    val searchable = fold(titleText + " " + cursor.getString(2).orEmpty() + " " + cursor.getString(4).orEmpty())
                    if (!words.all { it in searchable }) continue
                    val id = cursor.getLong(0)
                    if (!PrimeSourceSelection.isSelected(context, "calendars", cursor.getLong(5).toString(), surface)) continue
                    val title = cursor.getString(1).orEmpty()
                    val description = cursor.getString(2).orEmpty()
                    val start = cursor.getLong(3)
                    val location = cursor.getString(4).orEmpty()
                    val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(start))
                    results.add(Result(title.ifBlank { "Événement" },
                        listOf(date, location, description.take(120)).filter { it.isNotBlank() }.joinToString(" · "),
                        android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)))
                }
            }
        }
        return results
    }

    fun reminders(context: Context, query: String): List<Result> {
        if (query.isBlank() || !PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CALENDAR)) return emptyList()
        val results = mutableListOf<Result>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
                null, null, null
            )?.use { cursor ->
                val ids = mutableSetOf<Long>()
                while (cursor.moveToNext() && ids.size < 2000) ids.add(cursor.getLong(0))
                if (ids.isNotEmpty()) {
                    val columns = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE,
                        CalendarContract.Events.DTSTART, CalendarContract.Events.CALENDAR_ID)
                    context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI, columns,
                        "${CalendarContract.Events.TITLE} LIKE ?",
                        arrayOf("%${query.trim()}%"),
                        "${CalendarContract.Events.DTSTART} DESC"
                    )?.use { events ->
                        while (events.moveToNext() && results.size < 20) {
                            val id = events.getLong(0)
                            if (id !in ids || !PrimeSourceSelection.isSelected(context, "calendars", events.getLong(3).toString())) continue
                            val title = events.getString(1).orEmpty()
                            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(events.getLong(2)))
                            results.add(Result(title.ifBlank { "Rappel" }, date,
                                android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)))
                        }
                    }
                }
            }
        }
        return results
    }
}
