package app.lawnchair.search

import android.util.SparseIntArray
import android.app.Activity
import android.os.Build
import android.view.KeyEvent
import android.window.OnBackInvokedCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Typeface
import android.util.TypedValue
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.prime.search.PrimeSearchResultPreferences
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
    private val resultsRenderer = DrawerSearchResultsRenderer(appsView)
    private val categoryPageRenderer = DrawerSearchCategoryPageRenderer(
        appsView,
        ::applySearchTextColor,
    )
    private val previewRenderer = DrawerSearchPreviewRenderer(appsView) { category, title, results ->
        categoryPageRenderer.showCategoryPage(category, title, results)
    }


    init {
        // The adapter provider is created from ActivityAllAppsContainerView.initContent(),
        // before mSearchRecyclerView is assigned. Defer decoration installation until
        // the container has completed inflation to avoid crashing launcher startup.
        appsView.post {
            appsView.mSearchRecyclerView?.let { recyclerView ->
                recyclerView.adapter?.registerAdapterDataObserver(
                    object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                        override fun onChanged() = categoryPageRenderer.close()

                        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) =
                            categoryPageRenderer.close()

                        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
                            categoryPageRenderer.close()

                        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
                            categoryPageRenderer.close()

                        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
                            categoryPageRenderer.close()

                        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                            categoryPageRenderer.close()
                    },
                )
            }
        }
    }
    private fun categoryForResult(item: SearchAdapterItem) =
        DrawerSearchCategoryPresentation.categoryForResultType(item.searchTarget.resultType)



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
        if (adapterItem.searchTarget.layoutType == LayoutType.TEXT_HEADER) {
            previewRenderer.applyDrawerPreview(holder.itemView, position, adapterItem)
        }
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
            val labelLines = if (app.lawnchair.prime.search.PrimeSearchResultPreferences.twoLineAppLabels(holder.itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER)) 2 else 1
            val iconPercent = app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsIconSizePercent(holder.itemView.context, app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER)
            holder.itemView.minimumHeight = 0
            holder.itemView.layoutParams?.height = app.lawnchair.prime.search.PrimeAppGridGeometry.rowHeightPx(
                holder.itemView.context, iconPercent, labelLines,
            )
        } else if (adapterItem.searchTarget.layoutType != LayoutType.TEXT_HEADER) {
            // Use the same compact row contract as the embedded Prime Search
            // renderer so files, contacts, suggestions and shortcuts share
            // typography, spacing and exact row heights.
            val section = sectionBounds(
                appsView.mSearchRecyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem },
                position,
            )
            val category = section?.let { (start, end) ->
                categoryForSection(
                    appsView.mSearchRecyclerView.mApps.adapterItems.mapNotNull { it as? SearchAdapterItem },
                    start,
                    end,
                )
            }
            val rowHeight = SearchResultGeometry.compactRowHeightDp(
                adapterItem.searchTarget.layoutType,
                isAppShortcut = category == app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
            )
            app.lawnchair.search.SearchResultGeometry.styleCompactResultRow(
                holder.itemView,
                rowHeight,
                file = adapterItem.searchTarget.layoutType == LayoutType.THUMBNAIL,
            )
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
        // Apply the drawer-owned section surface after native binding.
        resultsRenderer.styleBoundRow(holder.itemView, position, adapterItem)
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
