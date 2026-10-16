package app.lawnchair.prime.search

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.LawnchairLauncher
import app.lawnchair.search.adapter.SearchAdapterItem
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
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
        val algorithm = LawnchairSearchAlgorithm.create(launcher)
        activeAlgorithm = algorithm
        val input = EditText(launcher).apply {
            isSingleLine = true
            hint = "Prime Search"
            setPadding(inset, inset, inset, inset)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }
        val results = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(launcher).apply {
            addView(results)
        }
        val container = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(inset, inset, inset, inset)
            addView(input, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val dialog = Dialog(launcher).apply {
            setContentView(container)
            setCanceledOnTouchOutside(true)
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
                items.orEmpty().filterIsInstance<SearchAdapterItem>().forEach { item ->
                    val target = item.searchTarget
                    val action = target.searchAction
                    val title = action?.title?.toString()
                        ?: if (target.resultType == SearchTargetCompat.RESULT_TYPE_APPLICATION) {
                            try {
                                launcher.packageManager.getApplicationLabel(
                                    launcher.packageManager.getApplicationInfo(target.packageName, 0),
                                ).toString()
                            } catch (_: Exception) {
                                target.packageName
                            }
                        } else null
                    if (title.isNullOrBlank()) return@forEach
                    val row = TextView(launcher).apply {
                        text = title
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        setPadding(inset, inset, inset, inset)
                        setOnClickListener {
                            try {
                                when {
                                    action?.pendingIntent != null -> action.pendingIntent.send()
                                    action?.intent != null -> launcher.startActivity(action.intent)
                                    target.resultType == SearchTargetCompat.RESULT_TYPE_APPLICATION -> {
                                        launcher.packageManager.getLaunchIntentForPackage(target.packageName)
                                            ?.let(launcher::startActivity)
                                    }
                                }
                                dialog.dismiss()
                            } catch (_: Exception) {
                                // Ignore unavailable targets; do not crash the launcher.
                            }
                        }
                    }
                    results.addView(row)
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
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
