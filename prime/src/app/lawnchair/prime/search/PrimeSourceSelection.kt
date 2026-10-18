package app.lawnchair.prime.search

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract

/** Per-source selection. Null stored set means all available sources. */
object PrimeSourceSelection {
    data class Source(val id: String, val label: String)

    fun calendars(context: Context): List<Source> {
        if (!PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CALENDAR)) return emptyList()
        val result = mutableListOf<Source>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME),
                null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0).toString()
                    val name = cursor.getString(1).orEmpty().ifBlank { "Agenda" }
                    val account = cursor.getString(2).orEmpty()
                    result.add(Source(id, if (account.isBlank()) name else "$name · $account"))
                }
            }
        }
        return result.distinctBy { it.id }
    }

    fun contactAccounts(context: Context): List<Source> {
        if (!PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CONTACTS)) return emptyList()
        val result = mutableListOf<Source>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0).orEmpty()
                    val name = cursor.getString(1).orEmpty()
                    val id = "$type|$name"
                    if (result.none { it.id == id }) {
                        result.add(Source(id, if (name.isBlank()) "Contacts locaux" else "$name · $type"))
                    }
                }
            }
        }
        return result.sortedBy { it.label }
    }

    private fun key(kind: String) = "selected_$kind"
    private fun prefs(context: Context) =
        context.getSharedPreferences("prime_search_sources", Context.MODE_PRIVATE)

    fun selected(context: Context, kind: String): Set<String>? =
        prefs(context).getStringSet(key(kind), null)?.toSet()

    fun isSelected(context: Context, kind: String, id: String): Boolean =
        selected(context, kind)?.contains(id) ?: true

    fun setSelected(context: Context, kind: String, id: String, enabled: Boolean, available: List<Source>) {
        val current = (selected(context, kind) ?: available.map { it.id }.toSet()).toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        prefs(context).edit().putStringSet(key(kind), current).apply()
    }

    fun reset(context: Context, kind: String) {
        prefs(context).edit().remove(key(kind)).apply()
    }
}
