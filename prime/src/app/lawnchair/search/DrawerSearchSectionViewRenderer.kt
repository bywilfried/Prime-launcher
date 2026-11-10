package app.lawnchair.search

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.allapps.views.SearchResultIcon
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.prime.search.PrimeAppGridGeometry
import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.adapter.SearchAdapterItem
import com.android.app.search.LayoutType
import com.android.launcher3.R

/**
 * Drawer-only category card: a single parent view owns all of its native
 * result children. No per-row RecyclerView backgrounds or hidden grid cells.
 */
internal class DrawerSearchSectionViewRenderer(
    private val openDetails: (PrimeSearchResultPreferences.Category, String, List<SearchAdapterItem>) -> Unit,
    private val onQuickLaunch: (SearchResultView) -> Unit,
) {
    private fun dp(view: View, value: Int): Int =
        (view.resources.displayMetrics.density * value).toInt()

    fun create(parent: ViewGroup?): LinearLayout {
        val context = requireNotNull(parent).context
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val margin = (context.resources.displayMetrics.density * 5).toInt()
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(margin, margin, margin, margin) }
            setPadding(dp(this, 16), dp(this, 12), dp(this, 16), dp(this, 8))
            background = DrawerSearchSectionBackground(context, true, true)
            clipChildren = false
        }
    }

    fun bind(card: LinearLayout, section: DrawerSearchSectionAdapterItem) {
        card.removeAllViews()
        val context = card.context
        val prefs = PrimeSearchResultPreferences
        val surface = PrimeSearchResultPreferences.Surface.DRAWER
        val heading = TextView(context).apply {
            text = when (section.category) {
                PrimeSearchResultPreferences.Category.WEB -> "Suggestions Web"
                PrimeSearchResultPreferences.Category.FILES -> "Fichiers de l’appareil"
                else -> section.heading
            }
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(card, 12))
        }
        card.addView(heading, LinearLayout.LayoutParams(-1, -2))

        val isApps = section.category == PrimeSearchResultPreferences.Category.APPS
        val columns = prefs.appResultsColumns(context, surface).coerceAtLeast(1)
        val maxRows = prefs.appResultsRows(context, surface).coerceAtLeast(1)
        val visible = if (isApps) {
            section.results.take(columns * maxRows)
        } else {
            val maxHeight = dp(card, DrawerSearchCategoryPresentation.compactPreviewHeightDp(section.category) ?: 190)
            val result = mutableListOf<SearchAdapterItem>()
            var height = 0
            for (item in section.results) {
                val rowHeight = when (item.searchTarget.layoutType) {
                    LayoutType.THUMBNAIL -> 72
                    LayoutType.ICON_HORIZONTAL_TEXT -> 60
                    else -> 48
                }
                if (result.isNotEmpty() && height + dp(card, rowHeight) > maxHeight) break
                result.add(item)
                height += dp(card, rowHeight)
            }
            result
        }
        if (isApps) {
            visible.chunked(columns).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                card.addView(row, LinearLayout.LayoutParams(-1, -2))
                rowItems.forEach { item ->
                    val child = nativeResult(card, item)
                    if (child is SearchResultIcon) {
                        child.setPrimeIconSize(PrimeAppGridGeometry.iconPx(
                            context, prefs.appResultsIconSizePercent(context, surface),
                        ))
                        child.setTextVisibility(true)
                        child.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, PrimeAppGridGeometry.LABEL_SP)
                        child.includeFontPadding = false
                        child.setPrimeSearchLabelLayout(true)
                        child.setSingleLine(false)
                        child.setHorizontallyScrolling(false)
                        val labelLines = if (prefs.twoLineAppLabels(context, surface)) 2 else 1
                        child.maxLines = labelLines
                        child.ellipsize = if (labelLines == 2) null else android.text.TextUtils.TruncateAt.END
                        child.breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
                        child.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        applyDrawerLabelWrapping(child, labelLines)
                    }
                    row.addView(child, LinearLayout.LayoutParams(
                        0,
                        PrimeAppGridGeometry.rowHeightPx(
                            context, prefs.appResultsIconSizePercent(context, surface),
                            if (prefs.twoLineAppLabels(context, surface)) 2 else 1,
                        ),
                        1f,
                    ))
                }
                repeat(columns - rowItems.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
        } else {
            visible.forEach { item ->
                val child = nativeResult(card, item)
                card.addView(child, LinearLayout.LayoutParams(-1, dp(card, when (item.searchTarget.layoutType) {
                    LayoutType.THUMBNAIL -> 72
                    LayoutType.ICON_HORIZONTAL_TEXT -> 60
                    else -> 48
                })))
            }
        }
        if (section.results.size > visible.size) {
            val suffix = when (section.category) {
                PrimeSearchResultPreferences.Category.APPS -> "applications"
                PrimeSearchResultPreferences.Category.CONTACTS -> "contacts"
                PrimeSearchResultPreferences.Category.FILES -> "fichiers"
                PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> "raccourcis"
                else -> "résultats"
            }
            card.addView(TextView(context).apply {
                text = "Plus de $suffix ⌄"
                textSize = 14f
                gravity = Gravity.CENTER
                minHeight = dp(card, 44)
                isClickable = true
                isFocusable = true
                setOnClickListener { openDetails(section.category, section.heading, section.results) }
            }, LinearLayout.LayoutParams(-1, dp(card, 44)))
        }
    }

    private fun nativeResult(parent: ViewGroup, item: SearchAdapterItem): View {
        val layout = LawnchairSearchAdapterProvider.layoutForViewType(item.viewType)
            ?: R.layout.search_result_small_icon_row
        val child = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        (child as? SearchResultView)?.let { result ->
            result.bind(item.searchTarget, emptyList())
            if (result.isQuickLaunch) onQuickLaunch(result)
        }
        // Prime entry targets use a generic monochrome search drawable.
        // On some icon themes it becomes a solid white square; show an
        // explicit category symbol instead of the unreadable placeholder.
        val category = DrawerSearchCategoryPresentation.categoryForResult(item.searchTarget)
        val symbol = when (category) {
            PrimeSearchResultPreferences.Category.CALENDAR -> "📅"
            PrimeSearchResultPreferences.Category.REMINDERS -> "✓"
            PrimeSearchResultPreferences.Category.NOTIFICATIONS -> "🔔"
            else -> null
        }
        if (symbol != null && child is LinearLayout) {
            child.findViewById<View>(R.id.icon)?.visibility = View.GONE
            val marker = TextView(parent.context).apply {
                text = symbol
                textSize = 23f
                gravity = Gravity.CENTER
                contentDescription = when (category) {
                    PrimeSearchResultPreferences.Category.CALENDAR -> "Agenda"
                    PrimeSearchResultPreferences.Category.REMINDERS -> "Tâches"
                    else -> "Notifications"
                }
            }
            child.addView(marker, 0, LinearLayout.LayoutParams(dp(child, 56), -1))
        }
        item.setRippleEffect(child)
        return child
    }
}
