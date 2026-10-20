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
                val scale = foregroundScale(shape)
                AdaptiveIconDrawable(
                    isolated.background,
                    FittedForegroundDrawable(isolated.foreground, scale),
                )
            } else {
                drawable.constantState?.newDrawable()?.mutate() ?: drawable
            }
            PrimeDebugLog.d("PrimeIconSizing", "bitmap shape=${shape.key}, input=${drawable.javaClass.simpleName}, adaptive=${drawable is AdaptiveIconDrawable}, scale=${foregroundScale(shape)}")
            createBadgedIconBitmap(
                source,
                BaseIconFactory.IconOptions().setUser(getUserInfo(user)),
            )
        }

    /**
     * Adaptive foreground layers use oversized bounds (often 150% of the icon).
     * Rasterize once, measure visible alpha, and fit those pixels to the safe
     * area instead of scaling the oversized drawable bounds blindly.
     */
    private class FittedForegroundDrawable(
        private val delegate: Drawable,
        private val safeScale: Float,
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
                    var left = bmp.width
                    var top = bmp.height
                    var right = 0
                    var bottom = 0
                    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                        if ((pixels[y * bmp.width + x] ushr 24) > 16) {
                            left = minOf(left, x)
                            top = minOf(top, y)
                            right = maxOf(right, x + 1)
                            bottom = maxOf(bottom, y + 1)
                        }
                    }
                    contentBounds = if (right > left && bottom > top) Rect(left, top, right, bottom)
                        else Rect(0, 0, bmp.width, bmp.height)
                    raster?.recycle()
                    raster = bmp
                }
            val contentWidth = contentBounds.width().toFloat()
            val contentHeight = contentBounds.height().toFloat()
            // AdaptiveIconDrawable expands each layer to 150% of the visible icon.
            // The previous 72% of that expanded layer was far too generous:
            // most foregrounds ended up with factor=1 even for a diamond.
            // Convert the safe square (relative to the visible icon) back into
            // layer coordinates, then fit only the foreground's visible pixels.
            val visibleIconSize = minOf(rect.width(), rect.height()) / 1.5f
            val targetSize = visibleIconSize * 0.72f * safeScale
            val factor = minOf(1f, targetSize / maxOf(contentWidth, contentHeight))
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            val dest = RectF(
                cx - contentWidth * factor / 2f,
                cy - contentHeight * factor / 2f,
                cx + contentWidth * factor / 2f,
                cy + contentHeight * factor / 2f,
            )
            canvas.drawBitmap(bitmap, contentBounds, dest, paint)
            PrimeDebugLog.d("PrimeIconSizing", "foreground fitted factor=$factor safeScale=$safeScale target=$targetSize content=${contentWidth}x$contentHeight bounds=$contentBounds")
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * Measure the centered safe square in the actual mask. Adaptive icon artwork
     * is commonly designed around a 72%-wide safe zone, but angular masks need
     * a smaller square so the logo stays away from their corners.
     */
    private fun foregroundScale(shape: IconShape): Float {
        val region = android.graphics.Region().apply {
            setPath(shape.getMaskPath(), android.graphics.Region(0, 0, 100, 100))
        }
        fun fits(halfSide: Float): Boolean {
            val edge = halfSide.toInt()
            return region.contains(50 - edge, 50 - edge) &&
                region.contains(50 + edge, 50 - edge) &&
                region.contains(50 - edge, 50 + edge) &&
                region.contains(50 + edge, 50 + edge)
        }
        var low = 0f
        var high = 50f
        repeat(10) {
            val mid = (low + high) / 2f
            if (fits(mid)) low = mid else high = mid
        }
        // Leave a small safety margin for antialiasing and icon artwork.
        // A full 72%-wide safe square needs no scaling; narrower masks do.
        return ((low - 2f) / 36f).coerceIn(0.65f, 1f)
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

        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons = context.appComponent.iconPool.obtain()

        @JvmStatic fun clearPool(context: Context) = context.appComponent.iconPool.clear()
    }
}
