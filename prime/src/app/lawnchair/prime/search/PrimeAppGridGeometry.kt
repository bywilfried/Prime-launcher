package app.lawnchair.prime.search

import android.content.Context
import android.text.TextPaint
import kotlin.math.ceil

/**
 * Single source of truth for the application-result tile geometry.
 * Both the real search renderer and its settings preview use these values.
 */
object PrimeAppGridGeometry {
    const val ICON_BASE_DP = 48f
    const val LABEL_SP = 11f
    const val TOP_DP = 4f
    const val ICON_LABEL_GAP_DP = 4f
    const val BOTTOM_DP = 4f

    fun iconPx(context: Context, percent: Int): Int =
        (ICON_BASE_DP * context.resources.displayMetrics.density * percent / 100f)
            .toInt().coerceAtLeast(1)

    fun labelHeightPx(context: Context, lines: Int): Int {
        val paint = TextPaint().apply {
            textSize = LABEL_SP * context.resources.displayMetrics.scaledDensity
        }
        return ceil((paint.fontMetrics.descent - paint.fontMetrics.ascent).toDouble())
            .toInt().coerceAtLeast(1) * lines
    }

    fun rowHeightPx(context: Context, percent: Int, lines: Int): Int {
        val density = context.resources.displayMetrics.density
        return (TOP_DP * density).toInt() + iconPx(context, percent) +
            (ICON_LABEL_GAP_DP * density).toInt() + labelHeightPx(context, lines) +
            (BOTTOM_DP * density).toInt()
    }
}
