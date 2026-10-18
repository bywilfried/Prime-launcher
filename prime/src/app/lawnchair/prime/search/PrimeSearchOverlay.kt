package app.lawnchair.prime.search

import android.app.Dialog
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
        if (activeDialog?.isShowing == true && activeDialog?.window?.decorView?.isAttachedToWindow == true) return
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
            setPadding(inset, inset, inset, 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
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
            val settings = ImageView(launcher).apply {
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
            val settingsFooter = LinearLayout(launcher).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(settings, LinearLayout.LayoutParams(
                    (40 * density).toInt(), (40 * density).toInt(),
                ))
            }
            addView(settingsFooter, LinearLayout.LayoutParams(-1, (40 * density).toInt()))
        }
        val dialog = Dialog(launcher).apply {
            setContentView(container)
            setCanceledOnTouchOutside(true)
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setOnDismissListener {
                algorithm.cancel(true)
                if (activeAlgorithm === algorithm) activeAlgorithm = null
                if (activeDialog === this) activeDialog = null
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
        val hasUsageAccess = try {
            val ops = launcher.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), launcher.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
        val sections = listOf("FRÉQUENTES", "RÉCENTES", "NOUVELLES")
        var selectedTab = 0
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
                    setOnClickListener { selectedTab = index; renderZeroState() }
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
            val usableWidth = launcher.resources.displayMetrics.widthPixels -
                (if (floating) (48 * density).toInt() else 0) - inset * 2
            val cellWidth = (usableWidth.coerceAtMost((600 * density).toInt() - inset * 2) / columns)
                .coerceAtLeast(1)
            // Always allocate the configured number of rows, even if a tab
            // has fewer apps (or none). This keeps the overlay height stable
            // while switching between Fréquentes, Récentes and Nouvelles.
            val rows = PrimeSearchResultPreferences.zeroStateRows(launcher)
            // Derive row height from the actual icon dimension, font metrics
            // and vertical padding instead of a hard-coded 100dp.
            val iconSize = (48 * density).toInt()
            val titleTextSizeSp = 11f
            val titlePaint = android.text.TextPaint().apply {
                textSize = titleTextSizeSp * launcher.resources.displayMetrics.scaledDensity
            }
            val titleHeight = (titlePaint.fontMetrics.bottom - titlePaint.fontMetrics.top)
                .toInt().coerceAtLeast(1)
            val cellTopPadding = (6 * density).toInt()
            val cellBottomPadding = (8 * density).toInt()
            val rowHeight = iconSize + titleHeight + cellTopPadding + cellBottomPadding
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
                        setPadding(2, cellTopPadding, 2, cellBottomPadding)
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
                        iconSize, iconSize,
                    ))
                    val title = TextView(launcher).apply {
                        text = packageManager.getApplicationLabel(app)
                        textSize = titleTextSizeSp
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(themed(ThemeColorRole.PRIME_SEARCH_APP_LABEL))
                    }
                    cell.addView(title, LinearLayout.LayoutParams(-1, -2))
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
                val label = TextView(launcher).apply {
                    text = title
                    textSize = 14f
                    setTextColor(foreground)
                    setPadding(inset, (11 * density).toInt(), inset, (11 * density).toInt())
                    setOnClickListener { action() }
                }
                webPanel.addView(label, LinearLayout.LayoutParams(-1, -2))
            }
            if (PrimeSearchResultPreferences.enabled(launcher, PrimeSearchResultPreferences.Category.WEB) && currentSuggestions.isNotEmpty()) {
                row("Suggestions Web") {}
                currentSuggestions.forEach { suggestion ->
                    row("⌕  $suggestion") {
                        input.setText(suggestion)
                        input.setSelection(suggestion.length)
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
                        row("☑  ${task.title}\n${task.detail}") {
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
                row("Recherche sur :") {}
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
                    val url = URL(if (provider == PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO)
                        "https://duckduckgo.com/ac/?q=" + Uri.encode(query)
                        else "https://suggestqueries.google.com/complete/search?client=firefox&q=" + Uri.encode(query))
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 2500
                    connection.readTimeout = 2500
                    try {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        if (provider == PrimeSearchResultPreferences.SuggestionProvider.DUCKDUCKGO) {
                            val entries = JSONArray(body)
                            (0 until minOf(entries.length(), count)).mapNotNull {
                                entries.optJSONObject(it)?.optString("phrase")?.takeIf(String::isNotBlank)
                            }
                        } else {
                            val entries = JSONArray(body).getJSONArray(1)
                            (0 until minOf(entries.length(), count)).map { entries.getString(it) }
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
                    if (isAppIcon) {
                        view.minimumHeight = 0
                    } else if (item.searchTarget.layoutType.toString().contains("FILE", ignoreCase = true)) {
                        // Compact file results only; keep other result types unchanged.
                        view.minimumHeight = 0
                        view.layoutParams = view.layoutParams.apply {
                            height = (76 * density).toInt()
                        }
                        fun compactFileImages(node: View) {
                            if (node is android.widget.ImageView) {
                                node.layoutParams?.let { params ->
                                    if (params.width > (56 * density).toInt()) {
                                        params.width = (52 * density).toInt()
                                        params.height = (52 * density).toInt()
                                        node.layoutParams = params
                                    }
                                }
                            }
                            if (node is ViewGroup) {
                                for (index in 0 until node.childCount) compactFileImages(node.getChildAt(index))
                            }
                        }
                        compactFileImages(view)
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
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                zeroState.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
                scroll.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                updateWebSuggestions(query)
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
                val width = (metrics.widthPixels - (12 * density).toInt() * 2)
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
            val margin = (12 * density).toInt()
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
                        .coerceAtLeast((40 * density).toInt())
                    val desired = (barHeight + desiredContent + footerHeight + inset)
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
        val dialog = activeDialog ?: return
        // Android can detach the dialog window before a second HOME intent
        // arrives. Dialog.dismiss() then throws from removeViewImmediate().
        activeDialog = null
        if (dialog.window?.decorView?.isAttachedToWindow != true) {
            activeAlgorithm?.cancel(true)
            activeAlgorithm = null
            return
        }
        try {
            dialog.dismiss()
        } catch (_: IllegalArgumentException) {
            // WindowManager already removed the decor view.
            activeAlgorithm?.cancel(true)
            activeAlgorithm = null
        }
    }
}
