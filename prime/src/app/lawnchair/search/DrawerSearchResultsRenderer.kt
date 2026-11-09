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
import com.android.launcher3.R
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

    private fun styleChild(view: View) {
        val recyclerView = appsView.mSearchRecyclerView ?: return
        val position = recyclerView.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val items = recyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem }
        val item = items.getOrNull(position) ?: return
        val bounds = DrawerSearchSectionModel.sectionBounds(items, position) ?: return
        val (start, end) = bounds
        val category = DrawerSearchSectionModel.categoryForSection(items, start, end) ?: return
        if (item.searchTarget.layoutType == LayoutType.EMPTY_DIVIDER ||
            DrawerSearchSectionModel.isStructuralHeader(item)
        ) {
            return
        }

        val title = if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
            view.findViewById<TextView>(R.id.title)?.text?.toString()?.trim().orEmpty()
        } else ""
        if (title.startsWith("Plus de ", ignoreCase = true) ||
            title.startsWith("Réduire", ignoreCase = true)
        ) return

        val resultPositions = (start until end).filter { index ->
            items[index].searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                items[index].searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
        }
        if (resultPositions.isEmpty()) return

        val firstPosition = start - 1
        val lastPosition = resultPositions.last()
        val density = view.resources.displayMetrics.density
        val radius = 14f * density
        val top = position == firstPosition
        val bottom = position == lastPosition
        val radii = floatArrayOf(
            if (top) radius else 0f, if (top) radius else 0f,
            if (top) radius else 0f, if (top) radius else 0f,
            if (bottom) radius else 0f, if (bottom) radius else 0f,
            if (bottom) radius else 0f, if (bottom) radius else 0f,
        )

        val context = view.context
        val profile = app.lawnchair.theme.ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        val backgroundColor = app.lawnchair.theme.ThemeColors.resolve(
            context, profile,
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND, variant,
        )
        val borderColor = app.lawnchair.theme.ThemeColors.resolve(
            context, profile,
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER, variant,
        )
        val sectionSurface = GradientDrawable().apply {
            cornerRadii = radii
            setColor(backgroundColor)
            setStroke((density).toInt().coerceAtLeast(1), borderColor)
        }

        if (!originalBackgrounds.containsKey(view)) {
            originalBackgrounds[view] = view.background
        }
        val original = originalBackgrounds[view]
        val layers = if (original == null) {
            LayerDrawable(arrayOf(sectionSurface))
        } else {
            LayerDrawable(arrayOf(sectionSurface, original))
        }
        val inset = (4f * density).toInt()
        layers.setLayerInset(0, inset, 0, inset, 0)
        view.background = layers
    }
}
