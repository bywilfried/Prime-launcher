package app.lawnchair.search.algorithms

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.adapter.SearchLinksTarget
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.algorithms.engine.ActionsSectionBuilder
import app.lawnchair.search.algorithms.engine.AppsAndShortcutsSectionBuilder
import app.lawnchair.search.algorithms.engine.CalculationSectionBuilder
import app.lawnchair.search.algorithms.engine.ContactsSectionBuilder
import app.lawnchair.search.algorithms.engine.EmptyStateSectionBuilder
import app.lawnchair.search.algorithms.engine.PrimeEntriesSectionBuilder
import app.lawnchair.search.algorithms.engine.FilesSectionBuilder
import app.lawnchair.search.algorithms.engine.HistorySectionBuilder
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.engine.SearchSettingsSectionBuilder
import app.lawnchair.search.algorithms.engine.SectionBuilder
import app.lawnchair.search.algorithms.engine.SettingsSectionBuilder
import app.lawnchair.search.algorithms.engine.WebSuggestionsSectionBuilder
import app.lawnchair.search.algorithms.engine.provider.CalculatorSearchProvider
import app.lawnchair.search.algorithms.engine.provider.ContactsSearchProvider
import app.lawnchair.search.algorithms.engine.provider.FileSearchProvider
import app.lawnchair.search.algorithms.engine.provider.HistorySearchProvider
import app.lawnchair.search.algorithms.engine.provider.SettingsSearchProvider
import app.lawnchair.search.algorithms.engine.provider.ShortcutSearchProvider
import app.lawnchair.search.algorithms.engine.provider.apps.AppSearchProvider
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.lawnchair.search.algorithms.engine.provider.web.WebSuggestionProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LawnchairLocalSearchAlgorithm(context: Context, private val primeSearch: Boolean = false) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null
    private var modelRetryJob: Job? = null
    @Volatile private var searchGeneration = 0L
    private var lastQuery: String? = null
    private var lastCallback: SearchCallback<BaseAllAppsAdapter.AdapterItem>? = null
    private var lastDeliveredItems: ArrayList<BaseAllAppsAdapter.AdapterItem>? = null

    private val appSearchProvider = AppSearchProvider
    private val shortcutSearchProvider = ShortcutSearchProvider
    private val historySearchProvider = HistorySearchProvider

    private val searchProviders: List<SearchProvider> = listOf(
        SettingsSearchProvider,
        FileSearchProvider,
        ContactsSearchProvider,
        WebSuggestionProvider,
    )

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        app.lawnchair.search.PrimeSearchTrace.record("engine doSearch len=${query.length} same=${query == lastQuery} callbackChanged=${callback !== lastCallback} prime=$primeSearch")
        // IME visibility/focus changes can request the same drawer search again.
        // Keep the current provider flows and displayed results instead of
        // restarting network suggestions for an unchanged query.
        if (query == lastQuery) {
            if (callback !== lastCallback) {
                lastCallback = callback
                lastDeliveredItems?.let { items ->
                    callback.onSearchResult(query, ArrayList(items))
                }
            }
            return
        }
        lastQuery = query
        lastCallback = callback
        lastDeliveredItems = null
        val generation = ++searchGeneration
        modelRetryJob?.cancel()
        currentJob?.cancel()
        modelRetryJob = appState.model.runWhenModelLoaded(coroutineScope) { apps ->
            if (generation != searchGeneration) return@runWhenModelLoaded
            val appResults = appSearchProvider.search(context, query, apps, if (primeSearch) Int.MAX_VALUE else null, if (primeSearch) app.lawnchair.prime.search.PrimeSearchResultPreferences.fuzzyApps(context) else null)
            val shortcutResults = shortcutSearchProvider.search(context, appResults)

            currentJob?.cancel()
            currentJob = coroutineScope.launch {
                // Each provider flow starts by emitting an empty list so that combine() can
                // produce a first value without waiting for the slowest provider (e.g. web
                // suggestions blocked on a network roundtrip, or contacts/files with no
                // timeout). App and shortcut matches are computed synchronously above, so this
                // lets them appear immediately and then be augmented as providers complete.
                // See issue #7325 (symptom 1: results gated behind the slowest provider).
                val nonAppProvidersFlow = combine(
                    searchProviders.map { provider ->
                        (if (primeSearch && provider === ContactsSearchProvider) {
                            ContactsSearchProvider.searchForPrime(context, query)
                        } else if (primeSearch && provider === FileSearchProvider) {
                            FileSearchProvider.searchForPrime(context, query)
                        } else if (!primeSearch && provider === WebSuggestionProvider) {
                            WebSuggestionProvider.searchForDrawer(context, query)
                        } else provider.search(context, query)).onStart { emit(emptyList()) }
                    },
                ) { resultsArray ->
                    resultsArray.toList().flatten()
                }

                nonAppProvidersFlow.collect { nonAppResults ->
                    val calcResult = CalculatorSearchProvider.search(context, query)
                        .firstOrNull()

                    val actions = generateActionResults(query).filterNot { primeSearch && it is SearchResult.Action.SearchSettings }
                    val drawerWebActions = if (primeSearch) emptyList() else {
                        val settings = app.lawnchair.prime.search.PrimeSearchResultPreferences
                        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
                        if (settings.enabled(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB_SHORTCUTS, surface)) {
                            settings.enabledWebEngines(context, surface).map { engine ->
                                SearchResult.Action.WebSearch(
                                    query = query,
                                    providerName = engine.title,
                                    searchUrl = engine.url + android.net.Uri.encode(query),
                                    providerIconRes = R.drawable.ic_allapps_search,
                                    tintIcon = true,
                                )
                            }
                        } else emptyList()
                    }
                    val allResults = appResults + shortcutResults + (calcResult ?: emptyList()) + nonAppResults + actions + drawerWebActions

                    val drawerEntries = if (primeSearch || query.isBlank()) emptyList() else {
                        val preferences = app.lawnchair.prime.search.PrimeSearchResultPreferences
                        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
                        val entries = mutableListOf<SearchResult.PrimeEntry>()
                        if (preferences.enabled(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALENDAR, surface)) {
                            runCatching { app.lawnchair.prime.search.PrimeCalendarSearch.events(context, query, surface) }
                                .getOrDefault(emptyList()).forEach {
                                    entries.add(SearchResult.PrimeEntry("Agenda", it.title, it.detail, it.uri))
                                }
                        }
                        if (preferences.enabled(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.REMINDERS, surface)) {
                            runCatching { app.lawnchair.prime.search.PrimeTasksSearch.search(context, query, surface) }
                                .getOrDefault(emptyList()).forEach {
                                    entries.add(SearchResult.PrimeEntry("Tâches", it.title, it.detail, it.uri))
                                }
                        }
                        if (preferences.enabled(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.NOTIFICATIONS, surface) &&
                            app.lawnchair.prime.search.PrimeNotificationHistory.isGranted(context)) {
                            runCatching { app.lawnchair.prime.search.PrimeNotificationHistory.searchForDrawer(context, query) }
                                .getOrDefault(emptyList()).forEach {
                                    entries.add(SearchResult.PrimeEntry("Notifications", it.app + " · " + it.title, it.body, null))
                                }
                        }
                        entries
                    }
                    val visibleResults = if (primeSearch) allResults else {
                        val prefs = app.lawnchair.prime.search.PrimeSearchResultPreferences
                        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
                        allResults.filter { result ->
                            val category = when (result) {
                                is SearchResult.App -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS
                                is SearchResult.Shortcut -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS
                                is SearchResult.File -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES
                                is SearchResult.Contact -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS
                                is SearchResult.Setting -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.SETTINGS
                                is SearchResult.History -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.HISTORY
                                is SearchResult.Calculation -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALCULATOR
                                is SearchResult.WebSuggestion -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB
                                else -> null
                            }
                            category == null || prefs.enabled(context, category, surface)
                        }
                    }
                    val searchTargets = translateToSearchTargets(visibleResults + drawerEntries)
                    setFirstItemQuickLaunch(searchTargets)
                    val adapterItems = transformSearchResults(searchTargets)
                    withContext(Dispatchers.Main) {
                        if (generation != searchGeneration) return@withContext
                        val snapshot = ArrayList(adapterItems)
                        lastDeliveredItems = ArrayList<BaseAllAppsAdapter.AdapterItem>(snapshot)
                        app.lawnchair.search.PrimeSearchTrace.record("engine deliver len=${query.length} items=${snapshot.size} generation=$generation")
                        lastCallback?.onSearchResult(query, ArrayList(snapshot))
                    }
                }
            }
        }
    }

    override fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        app.lawnchair.search.PrimeSearchTrace.record("engine zeroState prime=$primeSearch")
        lastQuery = null
        lastCallback = null
        lastDeliveredItems = null
        searchGeneration++
        modelRetryJob?.cancel()
        currentJob?.cancel()

        val prefs = PreferenceManager.getInstance(context)
        val historyEnabled = prefs.searchResulRecentSuggestion.get()

        if (!historyEnabled) {
            callback.clearSearchResult()
        } else {
            currentJob = coroutineScope.launch {
                val prefs2 = PreferenceManager2.getInstance(context)
                val maxHistory = prefs2.maxRecentResultCount.firstCached()

                val historyResults = historySearchProvider.getRecentKeywords(context, maxHistory)

                val resultsToTranslate = if (historyResults.isNotEmpty()) {
                    historyResults + listOf(SearchResult.Action.SearchSettings)
                } else {
                    listOf(
                        SearchResult.Action.EmptyState(
                            titleRes = R.string.search_empty_state_title,
                            subtitleRes = R.string.search_empty_state_no_history_subtitle,
                        ),
                        SearchResult.Action.SearchSettings,
                    )
                }

                val searchTargets = translateToSearchTargets(resultsToTranslate)
                val adapterItems = transformSearchResults(searchTargets)
                withContext(Dispatchers.Main) {
                    callback.onSearchResult("", ArrayList(adapterItems))
                }
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        app.lawnchair.search.PrimeSearchTrace.record("engine cancel interrupt=$interruptActiveRequests previousLen=${lastQuery?.length ?: 0} prime=$primeSearch")
        lastQuery = null
        lastCallback = null
        searchGeneration++
        modelRetryJob?.cancel()
        currentJob?.cancel()
    }

    private fun generateActionResults(query: String): List<SearchResult.Action> {
        val actions = mutableListOf<SearchResult.Action>()
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (prefs.searchResultStartPageSuggestion.get()) {
            val provider = prefs2.webSuggestionProvider.firstCached()
            val webProvider = provider.configure(context)

            val providerName = if (webProvider is CustomWebSearchProvider) {
                webProvider.getDisplayName()
            } else {
                context.getString(webProvider.label)
            }

            actions.add(
                SearchResult.Action.WebSearch(
                    query = query,
                    providerName = providerName,
                    searchUrl = webProvider.getSearchUrl(query),
                    providerIconRes = webProvider.iconRes,
                    tintIcon = webProvider is CustomWebSearchProvider,
                ),
            )
        }

        if (SearchLinksTarget.resolveMarketSearchActivity(context) != null) {
            actions.add(SearchResult.Action.MarketSearch(query = query))
        }

        actions.add(SearchResult.Action.SearchSettings)

        return actions
    }

    private val sectionBuilders: List<SectionBuilder> = listOf(
        AppsAndShortcutsSectionBuilder,
        CalculationSectionBuilder,
        WebSuggestionsSectionBuilder,
        ContactsSectionBuilder,
        FilesSectionBuilder,
        PrimeEntriesSectionBuilder,
        SettingsSectionBuilder,
        HistorySectionBuilder,
        ActionsSectionBuilder,
        EmptyStateSectionBuilder,
        SearchSettingsSectionBuilder,
    )

    private fun translateToSearchTargets(
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val factory = SearchTargetFactory(context)

        // The new function is just a flatMap. It's declarative and beautiful.
        val builders = if (primeSearch) sectionBuilders else {
            val settings = app.lawnchair.prime.search.PrimeSearchResultPreferences
            val order = settings.order(context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER)
            val byCategory = mapOf(
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS to AppsAndShortcutsSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALCULATOR to CalculationSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB to WebSuggestionsSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS to ContactsSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES to FilesSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.SETTINGS to SettingsSectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.HISTORY to HistorySectionBuilder,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALENDAR to PrimeEntriesSectionBuilder,
            )
            val ordered = order.mapNotNull { byCategory[it] }.distinct()
            ordered + sectionBuilders.filterNot { it in ordered }
        }
        return builders.flatMap { builder ->
            builder.build(context, factory, results)
        }
    }
}
