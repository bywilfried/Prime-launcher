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
        "Raccourcis d'app" to PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
        "Raccourcis d’app" to PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
        "Contacts" to PrimeSearchResultPreferences.Category.CONTACTS,
        "Événements de l'agenda" to PrimeSearchResultPreferences.Category.CALENDAR,
        "Tâches · Tasks.org" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Tâches" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Historique des notifications" to PrimeSearchResultPreferences.Category.NOTIFICATIONS,
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

    fun compactPreviewHeightDp(category: PrimeSearchResultPreferences.Category): Int? =
        when (category) {
            PrimeSearchResultPreferences.Category.APPS -> null
            PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> 160
            else -> 190
        }
}
