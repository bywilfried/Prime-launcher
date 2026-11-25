package app.lawnchair.search

import app.lawnchair.prime.search.PrimeSearchResultPreferences

/**
 * Drawer-owned category identity and compact preview rules.
 *
 * The drawer does not depend on PrimeSearchCategoryPresentation. It shares
 * only category identifiers with the surface-scoped preference store.
 */
internal object DrawerSearchCategoryPresentation {
    private val headingAliases = mapOf(
        "Suggestions Web" to PrimeSearchResultPreferences.Category.WEB,
        "Depuis le Web" to PrimeSearchResultPreferences.Category.WEB,
        "Raccourcis d'app" to PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
        "Raccourcis d’app" to PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
        "Contacts" to PrimeSearchResultPreferences.Category.CONTACTS,
        "Contacts de l’appareil" to PrimeSearchResultPreferences.Category.CONTACTS,
        "Contacts de l'appareil" to PrimeSearchResultPreferences.Category.CONTACTS,
        "Fichiers de l'appareil" to PrimeSearchResultPreferences.Category.FILES,
        "Fichiers de l’appareil" to PrimeSearchResultPreferences.Category.FILES,
        "Événements de l'agenda" to PrimeSearchResultPreferences.Category.CALENDAR,
        "Agenda" to PrimeSearchResultPreferences.Category.CALENDAR,
        "Tâches · Tasks.org" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Tâches" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Historique des notifications" to PrimeSearchResultPreferences.Category.NOTIFICATIONS,
        "Notifications" to PrimeSearchResultPreferences.Category.NOTIFICATIONS,
        "Paramètres de l'appareil" to PrimeSearchResultPreferences.Category.SETTINGS,
        "Device settings" to PrimeSearchResultPreferences.Category.SETTINGS,
        "Raccourcis Web" to PrimeSearchResultPreferences.Category.WEB_SHORTCUTS,
    )

    fun categoryForHeading(title: String?): PrimeSearchResultPreferences.Category? {
        val normalized = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return headingAliases[normalized]
            ?: PrimeSearchResultPreferences.Category.values().firstOrNull {
                it.title.equals(normalized, ignoreCase = true)
            }
    }


    /**
     * Maps launcher result types to drawer-owned categories. Keeping this
     * registry here prevents section rendering and detail rows from drifting.
     */
    fun categoryForResultType(resultType: Int): PrimeSearchResultPreferences.Category? =
        when (resultType) {
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_APPLICATION ->
                PrimeSearchResultPreferences.Category.APPS
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_SHORTCUT ->
                PrimeSearchResultPreferences.Category.APP_SHORTCUTS
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_CONTACT_TILE ->
                PrimeSearchResultPreferences.Category.CONTACTS
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_FILE_TILE ->
                PrimeSearchResultPreferences.Category.FILES
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_SETTING_TILE ->
                PrimeSearchResultPreferences.Category.SETTINGS
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_CALCULATOR ->
                PrimeSearchResultPreferences.Category.CALCULATOR
            app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_SUGGESTIONS ->
                PrimeSearchResultPreferences.Category.HISTORY
            else -> null
        }

    fun categoryForResult(target: app.lawnchair.search.adapter.SearchTargetCompat):
        PrimeSearchResultPreferences.Category? =
        if (target.resultType == app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_SUGGESTIONS) {
            when (target.packageName) {
                app.lawnchair.search.adapter.HISTORY ->
                    PrimeSearchResultPreferences.Category.HISTORY
                "Agenda" -> PrimeSearchResultPreferences.Category.CALENDAR
                "Tâches" -> PrimeSearchResultPreferences.Category.REMINDERS
                "Notifications" -> PrimeSearchResultPreferences.Category.NOTIFICATIONS
                else -> PrimeSearchResultPreferences.Category.WEB
            }
        } else {
            categoryForResultType(target.resultType)
        }

    fun compactPreviewHeightDp(category: PrimeSearchResultPreferences.Category): Int? =
        when (category) {
            PrimeSearchResultPreferences.Category.APPS -> null
            PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> 160
            else -> 190
        }
}
