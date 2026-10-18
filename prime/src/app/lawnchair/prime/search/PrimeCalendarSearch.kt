package app.lawnchair.prime.search

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract

/** Search calendar events and their reminders from the Android calendar provider. */
object PrimeCalendarSearch {
    data class Result(val title: String, val detail: String, val uri: Uri)
    fun events(context: Context, query: String): List<Result> {
        if (query.isBlank() || !PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CALENDAR)) return emptyList()
        val results = mutableListOf<Result>()
        val columns = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.CALENDAR_ID)
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, columns,
                "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ? OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ?",
                Array(3) { "%${query.trim()}%" },
                "${CalendarContract.Events.DTSTART} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < 25) {
                    val id = cursor.getLong(0)
                    if (!PrimeSourceSelection.isSelected(context, "calendars", cursor.getLong(5).toString())) continue
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
