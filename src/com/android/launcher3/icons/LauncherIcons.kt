/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import com.android.launcher3.PrimeDebugLog
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.UserIconInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import app.lawnchair.icons.shape.IconShape

/**
 * Wrapper class to provide access to [BaseIconFactory] and also to provide pool of this class that
 * are threadsafe.
 */
class LauncherIcons
@AssistedInject
internal constructor(
    @ApplicationContext context: Context,
    idp: InvariantDeviceProfile,
    private var themeManager: ThemeManager,
    private var userCache: UserCache,
    @Assisted private val pool: ConcurrentLinkedQueue<LauncherIcons>,
) : BaseIconFactory(context, idp.fillResIconDpi, idp.iconBitmapSize), AutoCloseable {

    private var primeIconShape: IconShape? = null

    init {
        mThemeController = themeManager.themeController
    }

    /** Recycles a LauncherIcons that may be in-use. */
    fun recycle() {
        clear()
        pool.add(this)
    }

    override fun getUserInfo(user: UserHandle): UserIconInfo {
        return userCache.getUserInfo(user)
    }

    override fun getShapePath(drawable: AdaptiveIconDrawable, iconBounds: Rect): Path {
        primeIconShape?.let { shape ->
            return Path(shape.getMaskPath()).apply {
                val matrix = android.graphics.Matrix()
                matrix.setRectToRect(
                    RectF(0f, 0f, 100f, 100f),
                    RectF(iconBounds),
                    android.graphics.Matrix.ScaleToFit.FILL,
                )
                transform(matrix)
            }
        }
        if (!Flags.enableLauncherIconShapes()) return super.getShapePath(drawable, iconBounds)
        return themeManager.iconShape.getPath(iconBounds)
    }

    /** Runs icon creation with a view-local Prime mask without changing the global theme shape. */
    fun <T> withPrimeIconShape(shape: IconShape, block: LauncherIcons.() -> T): T {
        primeIconShape = shape
        return try {
            block()
        } finally {
            primeIconShape = null
        }
    }

    /** Rasterizes an adaptive icon with the Prime mask replacing the system/Lawnchair mask. */
    fun createPrimeIconBitmap(drawable: Drawable, user: UserHandle, shape: IconShape): BitmapInfo =
        withPrimeIconShape(shape) {
            val safeArea = foregroundSafeArea(shape)
            val source = if (drawable is AdaptiveIconDrawable) {
                // Never rasterize with the child Drawable instances owned by IconCache. Icon
                // creation changes bounds/state while drawing; sharing those instances makes the
                // next live shape change start from a source already touched by the previous one.
                val isolated = (drawable.constantState?.newDrawable()?.mutate()
                    as? AdaptiveIconDrawable) ?: drawable
                // BaseIconFactory rasterizes adaptive layers itself, so overriding
                // AdaptiveIconDrawable.draw() does not affect the resulting bitmap.
                // Keep the adaptive background full-size; fit the foreground artwork
                // to the mask's centered safe area using its actual alpha bounds.
                AdaptiveIconDrawable(
                    isolated.background,
                    FittedForegroundDrawable(isolated.foreground, shape),
                )
            } else {
                drawable.constantState?.newDrawable()?.mutate() ?: drawable
            }
            PrimeDebugLog.d("PrimeIconSizing", "bitmap shape=${shape.key}, input=${drawable.javaClass.simpleName}, adaptive=${drawable is AdaptiveIconDrawable}, safeArea=${safeArea.width()}x${safeArea.height()}")
            createBadgedIconBitmap(
                source,
                BaseIconFactory.IconOptions().setUser(getUserInfo(user)),
            ).also { info ->
                // Diagnostic only: render the same adaptive icon twice without
                // changing the actual bitmap used by the launcher.
                if (drawable is AdaptiveIconDrawable && comparisonCount.getAndIncrement() < 3) {
                    fun renderDiagnostic(factor: Float): BitmapInfo {
                        val copy = (drawable.constantState?.newDrawable()?.mutate()
                            as? AdaptiveIconDrawable) ?: drawable
                        return createBadgedIconBitmap(
                            AdaptiveIconDrawable(
                                copy.background,
                                FittedForegroundDrawable(copy.foreground, shape, factor),
                            ),
                            BaseIconFactory.IconOptions().setUser(getUserInfo(user)),
                        )
                    }
                    val full = renderDiagnostic(1f)
                    val half = renderDiagnostic(0.5f)
                    val fullHash = bitmapFingerprint(full.icon)
                    val halfHash = bitmapFingerprint(half.icon)
                    PrimeDebugLog.d(
                        "PrimeIconSizing",
                        "COMPARE shape=${shape.key} source=${drawable.javaClass.simpleName} " +
                            "full100=$fullHash half50=$halfHash different=${fullHash != halfHash} " +
                            "normal=${bitmapFingerprint(info.icon)}",
                    )
                }
                // Check the actual bitmap returned by BaseIconFactory, rather than
                // assuming the foreground draw logs describe the final output.
                // A per-icon bitmap fingerprint lets us compare before/after
                // builds and identify a stale downstream cache.
                val output = info.icon
                val sampleStep = maxOf(1, output.width / 16)
                var fingerprint = 17
                var nonTransparent = 0
                for (y in 0 until output.height step sampleStep) {
                    for (x in 0 until output.width step sampleStep) {
                        val pixel = output.getPixel(x, y)
                        fingerprint = 31 * fingerprint + pixel
                        if ((pixel ushr 24) != 0) nonTransparent++
                    }
                }
                PrimeDebugLog.d(
                    "PrimeIconSizing",
                    "output shape=${shape.key} size=${output.width}x${output.height} " +
                        "fingerprint=${fingerprint.toUInt().toString(16)} sampledOpaque=$nonTransparent",
                )
            }
        }

    /**
     * Adaptive foreground layers use oversized bounds (often 150% of the icon).
     * Rasterize once, measure visible alpha, and fit those pixels to the safe
     * area instead of scaling the oversized drawable bounds blindly.
     */
    private class FittedForegroundDrawable(
        private val delegate: Drawable,
        private val shape: IconShape,
        private val diagnosticFactor: Float? = null,
    ) : Drawable() {
        private var raster: Bitmap? = null
        private var contentBounds = Rect()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            val rect = bounds
            if (rect.isEmpty) return
            val bitmap = raster?.takeIf { it.width == rect.width() && it.height == rect.height() }
                ?: Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888).also { bmp ->
                    val layerCanvas = Canvas(bmp)
                    delegate.bounds = Rect(0, 0, bmp.width, bmp.height)
                    delegate.draw(layerCanvas)
                    val pixels = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    // Trim sparse edge artifacts (shadows and stray pixels) before
                    // measuring the logo. A weighted centroid alone can move
                    // asymmetric logos away from the actual mask center.
                    val columns = DoubleArray(bmp.width)
                    val rows = DoubleArray(bmp.height)
                    var totalWeight = 0.0
                    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                        val alpha = pixels[y * bmp.width + x] ushr 24
                        if (alpha > 32) {
                            val weight = alpha.toDouble()
                            columns[x] += weight
                            rows[y] += weight
                            totalWeight += weight
                        }
                    }
                    fun percentile(weights: DoubleArray, target: Double): Int {
                        var cumulative = 0.0
                        for (i in weights.indices) {
                            cumulative += weights[i]
                            if (cumulative >= target) return i
                        }
                        return weights.lastIndex
                    }
                    if (totalWeight > 0.0) {
                        // Discard the outer 1% of alpha mass on each axis.
                        val left = percentile(columns, totalWeight * 0.01)
                        val right = percentile(columns, totalWeight * 0.99) + 1
                        val top = percentile(rows, totalWeight * 0.01)
                        val bottom = percentile(rows, totalWeight * 0.99) + 1
                        contentBounds = Rect(left, top, right, bottom)
                    } else {
                        contentBounds = Rect(0, 0, bmp.width, bmp.height)
                    }
                    raster?.recycle()
                    raster = bmp
                }
            val contentWidth = contentBounds.width().toFloat()
            val contentHeight = contentBounds.height().toFloat()
            // The safe area is measured in the actual 100x100 shape mask.
            // Convert it to the 150%-sized adaptive foreground layer. Fit
            // according to the artwork's own aspect ratio, not a fixed square.
            val visibleIconSize = minOf(rect.width(), rect.height()) / 1.5f
            // Fit each artwork's own aspect ratio into the actual shape, not
            // the single maximum-area rectangle (which can be wrong for a
            // wide logo in a diamond, or a tall logo in a narrow mask).
            val mask = android.graphics.Region().apply {
                setPath(shape.getMaskPath(), android.graphics.Region(0, 0, 100, 100))
            }
            val aspect = contentWidth / contentHeight
            fun fits(halfHeight: Float): Boolean {
                val halfWidth = halfHeight * aspect
                if (halfWidth >= 50f || halfHeight >= 50f) return false
                // Sample all four edges: corners alone miss concave masks.
                for (i in 0..40) {
                    val t = i / 40f
                    val x = 50f - halfWidth + 2f * halfWidth * t
                    val y = 50f - halfHeight + 2f * halfHeight * t
                    if (!mask.contains(x.toInt(), (50f - halfHeight).toInt()) ||
                        !mask.contains(x.toInt(), (50f + halfHeight).toInt()) ||
                        !mask.contains((50f - halfWidth).toInt(), y.toInt()) ||
                        !mask.contains((50f + halfWidth).toInt(), y.toInt())) return false
                }
                return true
            }
            var low = 0f
            var high = minOf(49f, 49f / aspect)
            repeat(12) {
                val mid = (low + high) / 2f
                if (fits(mid)) low = mid else high = mid
            }
            // Ten percent clearance from the shape boundary keeps artwork
            // visually separated from sharp tips and antialiased edges.
            val safeHeight = 2f * low * 0.90f
            val safeWidth = safeHeight * aspect
            val maxWidth = visibleIconSize * safeWidth / 100f
            val maxHeight = visibleIconSize * safeHeight / 100f
            val fit = minOf(maxWidth / contentWidth, maxHeight / contentHeight)
            val factor = diagnosticFactor ?: minOf(1f, fit)
            // The robust foreground bounds are centered on the mask.
            // Avoid an additional centroid shift: asymmetric artwork such as
            // shields can otherwise look off-center even when bounds are correct.
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            val dest = RectF(
                cx - contentWidth * factor / 2f,
                cy - contentHeight * factor / 2f,
                cx + contentWidth * factor / 2f,
                cy + contentHeight * factor / 2f,
            )
            canvas.drawBitmap(bitmap, contentBounds, dest, paint)
            PrimeDebugLog.d("PrimeIconSizing", "foreground fitted factor=$factor shape=${shape.key} safe=${safeWidth}x${safeHeight} max=${maxWidth}x${maxHeight} content=${contentWidth}x$contentHeight bounds=$contentBounds")
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * Find the largest centered axis-aligned rectangle wholly inside the
     * selected mask. Unlike checking only four corners, sample each edge:
     * concave/star masks can cut into a rectangle between its corners.
     * Use a conservative inset to allow for antialiased mask boundaries.
     */
    private fun foregroundSafeArea(shape: IconShape): RectF {
        val region = android.graphics.Region().apply {
            setPath(shape.getMaskPath(), android.graphics.Region(0, 0, 100, 100))
        }
        fun fits(halfWidth: Float, halfHeight: Float): Boolean {
            for (i in 0..20) {
                val t = i / 20f
                val x = 50f - halfWidth + 2f * halfWidth * t
                val y = 50f - halfHeight + 2f * halfHeight * t
                if (!region.contains(x.toInt(), (50f - halfHeight).toInt()) ||
                    !region.contains(x.toInt(), (50f + halfHeight).toInt()) ||
                    !region.contains((50f - halfWidth).toInt(), y.toInt()) ||
                    !region.contains((50f + halfWidth).toInt(), y.toInt())) return false
            }
            return true
        }
        // Maximize usable area across possible rectangle aspect ratios.
        var bestWidth = 0f
        var bestHeight = 0f
        var bestArea = 0f
        for (ratioStep in 1..40) {
            val ratio = ratioStep / 20f
            var low = 0f
            var high = minOf(49f, 49f / ratio)
            repeat(10) {
                val mid = (low + high) / 2f
                if (fits(mid * ratio, mid)) low = mid else high = mid
            }
            val width = low * ratio
            val height = low
            val area = width * height
            if (area > bestArea) {
                bestArea = area
                bestWidth = width
                bestHeight = height
            }
        }
        return RectF(50f - bestWidth, 50f - bestHeight,
            50f + bestWidth, 50f + bestHeight)
    }

    override fun close() {
        recycle()
    }

    @AssistedFactory
    internal interface LauncherIconsFactory {
        fun create(pool: ConcurrentLinkedQueue<LauncherIcons>): LauncherIcons
    }

    @LauncherAppSingleton
    class IconPool @Inject internal constructor(private val factory: LauncherIconsFactory) {
        private var pool = ConcurrentLinkedQueue<LauncherIcons>()

        fun obtain(): LauncherIcons = pool.let { it.poll() ?: factory.create(it) }

        fun clear() {
            pool = ConcurrentLinkedQueue()
        }
    }

    companion object {
        private val comparisonCount = AtomicInteger(0)

        private fun bitmapFingerprint(output: Bitmap): String {
            val step = maxOf(1, output.width / 16)
            var fingerprint = 17
            for (y in 0 until output.height step step) {
                for (x in 0 until output.width step step) {
                    fingerprint = 31 * fingerprint + output.getPixel(x, y)
                }
            }
            return fingerprint.toUInt().toString(16)
        }


        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons = context.appComponent.iconPool.obtain()

        @JvmStatic fun clearPool(context: Context) = context.appComponent.iconPool.clear()
    }
}
