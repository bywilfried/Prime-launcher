package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.content.pm.ShortcutInfo
import app.lawnchair.launcher
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.shortcuts.ShortcutRequest

object ShortcutSearchProvider {
    fun search(context: Context, appResults: List<SearchResult.App>): List<SearchResult.Shortcut> {
        if (!context.isDefaultLauncher()) return emptyList()

        // Multiple matching apps may expose shortcuts. Deduplicate launcher activities
        // and shortcut identities to avoid repeated results.
        val seenApps = HashSet<String>()
        val seenShortcuts = HashSet<String>()
        return appResults.asSequence()
            .map { it.data }
            .filter { app ->
                seenApps.add("${app.user}:${app.targetComponent?.packageName}")
            }
            .flatMap { app ->
                runCatching { getShortcuts(app, context).asSequence() }
                    .getOrDefault(emptySequence())
            }
            .filter { shortcut ->
                seenShortcuts.add("${shortcut.userHandle}:${shortcut.id}:${shortcut.activity}")
            }
            .map { SearchResult.Shortcut(data = it) }
            .toList()
    }


    // Prime searches shortcut titles independently of the owning app name.
    // Avoid querying every installed app again on every keystroke.
    private val primeCache = HashMap<String, Pair<Long, List<ShortcutInfo>>>()
    private const val PRIME_CACHE_MS = 60_000L

    fun searchForPrime(
        context: Context,
        query: String,
        allApps: List<AppInfo>,
        matchedApps: List<SearchResult.App>,
    ): List<SearchResult.Shortcut> {
        if (!context.isDefaultLauncher() || query.isBlank()) return emptyList()
        fun normalize(value: String): String =
            java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(java.util.Locale.getDefault())
        val needle = normalize(query.trim())
        val matchedPackages = matchedApps.mapNotNull { it.data.targetComponent?.packageName }.toSet()
        val now = android.os.SystemClock.elapsedRealtime()
        val seenApps = HashSet<String>()
        val seenShortcuts = HashSet<String>()
        return allApps.asSequence()
            .filter { app -> seenApps.add("${app.user}:${app.targetComponent?.packageName}") }
            .flatMap { app ->
                val key = "${app.user}:${app.targetComponent?.flattenToString()}"
                val cached = synchronized(primeCache) { primeCache[key] }
                val shortcuts = if (cached != null && now - cached.first < PRIME_CACHE_MS) {
                    cached.second
                } else {
                    val loaded = runCatching { getShortcuts(app, context) }.getOrDefault(emptyList())
                    synchronized(primeCache) { primeCache[key] = now to loaded }
                    loaded
                }
                val matchesApp = app.targetComponent?.packageName in matchedPackages
                shortcuts.asSequence().filter { shortcut ->
                    val shortName = shortcut.shortLabel?.toString().orEmpty()
                    val longName = shortcut.longLabel?.toString().orEmpty()
                    matchesApp || normalize(shortName).contains(needle) || normalize(longName).contains(needle)
                }
            }
            .filter { shortcut ->
                seenShortcuts.add("${shortcut.userHandle}:${shortcut.id}:${shortcut.activity}")
            }
            .map { SearchResult.Shortcut(data = it) }
            .toList()
    }

    private fun getShortcuts(app: AppInfo, context: Context): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts)
    }
}
