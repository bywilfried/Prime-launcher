package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import java.text.Normalizer
import java.util.Locale

object AppSearchProvider {

    private val DIACRITICS_REMOVE_PATTERN = "\\p{M}+".toRegex()
    private val wordSeparator = Regex("[^\\p{L}\\p{N}]+")
    private data class PreparedName(val title: String, val normalized: String, val words: List<String>)
    private val preparedNames = java.util.WeakHashMap<AppInfo, PreparedName>()
    private fun prepared(app: AppInfo): PreparedName = synchronized(preparedNames) {
        val title = app.title.toString()
        preparedNames[app]?.takeIf { it.title == title } ?: run {
            val normalized = stripDiacritics(title).lowercase(Locale.getDefault())
            PreparedName(title, normalized, normalized.split(wordSeparator).filter { it.isNotEmpty() })
                .also { preparedNames[app] = it }
        }
    }

    fun search(context: Context, query: String, allApps: AllAppsList, maxResultsOverride: Int? = null, fuzzyOverride: Boolean? = null, strictTypos: Boolean = false): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = maxResultsOverride ?: prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = fuzzyOverride ?: prefs.enableFuzzySearch.firstCached()

        val queryNormalized = stripDiacritics(query).lowercase(Locale.getDefault())

        val appResults = if (strictTypos) {
            strictTypoSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        return appResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(query, stripDiacritics(it.title.toString()), matcher) }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val filteredApps = apps.asSequence()
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()

        return filteredApps
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(stripDiacritics(app.title.toString()), query)
                if (matchResult.type == MatchType.NO_MATCH) null else Pair(app, matchResult)
            }
            .sortedWith(
                compareBy(
                    { it.second.type.priority },
                    { -it.second.score },
                ),
            )
            .map { it.first }
            .take(maxResultsCount)
    }

    // Prime: accept small spelling mistakes without broad fuzzy similarity.
    // A typo must be close to a whole word in the application's display name.
    private fun strictTypoSearch(
        apps: List<AppInfo>,
        query: String,
        maxResults: Int,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
    ): List<AppInfo> {
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val allowedEdits = when {
            needle.length < 4 -> 0
            needle.length < 7 -> 1
            else -> 2
        }
        return apps.asSequence()
            .filterHiddenApps(needle, hiddenApps, hiddenAppsInSearch)
            .mapNotNull { app ->
                val preparedName = prepared(app)
                val name = preparedName.normalized
                if (StringMatcherUtility.matches(needle, name, matcher)) {
                    app to 0
                } else if (allowedEdits > 0) {
                    val words = preparedName.words
                    val distance = words.minOfOrNull { word ->
                        if (kotlin.math.abs(word.length - needle.length) > allowedEdits ||
                            !sharesEnoughCharacters(word, needle, allowedEdits)) {
                            allowedEdits + 1
                        } else {
                            editDistanceWithin(word, needle, allowedEdits)
                        }
                    } ?: (allowedEdits + 1)
                    if (distance <= allowedEdits) app to distance else null
                } else null
            }
            .sortedBy { it.second }
            .map { it.first }
            .take(maxResults)
            .toList()
    }

    // A distance of at most k implies at least max(lengths)-k shared characters
    // (counting duplicates). Cheap rejection before allocating the DP matrix.
    private fun sharesEnoughCharacters(word: String, needle: String, edits: Int): Boolean {
        val required = maxOf(word.length, needle.length) - edits * 2
        if (required <= 0) return true
        val counts = HashMap<Char, Int>()
        word.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        var common = 0
        needle.forEach { c ->
            val count = counts[c] ?: 0
            if (count > 0) {
                common++
                counts[c] = count - 1
            }
        }
        return common >= required
    }

    // Bounded Damerau-Levenshtein: insertions, deletions, replacements
    // and adjacent letter transpositions count as one typo.
    private fun editDistanceWithin(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var distance = minOf(
                    rows[i - 1][j] + 1,
                    rows[i][j - 1] + 1,
                    rows[i - 1][j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    distance = minOf(distance, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = distance
            }
        }
        return rows[a.length][b.length]
    }

    private fun stripDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REMOVE_PATTERN, "")
    }
}
