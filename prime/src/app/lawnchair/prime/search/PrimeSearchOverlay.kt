package app.lawnchair.prime.search

import android.app.Dialog
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.TextView
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.HorizontalScrollView
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.destinations.SearchRoute
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import app.lawnchair.theme.ThemeColors
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.effectiveThemeVariant
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.GridLayout
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.android.app.search.LayoutType
import android.view.LayoutInflater
import android.view.View
import android.graphics.Rect
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.LawnchairLauncher
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.LawnchairLocalSearchAlgorithm
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback

/**
 * Floating search proof of concept. Uses the same configured search algorithm as All Apps.
 * TODO: replace simple result rows with the full Lawnchair result view holders.
 */
object PrimeSearchOverlay {
    private var activeDialog: Dialog? = null
    private var activeAlgorithm: LawnchairSearchAlgorithm? = null

    fun show(launcher: LawnchairLauncher) {
        if (activeDialog?.isShowing == true) return
        val density = launcher.resources.displayMetrics.density
        val inset = (16 * density).toInt()
        val floating = PrimeSearchResultPreferences.floatingWindow(launcher)
        val profile = ThemeProfile.current(launcher)
        val variant = launcher.effectiveThemeVariant()
        fun themed(role: ThemeColorRole) = ThemeColors.resolve(launcher, profile, role, variant)
        val algorithm = LawnchairLocalSearchAlgorithm(launcher, primeSearch = true)
        activeAlgorithm = algorithm
        val input = EditText(launcher).apply {
            isSingleLine = true
            hint = "Prime Search"
            setPadding(inset, inset, inset, inset)
            background = GradientDrawable().apply {
                setColor(themed(ThemeColorRole.PRIME_SEARCH_BAR_BACKGROUND))
                cornerRadius = 24 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), themed(ThemeColorRole.PRIME_SEARCH_BORDER))
            }
            setTextColor(themed(ThemeColorRole.PRIME_SEARCH_TEXT))
            setHintTextColor(themed(ThemeColorRole.PRIME_SEARCH_HINT))
        }
        val results = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themed(ThemeColorRole.PRIME_SEARCH_RESULT_BACKGROUND))
        }
        val zeroState = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tabs = LinearLayout(launcher).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val zeroApps = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
        }
        zeroState.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        zeroState.addView(zeroApps, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(launcher).apply {
            addView(results)
        }
        val container = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(themed(ThemeColorRole.PRIME_SEARCH_WINDOW_BACKGROUND))
                cornerRadius = if (floating) 22 * density else 0f
            }
            clipToOutline = floating
            setPadding(inset, inset, inset, inset)
            addView(input, LinearLayout.LayoutParams(-1, -2))
            addView(zeroState, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, -2))
            val settings = TextView(launcher).apply {
                text = "⚙"
                textSize = 24f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                minHeight = (48 * density).toInt()
                setTextColor(themed(ThemeColorRole.PRIME_SEARCH_TEXT))
                setPadding(inset, (6 * density).toInt(), inset, (6 * density).toInt())
                contentDescription = "Options Prime Search"
                setOnClickListener {
                    PrimeSearchOverlay.dismiss()
                    launcher.startActivity(
                        PreferenceActivity.createIntent(launcher, Search(SearchRoute.PRIME_SEARCH)),
                    )
                }
            }
            addView(settings, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = Dialog(launcher).apply {
            setContentView(container)
            setCanceledOnTouchOutside(true)
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setOnDismissListener {
                algorithm.cancel(true)
                if (activeAlgorithm === algorithm) activeAlgorithm = null
                activeDialog = null
            }
        }
        val packageManager = launcher.packageManager
        val installed = packageManager.getInstalledApplications(0).filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null
        }
        val now = System.currentTimeMillis()
        val usage = try {
            launcher.getSystemService(UsageStatsManager::class.java)
                ?.queryAndAggregateUsageStats(now - 30L * 24 * 60 * 60 * 1000, now)
                .orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
        val sections = listOf("FRÉQUENTES", "RÉCENTES", "NOUVELLES")
        var selectedTab = 0
        fun renderZeroState() {
            tabs.removeAllViews()
            zeroApps.removeAllViews()
            sections.forEachIndexed { index, name ->
                val tab = TextView(launcher).apply {
                    text = name
                    textSize = 12f
                    setTextColor(themed(ThemeColorRole.PRIME_SEARCH_TEXT))
                    alpha = if (index == selectedTab) 1f else 0.55f
                    gravity = Gravity.CENTER
                    setPadding((8 * density).toInt(), inset, (8 * density).toInt(), inset)
                    setOnClickListener { selectedTab = index; renderZeroState() }
                }
                tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val ranked = when (selectedTab) {
                0 -> installed.sortedByDescending {
                    usage[it.packageName]?.totalTimeInForeground ?: 0L
                }.filter { (usage[it.packageName]?.totalTimeInForeground ?: 0L) > 0L }
                1 -> installed.sortedByDescending {
                    usage[it.packageName]?.lastTimeUsed ?: 0L
                }.filter { (usage[it.packageName]?.lastTimeUsed ?: 0L) > 0L }
                else -> installed.sortedByDescending {
                    try { packageManager.getPackageInfo(it.packageName, 0).firstInstallTime }
                    catch (_: Exception) { 0L }
                }
            }
            val columns = PrimeSearchResultPreferences.appsPerRow(launcher)
            val maxCount = columns * PrimeSearchResultPreferences.zeroStateRows(launcher)
            val usableWidth = launcher.resources.displayMetrics.widthPixels -
                (if (floating) (48 * density).toInt() else 0) - inset * 2
            val cellWidth = (usableWidth.coerceAtMost((600 * density).toInt() - inset * 2) / columns)
                .coerceAtLeast(1)
            // Always allocate the configured number of rows, even if a tab
            // has fewer apps (or none). This keeps the overlay height stable
            // while switching between Fréquentes, Récentes and Nouvelles.
            val rows = PrimeSearchResultPreferences.zeroStateRows(launcher)
            val rowHeight = (100 * density).toInt()
            val displayedApps = ranked.take(maxCount)
            repeat(rows) { rowIndex ->
                val apps = displayedApps.drop(rowIndex * columns).take(columns)
                val row = LinearLayout(launcher).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumHeight = rowHeight
                }
                zeroApps.addView(row, LinearLayout.LayoutParams(-1, rowHeight))
                apps.forEach { app ->
                    val cell = LinearLayout(launcher).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(2, (6 * density).toInt(), 2, 0)
                        setOnClickListener {
                            packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                                dialog.dismiss()
                                launcher.startActivity(intent)
                            }
                        }
                    }
                    val icon = ImageView(launcher).apply {
                        setImageDrawable(packageManager.getApplicationIcon(app))
                    }
                    cell.addView(icon, LinearLayout.LayoutParams(
                        (48 * density).toInt(), (48 * density).toInt(),
                    ))
                    val title = TextView(launcher).apply {
                        text = packageManager.getApplicationLabel(app)
                        textSize = 11f
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(themed(ThemeColorRole.PRIME_SEARCH_TEXT))
                    }
                    cell.addView(title, LinearLayout.LayoutParams(-1, -2))
                    row.addView(cell, LinearLayout.LayoutParams(cellWidth, (100 * density).toInt()))
                }
            }
            zeroState.requestLayout()
        }
        renderZeroState()
        val callback = object : SearchCallback<BaseAllAppsAdapter.AdapterItem> {
            override fun onSearchResult(query: String, items: ArrayList<BaseAllAppsAdapter.AdapterItem>?) {
                if (!dialog.isShowing || input.text.toString() != query) return
                results.removeAllViews()
                // Re-measure after the search results have changed.
                results.post { if (dialog.isShowing) container.requestLayout() }
                // Preserve the algorithm's order and section boundaries. Each item is
                // inflated with the same layout used by the drawer's adapter provider.
                val inflater = LayoutInflater.from(launcher)
                val columns = PrimeSearchResultPreferences.appsPerRow(launcher)
                // Each row is centered independently. Fixed-width cells keep
                // identical spacing whether the row has 1, 2 or 6 apps.
                val availableWidth = launcher.resources.displayMetrics.widthPixels -
                    (if (floating) (48 * density).toInt() else 0) - inset * 2
                val gridWidth = availableWidth.coerceAtMost(
                    if (floating) (600 * density).toInt() - inset * 2 else availableWidth,
                )
                val cellWidth = (gridWidth / columns).coerceAtLeast(1)
                var appRow: LinearLayout? = null
                var appsInRow = 0
                PrimeSearchResultPreferences.filter(
                    launcher, items.orEmpty().filterIsInstance<SearchAdapterItem>(),
                ).forEach { item ->
                    val isAppIcon = item.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT
                    if (isAppIcon) {
                        if (appRow == null || appsInRow == columns) {
                            appRow = LinearLayout(launcher).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_HORIZONTAL
                            }
                            results.addView(appRow, LinearLayout.LayoutParams(-1, -2))
                            appsInRow = 0
                        }
                    } else {
                        appRow = null
                        appsInRow = 0
                    }
                    val parent = if (isAppIcon) appRow!! else results
                    val layout = LawnchairSearchAdapterProvider.layoutForViewType(item.viewType)
                        ?: return@forEach
                    val view = inflater.inflate(layout, parent, false)
                    val resultView = view as? SearchResultView ?: return@forEach
                    if (isAppIcon) {
                        parent.addView(view, LinearLayout.LayoutParams(
                            cellWidth, (112 * density).toInt(),
                        ))
                        appsInRow++
                    } else {
                        parent.addView(view)
                    }
                    item.setRippleEffect(view)
                    resultView.bind(item.searchTarget, emptyList())
                    if (isAppIcon) {
                        view.minimumHeight = 0
                    }
                }
                results.requestLayout()
            }

            override fun clearSearchResult() {
                results.removeAllViews()
                results.requestLayout()
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                zeroState.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
                scroll.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                if (query.isBlank()) {
                    results.removeAllViews()
                    renderZeroState()
                } else algorithm.doSearch(query, callback)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        activeDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP)
            if (floating) {
                val metrics = launcher.resources.displayMetrics
                val width = (metrics.widthPixels - (24 * density).toInt() * 2)
                    .coerceAtMost((600 * density).toInt())
                // Resize dynamically when the IME opens: fixed 72% screen height
                // otherwise extends underneath the keyboard on some devices.
                setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                attributes = attributes.apply { dimAmount = 0.18f }
            } else {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                attributes = attributes.apply { dimAmount = 0f }
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        if (floating) {
            val window = dialog.window
            val decor = window?.decorView
            val screenHeight = launcher.resources.displayMetrics.heightPixels
            val margin = (24 * density).toInt()
            val targetWidth = (launcher.resources.displayMetrics.widthPixels - margin * 2)
                .coerceAtMost((600 * density).toInt())
            var lastHeight = -1
            decor?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!dialog.isShowing) {
                        decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    val visible = Rect()
                    decor.getWindowVisibleDisplayFrame(visible)
                    val available = visible.height().coerceAtMost(screenHeight)
                    val maximum = (available - margin).coerceAtLeast((160 * density).toInt())
                    // Measure natural content height independently of the dialog
                    // height, then cap it to the visible region above the IME.
                    val barHeight = input.height.coerceAtLeast((64 * density).toInt())
                    val desiredContent = if (zeroState.visibility == View.VISIBLE) zeroState.measuredHeight else results.measuredHeight
                    val footerHeight = container.getChildAt(container.childCount - 1).measuredHeight
                        .coerceAtLeast((48 * density).toInt())
                    val desired = (barHeight + desiredContent + footerHeight + inset * 2)
                        .coerceIn((120 * density).toInt(), maximum)
                    if (desired != lastHeight) {
                        lastHeight = desired
                        window.setLayout(targetWidth, desired)
                    }
                }
            })
        }
        container.translationY = -120 * density
        container.animate().translationY(0f).setDuration(220).start()
        input.requestFocus()
        input.post {
            (launcher.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            zeroState.visibility = View.VISIBLE
            scroll.visibility = View.GONE
        }
    }

    fun dismiss() {
        activeDialog?.dismiss()
    }
}
