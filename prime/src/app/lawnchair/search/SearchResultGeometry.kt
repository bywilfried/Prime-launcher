package app.lawnchair.search

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.launcher3.R

/**
 * Shared search result geometry. Theme colors and backgrounds are intentionally
 * owned by the drawer and Prime Search independently.
 */
object SearchResultGeometry {
    private fun View.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    fun styleHeading(label: TextView, container: View) {
        label.setTypeface(label.typeface, android.graphics.Typeface.BOLD)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        label.includeFontPadding = false
        container.minimumHeight = 0
        container.setPadding(container.dp(6), container.dp(18), container.dp(6), container.dp(2))
        container.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        container.findViewById<View>(R.id.text_rows)?.setPadding(0, 0, 0, 0)
    }

    fun styleTextRow(view: View) {
        // Never force a height smaller than the content: this clipped task and web labels.
        view.minimumHeight = view.dp(44)
        view.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    fun styleFileRow(view: View) {
        view.minimumHeight = view.dp(52)
        view.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
