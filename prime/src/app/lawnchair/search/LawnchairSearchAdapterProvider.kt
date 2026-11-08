package app.lawnchair.search

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Typeface
import android.util.TypedValue
import app.lawnchair.theme.effectiveThemeVariant
import androidx.core.util.contains
import app.lawnchair.allapps.views.SearchItemDecorator
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import com.android.app.search.LayoutType
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.views.ActivityContext

class LawnchairSearchAdapterProvider(
    launcher: ActivityContext,
    private val appsView: ActivityAllAppsContainerView<*>,
) : DefaultSearchAdapterProvider(launcher) {

    private val decorator = SearchItemDecorator(appsView)

    /**
     * Draw one continuous surface for each category section in the drawer's
     * existing search adapter. The TEXT_HEADER is part of that surface, so
     * category titles sit inside their card instead of floating above rows.
     */
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

    init {
        // The adapter provider is created from ActivityAllAppsContainerView.initContent(),
        // before mSearchRecyclerView is assigned. Defer decoration installation until
        // the container has completed inflation to avoid crashing launcher startup.
        appsView.post {
            appsView.mSearchRecyclerView?.let { recyclerView ->
                if ((0 until recyclerView.itemDecorationCount).none {
                        recyclerView.getItemDecorationAt(it) === categoryCards
                    }) {
                    recyclerView.addItemDecoration(categoryCards)
                }
            }
        }
    }
    /**
     * The compact drawer preview stays clipped. "Plus de…" transfers the
     * category and active query to Prime Search's shared detail-page renderer;
     * the drawer no longer owns a second category-results dialog.
     */

    private fun isStructuralHeader(item: SearchAdapterItem): Boolean {
        if (item.searchTarget.layoutType != LayoutType.TEXT_HEADER) return false
        val title = item.searchTarget.searchAction?.title?.toString()?.trim().orEmpty()
        return title.equals(app.lawnchair.search.adapter.SPACE, true) ||
            title.equals(app.lawnchair.search.adapter.SPACE_MINI, true)
    }

    private fun categoryForSection(items: List<SearchAdapterItem>, start: Int, end: Int):
        app.lawnchair.prime.search.PrimeSearchResultPreferences.Category? {
        // Prefer the same heading-to-category registry used by Prime Search.
        // This preserves distinct sections such as Web, Calendar, Reminders and
        // Notifications instead of guessing from a provider package name.
        val heading = items.getOrNull(start - 1)?.searchTarget?.searchAction?.title?.toString()
        app.lawnchair.prime.search.PrimeSearchCategoryPresentation
            .categoryForHeading(heading)?.let { return it }
        for (index in start until end) {
            val target = items[index].searchTarget
            return when (target.resultType) {
                SearchTargetCompat.RESULT_TYPE_APPLICATION -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS
                SearchTargetCompat.RESULT_TYPE_SHORTCUT -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS
                SearchTargetCompat.RESULT_TYPE_CONTACT_TILE -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS
                SearchTargetCompat.RESULT_TYPE_FILE_TILE -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES
                SearchTargetCompat.RESULT_TYPE_SETTING_TILE -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.SETTINGS
                SearchTargetCompat.RESULT_TYPE_CALCULATOR -> app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALCULATOR
                // Match PrimeSearchOverlay.nativeCategoryFor(): suggestion tiles
                // are a single HISTORY section in the shared result renderer.
                SearchTargetCompat.RESULT_TYPE_SUGGESTIONS ->
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.HISTORY
                else -> null
            }
        }
        return null
    }

    private fun sectionBounds(items: List<SearchAdapterItem>, position: Int): Pair<Int, Int>? {
        var start = -1
        for (index in position downTo 0) {
            val item = items[index]
            if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER && !isStructuralHeader(item)) {
                start = index + 1
                break
            }
        }
        if (start < 0) return null
        var end = items.size
        for (index in position + 1 until items.size) {
            val item = items[index]
            if (item.searchTarget.layoutType == LayoutType.TEXT_HEADER && !isStructuralHeader(item)) {
                end = index
                break
            }
        }
        return start to end
    }

    private fun previewLimit(
        category: app.lawnchair.prime.search.PrimeSearchResultPreferences.Category,
        view: View,
    ): Int {
        val context = view.context
        val surface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
        if (category == app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS) {
            // Match the Prime Search compact app grid: its visible capacity is
            // controlled by the drawer's own rows and columns, not a fixed count.
            return app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsRows(context, surface) *
                app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsColumns(context, surface)
        }
        val heightDp = app.lawnchair.prime.search.PrimeSearchCategoryPresentation
            .compactPreviewHeightDp(category) ?: 190
        val rowHeightDp = when (category) {
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> 48
            else -> 52
        }
        // Use the shared compact-card height budget so categories do not expose
        // a different number of rows simply because the drawer uses XML holders.
        return (heightDp / rowHeightDp).coerceAtLeast(1)
    }

    private fun openCategoryDetails(
        category: app.lawnchair.prime.search.PrimeSearchResultPreferences.Category,
    ) {
        val launcher = appsView.context as? app.lawnchair.LawnchairLauncher ?: return
        // Use the launcher's actual All Apps search field, not a recursive scan
        // that could pick up an unrelated EditText from another drawer component.
        val query = appsView.findViewById<android.widget.EditText>(R.id.input)
            ?.text?.toString()
            .orEmpty()
        if (query.isBlank()) return

        // All expanded sections now go through the same result renderer and
        // navigation stack as Prime Search. Do not fall back to the old custom
        // dialog: that dialog had different layouts, actions and result limits.
        app.lawnchair.prime.search.PrimeSearchOverlay.showCategoryInDrawer(
            launcher,
            appsView,
            category,
            query,
        )
    }

    private fun applyDrawerPreview(itemView: View, position: Int, item: SearchAdapterItem) {
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
        val limit = previewLimit(category, itemView)
        if (isStructuralHeader(item)) {
            val overflow = resultPositions.size > limit
            val title = itemView.findViewById<TextView>(R.id.title)
            if (overflow) {
                title?.text = "Plus de " + when (category) {
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS -> "applications"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> "raccourcis"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS -> "contacts"
                    app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES -> "fichiers"
                    else -> "résultats"
                } + "  ⌄"
                title?.apply {
                    gravity = android.view.Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
                }
                itemView.minimumHeight = dp(40, itemView)
                itemView.layoutParams?.height = dp(40, itemView)
                itemView.isClickable = true
                itemView.setOnClickListener {
                    openCategoryDetails(category)
                }
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
            return
        }

        val resultIndex = resultPositions.indexOf(position)
        if (resultIndex >= limit) {
            itemView.visibility = View.GONE
        } else {
            itemView.visibility = View.VISIBLE
            itemView.minimumHeight = 0
        }
    }

    private fun dp(value: Int, view: View): Int =
        (value * view.resources.displayMetrics.density).toInt()

    private val layoutIdMap = SparseIntArray().apply {
        append(SEARCH_RESULT_ICON, R.layout.search_result_icon)
        append(SEARCH_RESULT_ICON_ROW, R.layout.search_result_tall_icon_row)
        append(SEARCH_RESULT_SMALL_ICON_ROW, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_DIVIDER, R.layout.search_result_divider)
        append(SEARCH_TEXT_HEADER, R.layout.search_result_text_header)
        append(SEARCH_PEOPLE_TILE, R.layout.search_result_icon_right_left)
        append(SEARCH_RESULT_FILE_TILE, R.layout.search_result_icon_right_left)
        append(SEARCH_RESULT_SUGGESTION_TILE, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_SETTINGS_TILE, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_RECENT_TILE, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_CALCULATOR, R.layout.search_result_tall_icon_row_calculator)
        append(SEARCH_RESULT_EMPTY_STATE, R.layout.search_result_empty_state)
        append(SEARCH_RESULT_SEARCH_SETTINGS, R.layout.search_result_search_settings)
    }
    private var quickLaunchItem: SearchResultView? = null
        set(value) {
            field = value
            appsView.searchUiManager.setFocusedResultTitle(
                field?.titleText,
                field?.titleText,
                field != null,
            )
            appsView.mSearchRecyclerView.invalidate()
        }

    override fun isViewSupported(viewType: Int): Boolean = layoutIdMap.contains(viewType)

    override fun onBindView(holder: BaseAllAppsAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.mSearchRecyclerView.mApps.adapterItems[position] as SearchAdapterItem
        adapterItem.setRippleEffect(holder.itemView)
        val itemView = holder.itemView as SearchResultView
        itemView.bind(
            adapterItem.searchTarget,
            emptyList(),
        )
        if (isStructuralHeader(adapterItem)) applyDrawerPreview(holder.itemView, position, adapterItem)
        // Match Prime Search's compact section headings and non-app result rows.
        // Applied after bind because individual result views restore their own heights.
        val density = holder.itemView.resources.displayMetrics.density
        val dp: (Int) -> Int = { (it * density).toInt() }
        // Search algorithms also emit structural spacer headers. They are not
        // user-facing labels and must never render as "space" in the drawer.
        if (adapterItem.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
            val label = holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString()?.trim().orEmpty()
            if (label.equals(app.lawnchair.search.adapter.SPACE, ignoreCase = true) ||
                label.equals(app.lawnchair.search.adapter.SPACE_MINI, ignoreCase = true)) {
                holder.itemView.visibility = View.GONE
                holder.itemView.minimumHeight = 0
                holder.itemView.layoutParams.height = 0
                return
            }
        }
        holder.itemView.visibility = View.VISIBLE
        when (adapterItem.searchTarget.layoutType) {
            LayoutType.TEXT_HEADER -> {
                val heading = holder.itemView.findViewById<TextView>(R.id.title)
                val label = heading?.text?.toString().orEmpty()
                if (label.isNotBlank() && label != app.lawnchair.search.adapter.SPACE &&
                    label != app.lawnchair.search.adapter.SPACE_MINI) {
                    SearchResultGeometry.styleHeading(heading, holder.itemView)
                    holder.itemView.setPadding(dp(6), dp(9), dp(6), dp(1))
                    if (label.startsWith("Plus de ") || label.startsWith("Réduire")) {
                        heading?.apply {
                            gravity = android.view.Gravity.CENTER
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTypeface(typeface, Typeface.NORMAL)
                        }
                        holder.itemView.setPadding(dp(6), dp(4), dp(6), dp(4))
                        holder.itemView.minimumHeight = dp(40)
                    }
                }
            }
            LayoutType.THUMBNAIL -> {
                if (adapterItem.searchTarget.resultType ==
                    app.lawnchair.search.adapter.SearchTargetCompat.RESULT_TYPE_FILE_TILE) {
                    SearchResultGeometry.styleFileRow(holder.itemView)
                    holder.itemView.findViewById<TextView>(R.id.title)?.apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        maxLines = 2
                        isSingleLine = false
                    }
                }
            }
            LayoutType.HORIZONTAL_MEDIUM_TEXT, LayoutType.SMALL_ICON_HORIZONTAL_TEXT,
            LayoutType.ICON_HORIZONTAL_TEXT, LayoutType.ICON_SLICE, LayoutType.WIDGET_LIVE -> {
                SearchResultGeometry.styleTextRow(holder.itemView)
            }
        }
        // Apply drawer-specific compact geometry AFTER SearchResultGeometry:
        // styleTextRow/styleFileRow restore their 52dp Prime Search baseline, which
        // otherwise silently overrides the earlier drawer sizing.
        if (adapterItem.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT) {
            val labelLines = if (app.lawnchair.prime.search.PrimeSearchResultPreferences.twoLineAppLabels(holder.itemView.context, PrimeSearchResultPreferences.Surface.DRAWER)) 2 else 1
            val iconPercent = app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsIconSizePercent(holder.itemView.context, PrimeSearchResultPreferences.Surface.DRAWER)
            holder.itemView.minimumHeight = 0
            holder.itemView.layoutParams?.height = app.lawnchair.prime.search.PrimeAppGridGeometry.rowHeightPx(
                holder.itemView.context, iconPercent, labelLines,
            )
        } else if (adapterItem.searchTarget.layoutType != LayoutType.TEXT_HEADER) {
            // Several platform result XMLs declare fixed 64/92/100dp heights.
            // WRAP_CONTENT is not compact here: custom SearchResultIconRow children
            // with MATCH_PARENT sizing can still measure against those large bounds.
            // Give each drawer row an explicit content-appropriate height instead.
            val rowHeight = when (adapterItem.searchTarget.layoutType) {
                LayoutType.THUMBNAIL -> 72
                LayoutType.ICON_HORIZONTAL_TEXT -> 60
                LayoutType.EMPTY_STATE, LayoutType.SEARCH_SETTINGS -> 56
                else -> 48
            }
            holder.itemView.minimumHeight = 0
            holder.itemView.layoutParams?.height = dp(rowHeight)
            holder.itemView.findViewById<View>(R.id.text_rows)?.apply {
                setPadding(paddingLeft, 0, paddingRight, 0)
            }
        }
        // Search results inherit the selected drawer/category palette, not the
        // launcher's default theme or Prime Search's independent palette.
        val context = holder.itemView.context
        val profile = app.lawnchair.theme.ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        val role = if (adapterItem.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT)
            app.lawnchair.theme.ThemeColorRole.DRAWER_ICON_TEXT
        else app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_TEXT
        val textColor = app.lawnchair.theme.ThemeColors.resolve(context, profile, role, variant)
        applySearchTextColor(holder.itemView, textColor)
        if (adapterItem.searchTarget.layoutType != LayoutType.TEXT_HEADER) {
            applyDrawerPreview(holder.itemView, position, adapterItem)
        }
        // The category-level ItemDecoration draws one shared card behind the
        // heading and all rows. Individual result rows stay transparent.
        holder.itemView.background = null
        if (itemView.isQuickLaunch) {
            quickLaunchItem = itemView
        }
    }

    private fun applySearchTextColor(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applySearchTextColor(view.getChildAt(index), color)
            }
        }
    }

    override fun onCreateViewHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int,
    ): BaseAllAppsAdapter.ViewHolder {
        val view = layoutInflater.inflate(layoutIdMap[viewType], parent, false)
        val grid: DeviceProfile = mLauncher.deviceProfile
        val leftMargin = grid.allAppsPadding.left
        val rightMargin = grid.allAppsPadding.right

        if (viewType != SEARCH_RESULT_ICON) {
            val layoutParams = ViewGroup.MarginLayoutParams(view.layoutParams)
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            view.layoutParams = layoutParams
        }
        if (viewType == SEARCH_TEXT_HEADER) {
            val layoutParams: ViewGroup.MarginLayoutParams = ViewGroup.MarginLayoutParams(0, 0)
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            view.layoutParams = layoutParams
        }

        return BaseAllAppsAdapter.ViewHolder(view)
    }

    override fun getItemsPerRow(viewType: Int, appsPerRow: Int) = if (viewType != SEARCH_RESULT_ICON) 1 else super.getItemsPerRow(viewType, appsPerRow)

    override fun launchHighlightedItem(): Boolean = quickLaunchItem?.launch() ?: false

    override fun getHighlightedItem() = quickLaunchItem as View?

    override fun clearHighlightedItem() {
        super.clearHighlightedItem()
        quickLaunchItem = null
    }

    override fun getDecorator() = decorator

    companion object {
        /** Shared layout lookup for the drawer and the Prime Search overlay. */
        fun layoutForViewType(viewType: Int): Int? = when (viewType) {
            SEARCH_RESULT_ICON -> R.layout.search_result_icon
            SEARCH_RESULT_ICON_ROW -> R.layout.search_result_tall_icon_row
            SEARCH_RESULT_SMALL_ICON_ROW -> R.layout.search_result_small_icon_row
            SEARCH_RESULT_DIVIDER -> R.layout.search_result_divider
            SEARCH_TEXT_HEADER -> R.layout.search_result_text_header
            SEARCH_PEOPLE_TILE, SEARCH_RESULT_FILE_TILE -> R.layout.search_result_icon_right_left
            SEARCH_RESULT_SUGGESTION_TILE, SEARCH_RESULT_SETTINGS_TILE, SEARCH_RESULT_RECENT_TILE -> R.layout.search_result_small_icon_row
            SEARCH_RESULT_CALCULATOR -> R.layout.search_result_tall_icon_row_calculator
            SEARCH_RESULT_EMPTY_STATE -> R.layout.search_result_empty_state
            SEARCH_RESULT_SEARCH_SETTINGS -> R.layout.search_result_search_settings
            else -> null
        }
        private const val SEARCH_RESULT_ICON = (1 shl 10) or AllAppsGridAdapter.VIEW_TYPE_ICON
        private const val SEARCH_RESULT_ICON_ROW = 1 shl 11
        private const val SEARCH_RESULT_SMALL_ICON_ROW = 1 shl 12
        private const val SEARCH_RESULT_DIVIDER = 1 shl 13
        private const val SEARCH_TEXT_HEADER = 1 shl 14
        private const val SEARCH_PEOPLE_TILE = 1 shl 15
        private const val SEARCH_RESULT_FILE_TILE = 1 shl 16
        private const val SEARCH_RESULT_SUGGESTION_TILE = 1 shl 17
        private const val SEARCH_RESULT_SETTINGS_TILE = 1 shl 18
        private const val SEARCH_RESULT_RECENT_TILE = 1 shl 19
        private const val SEARCH_RESULT_CALCULATOR = 1 shl 20
        private const val SEARCH_RESULT_EMPTY_STATE = 1 shl 21
        private const val SEARCH_RESULT_SEARCH_SETTINGS = 1 shl 22

        val viewTypeMap = mapOf(
            LayoutType.ICON_SINGLE_VERTICAL_TEXT to SEARCH_RESULT_ICON,
            LayoutType.ICON_HORIZONTAL_TEXT to SEARCH_RESULT_ICON_ROW,
            LayoutType.SMALL_ICON_HORIZONTAL_TEXT to SEARCH_RESULT_SMALL_ICON_ROW,
            LayoutType.HORIZONTAL_MEDIUM_TEXT to SEARCH_RESULT_SUGGESTION_TILE,
            LayoutType.EMPTY_DIVIDER to SEARCH_RESULT_DIVIDER,
            LayoutType.TEXT_HEADER to SEARCH_TEXT_HEADER,
            LayoutType.PEOPLE_TILE to SEARCH_PEOPLE_TILE,
            LayoutType.THUMBNAIL to SEARCH_RESULT_FILE_TILE,
            LayoutType.ICON_SLICE to SEARCH_RESULT_SETTINGS_TILE,
            LayoutType.WIDGET_LIVE to SEARCH_RESULT_RECENT_TILE,
            LayoutType.CALCULATOR to SEARCH_RESULT_CALCULATOR,
            LayoutType.EMPTY_STATE to SEARCH_RESULT_EMPTY_STATE,
            LayoutType.SEARCH_SETTINGS to SEARCH_RESULT_SEARCH_SETTINGS,
        )
    }
}
