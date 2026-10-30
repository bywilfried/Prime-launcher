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

    // Match the file results: 15sp text and 52dp minimum row height.
    fun styleTextRow(view: View) {
        view.minimumHeight = view.dp(52)
        view.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.findViewById<TextView>(R.id.title)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            includeFontPadding = false
        }
        view.findViewById<TextView>(R.id.subtitle)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            includeFontPadding = false
        }
        view.findViewById<View>(R.id.text_rows)?.apply {
            setPadding(paddingLeft, view.dp(4), paddingRight, view.dp(4))
        }
    }

    fun styleFileRow(view: View) {
        view.minimumHeight = view.dp(52)
        view.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.findViewById<TextView>(R.id.title)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            includeFontPadding = false
        }
    }
}
