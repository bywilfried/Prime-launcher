package app.lawnchair.search

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.ThemeColors
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.effectiveThemeVariant
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

    // Every non-grid search result uses the file-result typography and row rhythm.
    private fun styleResultRow(view: View, file: Boolean) {
        view.minimumHeight = view.dp(52)
        view.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.findViewById<TextView>(R.id.title)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            includeFontPadding = false
            if (file) {
                maxLines = 2
                isSingleLine = false
            }
        }
        view.findViewById<TextView>(R.id.subtitle)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            includeFontPadding = false
        }
        view.findViewById<View>(R.id.text_rows)?.apply {
            setPadding(paddingLeft, view.dp(4), paddingRight, view.dp(4))
        }
    }

    fun styleTextRow(view: View) = styleResultRow(view, false)

    fun styleFileRow(view: View) = styleResultRow(view, true)

    /**
     * Drawer-only result card treatment. Prime Search owns its own surface and
     * never calls this method, so its palette and rendering remain untouched.
     */
    fun styleDrawerCard(view: View) {
        val context = view.context
        val profile = ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        val cardColor = ThemeColors.resolve(
            context,
            profile,
            ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE,
            variant,
        )
        val selectedColor = ThemeColors.resolve(
            context,
            profile,
            ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND,
            variant,
        )
        val density = view.resources.displayMetrics.density
        val radius = 14f * density
        view.background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = radius
        }
        view.foreground = RippleDrawable(
            ColorStateList.valueOf(selectedColor),
            null,
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(android.graphics.Color.WHITE)
            },
        )
        view.clipToOutline = true
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.leftMargin = (4 * density).toInt()
            params.rightMargin = (4 * density).toInt()
            view.layoutParams = params
        }
    }
}
