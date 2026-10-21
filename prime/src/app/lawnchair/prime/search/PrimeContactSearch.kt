package app.lawnchair.prime.search

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object PrimeContactSearch {
    data class Result(val name: String, val uri: Uri)
    fun search(context: Context, query: String): List<Result> =
        search(context, query, PrimeSearchResultPreferences.Surface.PRIME_SEARCH)

    fun search(context: Context, query: String, surface: PrimeSearchResultPreferences.Surface): List<Result> {
        if (query.isBlank() || !PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.CONTACTS)) return emptyList()
        val selected = PrimeSourceSelection.selected(context, "contacts", surface)
        val results = linkedMapOf<Long, Result>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME),
                "${ContactsContract.RawContacts.DELETED}=0", null, null
            )?.use { raw ->
                val allowedIds = mutableSetOf<Long>()
                while (raw.moveToNext()) {
                    val id = raw.getLong(0)
                    val source = raw.getString(1).orEmpty() + "|" + raw.getString(2).orEmpty()
                    if (selected == null || source in selected) allowedIds.add(id)
                }
                if (allowedIds.isNotEmpty()) {
                    context.contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                        arrayOf("%${query.trim()}%"),
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
                    )?.use { cursor ->
                        while (cursor.moveToNext() && results.size < 30) {
                            val id = cursor.getLong(0)
                            if (id !in allowedIds) continue
                            results[id] = Result(cursor.getString(1).orEmpty(),
                                android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id))
                        }
                    }
                }
            }
        }
        return results.values.toList()
    }
}
