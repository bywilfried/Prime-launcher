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
    /** Drawer and overlay preferences are deliberately isolated. */
    enum class Surface(val store: String) {
        PRIME_SEARCH(STORE),
        DRAWER("prime_drawer_search_result_preferences"),
    }

    private fun surfacePrefs(context: Context, surface: Surface) =
        context.getSharedPreferences(surface.store, Context.MODE_PRIVATE)

    fun showCompletedTasks(context: Context, surface: Surface): Boolean =
        surfacePrefs(context, surface).getBoolean("show_completed_tasks", false)

    fun setShowCompletedTasks(context: Context, surface: Surface, enabled: Boolean) {
        surfacePrefs(context, surface).edit().putBoolean("show_completed_tasks", enabled).apply()
    }

    fun hasCategorySetting(context: Context, category: Category, surface: Surface): Boolean =
        surfacePrefs(context, surface).contains(category.key)

    fun enabled(context: Context, category: Category, surface: Surface): Boolean =
        surfacePrefs(context, surface).getBoolean(category.key, true) &&
            (!PrimeSearchPermissions.required(category) || PrimeSearchPermissions.granted(context, category))

    fun setEnabled(context: Context, category: Category, enabled: Boolean, surface: Surface) {
        surfacePrefs(context, surface).edit().putBoolean(category.key, enabled).apply()
    }

    fun order(context: Context, surface: Surface): List<Category> {
        val saved = surfacePrefs(context, surface).getString("category_order", null)
            ?.split(",")?.mapNotNull { key -> Category.entries.firstOrNull { it.key == key } }.orEmpty()
        return (saved + Category.entries).distinct()
    }

    fun saveOrder(context: Context, categories: List<Category>, surface: Surface) {
        surfacePrefs(context, surface).edit().putString(
            "category_order", (categories + Category.entries).distinct().joinToString(",") { it.key }
        ).apply()
    }

    fun fileOptionEnabled(context: Context, option: FileOption, surface: Surface): Boolean =
        surfacePrefs(context, surface).getBoolean("file_option_" + option.name, option.defaultEnabled)

    fun setFileOptionEnabled(context: Context, option: FileOption, enabled: Boolean, surface: Surface) {
        surfacePrefs(context, surface).edit().putBoolean("file_option_" + option.name, enabled).apply()
    }

    fun fileAllowed(context: Context, filename: String, mime: String?, surface: Surface): Boolean {
        val name = filename.trim()
        if (name.isEmpty()) return true
        val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        if (!fileOptionEnabled(context, FileOption.SYSTEM, surface) &&
            (name.startsWith(".") || name.endsWith(".bin", ignoreCase = true))) return false
        val option = when {
            mime == "vnd.android.document/directory" -> FileOption.FOLDERS
            ext in setOf("pdf", "doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "epub", "csv") -> FileOption.DOCUMENTS
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg") -> FileOption.IMAGES
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") -> FileOption.VIDEOS
            ext in setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus") -> FileOption.AUDIO
            ext == "apk" -> FileOption.APKS
            else -> FileOption.OTHER
        }
        return fileOptionEnabled(context, option, surface)
    }

    fun webSuggestionCount(context: Context, surface: Surface): Int =
        surfacePrefs(context, surface).getInt("web_suggestion_count", 4).coerceIn(0, 8)

    fun setWebSuggestionCount(context: Context, count: Int, surface: Surface) {
        surfacePrefs(context, surface).edit().putInt("web_suggestion_count", count.coerceIn(0, 8)).apply()
    }

    fun suggestionProvider(context: Context, surface: Surface): SuggestionProvider =
        runCatching {
            SuggestionProvider.valueOf(
                surfacePrefs(context, surface).getString("suggestion_provider", "DUCKDUCKGO") ?: "DUCKDUCKGO"
            )
        }.getOrDefault(SuggestionProvider.DUCKDUCKGO)

    fun setSuggestionProvider(context: Context, provider: SuggestionProvider, surface: Surface) {
        surfacePrefs(context, surface).edit().putString("suggestion_provider", provider.name).apply()
    }

    fun enabledWebEngines(context: Context, surface: Surface): List<WebEngine> {
        val preferences = surfacePrefs(context, surface)
        val selected = preferences.getStringSet("web_engines", setOf(WebEngine.DUCKDUCKGO.name)).orEmpty()
        val saved = preferences.getString("web_engine_order", null)
            ?.split(",")?.mapNotNull { name -> WebEngine.entries.firstOrNull { it.name == name } }.orEmpty()
        return (saved + WebEngine.entries).distinct().filter { it.name in selected }
    }

    fun setWebEngineEnabled(context: Context, engine: WebEngine, enabled: Boolean, surface: Surface) {
        val current = enabledWebEngines(context, surface).map { it.name }.toMutableSet()
        if (enabled) current.add(engine.name) else current.remove(engine.name)
        surfacePrefs(context, surface).edit().putStringSet("web_engines", current).apply()
    }

    fun hasCustomFileLimit(context: Context, surface: Surface): Boolean =
        surfacePrefs(context, surface).contains("prime_max_file_results")

    fun maxFileResults(context: Context, surface: Surface): Int =
        surfacePrefs(context, surface).getInt("prime_max_file_results", 5).coerceIn(3, 100)

    fun setMaxFileResults(context: Context, value: Int, surface: Surface) {
        surfacePrefs(context, surface).edit().putInt("prime_max_file_results", value.coerceIn(3, 100)).apply()
    }


    enum class Category(val key: String, val title: String, val description: String) {
        APPS("apps", "Applications", "Applications installées"),
        APP_SHORTCUTS("app_shortcuts", "Raccourcis d’app", "Raccourcis des applications"),
        WEB("web", "Suggestions Web", "Suggestions Internet pendant la saisie"),
        WEB_SHORTCUTS("web_shortcuts", "Recherche sur :", "Raccourcis vers les moteurs de recherche"),
        CONTACTS("contacts", "Contacts", "Contacts et autres"),
        CALENDAR("calendar", "Événements de l’agenda", "Événements du calendrier"),
        REMINDERS("reminders", "Tâches", "Tâches locales et synchronisées"),
        NOTIFICATIONS("notifications", "Notifications", "Notifications des applications"),
        NOTES("notes", "Notes", "Notes disponibles"),
        FILES("files", "Fichiers", "Contenu multimédia et fichiers"),
        SETTINGS("settings", "Paramètres de l’appareil", "Réglages du système"),
        HISTORY("history", "Historique des recherches", "Recherches récentes"),
        CALCULATOR("calculator", "Calculatrice", "Résultats de calcul"),
    }

    fun maxFileResults(context: Context): Int =
        prefs(context).getInt("prime_max_file_results", 5).coerceIn(3, 100)

    fun setMaxFileResults(context: Context, value: Int) {
        prefs(context).edit().putInt("prime_max_file_results", value.coerceIn(3, 100)).apply()
    }

    enum class FileOption(val label: String, val defaultEnabled: Boolean) {
        PREVIEW("Aperçu des fichiers", true),
        FOLDERS("Dossiers", false),
        DOCUMENTS("Documents", true),
        IMAGES("Images", true),
        VIDEOS("Vidéos", true),
        AUDIO("Audio", true),
        APKS("APKs", true),
        OTHER("Autre", true),
        SYSTEM("Fichiers système", false),
    }

    fun fileOptionEnabled(context: Context, option: FileOption): Boolean =
        prefs(context).getBoolean("file_option_" + option.name, option.defaultEnabled)

    fun setFileOptionEnabled(context: Context, option: FileOption, enabled: Boolean) {
        prefs(context).edit().putBoolean("file_option_" + option.name, enabled).apply()
    }

    fun fileAllowed(context: Context, filename: String, mime: String? = null): Boolean {
        val name = filename.trim()
        if (name.isEmpty()) return true
        val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        if (!fileOptionEnabled(context, FileOption.SYSTEM) &&
            (name.startsWith(".") || name.endsWith(".bin", ignoreCase = true))) return false
        val type = when {
            mime == "vnd.android.document/directory" -> FileOption.FOLDERS
            ext in setOf("pdf", "doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "epub", "csv") -> FileOption.DOCUMENTS
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg") -> FileOption.IMAGES
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") -> FileOption.VIDEOS
            ext in setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus") -> FileOption.AUDIO
            ext == "apk" -> FileOption.APKS
            else -> FileOption.OTHER
        }
        return fileOptionEnabled(context, type)
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
        val selected = prefs(context).getStringSet("web_engines", setOf(WebEngine.DUCKDUCKGO.name))
            .orEmpty()
        return webEngineOrder(context).filter { it.name in selected }
    }

    fun setWebEngineEnabled(context: Context, engine: WebEngine, enabled: Boolean) {
        val current = enabledWebEngines(context).map { it.name }.toMutableSet()
        if (enabled) current.add(engine.name) else current.remove(engine.name)
        prefs(context).edit().putStringSet("web_engines", current).apply()
    }

    enum class SuggestionProvider(val title: String) {
        GOOGLE("Google"), DUCKDUCKGO("DuckDuckGo"), BING("Bing"), QWANT("Qwant"), STARTPAGE("Startpage")
    }

    fun suggestionProvider(context: Context): SuggestionProvider =
        runCatching { SuggestionProvider.valueOf(prefs(context).getString("suggestion_provider", "DUCKDUCKGO") ?: "DUCKDUCKGO") }
            .getOrDefault(SuggestionProvider.GOOGLE)

    fun setSuggestionProvider(context: Context, provider: SuggestionProvider) {
        prefs(context).edit().putString("suggestion_provider", provider.name).apply()
    }

    enum class NotificationRetention(val label: String, val months: Int) {
        ONE_MONTH("1 mois", 1),
        TWO_MONTHS("2 mois", 2),
        THREE_MONTHS("3 mois", 3),
        SIX_MONTHS("6 mois", 6),
        TWELVE_MONTHS("12 mois", 12),
        FOREVER("Toujours", 0),
    }

    fun notificationRetention(context: Context, surface: Surface): NotificationRetention =
        runCatching {
            NotificationRetention.valueOf(
                surfacePrefs(context, surface).getString("notification_retention", NotificationRetention.ONE_MONTH.name)
                    ?: NotificationRetention.ONE_MONTH.name
            )
        }.getOrDefault(NotificationRetention.ONE_MONTH)

    fun setNotificationRetention(context: Context, retention: NotificationRetention, surface: Surface) {
        surfacePrefs(context, surface).edit().putString("notification_retention", retention.name).apply()
    }

    fun notificationRetention(context: Context): NotificationRetention =
        runCatching {
            NotificationRetention.valueOf(
                prefs(context).getString("notification_retention", NotificationRetention.ONE_MONTH.name)
                    ?: NotificationRetention.ONE_MONTH.name
            )
        }.getOrDefault(NotificationRetention.ONE_MONTH)

    fun setNotificationRetention(context: Context, retention: NotificationRetention) {
        prefs(context).edit().putString("notification_retention", retention.name).apply()
    }

    fun clearNotificationHistory(context: Context) {
        PrimeNotificationHistory.clear(context)
    }

    fun webSuggestionCount(context: Context): Int =
        prefs(context).getInt("web_suggestion_count", 4).coerceIn(0, 8)

    fun setWebSuggestionCount(context: Context, count: Int) {
        prefs(context).edit().putInt("web_suggestion_count", count.coerceIn(0, 8)).apply()
    }

    enum class OpeningTab(val label: String) {
        LAST("Dernière consultée"),
        FREQUENT("Fréquentes"),
        RECENT("Récentes"),
        NEW("Nouvelles"),
    }

    fun openingTab(context: Context): OpeningTab =
        runCatching {
            OpeningTab.valueOf(prefs(context).getString("opening_tab", OpeningTab.FREQUENT.name)!!)
        }.getOrDefault(OpeningTab.FREQUENT)

    fun setOpeningTab(context: Context, tab: OpeningTab) {
        prefs(context).edit().putString("opening_tab", tab.name).apply()
    }

    fun lastOpenedTab(context: Context): Int =
        prefs(context).getInt("last_opened_tab", 0).coerceIn(0, 2)

    fun setLastOpenedTab(context: Context, tab: Int) {
        prefs(context).edit().putInt("last_opened_tab", tab.coerceIn(0, 2)).apply()
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

    fun twoLineAppLabels(context: Context): Boolean =
        prefs(context).getBoolean("two_line_app_labels", false)

    fun setTwoLineAppLabels(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("two_line_app_labels", enabled).apply()
    }

    fun appsPerRow(context: Context): Int =
        prefs(context).getInt("apps_per_row", 4).coerceIn(3, 6)

    fun setAppsPerRow(context: Context, count: Int) {
        prefs(context).edit().putInt("apps_per_row", count.coerceIn(3, 6)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun enabled(context: Context, category: Category): Boolean =
        prefs(context).getBoolean(category.key, true) &&
            (!PrimeSearchPermissions.required(category) || PrimeSearchPermissions.granted(context, category))

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

    fun resetOrder(context: Context, surface: Surface) {
        surfacePrefs(context, surface).edit().remove("category_order").apply()
    }

    fun resetOrder(context: Context) {
        prefs(context).edit().remove("category_order").apply()
    }

    private fun category(item: SearchAdapterItem): Category? {
        val target = item.searchTarget
        return when (target.resultType) {
            SearchTargetCompat.RESULT_TYPE_APPLICATION -> Category.APPS
            SearchTargetCompat.RESULT_TYPE_SHORTCUT -> Category.APP_SHORTCUTS
            SearchTargetCompat.RESULT_TYPE_CONTACT_TILE -> Category.CONTACTS
            SearchTargetCompat.RESULT_TYPE_FILE_TILE -> Category.FILES
            SearchTargetCompat.RESULT_TYPE_SETTING_TILE ->
                Category.SETTINGS
            SearchTargetCompat.RESULT_TYPE_CALCULATOR -> Category.CALCULATOR
            SearchTargetCompat.RESULT_TYPE_SUGGESTIONS -> {
                // Prime Search renders its own configurable Web suggestions.
                // Keep local search history, but suppress the drawer's Web provider
                // in Prime Search only to avoid duplicate suggestion sections.
                if (target.packageName == app.lawnchair.search.adapter.HISTORY) Category.HISTORY
                else Category.WEB
            }
            else -> null
        }
    }

    fun filter(context: Context, items: List<SearchAdapterItem>): List<SearchAdapterItem> {
        // Headers/dividers are retained only when their section still has visible results.
        val kept = items.filter { item ->
            val target = item.searchTarget
            val type = target.resultType
            if (type == SearchTargetCompat.RESULT_TYPE_SETTING_TILE) {
                // Replace raw Android intent labels with localized Prime Search settings.
                false
            } else if (type == SearchTargetCompat.RESULT_TYPE_CONTACT_TILE) {
                // Contacts are rendered by PrimeContactSearch so source selection is enforced.
                false
            } else if (type == SearchTargetCompat.RESULT_TYPE_SUGGESTIONS &&
                item.searchTarget.packageName != app.lawnchair.search.adapter.HISTORY) {
                false
            } else {
                category(item)?.let { enabled(context, it) } ?: true
            }
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
