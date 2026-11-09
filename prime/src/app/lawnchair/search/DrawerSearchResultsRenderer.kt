package app.lawnchair.search

import android.view.View
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.allapps.ActivityAllAppsContainerView

/**
 * Applies a drawer-owned section surface to each native result row. The row's
 * native feedback drawable is layered above the surface and remains interactive.
 */
internal class DrawerSearchResultsRenderer(
    private val appsView: ActivityAllAppsContainerView<*>,
) {
    fun styleBoundRow(view: View, position: Int, item: SearchAdapterItem) {
        val recyclerView = appsView.mSearchRecyclerView ?: return
        val items = recyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem }
        if (items.getOrNull(position) !== item) return
        val bounds = DrawerSearchSectionModel.sectionBounds(items, position) ?: return
        val (start, end) = bounds
        DrawerSearchSectionModel.categoryForSection(items, start, end) ?: return
        if (item.searchTarget.layoutType == LayoutType.EMPTY_DIVIDER ||
            DrawerSearchSectionModel.isStructuralHeader(item)
        ) return

        val resultPositions = (start until end).filter { index ->
            items[index].searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                items[index].searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
        }
        if (resultPositions.isEmpty()) return

        val density = view.resources.displayMetrics.density
        val isSectionHeading = item.searchTarget.layoutType == LayoutType.TEXT_HEADER && position == start - 1
        val top = isSectionHeading || position == resultPositions.first()
        val bottom = position == resultPositions.last()
        val sectionSurface = android.graphics.drawable.InsetDrawable(
            DrawerSearchSectionBackground(view.context, top, bottom),
            (4f * density).toInt(), 0, (4f * density).toInt(), 0,
        )
        val feedback = view.background
        view.background = if (feedback == null) {
            sectionSurface
        } else {
            android.graphics.drawable.LayerDrawable(arrayOf(sectionSurface, feedback))
        }
    }
}
