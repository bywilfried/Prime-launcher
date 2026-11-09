package app.lawnchair.search

import android.graphics.Paint
import android.graphics.RectF
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.theme.effectiveThemeVariant
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView

/**
 * Owns the drawer's category-card composition independently from Prime Search.
 *
 * This is the first renderer boundary: the provider only installs this
 * drawer-owned renderer, while card grouping and drawing live here.
 */
internal class DrawerSearchResultsRenderer(
    private val appsView: ActivityAllAppsContainerView<*>,
) {
    private val categoryCards = object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
        private val density = appsView.resources.displayMetrics.density
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(
            canvas: android.graphics.Canvas,
            parent: androidx.recyclerview.widget.RecyclerView,
            state: androidx.recyclerview.widget.RecyclerView.State,
        ) {
            val children = (0 until parent.childCount).mapNotNull { index ->
                val child = parent.getChildAt(index)
                val position = parent.getChildAdapterPosition(child)
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) null
                else Triple(position, child, appsView.mSearchRecyclerView.mApps.adapterItems.getOrNull(position) as? SearchAdapterItem)
            }.sortedBy { it.first }

            var start: android.view.View? = null
            var end: android.view.View? = null

            // A category heading can scroll above the viewport while its rows
            // remain visible. Continue painting that card from the first visible
            // row instead of dropping its background during a fling.
            val firstVisible = children.firstOrNull()
            if (firstVisible != null) {
                val firstItem = firstVisible.third
                val firstIsHeading = firstItem?.searchTarget?.layoutType == LayoutType.TEXT_HEADER
                if (!firstIsHeading) {
                    val adapterItems = appsView.mSearchRecyclerView.mApps.adapterItems
                    for (index in firstVisible.first - 1 downTo 0) {
                        val previous = adapterItems.getOrNull(index) as? SearchAdapterItem ?: continue
                        if (previous.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
                            val title = previous.searchTarget.searchAction?.title?.toString()?.trim().orEmpty()
                            if (!title.equals(app.lawnchair.search.adapter.SPACE, true) &&
                                !title.equals(app.lawnchair.search.adapter.SPACE_MINI, true)) {
                                start = firstVisible.second
                                end = firstVisible.second
                            }
                            break
                        }
                    }
                }
            }

            fun drawSection() {
                val first = start ?: return
                val last = end ?: return
                val top = first.top.toFloat() + first.translationY
                val bottom = last.bottom.toFloat() + last.translationY
                if (bottom <= top) return
                val profile = app.lawnchair.theme.ThemeProfile.current(parent.context)
                val variant = parent.context.effectiveThemeVariant()
                val background = app.lawnchair.theme.ThemeColors.resolve(
                    parent.context, profile,
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND, variant,
                )
                val border = app.lawnchair.theme.ThemeColors.resolve(
                    parent.context, profile,
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER, variant,
                )
                val inset = 4f * density
                val rect = android.graphics.RectF(
                    parent.paddingLeft + inset, top,
                    parent.width - parent.paddingRight - inset, bottom,
                )
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = background
                canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = density
                paint.color = border
                canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)
            }

            children.forEach { (_, child, item) ->
                val isHeading = item?.searchTarget?.layoutType == LayoutType.TEXT_HEADER
                val headingText = if (isHeading) child.findViewById<TextView>(R.id.title)?.text?.toString()?.trim().orEmpty() else ""
                val structural = headingText.equals(app.lawnchair.search.adapter.SPACE, true) ||
                    headingText.equals(app.lawnchair.search.adapter.SPACE_MINI, true) ||
                    headingText.startsWith("Plus de ", ignoreCase = true) ||
                    headingText.startsWith("Réduire", ignoreCase = true)
                if (isHeading && !structural) {
                    drawSection()
                    start = child
                    end = child
                } else if (start != null && item?.searchTarget?.layoutType != LayoutType.EMPTY_DIVIDER) {
                    end = child
                }
            }
            drawSection()
        }
    }


    fun attach(recyclerView: RecyclerView) {
        if ((0 until recyclerView.itemDecorationCount).none {
                recyclerView.getItemDecorationAt(it) === categoryCards
            }) {
            recyclerView.addItemDecoration(categoryCards)
        }
    }
}
