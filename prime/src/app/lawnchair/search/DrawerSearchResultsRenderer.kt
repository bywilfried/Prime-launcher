package app.lawnchair.search

import android.view.View
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.allapps.ActivityAllAppsContainerView

/**
 * Builds drawer section surfaces from the attached result rows instead of
 * painting cards in RecyclerView.ItemDecoration. The original row background
 * stays above the section surface so native ripple feedback is retained.
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
            item.searchTarget.layoutType == LayoutType.TEXT_HEADER ||
            DrawerSearchSectionModel.isStructuralHeader(item)
        ) return

        val resultPositions = (start until end).filter { index ->
            items[index].searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                items[index].searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
        }
        if (resultPositions.isEmpty()) return

        val density = view.resources.displayMetrics.density
        val top = position == resultPositions.first()
        val bottom = position == resultPositions.last()
        val sectionSurface = DrawerSearchSectionBackground(view.context, top, bottom)
        val inset = (4f * density).toInt()
        val surface = DrawerSearchSectionBackground(view.context, top, bottom).apply {
            setBounds(0, 0, view.width, view.height)
        }
        view.background = android.graphics.drawable.InsetDrawable(sectionSurface, inset)
    }
}
