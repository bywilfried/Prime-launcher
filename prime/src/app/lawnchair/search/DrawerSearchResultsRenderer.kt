package app.lawnchair.search

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.theme.effectiveThemeVariant
import com.android.app.search.LayoutType
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import java.util.WeakHashMap

/**
 * Builds drawer section surfaces from the attached result rows instead of
 * painting cards in RecyclerView.ItemDecoration. The original row background
 * stays above the section surface so native ripple feedback is retained.
 */
internal class DrawerSearchResultsRenderer(
    private val appsView: ActivityAllAppsContainerView<*>,
) {
    private val originalBackgrounds = WeakHashMap<View, Drawable?>()

    private val childListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) = styleChild(view)

        override fun onChildViewDetachedFromWindow(view: View) {
            if (originalBackgrounds.containsKey(view)) {
                view.background = originalBackgrounds.remove(view)
            }
        }
    }

    fun attach(recyclerView: RecyclerView) {
        recyclerView.removeOnChildAttachStateChangeListener(childListener)
        recyclerView.addOnChildAttachStateChangeListener(childListener)
        for (index in 0 until recyclerView.childCount) {
            styleChild(recyclerView.getChildAt(index))
        }
    }

    fun styleBoundRow(view: View, position: Int, item: SearchAdapterItem) {
        val recyclerView = appsView.mSearchRecyclerView ?: return
        originalBackgrounds.remove(view)
        view.background = null

        val items = recyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem }
        if (items.getOrNull(position) !== item) return
        val bounds = DrawerSearchSectionModel.sectionBounds(items, position) ?: return
        val (start, end) = bounds
        val category = DrawerSearchSectionModel.categoryForSection(items, start, end) ?: return
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
        val radius = 14f * density
        val top = position == resultPositions.first()
        val bottom = position == resultPositions.last()
        val topLeft = if (top) radius else 0f
        val topRight = if (top) radius else 0f
        val bottomRight = if (bottom) radius else 0f
        val bottomLeft = if (bottom) radius else 0f
        val radii = floatArrayOf(
            topLeft, topLeft, topRight, topRight,
            bottomRight, bottomRight, bottomLeft, bottomLeft,
        )
        val shape = GradientDrawable().apply {
            cornerRadii = radii
            setColor(
                app.lawnchair.theme.ThemeColors.resolve(
                    view.context,
                    app.lawnchair.theme.ThemeProfile.current(view.context),
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND,
                    view.context.effectiveThemeVariant(),
                ),
            )
            setStroke(
                density.toInt().coerceAtLeast(1),
                app.lawnchair.theme.ThemeColors.resolve(
                    view.context,
                    app.lawnchair.theme.ThemeProfile.current(view.context),
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER,
                    view.context.effectiveThemeVariant(),
                ),
            )
        }
        val inset = (4f * density).toInt()
        val surface = LayerDrawable(arrayOf(shape)).apply {
            setLayerInset(0, inset, 0, inset, 0)
        }
        originalBackgrounds[view] = null
        view.background = surface
    }

    private fun styleChild(view: View) {
        val recyclerView = appsView.mSearchRecyclerView ?: return
        val position = recyclerView.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val item = recyclerView.mApps.adapterItems.getOrNull(position) as? SearchAdapterItem ?: return
        styleBoundRow(view, position, item)
    }
}
