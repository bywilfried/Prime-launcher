package app.lawnchair.search.algorithms.engine.provider.web

import android.content.Context
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout

object WebSuggestionProvider : SearchProvider {
    override val id = "web_suggestions"

    // Preserve suggestions if the drawer restarts the same query on an IME transition.
    private data class DrawerSuggestionKey(val query: String, val provider: String, val limit: Int)
    private val cacheLock = Any()
    private val drawerSuggestionCache =
        object : LinkedHashMap<DrawerSuggestionKey, List<SearchResult>>(24, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DrawerSuggestionKey, List<SearchResult>>?): Boolean =
                size > 24
        }


    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank() || !prefs.searchResultStartPageSuggestion.get()) {
            return flow { emit(emptyList()) }
        }

        val provider = prefs2.webSuggestionProvider.firstCached()
        val timeout = prefs2.maxWebSuggestionDelay.firstCached()
        val maxResults = prefs2.maxWebSuggestionResultCount.firstCached()

        val webProvider = provider
            .configure(context)

        return webProvider.getSuggestions(query)
            .timeout(timeout.milliseconds)
            .catch {
                if (it is TimeoutCancellationException) {
                    Log.w(TAG, "Web suggestion request timed out")
                    emit(emptyList())
                }
            }
            .map { suggestions ->
                suggestions
                    .take(maxResults)
                    .map { suggestion ->
                        SearchResult.WebSuggestion(suggestion = suggestion, provider = webProvider.id)
                    }
            }
    }

    /**
     * Drawer suggestions use the same endpoints and JSON formats as Prime Search,
     * but retain the drawer's own provider, visibility and result-count settings.
     */
    fun searchForDrawer(context: Context, query: String): Flow<List<SearchResult>> {
        val settings = app.lawnchair.prime.search.PrimeSearchResultPreferences
        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
        val limit = settings.webSuggestionCount(context, surface)
        if (query.isBlank() || limit == 0 ||
            !settings.enabled(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB, surface)) {
            return flow { emit(emptyList()) }
        }

        val selected = settings.suggestionProvider(context, surface)
        val cacheKey = DrawerSuggestionKey(query, selected.name, limit)
        return flow {
            val cached = synchronized(cacheLock) { drawerSuggestionCache[cacheKey] }
            if (cached != null) {
                emit(cached)
                return@flow
            }
            fun fetchSuggestions(
                provider: app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider,
            ): List<String> {
                val endpoint = when (provider) {
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO ->
                        "https://duckduckgo.com/ac/?q="
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.GOOGLE ->
                        "https://suggestqueries.google.com/complete/search?client=firefox&q="
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.BING ->
                        "https://api.bing.com/osjson.aspx?query="
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.QWANT ->
                        "https://api.qwant.com/v3/suggest?q="
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.STARTPAGE ->
                        "https://www.startpage.com/suggestions?format=opensearch&q="
                }
                val connection = java.net.URL(endpoint + android.net.Uri.encode(query))
                    .openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    return when (provider) {
                        app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO -> {
                            val items = org.json.JSONArray(body)
                            (0 until minOf(items.length(), limit)).mapNotNull {
                                items.optJSONObject(it)?.optString("phrase")?.takeIf(String::isNotBlank)
                            }
                        }
                        app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.QWANT -> {
                            val items = org.json.JSONObject(body).optJSONObject("data")?.optJSONArray("items")
                            if (items == null) emptyList() else
                                (0 until minOf(items.length(), limit)).mapNotNull {
                                    items.optJSONObject(it)?.optString("value")?.takeIf(String::isNotBlank)
                                }
                        }
                        else -> {
                            val items = org.json.JSONArray(body).getJSONArray(1)
                            (0 until minOf(items.length(), limit)).map { items.getString(it) }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            val suggestions = try {
                fetchSuggestions(selected)
            } catch (error: Exception) {
                Log.w(TAG, "Drawer web suggestions failed for $selected; trying DuckDuckGo", error)
                runCatching {
                    fetchSuggestions(app.lawnchair.prime.search.PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO)
                }.getOrDefault(emptyList())
            }
            val results: List<SearchResult> = suggestions.distinct().take(limit).map {
                SearchResult.WebSuggestion(it, selected.name.lowercase(java.util.Locale.ROOT))
            }
            if (results.isNotEmpty()) {
                synchronized(cacheLock) { drawerSuggestionCache[cacheKey] = results }
            }
            emit(results)
        }.flowOn(kotlinx.coroutines.Dispatchers.IO).catch { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "Drawer web suggestions unavailable", error)
            emit(emptyList())
        }
    }

    const val TAG: String = "WebSuggestionProvider"
}
