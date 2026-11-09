package app.lawnchair.search

import android.view.View
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.allapps.ActivityAllAppsContainerView

/**
 * Applies one continuous drawer-owned surface across a section heading and its
 * result rows. Native row backgrounds remain layered above it for ripple feedback.
 */
internal class DrawerSearchResultsRenderer(
    private val appsView: ActivityAllAppsContainerView<*>,
) {
    fun styleBoundRow(view: View, position: Int, item: SearchAdapterItem) {
        val recyclerView = appsView.mSearchRecyclerView ?: return
        val items = recyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem }
        if (items.getOrNull(position) !== item) return
        if (item.searchTarget.layoutType == LayoutType.EMPTY_DIVIDER ||
            DrawerSearchSectionModel.isStructuralHeader(item)
        ) return

        val isHeading = item.searchTarget.layoutType == LayoutType.TEXT_HEADER
        val firstResultPosition = if (isHeading) {
            var first: Int? = null
            for (index in position + 1 until items.size) {
                val candidate = items[index]
                if (candidate.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
                    if (!DrawerSearchSectionModel.isStructuralHeader(candidate)) break
                    continue
                }
                if (candidate.searchTarget.layoutType == LayoutType.EMPTY_DIVIDER) continue
                first = index
                break
            }
            first ?: return
        } else {
            position
        }
        val bounds = DrawerSearchSectionModel.sectionBounds(items, firstResultPosition) ?: return
        val (start, end) = bounds
        if (position !in start until end && !isHeading) return
        DrawerSearchSectionModel.categoryForSection(items, start, end) ?: return

        val resultPositions = (start until end).filter { index ->
            items[index].searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                items[index].searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
        }
        if (resultPositions.isEmpty()) return

        val headingPosition = (start - 1).takeIf { index ->
            index >= 0 &&
                items[index].searchTarget.layoutType == LayoutType.TEXT_HEADER &&
                !DrawerSearchSectionModel.isStructuralHeader(items[index])
        }
        if (isHeading && position != headingPosition) return
        if (!isHeading && position !in resultPositions) return

        val density = view.resources.displayMetrics.density
        // The heading owns the top edge whenever it exists; otherwise the first
        // result row starts the card. This avoids a seam between separate surfaces.
        val top = if (headingPosition != null) position == headingPosition
            else position == resultPositions.first()
        val bottom = !isHeading && position == resultPositions.last()
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
