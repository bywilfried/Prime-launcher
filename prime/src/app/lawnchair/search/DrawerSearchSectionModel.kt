package app.lawnchair.search

import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType

/**
 * Converts the drawer's flat launcher adapter stream into category boundaries.
 * Kept separate from the view provider so a drawer-owned section adapter can
 * consume the same grouping rules without depending on Prime Search.
 */
internal object DrawerSearchSectionModel {
    fun isStructuralHeader(item: SearchAdapterItem): Boolean {
        if (item.searchTarget.layoutType != LayoutType.TEXT_HEADER) return false
        val title = item.searchTarget.searchAction?.title?.toString()?.trim().orEmpty()
        return title.equals(app.lawnchair.search.adapter.SPACE, true) ||
            title.equals(app.lawnchair.search.adapter.SPACE_MINI, true)
    }

    fun categoryForSection(items: List<SearchAdapterItem>, start: Int, end: Int):
        app.lawnchair.prime.search.PrimeSearchResultPreferences.Category? {
        // Prefer the same heading-to-category registry used by Prime Search.
        // This preserves distinct sections such as Web, Calendar, Reminders and
        // Notifications instead of guessing from a provider package name.
        val heading = items.getOrNull(start - 1)?.searchTarget?.searchAction?.title?.toString()
        DrawerSearchCategoryPresentation.categoryForHeading(heading)?.let { return it }
        for (index in start until end) {
            val target = items[index].searchTarget
            val category = DrawerSearchCategoryPresentation.categoryForResult(target)
            if (category != null) return category
        }
        return null
    }

    fun sectionBounds(items: List<SearchAdapterItem>, position: Int): Pair<Int, Int>? {
        var start = -1
        for (index in position downTo 0) {
            val item = items[index]
            if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER && !isStructuralHeader(item)) {
                start = index + 1
                break
            }
        }
        if (start < 0) return null
        var end = items.size
        for (index in position + 1 until items.size) {
            val item = items[index]
            if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER && !isStructuralHeader(item)) {
                end = index
                break
            }
        }
        return start to end
    }


}
