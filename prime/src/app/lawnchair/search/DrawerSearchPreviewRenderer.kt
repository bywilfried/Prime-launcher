package app.lawnchair.search

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView

/**
 * Applies drawer-only compact previews and decides when a section expands.
 * Expansion is delegated to the drawer's own category page renderer/provider.
 */
internal class DrawerSearchPreviewRenderer(
    private val appsView: ActivityAllAppsContainerView<*>,
    private val onOpenCategoryPage: (
        PrimeSearchResultPreferences.Category,
        String,
        List<SearchAdapterItem>,
    ) -> Unit,
) {
    private fun isStructuralHeader(item: SearchAdapterItem): Boolean =
        DrawerSearchSectionModel.isStructuralHeader(item)

    private fun categoryForSection(items: List<SearchAdapterItem>, start: Int, end: Int):
        PrimeSearchResultPreferences.Category? =
        DrawerSearchSectionModel.categoryForSection(items, start, end)

    private fun sectionBounds(items: List<SearchAdapterItem>, position: Int): Pair<Int, Int>? =
        DrawerSearchSectionModel.sectionBounds(items, position)

    fun previewLimitPx(
        category: app.lawnchair.prime.search.PrimeSearchResultPreferences.Category,
        view: View,
    ): Int {
        val context = view.context
        val density = context.resources.displayMetrics.density
        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
        if (category == app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS) {
            val rows = app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsRows(context, surface)
            val rowHeight = app.lawnchair.prime.search.PrimeAppGridGeometry.rowHeightPx(
                context,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsIconSizePercent(context, surface),
                if (app.lawnchair.prime.search.PrimeSearchResultPreferences.twoLineAppLabels(context, surface)) 2 else 1,
            )
            return rows * rowHeight
        }
        val heightDp = DrawerSearchCategoryPresentation.compactPreviewHeightDp(category) ?: 190
        return (heightDp * density).toInt().coerceAtLeast(1)
    }

    fun resultRowHeightPx(item: SearchAdapterItem, view: View): Int {
        val density = view.resources.displayMetrics.density
        return when (item.searchTarget.layoutType) {
            LayoutType.THUMBNAIL -> dp(72, view)
            LayoutType.ICON_HORIZONTAL_TEXT -> dp(60, view)
            LayoutType.EMPTY_STATE, LayoutType.SEARCH_SETTINGS -> dp(56, view)
            LayoutType.ICON_SINGLE_VERTICAL_TEXT -> app.lawnchair.prime.search.PrimeAppGridGeometry.rowHeightPx(
                view.context,
                app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsIconSizePercent(
                    view.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
                ),
                if (app.lawnchair.prime.search.PrimeSearchResultPreferences.twoLineAppLabels(
                    view.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
                )) 2 else 1,
            )
            else -> (48 * density).toInt()
        }
    }

    fun applyDrawerPreview(itemView: View, position: Int, item: SearchAdapterItem) {
        val items = appsView.mSearchRecyclerView.mApps.adapterItems
            .mapNotNull { it as? SearchAdapterItem }
        val bounds = sectionBounds(items, position) ?: run {
            itemView.visibility = View.VISIBLE
            return
        }
        val (start, end) = bounds
        val category = categoryForSection(items, start, end) ?: run {
            itemView.visibility = View.VISIBLE
            return
        }
        val resultPositions = (start until end).filter { index ->
            val target = items[index].searchTarget
            target.layoutType != LayoutType.TEXT_HEADER &&
                target.layoutType != LayoutType.EMPTY_DIVIDER
        }
        val limitPx = previewLimitPx(category, itemView)
        if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
            if (isStructuralHeader(item)) return
            val totalHeight = resultPositions.sumOf { index ->
                resultRowHeightPx(items[index], itemView)
            }
            val overflow = if (category == app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS) {
                resultPositions.size > app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsRows(
                    itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
                ) * app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsColumns(
                    itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
                )
            } else totalHeight > limitPx
            val title = itemView.findViewById<TextView>(R.id.title)
            if (overflow) {
                // Keep the category title visible. Replacing it with "Plus de"
                // makes the entire section lose its heading after recycling.
                // The drawer owns this expansion affordance independently.
                val expansionLabel = "Plus de " + when (category) {
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS -> "applications"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> "raccourcis"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS -> "contacts"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES -> "fichiers"
                    else -> "résultats"
                } + "  ⌄"
                title?.contentDescription = "${title.text}. $expansionLabel"
                itemView.minimumHeight = dp(40, itemView)
                itemView.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                itemView.isClickable = true
                itemView.setOnClickListener {
                    val heading = items.getOrNull(start - 1)?.searchTarget?.searchAction?.title
                        ?.toString()?.trim().orEmpty().ifBlank { "Résultats" }
                    val categoryResults = resultPositions.mapNotNull(items::getOrNull)
                    onOpenCategoryPage(category, heading, categoryResults)
                }
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
            return
        }

        val resultIndex = resultPositions.indexOf(position)
        if (resultIndex < 0) return
        val previousHeight = resultPositions.take(resultIndex).sumOf { index ->
            resultRowHeightPx(items[index], itemView)
        }
        val currentHeight = resultRowHeightPx(item, itemView)
        val isApps = category == app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS
        val maxAppItems = app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsRows(
            itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
        ) * app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsColumns(
            itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER,
        )
        val shouldShow = if (isApps) resultIndex < maxAppItems
            else previousHeight == 0 || previousHeight + currentHeight <= limitPx
        itemView.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (shouldShow) {
            itemView.minimumHeight = 0
        } else {
            // RecyclerView's grid still measures GONE children; collapse the
            // hidden preview entries so they cannot overlap the next section.
            itemView.minimumHeight = 0
            itemView.layoutParams?.height = 0
        }
    }

    private fun dp(value: Int, view: View): Int =
        (value * view.resources.displayMetrics.density).toInt()
}
