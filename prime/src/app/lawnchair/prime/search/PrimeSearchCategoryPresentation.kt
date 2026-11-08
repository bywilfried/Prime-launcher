package app.lawnchair.prime.search

import android.content.Context
import app.lawnchair.search.algorithms.engine.ActionsSectionBuilder
import app.lawnchair.search.algorithms.engine.AppsAndShortcutsSectionBuilder
import app.lawnchair.search.algorithms.engine.CalculationSectionBuilder
import app.lawnchair.search.algorithms.engine.ContactsSectionBuilder
import app.lawnchair.search.algorithms.engine.FilesSectionBuilder
import app.lawnchair.search.algorithms.engine.EmptyStateSectionBuilder
import app.lawnchair.search.algorithms.engine.HistorySectionBuilder
import app.lawnchair.search.algorithms.engine.PrimeEntriesSectionBuilder
import app.lawnchair.search.algorithms.engine.SectionBuilder
import app.lawnchair.search.algorithms.engine.SearchSettingsSectionBuilder
import app.lawnchair.search.algorithms.engine.SettingsSectionBuilder
import app.lawnchair.search.algorithms.engine.WebSuggestionsSectionBuilder

/**
 * Presentation rules shared by the drawer and Prime Search.
 *
 * Surface-specific visibility, order and colours remain in
 * [PrimeSearchResultPreferences]; this object only describes category identity
 * and the common compact-preview geometry.
 */
object PrimeSearchCategoryPresentation {

    private val builderByCategory = mapOf(
        PrimeSearchResultPreferences.Category.APPS to AppsAndShortcutsSectionBuilder,
        PrimeSearchResultPreferences.Category.CALCULATOR to CalculationSectionBuilder,
        PrimeSearchResultPreferences.Category.WEB to WebSuggestionsSectionBuilder,
        PrimeSearchResultPreferences.Category.CONTACTS to ContactsSectionBuilder,
        PrimeSearchResultPreferences.Category.FILES to FilesSectionBuilder,
        PrimeSearchResultPreferences.Category.SETTINGS to SettingsSectionBuilder,
        PrimeSearchResultPreferences.Category.HISTORY to HistorySectionBuilder,
        PrimeSearchResultPreferences.Category.CALENDAR to PrimeEntriesSectionBuilder,
    )

    private val headings = mapOf(
        "Suggestions Web" to PrimeSearchResultPreferences.Category.WEB,
        "Contacts" to PrimeSearchResultPreferences.Category.CONTACTS,
        "Événements de l'agenda" to PrimeSearchResultPreferences.Category.CALENDAR,
        "Tâches · Tasks.org" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Tâches" to PrimeSearchResultPreferences.Category.REMINDERS,
        "Historique des notifications" to PrimeSearchResultPreferences.Category.NOTIFICATIONS,
        "Paramètres de l'appareil" to PrimeSearchResultPreferences.Category.SETTINGS,
        "Device settings" to PrimeSearchResultPreferences.Category.SETTINGS,
        "Raccourcis Web" to PrimeSearchResultPreferences.Category.WEB_SHORTCUTS,
    )

    fun categoryForHeading(title: String?): PrimeSearchResultPreferences.Category? =
        title?.trim()?.let(headings::get)

    fun orderedDrawerBuilders(context: Context): List<SectionBuilder> {
        val surface = PrimeSearchResultPreferences.Surface.DRAWER
        val ordered = PrimeSearchResultPreferences.order(context, surface)
            .mapNotNull(builderByCategory::get)
            .distinct()
        return ordered + listOf(
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
        ).filterNot { it in ordered }
    }

    /** Compact card preview heights, in dp. App grids use their measured row height. */
    fun compactPreviewHeightDp(category: PrimeSearchResultPreferences.Category): Int? =
        when (category) {
            PrimeSearchResultPreferences.Category.APPS -> null
            PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> 160
            else -> 190
        }
}
