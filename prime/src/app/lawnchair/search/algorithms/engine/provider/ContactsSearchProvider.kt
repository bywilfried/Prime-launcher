package app.lawnchair.search.algorithms.engine.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.engine.SearchPermission
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ContactsSearchProvider : SearchProvider, SearchPermission {
    override val id: String = "contacts"

    // A short-lived snapshot of the provider's matching rows. Repeated queries
    // (IME refreshes, returning to the overlay) avoid a second database scan.
    // Observe the contacts provider so edits invalidate the cached results.
    private val cacheLock = Any()
    private const val CACHE_TTL_MS = 60_000L
    private const val CACHE_LIMIT = 32
    private val queryCache = object : LinkedHashMap<String, Pair<Long, List<ContactInfo>>>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<ContactInfo>>>?): Boolean =
            size > CACHE_LIMIT
    }
    private var observedResolver: android.content.ContentResolver? = null
    private data class IndexedContact(val normalizedName: String, val info: ContactInfo)
    private var contactIndex: Pair<Long, List<IndexedContact>>? = null
    private var indexGeneration = 0L
    private const val INDEX_TTL_MS = 10L * 60L * 1000L
    private const val INDEX_MAX_CONTACTS = 5_000
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            synchronized(cacheLock) {
                queryCache.clear()
                contactIndex = null
                indexGeneration++
            }
        }
    }
    private fun ensureObserver(context: Context) {
        synchronized(cacheLock) {
            if (observedResolver != null) return
            runCatching {
                context.applicationContext.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true, observer
                )
                observedResolver = context.applicationContext.contentResolver
            }
        }
    }

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = searchInternal(context, query, false)

    fun searchForPrime(context: Context, query: String): Flow<List<SearchResult>> =
        searchInternal(context, query, true)

    private fun searchInternal(context: Context, query: String, primeSearch: Boolean): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        val permissionsGranted = checkPermission(context)
        if (query.isBlank() || (!primeSearch && !prefs.searchResultPeople.get()) || !permissionsGranted) {
            emit(emptyList())
            return@flow
        }

        val maxResults = prefs2.maxPeopleResultCount.firstCached()
        ensureObserver(context)
        val started = SystemClock.elapsedRealtime()
        val contactInfoList = findContactsByName(context, query, maxResults)
        app.lawnchair.search.PrimeSearchTrace.record(
            "contacts query len=${query.length} count=${contactInfoList.size} ms=${SystemClock.elapsedRealtime() - started}"
        )

        val surface = if (primeSearch)
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.PRIME_SEARCH
        else app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
        val selected = app.lawnchair.prime.search.PrimeSourceSelection.selected(context, "contacts", surface)
        val allowedIds = selected?.let { accounts ->
            val ids = mutableSetOf<String>()
            runCatching {
                context.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                    ContactsContract.RawContacts.DELETED + "=0", null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val account = cursor.getString(1).orEmpty() + "|" + cursor.getString(2).orEmpty()
                        if (account in accounts) ids.add(cursor.getLong(0).toString())
                    }
                }
            }
            ids
        }
        val searchResults = contactInfoList.filter { allowedIds == null || it.contactId in allowedIds }.map { contactInfo ->
            SearchResult.Contact(data = contactInfo)
        }
        emit(searchResults)
    }

    override fun checkPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun findContactsByName(context: Context, query: String, max: Int): List<ContactInfo> {
        try {
            if (query.isEmpty() || query.isBlank() || max <= 0) return emptyList()
            val exceptionHandler = CoroutineExceptionHandler { _, e ->
                Log.e("ContactSearch", "Something went wrong ", e)
            }
            return withContext(Dispatchers.IO + exceptionHandler) {
                val key = query.lowercase(Locale.getDefault()) + "|" + max
                val now = SystemClock.elapsedRealtime()
                synchronized(cacheLock) {
                    queryCache[key]?.takeIf { now - it.first in 0 until CACHE_TTL_MS }?.second
                }?.let { return@withContext it }
                val normalizedQuery = query.lowercase(Locale.getDefault())
                val cachedIndex = synchronized(cacheLock) {
                    contactIndex?.takeIf { now - it.first in 0 until INDEX_TTL_MS }?.second
                }
                if (cachedIndex != null) {
                    val matches = cachedIndex.asSequence()
                        .filter { it.normalizedName.contains(normalizedQuery) }
                        .map { it.info }.take(max).toList()
                    synchronized(cacheLock) { queryCache[key] = now to matches }
                    return@withContext matches
                }
                val generation = synchronized(cacheLock) { indexGeneration }
                val contactMap = LinkedHashMap<String, ContactInfo>()
                var indexComplete = true

                val projection = arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA3,
                    ContactsContract.Data.DATA5,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.PHOTO_URI,
                )

                val selection: String? = null
                val selectionArgs: Array<String>? = null

                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use {
                    while (it.moveToNext()) {
                        if (contactMap.size >= INDEX_MAX_CONTACTS) {
                            indexComplete = false
                            break
                        }
                        val contactIdIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                        val displayNameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                        val data1Index = it.getColumnIndex(ContactsContract.Data.DATA1)
                        val data3Index = it.getColumnIndex(ContactsContract.Data.DATA3)
                        val data5Index = it.getColumnIndex(ContactsContract.Data.DATA5)
                        val mimeTypeIndex = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
                        val photoUriIndex = it.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                        val contactId = it.getString(contactIdIndex)
                        val displayName = it.getString(displayNameIndex)
                        val data1 = it.getString(data1Index)
                        val data3 = it.getString(data3Index)
                        val data5 = it.getString(data5Index)
                        val mimeType = it.getString(mimeTypeIndex)
                        val photoUri = it.getString(photoUriIndex)
                        val phoneNumber = data3 ?: data5 ?: data1
                        val key = contactId ?: phoneNumber
                        val imageUri = photoUri ?: ""
                        val pkg = contactId + displayName + phoneNumber
                        if (key != null && !EXCLUDED_MIME_TYPES.contains(mimeType)) {
                            contactMap[key] = ContactInfo(
                                contactId,
                                displayName,
                                phoneNumber,
                                imageUri,
                                pkg,
                            )
                        } else {
                            if (contactMap.containsKey(contactId)) {
                                val existingContact = contactMap[contactId]
                                val jsonArray = buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put(CONTACT_ACCOUNT_ID, key)
                                            put(CONTACT_ACCOUNT_TITLE, data5)
                                            put(CONTACT_ACCOUNT_MIME, mimeType)
                                        },
                                    )
                                }
                                existingContact?.packages = jsonArray.toString()
                            }
                        }
                    }
                }
                if (!indexComplete) {
                    contactMap.clear()
                    context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI, projection,
                        ContactsContract.Data.DISPLAY_NAME + " LIKE ?",
                        arrayOf("%$query%"), null
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                        val nameIndex = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                        val dataIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                        val data3Index = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                        val data5Index = cursor.getColumnIndex(ContactsContract.Data.DATA5)
                        val mimeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                        val photoIndex = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                        while (cursor.moveToNext() && contactMap.size < max) {
                            val id = cursor.getString(idIndex)
                            val name = cursor.getString(nameIndex)
                            val phone = cursor.getString(data3Index) ?: cursor.getString(data5Index) ?: cursor.getString(dataIndex)
                            val identity = id ?: phone
                            if (identity != null && !EXCLUDED_MIME_TYPES.contains(cursor.getString(mimeIndex))) {
                                contactMap[identity] = ContactInfo(id, name, phone,
                                    cursor.getString(photoIndex).orEmpty(), id + name + phone)
                            }
                        }
                    }
                }
                val matches = contactMap.values.asSequence()
                    .filter { it.name.lowercase(Locale.getDefault()).contains(normalizedQuery) }
                    .take(max).toList()
                synchronized(cacheLock) {
                    if (generation == indexGeneration) {
                        if (indexComplete) {
                            contactIndex = SystemClock.elapsedRealtime() to contactMap.values.map { info ->
                                IndexedContact(info.name.lowercase(Locale.getDefault()), info)
                            }
                        }
                        queryCache[key] = SystemClock.elapsedRealtime() to matches
                    }
                }
                matches
            }
        } catch (e: Exception) {
            Log.e("ContactSearch", "Something went wrong ", e)
            return emptyList()
        }
    }

    private const val CONTACT_ACCOUNT_ID = "contact.id"
    private const val CONTACT_ACCOUNT_MIME = "contact.mime"
    private const val CONTACT_ACCOUNT_TITLE = "contact.title"

    private val EXCLUDED_MIME_TYPES = arrayOf(
        "vnd.android.cursor.item/name",
        "vnd.android.cursor.item/nickname",
        "vnd.android.cursor.item/note",
        "vnd.android.cursor.item/photo",
        "vnd.com.google.cursor.item/contact_misc",
        "vnd.android.cursor.item/identity",
        "vnd.android.cursor.item/website",
    )
}
