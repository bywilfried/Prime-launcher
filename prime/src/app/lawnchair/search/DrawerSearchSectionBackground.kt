package app.lawnchair.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import app.lawnchair.theme.effectiveThemeVariant

/**
 * A single row's contribution to a continuous drawer section card.
 * Only the first and last rows draw the corresponding horizontal edge and
 * rounded corners, avoiding the stacked borders produced by per-row shapes.
 */
internal class DrawerSearchSectionBackground(
    context: Context,
    private val first: Boolean,
    private val last: Boolean,
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val radius = 14f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = app.lawnchair.theme.ThemeColors.resolve(
            context,
            app.lawnchair.theme.ThemeProfile.current(context),
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND,
            context.effectiveThemeVariant(),
        )
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
        color = app.lawnchair.theme.ThemeColors.resolve(
            context,
            app.lawnchair.theme.ThemeProfile.current(context),
            app.lawnchair.theme.ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER,
            context.effectiveThemeVariant(),
        )
    }
    private val rect = RectF()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val halfStroke = borderPaint.strokeWidth / 2f
        rect.set(
            b.left + halfStroke,
            b.top + halfStroke,
            b.right - halfStroke,
            b.bottom - halfStroke,
        )
        val topRadius = if (first) radius else 0f
        val bottomRadius = if (last) radius else 0f
        val radii = floatArrayOf(
            topRadius, topRadius, topRadius, topRadius,
            bottomRadius, bottomRadius, bottomRadius, bottomRadius,
        )
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(path, fillPaint)

        val left = rect.left
        val right = rect.right
        val top = rect.top
        val bottom = rect.bottom
        val rTop = topRadius
        val rBottom = bottomRadius
        canvas.drawLine(left, top + rTop, left, bottom - rBottom, borderPaint)
        canvas.drawLine(right, top + rTop, right, bottom - rBottom, borderPaint)
        if (first) {
            canvas.drawLine(left + rTop, top, right - rTop, top, borderPaint)
            canvas.drawArc(RectF(left, top, left + 2 * rTop, top + 2 * rTop), 180f, 90f, false, borderPaint)
            canvas.drawArc(RectF(right - 2 * rTop, top, right, top + 2 * rTop), 270f, 90f, false, borderPaint)
        }
        if (last) {
            canvas.drawLine(left + rBottom, bottom, right - rBottom, bottom, borderPaint)
            canvas.drawArc(RectF(left, bottom - 2 * rBottom, left + 2 * rBottom, bottom), 90f, 90f, false, borderPaint)
            canvas.drawArc(RectF(right - 2 * rBottom, bottom - 2 * rBottom, right, bottom), 0f, 90f, false, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        borderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
