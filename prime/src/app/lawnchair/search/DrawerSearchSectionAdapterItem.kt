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
                val item = items[index]
                val target = item.searchTarget
                if (target.layoutType != LayoutType.TEXT_HEADER ||
                    DrawerSearchSectionModel.isStructuralHeader(item) ||
                    target.searchAction?.title?.toString()?.startsWith("Plus de") == true
                ) {
                    // Structural separators are not visible content.
                    if (!DrawerSearchSectionModel.isStructuralHeader(item)) output.add(item)
                    index++
                    continue
                }
                val heading = target.searchAction?.title?.toString().orEmpty()
                val category = DrawerSearchCategoryPresentation.categoryForHeading(heading)
                var end = index + 1
                while (end < items.size) {
                    val candidate = items[end]
                    if (candidate.searchTarget.layoutType == LayoutType.TEXT_HEADER &&
                        !DrawerSearchSectionModel.isStructuralHeader(candidate)
                    ) break
                    end++
                }
                val results = items.subList(index + 1, end).filter {
                    it.searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                        it.searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
                }
                if (category != null && results.isNotEmpty()) {
                    output.add(DrawerSearchSectionAdapterItem(category, heading, results))
                } else {
                    output.add(item)
                    output.addAll(items.subList(index + 1, end).filterNot {
                        DrawerSearchSectionModel.isStructuralHeader(it)
                    })
                }
                index = end
            }
            return output
        }
    }
}
