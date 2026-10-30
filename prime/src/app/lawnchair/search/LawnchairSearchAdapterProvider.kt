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
            LayoutType.HORIZONTAL_MEDIUM_TEXT, LayoutType.ICON_SLICE, LayoutType.WIDGET_LIVE -> {
                SearchResultGeometry.styleTextRow(holder.itemView)
            }
        }
        // Search results inherit the selected drawer/category palette, not the
        // launcher's default theme or Prime Search's independent palette.
        val context = holder.itemView.context
        val profile = app.lawnchair.theme.ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        val role = if (adapterItem.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT)
            app.lawnchair.theme.ThemeColorRole.DRAWER_ICON_TEXT
        else app.lawnchair.theme.ThemeColorRole.DRAWER_TEXT
        val textColor = app.lawnchair.theme.ThemeColors.resolve(context, profile, role, variant)
        applySearchTextColor(holder.itemView, textColor)
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
