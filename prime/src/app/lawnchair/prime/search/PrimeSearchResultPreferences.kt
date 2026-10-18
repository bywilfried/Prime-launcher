package app.lawnchair.prime.search

import android.content.Context
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import com.android.app.search.LayoutType

/**
 * Prime Search visibility choices are separate from drawer preferences.
 * Filtering happens after the common search algorithm has produced ordered results.
 */
object PrimeSearchResultPreferences {
    private const val STORE = "prime_search_result_preferences"

    enum class Category(val key: String, val title: String, val description: String) {
        APPS("apps", "Applis et raccourcis", "Applications et raccourcis"),
        WEB("web", "Suggestions Web", "Recherches et suggestions Internet"),
        CONTACTS("contacts", "Personnes", "Contacts et autres"),
        FILES("files", "Fichiers", "Contenu multimédia et fichiers"),
        SETTINGS("settings", "Paramètres Android", "Réglages du système"),
        HISTORY("history", "Historique des recherches", "Recherches récentes"),
        CALCULATOR("calculator", "Calculatrice", "Résultats de calcul"),
    }

    enum class WebEngine(val title: String, val url: String) {
        GOOGLE("Google", "https://www.google.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        BRAVE("Brave Search", "https://search.brave.com/search?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
        QWANT("Qwant", "https://www.qwant.com/?q="),
        YOUTUBE("YouTube", "https://www.youtube.com/results?search_query="),
        WIKIPEDIA("Wikipédia", "https://fr.wikipedia.org/w/index.php?search="),
        REDDIT("Reddit", "https://www.reddit.com/search/?q="),
        SPOTIFY("Spotify", "https://open.spotify.com/search/"),
        GITHUB("GitHub", "https://github.com/search?q="),
        AMAZON("Amazon", "https://www.amazon.fr/s?k="),
        CHATGPT("ChatGPT", "https://chatgpt.com/?q="),
        GEMINI("Gemini", "https://gemini.google.com/app?q="),
        PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q="),
        GOOGLE_PLAY("Google Play", "https://play.google.com/store/search?q="),
        GOOGLE_MAPS("Google Maps", "https://www.google.com/maps/search/?api=1&query="),
        GROK("Grok", "https://grok.com/?q="),
        FDROID("F-Droid", "https://search.f-droid.org/?q="),
        X("X", "https://x.com/search?q="),
        GOOGLE_DRIVE("Google Drive", "https://drive.google.com/drive/u/0/search?q="),
        GOOGLE_PHOTOS("Google Photos", "https://photos.google.com/search/"),
        MODE_AI("Mode AI", "https://www.google.com/search?udm=50&q="),
        FACEBOOK_MARKETPLACE("Facebook Marketplace", "https://www.facebook.com/marketplace/search/?query="),
        YOU_COM("You.com", "https://you.com/search?q="),
        KAGI("Kagi", "https://kagi.com/search?q="),
        BRAVE_BROWSER("Navigateur Brave", "https://search.brave.com/search?q="),
    }

    fun webEngineOrder(context: Context): List<WebEngine> {
        val saved = prefs(context).getString("web_engine_order", null)
            ?.split(",")?.mapNotNull { name -> WebEngine.entries.firstOrNull { it.name == name } }
            .orEmpty()
        return (saved + WebEngine.entries).distinct()
    }

    fun saveWebEngineOrder(context: Context, engines: List<WebEngine>) {
        prefs(context).edit().putString("web_engine_order",
            (engines + WebEngine.entries).distinct().joinToString(",") { it.name }).apply()
    }

    fun enabledWebEngines(context: Context): List<WebEngine> {
        val selected = prefs(context).getStringSet("web_engines", setOf(WebEngine.GOOGLE.name))
            .orEmpty()
        return webEngineOrder(context).filter { it.name in selected }
    }

    fun setWebEngineEnabled(context: Context, engine: WebEngine, enabled: Boolean) {
        val current = enabledWebEngines(context).map { it.name }.toMutableSet()
        if (enabled) current.add(engine.name) else current.remove(engine.name)
        prefs(context).edit().putStringSet("web_engines", current).apply()
    }

    fun webSuggestionCount(context: Context): Int =
        prefs(context).getInt("web_suggestion_count", 4).coerceIn(0, 8)

    fun setWebSuggestionCount(context: Context, count: Int) {
        prefs(context).edit().putInt("web_suggestion_count", count.coerceIn(0, 8)).apply()
    }

    fun zeroStateRows(context: Context): Int =
        prefs(context).getInt("zero_state_rows", 1).coerceIn(1, 2)

    fun setZeroStateRows(context: Context, rows: Int) {
        prefs(context).edit().putInt("zero_state_rows", rows.coerceIn(1, 2)).apply()
    }

    fun floatingWindow(context: Context): Boolean = prefs(context).getBoolean("floating_window", true)

    fun setFloatingWindow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("floating_window", enabled).apply()
    }

    fun fuzzyApps(context: Context): Boolean = prefs(context).getBoolean("fuzzy_apps", true)

    fun setFuzzyApps(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("fuzzy_apps", enabled).apply()
    }

    fun appsPerRow(context: Context): Int =
        prefs(context).getInt("apps_per_row", 4).coerceIn(3, 6)

    fun setAppsPerRow(context: Context, count: Int) {
        prefs(context).edit().putInt("apps_per_row", count.coerceIn(3, 6)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun enabled(context: Context, category: Category): Boolean =
        prefs(context).getBoolean(category.key, true)

    fun setEnabled(context: Context, category: Category, enabled: Boolean) {
        prefs(context).edit().putBoolean(category.key, enabled).apply()
    }

    fun order(context: Context): List<Category> {
        val saved = prefs(context).getString("category_order", null)
            ?.split(",")
            ?.mapNotNull { key -> Category.entries.firstOrNull { it.key == key } }
            .orEmpty()
        return (saved + Category.entries).distinct()
    }

    fun saveOrder(context: Context, categories: List<Category>) {
        prefs(context).edit().putString(
            "category_order",
            (categories + Category.entries).distinct().joinToString(",") { it.key },
        ).apply()
    }

    fun resetOrder(context: Context) {
        prefs(context).edit().remove("category_order").apply()
    }

    private fun category(item: SearchAdapterItem): Category? {
        val target = item.searchTarget
        return when (target.resultType) {
            SearchTargetCompat.RESULT_TYPE_APPLICATION,
            SearchTargetCompat.RESULT_TYPE_SHORTCUT -> Category.APPS
            SearchTargetCompat.RESULT_TYPE_CONTACT_TILE -> Category.CONTACTS
            SearchTargetCompat.RESULT_TYPE_FILE_TILE -> Category.FILES
            SearchTargetCompat.RESULT_TYPE_SETTING_TILE -> Category.SETTINGS
            SearchTargetCompat.RESULT_TYPE_CALCULATOR -> Category.CALCULATOR
            SearchTargetCompat.RESULT_TYPE_SUGGESTIONS -> {
                if (target.packageName == app.lawnchair.search.adapter.HISTORY) Category.HISTORY
                else Category.WEB
            }
            else -> null
        }
    }

    fun filter(context: Context, items: List<SearchAdapterItem>): List<SearchAdapterItem> {
        // Headers/dividers are retained only when their section still has visible results.
        val kept = items.filter { item ->
            category(item)?.let { enabled(context, it) } ?: true
        }
        val cleaned = kept.filterIndexed { index, item ->
            if (item.searchTarget.resultType != SearchTargetCompat.RESULT_TYPE_SECTION_HEADER &&
                item.searchTarget.layoutType != LayoutType.EMPTY_DIVIDER) return@filterIndexed true
            kept.drop(index + 1).takeWhile {
                it.searchTarget.resultType != SearchTargetCompat.RESULT_TYPE_SECTION_HEADER
            }.any { category(it) != null }
        }
        // The engine already emits sections in order. Keep each section intact
        // (header, items and spacers) while sorting whole sections by preference.
        val sections = mutableListOf<MutableList<SearchAdapterItem>>()
        cleaned.forEach { item ->
            if (sections.isEmpty() ||
                (item.searchTarget.resultType == SearchTargetCompat.RESULT_TYPE_SECTION_HEADER &&
                    item.searchTarget.layoutType != LayoutType.EMPTY_DIVIDER)
            ) {
                sections.add(mutableListOf())
            }
            sections.last().add(item)
        }
        val priorities = order(context).withIndex().associate { it.value to it.index }
        return sections.withIndex().sortedWith(
            compareBy({ indexed ->
                indexed.value.firstNotNullOfOrNull { category(it) }
                    ?.let { priorities[it] } ?: Int.MAX_VALUE
            }, { it.index }),
        ).flatMap { it.value }
    }
}
