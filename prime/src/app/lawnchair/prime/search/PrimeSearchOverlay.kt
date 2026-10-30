package app.lawnchair.prime.search

import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.app.AppOpsManager
import android.os.Process
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.LawnchairLocalSearchAlgorithm
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback

/**
 * Floating search proof of concept. Uses the same configured search algorithm as All Apps.
 * TODO: replace simple result rows with the full Lawnchair result view holders.
 */
object PrimeSearchOverlay {
    val isShowing: Boolean get() = activeDialog?.isShowing == true

    private var activeDialog: PrimeSearchSurface? = null
    private var activeAlgorithm: LawnchairSearchAlgorithm? = null
    @Volatile private var cachedApps: List<android.content.pm.ApplicationInfo>? = null
    @Volatile private var cachedUsage: Map<String, android.app.usage.UsageStats>? = null
    @Volatile private var cachedUsageGranted = false

    /**
     * Prime Search shares the launcher's window and DragLayer with the workspace.
     * This preserves the touch stream and the native popup's icon coordinates.
     */
    private class PrimeSearchSurface(
        private val launcher: LawnchairLauncher,
        private val content: View,
        private val floating: Boolean,
        private val density: Float,
        private val onDismiss: () -> Unit,
    ) {
        private val layer = launcher.dragLayer
        private val scrim = FrameLayout(launcher).apply {
            isClickable = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }
        val isShowing get() = scrim.parent != null
        fun show() {
            if (isShowing) return
            layer.setPrimeSearchOpen(true)
            layer.addView(scrim, FrameLayout.LayoutParams(-1, -1))
            val width = if (floating) {
                (launcher.resources.displayMetrics.widthPixels - (24 * density).toInt())
                    .coerceAtMost((600 * density).toInt())
            } else FrameLayout.LayoutParams.MATCH_PARENT
            scrim.addView(content, FrameLayout.LayoutParams(width, if (floating) -2 else -1).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                if (floating) topMargin = (12 * density).toInt()
            })
            content.isClickable = true
        }
        fun hideKeyboardForSelection() {
            if (!isShowing) return
            // Transfer focus away from the editor before launching the target.
            // This prevents the IME from remaining tied to the launcher during
            // the destination activity's startup transition.
            content.findFocus()?.clearFocus()
            scrim.isFocusableInTouchMode = true
            scrim.requestFocus()
            (launcher.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(content.windowToken, 0)
        }
        fun dismiss() {
            if (!isShowing) return
            (content.parent as? ViewGroup)?.removeView(content)
            layer.removeView(scrim)
            layer.setPrimeSearchOpen(false)
            onDismiss()
        }
        fun hideForDrag() {
            scrim.visibility = View.INVISIBLE
            layer.setPrimeSearchDrag(true)
        }
        fun restoreAfterDrag() {
            layer.setPrimeSearchDrag(false)
            if (isShowing) scrim.visibility = View.VISIBLE
        }
        fun updateHeight(height: Int) {
            val params = content.layoutParams as? FrameLayout.LayoutParams ?: return
            if (params.height != height) {
                params.height = height
                content.layoutParams = params
            }
        }
        fun observeLayout(listener: ViewTreeObserver.OnGlobalLayoutListener) {
            content.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }
        val decorView: View get() = content
    }

    private fun showNativeAppPopup(
        launcher: LawnchairLauncher,
        surface: PrimeSearchSurface,
        icon: com.android.launcher3.BubbleTextView,
    ): Boolean {
        if (icon.tag !is com.android.launcher3.model.data.ItemInfo) return false
        if (com.android.launcher3.popup.PopupContainerWithArrow.getOpen(launcher) != null) return false
        return com.android.launcher3.popup.PopupContainerWithArrow.showForIcon(icon) != null
    }

    private fun enableNativeAppGestures(
        launcher: LawnchairLauncher,
        surface: PrimeSearchSurface,
        icon: com.android.launcher3.BubbleTextView,
    ) {
        val slop = ViewConfiguration.get(launcher).scaledTouchSlop
        var pressed = false
        var dragging = false
        var downX = 0f
        var downY = 0f
        icon.setOnLongClickListener {
            pressed = showNativeAppPopup(launcher, surface, icon)
            if (pressed) {
                // The search surface must keep ownership of the gesture. Otherwise
                // vertical movement reaches the launcher's swipe gesture handlers.
                icon.parent?.requestDisallowInterceptTouchEvent(true)
                (launcher.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(icon.windowToken, 0)
                icon.clearFocus()
            }
            pressed
        }
        icon.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = false
                    dragging = false
                    downX = event.x
                    downY = event.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pressed && !dragging && kotlin.math.hypot(
                            (event.x - downX).toDouble(),
                            (event.y - downY).toDouble(),
                        ) > slop * 2) {
                        pressed = false
                        dragging = true
                        com.android.launcher3.popup.PopupContainerWithArrow.getOpen(launcher)?.close(true)
                        if (com.android.launcher3.touch.ItemLongClickListener.canStartDrag(launcher)) {
                            val listener = object : com.android.launcher3.dragndrop.DragController.DragListener {
                                override fun onDragStart(
                                    dragObject: com.android.launcher3.DropTarget.DragObject,
                                    options: com.android.launcher3.dragndrop.DragOptions,
                                ) = Unit
                                override fun onDragEnd() {
                                    launcher.dragController.removeDragListener(this)
                                    surface.restoreAfterDrag()
                                    surface.dismiss()
                                }
                            }
                            // MotionEvent coordinates here are relative to the icon,
                            // not DragLayer. Seed the native drag at the actual finger
                            // location before creating its preview and registration offset.
                            val layerPosition = IntArray(2)
                            launcher.dragLayer.getLocationOnScreen(layerPosition)
                            val pointerX = (event.rawX - layerPosition[0]).toInt()
                            val pointerY = (event.rawY - layerPosition[1]).toInt()
                            launcher.dragController.setPrimeSearchDragStart(pointerX, pointerY)
                            launcher.dragController.addDragListener(listener)
                            surface.hideForDrag()
                            launcher.workspace.beginDragShared(
                                icon, launcher.appsView,
                                com.android.launcher3.dragndrop.DragOptions(),
                            )
                        } else {
                            dragging = false
                        }
                    }
                    // Consume movement after long press instead of triggering swipe actions.
                    pressed || dragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val consumed = pressed || dragging
                    pressed = false
                    dragging = false
                    consumed
                }
                else -> pressed || dragging
            }
        }
    }

    fun show(launcher: LawnchairLauncher) {
        if (activeDialog?.isShowing == true) return
        if (activeDialog != null) dismiss()
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
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (56 * density).toInt()
            setPadding(inset, 0, inset, 0)
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
            setPadding((12 * density).toInt(), (12 * density).toInt(),
                (12 * density).toInt(), (8 * density).toInt())
            // Search results must not start flush against the search field.
            // This padding belongs only to typed results, not the zero-state tabs.
            setBackgroundColor(themed(ThemeColorRole.PRIME_SEARCH_RESULT_BACKGROUND))
        }
        val zeroState = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
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
        lateinit var settings: ImageView
        val container = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(themed(ThemeColorRole.PRIME_SEARCH_WINDOW_BACKGROUND))
                cornerRadius = if (floating) 22 * density else 0f
            }
            clipToOutline = floating
            setPadding(inset, inset, inset, 0)
            val searchBar = FrameLayout(launcher)
            searchBar.addView(input, FrameLayout.LayoutParams(-1, -2))
            addView(searchBar, LinearLayout.LayoutParams(-1, -2))
            addView(zeroState, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, -2))
            // Use a real outlined vector-like gear instead of the platform
            // Unicode gear glyph, whose filled appearance varies by font.
            val settingsIcon = object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = themed(ThemeColorRole.PRIME_SEARCH_SETTINGS_ICON)
                    style = Paint.Style.STROKE
                    strokeWidth = 2.1f
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                override fun draw(canvas: Canvas) {
                    val bounds = bounds
                    val side = minOf(bounds.width(), bounds.height()).toFloat()
                    val save = canvas.save()
                    canvas.translate(bounds.exactCenterX(), bounds.exactCenterY())
                    canvas.scale(side / 24f, side / 24f)
                    val outline = Path()
                    // Eight teeth, each with two shoulders and a flat tip.
                    for (tooth in 0 until 8) {
                        for (point in 0 until 4) {
                            val angle = -PI / 2 + (tooth * 4 + point) * PI / 16
                            val radius = if (point == 1 || point == 2) 10.5 else 8.1
                            val x = (cos(angle) * radius).toFloat()
                            val y = (sin(angle) * radius).toFloat()
                            if (tooth == 0 && point == 0) outline.moveTo(x, y)
                            else outline.lineTo(x, y)
                        }
                    }
                    outline.close()
                    canvas.drawPath(outline, paint)
                    canvas.drawCircle(0f, 0f, 3.2f, paint)
                    canvas.restoreToCount(save)
                }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
                override fun setColorFilter(filter: android.graphics.ColorFilter?) {
                    paint.colorFilter = filter
                    invalidateSelf()
                }
                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
            settings = ImageView(launcher).apply {
                setImageDrawable(settingsIcon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding((10 * density).toInt(), (10 * density).toInt(),
                    (10 * density).toInt(), (10 * density).toInt())
                contentDescription = "Options Prime Search"
                setOnClickListener {
                    PrimeSearchOverlay.dismiss()
                    launcher.startActivity(
                        PreferenceActivity.createIntent(launcher, Search(SearchRoute.PRIME_SEARCH)),
                    )
                }
            }
            searchBar.addView(settings, FrameLayout.LayoutParams(
                (40 * density).toInt(), (40 * density).toInt(),
                Gravity.END or Gravity.CENTER_VERTICAL,
            ).apply { marginEnd = (8 * density).toInt() })
            input.setPadding(inset, 0, (56 * density).toInt(), 0)
        }
        lateinit var dialog: PrimeSearchSurface
        dialog = PrimeSearchSurface(launcher, container, floating, density) {
            algorithm.cancel(true)
            if (activeAlgorithm === algorithm) activeAlgorithm = null
            if (activeDialog === dialog) activeDialog = null
        }
        val packageManager = launcher.packageManager
        // Do not enumerate installed apps or query usage stats on the UI thread
        // before the search window and its keyboard can appear.
        var installed: List<android.content.pm.ApplicationInfo> = cachedApps.orEmpty()
        var usage: Map<String, android.app.usage.UsageStats> = cachedUsage.orEmpty()
        var hasUsageAccess = cachedUsageGranted
        val sections = listOf("FRÉQUENTES", "RÉCENTES", "NOUVELLES")
        var selectedTab = when (PrimeSearchResultPreferences.openingTab(launcher)) {
            PrimeSearchResultPreferences.OpeningTab.LAST -> PrimeSearchResultPreferences.lastOpenedTab(launcher)
            PrimeSearchResultPreferences.OpeningTab.FREQUENT -> 0
            PrimeSearchResultPreferences.OpeningTab.RECENT -> 1
            PrimeSearchResultPreferences.OpeningTab.NEW -> 2
        }
        fun renderZeroState() {
            tabs.removeAllViews()
            zeroApps.removeAllViews()
            sections.forEachIndexed { index, name ->
                val tab = TextView(launcher).apply {
                    text = name
                    textSize = 12f
                    setTextColor(themed(if (index == selectedTab) ThemeColorRole.PRIME_SEARCH_TAB_ACTIVE else ThemeColorRole.PRIME_SEARCH_TAB_INACTIVE))
                    // Preserve independent theme colors while making selection
                    // legible even when both roles resolve to similar colors.
                    alpha = if (index == selectedTab) 1f else 0.55f
                    gravity = Gravity.CENTER
                    setPadding((8 * density).toInt(), inset, (8 * density).toInt(), inset)
                    setOnClickListener {
                        selectedTab = index
                        PrimeSearchResultPreferences.setLastOpenedTab(launcher, index)
                        renderZeroState()
                    }
                }
                tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (selectedTab != 2 && !hasUsageAccess) {
                val permission = TextView(launcher).apply {
                    text = "Autoriser l’accès aux données d’utilisation pour afficher les applications fréquentes et récentes.\nAppuyer pour ouvrir les paramètres Android."
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(themed(ThemeColorRole.PRIME_SEARCH_TEXT))
                    setPadding(inset, (8 * density).toInt(), inset, (8 * density).toInt())
                    setOnClickListener {
                        launcher.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
                zeroApps.addView(permission, LinearLayout.LayoutParams(-1, -2))
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
            val usableWidth = (if (floating) {
                (launcher.resources.displayMetrics.widthPixels - (24 * density).toInt())
                    .coerceAtMost((600 * density).toInt())
            } else launcher.resources.displayMetrics.widthPixels) - inset * 2
            val cellWidth = (usableWidth / columns)
                .coerceAtLeast(1)
            // Always allocate the configured number of rows, even if a tab
            // has fewer apps (or none). This keeps the overlay height stable
            // while switching between Fréquentes, Récentes and Nouvelles.
            val rows = PrimeSearchResultPreferences.zeroStateRows(launcher)
            // Derive row height from the actual icon dimension, font metrics
            // and vertical padding instead of a hard-coded 100dp.
            val iconSize = (48 * density * PrimeSearchResultPreferences.iconSizePercent(launcher) / 100f).toInt()
            val titleTextSizeSp = 11f
            val titlePaint = android.text.TextPaint().apply {
                textSize = titleTextSizeSp * launcher.resources.displayMetrics.scaledDensity
            }
            val labelLines = if (PrimeSearchResultPreferences.twoLineAppLabels(launcher)) 2 else 1
            val titleHeight = kotlin.math.ceil(
                (titlePaint.fontMetrics.descent - titlePaint.fontMetrics.ascent).toDouble()
            ).toInt().coerceAtLeast(1) * labelLines
            val cellTopPadding = (4 * density).toInt()
            val iconLabelGap = (4 * density).toInt()
            val cellBottomPadding = (10 * density).toInt()
            val rowHeight = iconSize + iconLabelGap + titleHeight +
                cellTopPadding + cellBottomPadding
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
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        setPadding(2, cellTopPadding, 2, cellBottomPadding)
                        setOnClickListener {
                            packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                                dialog.hideKeyboardForSelection()
                                dialog.dismiss()
                                launcher.startActivity(intent)
                            }
                        }
                    }
                    val component = packageManager.getLaunchIntentForPackage(app.packageName)?.component
                    val appInfo = component?.let { componentName ->
                        launcher.appsView.appsStore.getApp(
                            com.android.launcher3.util.ComponentKey(componentName, android.os.Process.myUserHandle())
                        )
                    }
                    if (appInfo != null) {
                        val nativeIcon = com.android.launcher3.BubbleTextView(launcher).apply {
                            applyFromApplicationInfo(appInfo)
                            // The default BubbleTextView uses the workspace icon size,
                            // which exceeds this 48dp zero-state slot and gets clipped.
                            // Match the slot and use the separate label below.
                            setPrimeIconSize(iconSize)
                            // These zero-state app rows bypass SearchResultIcon.bind().
                            // Trigger the same geometric fit after binding and sizing.
                            setPrimeIconShape(
                                PreferenceManager2.getInstance(launcher).iconShape.firstCached()
                            )
                            setTextVisibility(false)
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            setCompoundDrawablePadding(0)
                            setPadding(0, 0, 0, 0)
                            setOnClickListener {
                                // ItemClickHandler ignores detached views. Dispatch while
                                // this icon is still attached to Prime Search, then close.
                                com.android.launcher3.touch.ItemClickHandler.INSTANCE.onClick(this)
                                dialog.dismiss()
                            }
                            
                        }
                        cell.addView(nativeIcon, LinearLayout.LayoutParams(iconSize, iconSize))
                        enableNativeAppGestures(launcher, dialog, nativeIcon)
                    } else {
                        val fallbackIcon = ImageView(launcher).apply {
                            setImageDrawable(packageManager.getApplicationIcon(app))
                        }
                        cell.addView(fallbackIcon, LinearLayout.LayoutParams(iconSize, iconSize))
                    }
                    val title = TextView(launcher).apply {
                        text = packageManager.getApplicationLabel(app)
                        textSize = titleTextSizeSp
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        includeFontPadding = false
                        maxLines = labelLines
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(themed(ThemeColorRole.PRIME_SEARCH_APP_LABEL))
                    }
                    cell.addView(title, LinearLayout.LayoutParams(-1, titleHeight).apply {
                        topMargin = iconLabelGap
                    })
                    row.addView(cell, LinearLayout.LayoutParams(cellWidth, rowHeight))
                }
            }
            zeroState.requestLayout()
        }
        renderZeroState()
        val webHandler = Handler(Looper.getMainLooper())
        var webRequestSerial = 0
        var currentSuggestions = emptyList<String>()
        val webPanel = LinearLayout(launcher).apply { orientation = LinearLayout.VERTICAL }
        fun renderWebPanel(query: String) {
            webPanel.removeAllViews()
            if (query.isBlank()) return
            val foreground = themed(ThemeColorRole.PRIME_SEARCH_RESULT_TEXT)
            fun row(title: String, action: () -> Unit) {
                val isHeading = title in setOf("Suggestions Web", "Raccourcis Web", "Contacts", "Événements de l'agenda", "Tâches · Tasks.org")
                val label = TextView(launcher).apply {
                    text = title
                    textSize = 15f
                    setTextColor(foreground)
                    if (isHeading) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    val top = ((if (isHeading) 18 else 4) * density).toInt()
                    val bottom = ((if (isHeading) 2 else 4) * density).toInt()
                    val left = (12 * density).toInt() + (if (isHeading) 0 else (42 * density).toInt())
                    setPadding(left, top, (12 * density).toInt(), bottom)
                    if (!isHeading) minimumHeight = (44 * density).toInt()
                    setOnClickListener {
                        if (title != "Suggestions Web" && title != "Raccourcis Web") dialog.hideKeyboardForSelection()
                        action()
                    }
                }
                webPanel.addView(label, LinearLayout.LayoutParams(-1, -2))
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.WEB) && currentSuggestions.isNotEmpty()) {
                row("Suggestions Web") {}
                currentSuggestions.forEach { suggestion ->
                    row("⌕  $suggestion") {
                        val provider = PrimeSearchResultPreferences.suggestionProvider(launcher)
                        val engine = when (provider) {
                            PrimeSearchResultPreferences.SuggestionProvider.GOOGLE -> PrimeSearchResultPreferences.WebEngine.GOOGLE
                            PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO -> PrimeSearchResultPreferences.WebEngine.DUCKDUCKGO
                            PrimeSearchResultPreferences.SuggestionProvider.BING -> PrimeSearchResultPreferences.WebEngine.BING
                            PrimeSearchResultPreferences.SuggestionProvider.QWANT -> PrimeSearchResultPreferences.WebEngine.QWANT
                            PrimeSearchResultPreferences.SuggestionProvider.STARTPAGE -> PrimeSearchResultPreferences.WebEngine.STARTPAGE
                        }
                        runCatching {
                            launcher.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(engine.url + Uri.encode(suggestion))))
                            dismiss()
                        }
                    }
                }
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.CONTACTS)) {
                val contacts = PrimeContactSearch.search(launcher, query)
                if (contacts.isNotEmpty()) {
                    row("Contacts") {}
                    contacts.forEach { contact ->
                        row("👤  ${contact.name}") {
                            runCatching {
                                launcher.startActivity(Intent(Intent.ACTION_VIEW, contact.uri))
                                dismiss()
                            }
                        }
                    }
                }
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.CALENDAR)) {
                val events = PrimeCalendarSearch.events(launcher, query)
                if (events.isNotEmpty()) {
                    row("Événements de l'agenda") {}
                    events.forEach { event ->
                        row("📅  ${event.title}\n${event.detail}") {
                            runCatching {
                                launcher.startActivity(Intent(Intent.ACTION_VIEW, event.uri))
                                dismiss()
                            }
                        }
                    }
                }
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.REMINDERS)) {
                val tasks = PrimeTasksSearch.search(launcher, query)
                if (tasks.isNotEmpty()) {
                    row("Tâches · Tasks.org") {}
                    tasks.forEach { task ->
                        row("☑  ${task.title}" + task.detail.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()) {
                            runCatching {
                                launcher.startActivity(Intent(Intent.ACTION_VIEW, task.uri))
                                dismiss()
                            }
                        }
                    }
                }
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.NOTIFICATIONS)) {
                if (PrimeNotificationHistory.isGranted(launcher)) {
                    val matches = runCatching { PrimeNotificationHistory.search(launcher, query) }
                        .getOrDefault(emptyList())
                    if (matches.isNotEmpty()) {
                        row("Historique des notifications") {}
                        val formatter = java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
                        )
                        matches.forEach { entry ->
                            row("🔔  ${entry.app} · ${entry.title}\n${entry.body.take(180)}\n${formatter.format(java.util.Date(entry.posted))}") {}
                        }
                    }
                } else if (query.contains("notif", ignoreCase = true)) {
                    row("Autoriser l'accès aux notifications") {
                        launcher.startActivity(PrimeNotificationHistory.accessIntent(launcher))
                    }
                }
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.SETTINGS)) {
                // Use localized labels rather than Android intent action class names.
                val settingsActions = listOf(
                    Triple("Wi-Fi", android.provider.Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi-fi", "réseau", "network")),
                    Triple("Bluetooth", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth")),
                    Triple("Affichage", android.provider.Settings.ACTION_DISPLAY_SETTINGS, listOf("affichage", "écran", "display", "screen")),
                    Triple("Son", android.provider.Settings.ACTION_SOUND_SETTINGS, listOf("son", "audio", "sound")),
                    Triple("Batterie", android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("batterie", "économie", "battery")),
                    Triple("Date et heure", android.provider.Settings.ACTION_DATE_SETTINGS, listOf("date", "heure", "time")),
                    Triple("Applications", android.provider.Settings.ACTION_APPLICATION_SETTINGS, listOf("application", "appli", "apps")),
                    Triple("Paramètres Android", android.provider.Settings.ACTION_SETTINGS, listOf("paramètre", "réglage", "settings"))
                )
                val locale = launcher.resources.configuration.locales[0].language
                val labels = if (locale == "fr") settingsActions else settingsActions.map { (name, action, keywords) ->
                    Triple(when (name) {
                        "Affichage" -> "Display"
                        "Son" -> "Sound"
                        "Batterie" -> "Battery"
                        "Date et heure" -> "Date & time"
                        "Applications" -> "Apps"
                        "Paramètres Android" -> "Android Settings"
                        else -> name
                    }, action, keywords)
                }
                val term = query.trim()
                val generic = listOf("param", "régl", "setting").any { term.contains(it, ignoreCase = true) }
                val matches = labels.filter { (name, _, keywords) ->
                    generic || name.contains(term, ignoreCase = true) ||
                        keywords.any { it.contains(term, ignoreCase = true) || term.contains(it, ignoreCase = true) }
                }
                if (term.length >= 2 && matches.isNotEmpty()) {
                    row(if (locale == "fr") "Paramètres de l'appareil" else "Device settings") {}
                    matches.forEach { (name, action, _) ->
                        row("⚙  $name") {
                            runCatching {
                                launcher.startActivity(Intent(action))
                                dismiss()
                            }
                        }
                    }
                }
            }
            val engines = if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.WEB_SHORTCUTS))
                PrimeSearchResultPreferences.enabledWebEngines(launcher) else emptyList()
            if (engines.isNotEmpty()) {
                row("Raccourcis Web") {}
                val columns = 5
                engines.chunked(columns).forEach { group ->
                    val icons = LinearLayout(launcher).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START
                    }
                    group.forEach { engine ->
                        val cell = LinearLayout(launcher).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding((4 * density).toInt(), (5 * density).toInt(),
                                (4 * density).toInt(), (8 * density).toInt())
                            contentDescription = "Rechercher sur ${engine.title}"
                            setOnClickListener {
                                dialog.hideKeyboardForSelection()
                                val url = engine.url + Uri.encode(query)
                                val preferredPackages = when (engine) {
                                    PrimeSearchResultPreferences.WebEngine.FDROID ->
                                        listOf("org.fdroid.fdroid", "com.machiav3lli.fdroid")
                                    PrimeSearchResultPreferences.WebEngine.GOOGLE_PLAY ->
                                        listOf("com.android.vending")
                                    else -> emptyList()
                                }
                                var opened = false
                                // Try an installed compatible client first, then the browser.
                                for (packageId in preferredPackages) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            setPackage(packageId)
                                        }
                                        launcher.startActivity(intent)
                                        opened = true
                                        break
                                    } catch (_: android.content.ActivityNotFoundException) { }
                                      catch (_: SecurityException) { }
                                }
                                if (!opened) {
                                    try {
                                        launcher.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        opened = true
                                    } catch (_: android.content.ActivityNotFoundException) { }
                                }
                                if (opened) dismiss()
                            }
                        }
                        val icon = ImageView(launcher).apply {
                            val iconSize = (36 * density).toInt()
                            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                            contentDescription = engine.title
                            // Start with an installed app icon when available.
                            val packageName = when (engine) {
                                PrimeSearchResultPreferences.WebEngine.SPOTIFY -> "com.spotify.music"
                                PrimeSearchResultPreferences.WebEngine.DUCKDUCKGO -> "com.duckduckgo.mobile.android"
                                PrimeSearchResultPreferences.WebEngine.WIKIPEDIA -> "org.wikipedia"
                                PrimeSearchResultPreferences.WebEngine.YOUTUBE -> "com.google.android.youtube"
                                PrimeSearchResultPreferences.WebEngine.REDDIT -> "com.reddit.frontpage"
                                else -> null
                            }
                            val drawable = try { packageName?.let { packageManager.getApplicationIcon(it) } }
                                catch (_: Exception) { null }
                            if (drawable != null) {
                                setImageDrawable(drawable)
                            } else {
                                val fallback = android.graphics.drawable.GradientDrawable().apply {
                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                    setColor(themed(ThemeColorRole.PRIME_SEARCH_BAR_BACKGROUND))
                                    setStroke((1 * density).toInt().coerceAtLeast(1),
                                        themed(ThemeColorRole.PRIME_SEARCH_BORDER))
                                }
                                background = fallback
                                val site = Uri.parse(engine.url).host.orEmpty()
                                Thread {
                                    val bitmap = try {
                                        val connection = URL("https://www.google.com/s2/favicons?domain=" +
                                            Uri.encode(site) + "&sz=96").openConnection() as HttpURLConnection
                                        connection.connectTimeout = 2000
                                        connection.readTimeout = 2000
                                        try { connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } }
                                        finally { connection.disconnect() }
                                    } catch (_: Exception) { null }
                                    if (bitmap != null) post { setImageBitmap(bitmap) }
                                }.start()
                            }
                        }
                        cell.addView(icon)
                        cell.addView(TextView(launcher).apply {
                            text = engine.title
                            textSize = 10f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = Gravity.CENTER
                            setTextColor(foreground)
                        }, LinearLayout.LayoutParams(-1, -2))
                        icons.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    webPanel.addView(icons, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
        fun updateWebSuggestions(query: String) {
            val request = ++webRequestSerial
            currentSuggestions = emptyList()
            val count = PrimeSearchResultPreferences.webSuggestionCount(launcher)
            if (query.isBlank() || count == 0 ||
                !PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.WEB)) return
            // Keep network I/O off the main thread; ignore stale responses.
            webHandler.postDelayed({
                Thread {
                val suggestions = try {
                    val provider = PrimeSearchResultPreferences.suggestionProvider(launcher)
                    val endpoint = when (provider) {
                        PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO ->
                            "https://duckduckgo.com/ac/?q="
                        PrimeSearchResultPreferences.SuggestionProvider.BING ->
                            "https://api.bing.com/osjson.aspx?query="
                        PrimeSearchResultPreferences.SuggestionProvider.QWANT ->
                            "https://api.qwant.com/v3/suggest?q="
                        PrimeSearchResultPreferences.SuggestionProvider.STARTPAGE ->
                            "https://www.startpage.com/suggestions?format=opensearch&q="
                        PrimeSearchResultPreferences.SuggestionProvider.GOOGLE ->
                            "https://suggestqueries.google.com/complete/search?client=firefox&q="
                    }
                    val url = URL(endpoint + Uri.encode(query))
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 2500
                    connection.readTimeout = 2500
                    try {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        when (provider) {
                            PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO -> {
                                val entries = JSONArray(body)
                                (0 until minOf(entries.length(), count)).mapNotNull {
                                    entries.optJSONObject(it)?.optString("phrase")?.takeIf(String::isNotBlank)
                                }
                            }
                            PrimeSearchResultPreferences.SuggestionProvider.QWANT -> {
                                val items = org.json.JSONObject(body).optJSONObject("data")?.optJSONArray("items")
                                if (items == null) emptyList() else
                                    (0 until minOf(items.length(), count)).mapNotNull {
                                        items.optJSONObject(it)?.optString("value")?.takeIf(String::isNotBlank)
                                    }
                            }
                            else -> {
                                val entries = JSONArray(body).getJSONArray(1)
                                (0 until minOf(entries.length(), count)).map { entries.getString(it) }
                            }
                        }
                    } finally { connection.disconnect() }
                } catch (_: Exception) {
                    // Google suggestions can be unavailable on some networks.
                    // DuckDuckGo provides a compatible fallback.
                    try {
                        val connection = URL("https://duckduckgo.com/ac/?q=" + Uri.encode(query))
                            .openConnection() as HttpURLConnection
                        connection.connectTimeout = 2500
                        connection.readTimeout = 2500
                        try {
                            val entries = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                            (0 until minOf(entries.length(), count)).mapNotNull {
                                entries.optJSONObject(it)?.optString("phrase")?.takeIf(String::isNotBlank)
                            }
                        } finally { connection.disconnect() }
                    } catch (_: Exception) { emptyList() }
                }
                webHandler.post {
                    if (request == webRequestSerial && input.text.toString() == query && dialog.isShowing) {
                        currentSuggestions = suggestions
                        renderWebPanel(query)
                        container.requestLayout()
                    }
                }
                }.start()
            }, 250L)
        }
        val callback = object : SearchCallback<BaseAllAppsAdapter.AdapterItem> {
            override fun onSearchResult(query: String, items: ArrayList<BaseAllAppsAdapter.AdapterItem>?) {
                if (!dialog.isShowing || input.text.toString() != query) return
                results.removeAllViews()
                scroll.scrollTo(0, 0)
                scroll.post { if (dialog.isShowing && input.text.toString() == query) scroll.scrollTo(0, 0) }
                // Re-measure after the search results have changed.
                results.post { if (dialog.isShowing) container.requestLayout() }
                // Preserve the algorithm's order and section boundaries. Each item is
                // inflated with the same layout used by the drawer's adapter provider.
                val inflater = LayoutInflater.from(launcher)
                val columns = PrimeSearchResultPreferences.appsPerRow(launcher)
                // Each row is centered independently. Fixed-width cells keep
                // identical spacing whether the row has 1, 2 or 6 apps.
                val gridWidth = (if (floating) {
                    (launcher.resources.displayMetrics.widthPixels - (24 * density).toInt())
                        .coerceAtMost((600 * density).toInt())
                } else launcher.resources.displayMetrics.widthPixels) - inset * 2
                val cellWidth = (gridWidth / columns).coerceAtLeast(1)
                // Match the zero-state Prime Search icon and label settings.
                // This is local to the Prime overlay; drawer search is untouched.
                val searchIconSize = (48 * density *
                    PrimeSearchResultPreferences.iconSizePercent(launcher) / 100f).toInt().coerceAtLeast(1)
                val searchLabelLines = if (PrimeSearchResultPreferences.twoLineAppLabels(launcher)) 2 else 1
                val searchLabelPaint = android.text.TextPaint().apply {
                    textSize = 11f * launcher.resources.displayMetrics.scaledDensity
                }
                val searchLabelHeight = kotlin.math.ceil(
                    (searchLabelPaint.fontMetrics.descent - searchLabelPaint.fontMetrics.ascent).toDouble()
                ).toInt().coerceAtLeast(1) * searchLabelLines
                // Use the same geometry as the working Fréquentes/Récentes/Nouvelles grid.
                val searchCellTopPadding = (4 * density).toInt()
                val searchIconLabelGap = (4 * density).toInt()
                val searchRowBottomPadding = (10 * density).toInt()
                val iconViewHeight = searchIconSize
                val searchRowHeight = searchCellTopPadding + iconViewHeight +
                    searchIconLabelGap + searchLabelHeight + searchRowBottomPadding
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
                            cellWidth, searchRowHeight,
                        ))
                        appsInRow++
                    } else {
                        parent.addView(view)
                    }
                    item.setRippleEffect(view)
                    resultView.bind(item.searchTarget, emptyList())
                    // Bound drawer results own their click actions. Hide the keyboard
                    // on a completed tap without replacing those click handlers.
                    view.setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN && view.isClickable) {
                            dialog.hideKeyboardForSelection()
                        }
                        false
                    }
                    // The inherited drawer search action has no title property on
                    // SearchTargetCompat. Inspect the actual bound row instead.
                    // This affects Prime Search only, not the app drawer.
                    fun isLegacyGoogleAction(node: View): Boolean {
                        if (node is TextView) {
                            val label = node.text?.toString()?.trim().orEmpty()
                            if (label.equals("Rechercher avec Google", ignoreCase = true) ||
                                label.equals("Search with Google", ignoreCase = true)) return true
                        }
                        if (node is ViewGroup) {
                            for (i in 0 until node.childCount) {
                                if (isLegacyGoogleAction(node.getChildAt(i))) return true
                            }
                        }
                        return false
                    }
                    if (!isAppIcon && isLegacyGoogleAction(view)) {
                        parent.removeView(view)
                        return@forEach
                    }
                    if (item.searchTarget.resultType == SearchTargetCompat.RESULT_TYPE_FILE_TILE) {
                        // The drawer file tile has large internal spacing; build a
                        // dedicated compact Prime Search row instead.
                        fun findFileTitle(node: View): String? {
                            if (node is TextView && !node.text.isNullOrBlank()) return node.text.toString()
                            if (node is ViewGroup) for (i in 0 until node.childCount) {
                                findFileTitle(node.getChildAt(i))?.let { return it }
                            }
                            return null
                        }
                        fun findFileIcon(node: View): android.graphics.drawable.Drawable? {
                            if (node is ImageView && node.drawable != null) return node.drawable
                            if (node is ViewGroup) for (i in 0 until node.childCount) {
                                findFileIcon(node.getChildAt(i))?.let { return it }
                            }
                            return null
                        }
                        val filename = findFileTitle(view).orEmpty()
                        if (!PrimeSearchResultPreferences.fileAllowed(launcher, filename)) {
                            parent.removeView(view)
                            return@forEach
                        }
                        val compact = LinearLayout(launcher).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((12 * density).toInt(), (2 * density).toInt(),
                                (12 * density).toInt(), (2 * density).toInt())
                            minimumHeight = (52 * density).toInt()
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { view.performClick() }
                        }
                        val icon = ImageView(launcher).apply {
                            setImageDrawable(findFileIcon(view)?.constantState?.newDrawable()?.mutate()
                                ?: findFileIcon(view))
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        compact.addView(icon, LinearLayout.LayoutParams(
                            (32 * density).toInt(), (32 * density).toInt()).apply {
                            marginEnd = (10 * density).toInt()
                        })
                        val title = TextView(launcher).apply {
                            text = filename
                            setTextColor(themed(ThemeColorRole.PRIME_SEARCH_RESULT_TEXT))
                            textSize = 15f
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        compact.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                        parent.removeView(view)
                        parent.addView(compact)
                        return@forEach
                    }
                    // Drawer search result views may apply their own text colors
                    // during bind. Override them after binding for Prime Search.
                    fun applyPrimeTextColors(node: View) {
                        if (node is TextView) {
                            node.setTextColor(themed(
                                if (isAppIcon) ThemeColorRole.PRIME_SEARCH_APP_LABEL
                                else ThemeColorRole.PRIME_SEARCH_RESULT_TEXT,
                            ))
                        }
                        if (node is ViewGroup) {
                            for (childIndex in 0 until node.childCount) {
                                applyPrimeTextColors(node.getChildAt(childIndex))
                            }
                        }
                    }
                    applyPrimeTextColors(view)
                    // Normalize every bound text result after the drawer view has
                    // finished binding; drawer styles otherwise overwrite Prime's scale.
                    if (!isAppIcon) {
                        fun normalizeText(node: View) {
                            if (node is TextView) {
                                node.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                                node.includeFontPadding = false
                            }
                            if (node is ViewGroup) {
                                for (index in 0 until node.childCount) normalizeText(node.getChildAt(index))
                            }
                        }
                        normalizeText(view)
                    }
                    if (!isAppIcon && item.searchTarget.layoutType.toString().contains("HEADER", ignoreCase = true)) {
                        // Replace the drawer's fixed-height header wrapper with a compact label.
                        fun headerLabel(node: View): CharSequence? {
                            if (node is TextView && !node.text.isNullOrBlank()) return node.text
                            if (node is ViewGroup) for (index in 0 until node.childCount) {
                                headerLabel(node.getChildAt(index))?.let { return it }
                            }
                            return null
                        }
                        val heading = headerLabel(view)
                        if (heading != null) {
                            val compactHeading = TextView(launcher).apply {
                                text = heading
                                setTextColor(themed(ThemeColorRole.PRIME_SEARCH_RESULT_TEXT))
                                gravity = Gravity.CENTER_VERTICAL
                            }
                            parent.removeView(view)
                            parent.addView(compactHeading, LinearLayout.LayoutParams(-1, -2))
                            app.lawnchair.search.SearchResultGeometry.styleHeading(compactHeading, compactHeading)
                            return@forEach
                        }
                    }
                    if (isAppIcon) {
                        view.minimumHeight = 0
                        (view as? app.lawnchair.allapps.views.SearchResultIcon)?.let { icon ->
                            icon.setPrimeIconSize(searchIconSize)
                            icon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                            // The drawer's BubbleTextView text layout still truncates
                            // at one line. Match zero-state: icon and label are two
                            // independent views inside the same Prime-only cell.
                            val appTitle = icon.text.toString()
                            icon.setTextVisibility(false)
                            icon.setPrimeSearchLabelLayout(true)
                            icon.setPrimeIconOnlyLayout(true)
                            icon.setPadding(0, 0, 0, 0)
                            icon.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            icon.setCompoundDrawablePadding(0)
                            parent.removeView(icon)
                            val cell = LinearLayout(launcher).apply {
                                orientation = LinearLayout.VERTICAL
                                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                setPadding(0, searchCellTopPadding, 0, searchRowBottomPadding)
                            }
                            cell.addView(icon, LinearLayout.LayoutParams(cellWidth, iconViewHeight))
                            val title = TextView(launcher).apply {
                                text = appTitle
                                textSize = 11f
                                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                setSingleLine(false)
                                maxLines = searchLabelLines
                                includeFontPadding = false
                                ellipsize = android.text.TextUtils.TruncateAt.END
                                setTextColor(themed(ThemeColorRole.PRIME_SEARCH_APP_LABEL))
                            }
                            cell.addView(title, LinearLayout.LayoutParams(-1, searchLabelHeight).apply {
                                topMargin = searchIconLabelGap
                            })
                            parent.addView(cell, LinearLayout.LayoutParams(cellWidth, searchRowHeight))
                            enableNativeAppGestures(launcher, dialog, icon)
                        }
                    }
                }
                results.addView(webPanel)
                renderWebPanel(query)
                results.requestLayout()
            }

            override fun clearSearchResult() {
                results.removeAllViews()
                results.requestLayout()
            }
        }
        // IME visibility and focus changes must not restart an unchanged query.
        // Only an actual change in the search text starts a new request.
        var lastSubmittedQuery = input.text?.toString().orEmpty()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                if (query == lastSubmittedQuery) return
                lastSubmittedQuery = query
                scroll.scrollTo(0, 0)
                zeroState.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
                scroll.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                settings.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
                updateWebSuggestions(query)
                if (query.isBlank()) {
                    results.removeAllViews()
                    renderZeroState()
                } else algorithm.doSearch(query, callback)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        activeDialog = dialog
        input.requestFocus()
        dialog.show()
        if (floating) {
            val decor = dialog.decorView
            val screenHeight = launcher.resources.displayMetrics.heightPixels
            val margin = (12 * density).toInt()
            var lastHeight = -1
            dialog.observeLayout(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!dialog.isShowing) {
                        decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    val visible = Rect()
                    decor.getWindowVisibleDisplayFrame(visible)
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                    val imeVisible = insets?.isVisible(
                        androidx.core.view.WindowInsetsCompat.Type.ime()
                    ) == true
                    val root = launcher.window?.decorView ?: decor.rootView
                    val rootLocation = IntArray(2)
                    val contentLocation = IntArray(2)
                    root.getLocationOnScreen(rootLocation)
                    decor.getLocationOnScreen(contentLocation)
                    val rootBottom = rootLocation[1] + root.height
                    val imeBottomInset = if (imeVisible) {
                        insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    } else 0
                    // adjustResize already moves the visible frame above the keyboard.
                    // On adjustNothing/adjustPan the window stays full height, so use
                    // the IME top instead. Never subtract the keyboard height twice.
                    val keyboardTop = if (imeVisible && imeBottomInset > 0) {
                        if (rootBottom < screenHeight - (48 * density).toInt()) {
                            visible.bottom
                        } else {
                            minOf(visible.bottom, rootBottom - imeBottomInset)
                        }
                    } else {
                        visible.bottom
                    }
                    val maximum = (keyboardTop - contentLocation[1] -
                        if (imeVisible) (8 * density).toInt() else margin)
                        .coerceAtLeast((160 * density).toInt())
                    val barHeight = input.height.coerceAtLeast((64 * density).toInt())
                    val desiredContent = if (zeroState.visibility == View.VISIBLE) zeroState.measuredHeight else results.measuredHeight
                    val desired = if (imeVisible && scroll.visibility == View.VISIBLE) {
                        // With the keyboard visible, fill the space above it;
                        // the ScrollView handles content that exceeds that height.
                        maximum
                    } else {
                        (barHeight + desiredContent + inset)
                            .coerceIn((120 * density).toInt(), maximum)
                    }
                    if (desired != lastHeight) {
                        lastHeight = desired
                        dialog.updateHeight(desired)
                    }
                }
            })
        }
        Thread {
            val loadedApps = runCatching {
                packageManager.getInstalledApplications(0).filter {
                    packageManager.getLaunchIntentForPackage(it.packageName) != null
                }
            }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            val loadedUsage = runCatching {
                launcher.getSystemService(UsageStatsManager::class.java)
                    ?.queryAndAggregateUsageStats(now - 30L * 24 * 60 * 60 * 1000, now)
                    .orEmpty()
            }.getOrDefault(emptyMap())
            val granted = runCatching {
                val ops = launcher.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), launcher.packageName) ==
                    AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
            cachedApps = loadedApps
            cachedUsage = loadedUsage
            cachedUsageGranted = granted
            input.post {
                if (activeDialog === dialog && dialog.isShowing) {
                    installed = loadedApps
                    usage = loadedUsage
                    hasUsageAccess = granted
                    if (input.text.isNullOrBlank()) renderZeroState()
                }
            }
        }.start()
        container.translationY = -120 * density
        container.animate().translationY(0f).setDuration(220).start()
        // Request the IME as soon as the search field is attached, rather than
        // waiting for the next posted UI task. Keep the existing Prime Search
        // entrance animation and its 220 ms duration unchanged.
        val keyboard = launcher.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!PrimeSearchResultPreferences.autoShowKeyboard(launcher)) {
            // Keep the field usable: tapping it still opens the keyboard normally.
        } else if (input.isAttachedToWindow) {
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } else {
            input.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    input.removeOnAttachStateChangeListener(this)
                    if (activeDialog === dialog && dialog.isShowing) {
                        keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {
                    input.removeOnAttachStateChangeListener(this)
                }
            })
        }
        zeroState.visibility = View.VISIBLE
        scroll.visibility = View.GONE
    }

    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
    }
}
