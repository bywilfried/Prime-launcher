package app.lawnchair.search

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.widget.TextView
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.theme.effectiveThemeVariant
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.views.ActivityContext

/**
 * Owns the drawer's expanded category page, including its local back handling
 * and result adapter. It never delegates navigation to Prime Search.
 */
internal class DrawerSearchCategoryPageRenderer(
    private val launcher: ActivityContext,
    private val appsView: ActivityAllAppsContainerView<*>,
    private val applyTextColor: (View, Int) -> Unit,
) {
    private var categoryPage: View? = null
    private var categoryBackActivity: Activity? = null
    private var categoryBackCallback: OnBackInvokedCallback? = null

    fun showCategoryPage(
        category: app.lawnchair.prime.search.PrimeSearchResultPreferences.Category,
        title: String,
        results: List<SearchAdapterItem>,
    ) {
        close()

        val context = appsView.context
        val page = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close()
                    true
                } else {
                    keyCode == KeyEvent.KEYCODE_BACK
                }
            }
        }

        // Prime Search's expanded view is a single full-height rounded card.
        // Keep the same hierarchy and spacing, but resolve colors from the
        // drawer palette so this page remains visually and functionally separate.
        val density = appsView.resources.displayMetrics.density
        val cardBackground = app.lawnchair.theme.ThemeColors.resolve(
            context,
            app.lawnchair.theme.ThemeProfile.current(context),
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND,
            context.effectiveThemeVariant(),
        )
        val cardBorder = app.lawnchair.theme.ThemeColors.resolve(
            context,
            app.lawnchair.theme.ThemeProfile.current(context),
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER,
            context.effectiveThemeVariant(),
        )
        val contentCard = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(8, this), dp(8, this), dp(8, this), dp(12, this))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(cardBackground)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardBorder)
            }
            clipToOutline = true
        }
        val detailTitle = when (category) {
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS -> "Applications"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS -> "Raccourcis d'app"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CONTACTS -> "Contacts"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.FILES -> "Fichiers"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB -> "Suggestions Web"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.WEB_SHORTCUTS -> "Raccourcis Web"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.CALENDAR -> "Événements de l'agenda"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.REMINDERS -> "Tâches"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.NOTIFICATIONS -> "Historique des notifications"
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.SETTINGS -> "Paramètres de l'appareil"
            else -> title
        }
        val heading = android.widget.TextView(context).apply {
            text = detailTitle
            textSize = 16f
            setTextColor(
                app.lawnchair.theme.ThemeColors.resolve(
                    context,
                    app.lawnchair.theme.ThemeProfile.current(context),
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_TEXT,
                    context.effectiveThemeVariant(),
                ),
            )
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18, this), dp(12, this), dp(12, this), dp(8, this))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        contentCard.addView(heading, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val isAppGrid = category ==
            app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APPS
        val drawerSurface = app.lawnchair.prime.search.PrimeSearchResultPreferences.Surface.DRAWER
        val appColumns = if (isAppGrid) {
            app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsColumns(context, drawerSurface)
                .coerceAtLeast(1)
        } else 1
        val appIconSizePercent = if (isAppGrid) {
            app.lawnchair.prime.search.PrimeSearchResultPreferences.appResultsIconSizePercent(
                context, drawerSurface,
            )
        } else 100
        val appLabelLines = if (isAppGrid &&
            app.lawnchair.prime.search.PrimeSearchResultPreferences.twoLineAppLabels(context, drawerSurface)
        ) 2 else 1
        val appIconSizePx = if (isAppGrid) {
            app.lawnchair.prime.search.PrimeAppGridGeometry.iconPx(context, appIconSizePercent)
        } else 0
        val appRowHeight = if (isAppGrid) {
            app.lawnchair.prime.search.PrimeAppGridGeometry.rowHeightPx(
                context, appIconSizePercent, appLabelLines,
            )
        } else 0
        val categoryItems = results.filter {
            it.searchTarget.layoutType != LayoutType.TEXT_HEADER &&
                it.searchTarget.layoutType != LayoutType.EMPTY_DIVIDER
        }.let { items ->
            if (isAppGrid) items.distinctBy { it.searchTarget.packageName } else items
        }
        if (isAppGrid) {
            // Prime Search builds application results as fixed-width cells inside
            // independently aligned rows. A GridLayoutManager stretches the final
            // partial row and changes the visual rhythm, so mirror the same row
            // composition here while keeping every view owned by the drawer.
            val appItems = categoryItems.filter {
                it.searchTarget.resultType == SearchTargetCompat.RESULT_TYPE_APPLICATION &&
                    it.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT
            }.distinctBy { it.searchTarget.packageName }
            val contentWidth = (
                (appsView.width.takeIf { it > 0 } ?: appsView.resources.displayMetrics.widthPixels) -
                    dp(16, appsView)
                ).coerceAtLeast(appColumns)
            val cellWidth = (contentWidth / appColumns).coerceAtLeast(1)
            val alignment = app.lawnchair.prime.search.PrimeSearchResultPreferences
                .appResultsAlignment(context, drawerSurface)
            val scroll = android.widget.ScrollView(context).apply {
                clipToPadding = false
                isFillViewport = false
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(32, this))
                setPadding(0, 0, 0, 0)
                isVerticalScrollBarEnabled = false
            }
            val grid = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            var row: android.widget.LinearLayout? = null
            var inRow = 0
            appItems.forEach { item ->
                if (row == null || inRow == appColumns) {
                    row = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = when (alignment) {
                            app.lawnchair.prime.search.PrimeSearchResultPreferences.AppResultsAlignment.LEFT ->
                                android.view.Gravity.START
                            app.lawnchair.prime.search.PrimeSearchResultPreferences.AppResultsAlignment.RIGHT ->
                                android.view.Gravity.END
                            else -> android.view.Gravity.CENTER_HORIZONTAL
                        }
                    }
                    grid.addView(row, android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, appRowHeight,
                    ))
                    inRow = 0
                }
                val layout = LawnchairSearchAdapterProvider.layoutForViewType(item.viewType) ?: return@forEach
                val resultView = LayoutInflater.from(context).inflate(layout, row, false)
                val searchResultView = resultView as? SearchResultView ?: return@forEach
                item.setRippleEffect(resultView)
                searchResultView.bind(item.searchTarget, emptyList())
                applyTextColor(
                    resultView,
                    app.lawnchair.theme.ThemeColors.resolve(
                        context,
                        app.lawnchair.theme.ThemeProfile.current(context),
                        app.lawnchair.theme.ThemeColorRole.DRAWER_ICON_TEXT,
                        context.effectiveThemeVariant(),
                    ),
                )
                resultView.minimumHeight = 0
                // Keep the inflated native tile's vertical insets. Prime Search
                // sizes the row around those defaults rather than adding another
                // layer of top/bottom padding.
                (resultView as? app.lawnchair.allapps.views.SearchResultIcon)?.apply {
                    setPrimeIconSize(appIconSizePx)
                    setTextVisibility(true)
                    setPrimeSearchLabelLayout(true)
                    gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                    setCompoundDrawablePadding(dp(app.lawnchair.prime.search.PrimeAppGridGeometry.ICON_LABEL_GAP_DP.toInt(), this))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, app.lawnchair.prime.search.PrimeAppGridGeometry.LABEL_SP)
                    includeFontPadding = false
                    maxLines = appLabelLines
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                row!!.addView(resultView, android.widget.LinearLayout.LayoutParams(
                    cellWidth, appRowHeight,
                ))
                inRow++
            }
            scroll.addView(grid)
            contentCard.addView(scroll, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
        } else {
            val list = androidx.recyclerview.widget.RecyclerView(context).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                clipToPadding = false
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(32, this))
                setPadding(0, 0, 0, 0)
                adapter = DrawerCategoryResultsAdapter(categoryItems, false, appRowHeight, appIconSizePx, appLabelLines)
            }
            contentCard.addView(list, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
        }
        page.addView(contentCard, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply {
            marginStart = dp(4, page)
            marginEnd = dp(4, page)
            topMargin = dp(16, page)
            bottomMargin = dp(4, page)
        })
        val reduce = android.widget.TextView(context).apply {
            text = "⌃  Réduire"
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setTextColor(
                app.lawnchair.theme.ThemeColors.resolve(
                    context,
                    app.lawnchair.theme.ThemeProfile.current(context),
                    app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_TEXT,
                    context.effectiveThemeVariant(),
                ),
            )
            setPadding(0, dp(12, this), 0, dp(12, this))
            isClickable = true
            isFocusable = true
            setOnClickListener { close() }
        }
        page.addView(reduce, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        categoryPage = page
        appsView.addView(page, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        page.bringToFront()
        page.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (context as? Activity)?.let { activity ->
                val callback = OnBackInvokedCallback { close() }
                activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback,
                )
                categoryBackActivity = activity
                categoryBackCallback = callback
            }
        }
    }

    fun close() {
        val activity = categoryBackActivity
        val callback = categoryBackCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity != null && callback != null
        ) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        categoryBackActivity = null
        categoryBackCallback = null
        categoryPage?.let { page ->
            (page.parent as? ViewGroup)?.removeView(page)
        }
        categoryPage = null
    }

    private inner class DrawerCategoryResultsAdapter(
        private val results: List<SearchAdapterItem>,
        private val appGrid: Boolean,
        private val appRowHeightPx: Int,
        private val appIconSizePx: Int,
        private val appLabelLines: Int,
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DrawerCategoryResultsAdapter.ResultHolder>() {

        inner class ResultHolder(val resultView: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(resultView)

        override fun getItemCount() = results.size

        override fun getItemViewType(position: Int) = results[position].viewType

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultHolder {
            val layout = LawnchairSearchAdapterProvider.layoutForViewType(viewType)
                ?: throw IllegalArgumentException("Unsupported drawer category result type: $viewType")
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            view.layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (appGrid) appRowHeightPx else ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            return ResultHolder(view)
        }

        override fun onBindViewHolder(holder: ResultHolder, position: Int) {
            val item = results[position]
            val view = holder.resultView
            // Keep the same target-owned ripple feedback as the drawer's compact
            // result rows and the expanded application grid.
            item.setRippleEffect(view)
            (view as? SearchResultView)?.bind(item.searchTarget, emptyList())
            val context = view.context
            val density = context.resources.displayMetrics.density
            val color = app.lawnchair.theme.ThemeColors.resolve(
                context,
                app.lawnchair.theme.ThemeProfile.current(context),
                app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_TEXT,
                context.effectiveThemeVariant(),
            )
            applyTextColor(view, color)
            if (appGrid) {
                view.minimumHeight = 0
                view.layoutParams?.height = appRowHeightPx
                view.setPadding(0, dp(4, view), 0, dp(4, view))
                (view as? app.lawnchair.allapps.views.SearchResultIcon)?.apply {
                    setPrimeIconSize(appIconSizePx)
                    setTextVisibility(true)
                    setPrimeSearchLabelLayout(true)
                    gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                    setCompoundDrawablePadding(dp(4, this))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    includeFontPadding = false
                    maxLines = appLabelLines
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            } else if (item.searchTarget.layoutType != LayoutType.ICON_SINGLE_VERTICAL_TEXT) {
                val category = DrawerSearchCategoryPresentation.categoryForResultType(item.searchTarget.resultType)
                val rowHeight = SearchResultGeometry.compactRowHeightDp(
                    item.searchTarget.layoutType,
                    isAppShortcut = category ==
                        app.lawnchair.prime.search.PrimeSearchResultPreferences.Category.APP_SHORTCUTS,
                )
                app.lawnchair.search.SearchResultGeometry.styleCompactResultRow(
                    view,
                    rowHeight,
                    file = item.searchTarget.layoutType == LayoutType.THUMBNAIL,
                )
            } else {
                view.minimumHeight = (56 * density).toInt()
            }
        }
    }


    private fun dp(value: Int, view: View): Int =
        (value * view.resources.displayMetrics.density).toInt()
}
