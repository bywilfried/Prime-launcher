package app.lawnchair.search

import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.allapps.BaseAllAppsAdapter

/**
 * One RecyclerView item per drawer search category. The section owns its
 * heading, child result views, clipping and expansion action.
 */
internal class DrawerSearchSectionAdapterItem(
    val category: PrimeSearchResultPreferences.Category,
    val heading: String,
    val results: List<SearchAdapterItem>,
) : BaseAllAppsAdapter.AdapterItem(VIEW_TYPE) {
    override fun isSameAs(other: BaseAllAppsAdapter.AdapterItem): Boolean =
        other is DrawerSearchSectionAdapterItem && category == other.category

    override fun isContentSame(other: BaseAllAppsAdapter.AdapterItem): Boolean =
        other is DrawerSearchSectionAdapterItem &&
            heading == other.heading &&
            results.map { it.searchTarget } == other.results.map { it.searchTarget }

    companion object {
        const val VIEW_TYPE = 1 shl 23

        fun group(items: List<SearchAdapterItem>): List<BaseAllAppsAdapter.AdapterItem> {
            val output = mutableListOf<BaseAllAppsAdapter.AdapterItem>()
            var index = 0
            while (index < items.size) {
                val current = items[index]
                val target = current.searchTarget
                // Settings shortcut belongs in the empty search bar, never in results.
                if (target.layoutType == LayoutType.SEARCH_SETTINGS) {
                    index++
                    continue
                }
                if (DrawerSearchSectionModel.isStructuralHeader(current)) {
                    index++
                    continue
                }
                val isHeading = target.layoutType == LayoutType.TEXT_HEADER
                val title = target.searchAction?.title?.toString().orEmpty()
                if (isHeading && title.startsWith("Plus de")) {
                    // Expansion is owned by the section container.
                    index++
                    continue
                }
                val category = if (isHeading) {
                    DrawerSearchCategoryPresentation.categoryForHeading(title)
                } else {
                    DrawerSearchCategoryPresentation.categoryForResult(target)
                }
                if (category == null) {
                    output.add(current)
                    index++
                    continue
                }

                val results = mutableListOf<SearchAdapterItem>()
                var end = index + if (isHeading) 1 else 0
                while (end < items.size) {
                    val candidate = items[end]
                    val candidateTarget = candidate.searchTarget
                    if (candidateTarget.layoutType == LayoutType.SEARCH_SETTINGS) {
                        end++
                        continue
                    }
                    if (DrawerSearchSectionModel.isStructuralHeader(candidate)) {
                        end++
                        continue
                    }
                    if (candidateTarget.layoutType == LayoutType.TEXT_HEADER) {
                        val nextTitle = candidateTarget.searchAction?.title?.toString().orEmpty()
                        // The provider may emit a broad section heading and
                        // a second heading for the same category.
                        if (DrawerSearchCategoryPresentation.categoryForHeading(nextTitle) == category) {
                            end++
                            continue
                        }
                        break
                    }
                    // A missing heading must not merge unrelated result categories.
                    // A provider heading is authoritative: its children can use
                    // generic result types (notably Web shortcuts and entries).
                    // For heading-less sections, category changes delimit cards.
                    if (!isHeading &&
                        DrawerSearchCategoryPresentation.categoryForResult(candidateTarget) != category
                    ) break
                    results.add(candidate)
                    end++
                }
                if (results.isNotEmpty()) {
                    output.add(DrawerSearchSectionAdapterItem(
                        category,
                        if (isHeading) title else category.title,
                        results,
                    ))
                    index = end
                } else {
                    // Do not leak orphan headings outside the card layout.
                    // A heading without results has no visible section.
                    index = if (isHeading) end.coerceAtLeast(index + 1) else index + 1
                }
            }
            return output
        }
    }
}
