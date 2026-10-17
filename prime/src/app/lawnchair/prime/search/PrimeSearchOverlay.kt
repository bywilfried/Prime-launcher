package app.lawnchair.prime.search

import android.app.Dialog
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
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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
        val callback = object : SearchCallback<BaseAllAppsAdapter.AdapterItem> {
            override fun onSearchResult(query: String, items: ArrayList<BaseAllAppsAdapter.AdapterItem>?) {
                if (!dialog.isShowing || input.text.toString() != query) return
                results.removeAllViews()
                // Preserve the algorithm's order and section boundaries. Each item is
                // inflated with the same layout used by the drawer's adapter provider.
                val inflater = LayoutInflater.from(launcher)
                val columns = PrimeSearchResultPreferences.appsPerRow(launcher)
                var appGrid: GridLayout? = null
                PrimeSearchResultPreferences.filter(
                    launcher, items.orEmpty().filterIsInstance<SearchAdapterItem>(),
                ).forEach { item ->
                    val isAppIcon = item.searchTarget.layoutType == LayoutType.ICON_SINGLE_VERTICAL_TEXT
                    if (isAppIcon) {
                        if (appGrid == null) {
                            appGrid = GridLayout(launcher).apply {
                                columnCount = columns
                                alignmentMode = GridLayout.ALIGN_BOUNDS
                                useDefaultMargins = false
                            }
                            results.addView(appGrid, LinearLayout.LayoutParams(-1, -2))
                        }
                    } else {
                        appGrid = null
                    }
                    val parent = if (isAppIcon) appGrid!! else results
                    val layout = LawnchairSearchAdapterProvider.layoutForViewType(item.viewType)
                        ?: return@forEach
                    val view = inflater.inflate(layout, parent, false)
                    val resultView = view as? SearchResultView ?: return@forEach
                    if (isAppIcon) {
                        val params = GridLayout.LayoutParams().apply {
                            width = 0
                            height = GridLayout.LayoutParams.WRAP_CONTENT
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        }
                        parent.addView(view, params)
                    } else {
                        parent.addView(view)
                    }
                    item.setRippleEffect(view)
                    resultView.bind(item.searchTarget, emptyList())
                    if (isAppIcon) {
                        // Drawer icons assign an all-apps cell height during inflation.
                        // Override it for compact, top-aligned Prime Search rows.
                        val params = view.layoutParams as GridLayout.LayoutParams
                        params.width = 0
                        params.height = (112 * density).toInt()
                        params.setGravity(Gravity.TOP)
                        view.layoutParams = params
                        view.minimumHeight = 0
                    }
                }
            }

            override fun clearSearchResult() {
                results.removeAllViews()
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                if (query.isBlank()) algorithm.doZeroStateSearch(callback)
                else algorithm.doSearch(query, callback)
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
                    // For a dialog with ADJUST_RESIZE, the visible frame tracks the
                    // area above the IME. Avoid relying on a fixed display height.
                    val available = visible.height().coerceAtMost(screenHeight)
                    val desired = (available - margin).coerceAtLeast((160 * density).toInt())
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
            algorithm.doZeroStateSearch(callback)
        }
    }

    fun dismiss() {
        activeDialog?.dismiss()
    }
}
