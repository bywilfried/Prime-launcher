package app.lawnchair.gestures

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.util.TouchController
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Recognizes additional gestures on empty workspace areas, leaving normal icon/widget
 * interactions and the standard page scroller alone unless an enabled gesture wins.
 */
class WorkspaceExtraGestureController(
    private val launcher: LawnchairLauncher,
    private val gestures: GestureController,
) : TouchController {
    private val prefs = PreferenceManager2.getInstance(launcher)
    private val slop = ViewConfiguration.get(launcher).scaledTouchSlop.toFloat()
    private val swipeDistance = 28f * launcher.resources.displayMetrics.density
    private val pinchDistance = 48f * launcher.resources.displayMetrics.density
    private var enabled = BooleanArray(7)
    private var feedEnabled = false
    private var twoFingerUpEnabled = false
    private var twoFingerDownEnabled = false
    private var startedOnEmptySpace = false
    private var active = false
    private var captured = false
    private var fingers = 0
    private var startX = 0f
    private var startY = 0f
    private var startSpan = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var tapStart = 0L
    private var previousTwoTap = 0L
    private var twoTapCandidate = false
    private var fired = false

    init {
        listOf(
            prefs.swipeLeftGestureHandler, prefs.swipeRightGestureHandler,
            prefs.twoFingerSwipeLeftGestureHandler, prefs.twoFingerSwipeRightGestureHandler,
            prefs.pinchInGestureHandler, prefs.pinchOutGestureHandler,
            prefs.twoFingerDoubleTapGestureHandler,
        ).forEachIndexed { index, pref ->
            pref.get().onEach { enabled[index] = it !is GestureHandlerConfig.NoOp }
                .launchIn(launcher.lifecycleScope)
        }
        prefs.twoFingerSwipeUpGestureHandler.get()
            .onEach { twoFingerUpEnabled = it !is GestureHandlerConfig.NoOp }
            .launchIn(launcher.lifecycleScope)
        prefs.twoFingerSwipeDownGestureHandler.get()
            .onEach { twoFingerDownEnabled = it !is GestureHandlerConfig.NoOp }
            .launchIn(launcher.lifecycleScope)
        prefs.enableFeed.get().onEach { feedEnabled = it }.launchIn(launcher.lifecycleScope)
    }

    private fun workspaceReady(): Boolean =
        launcher.isInState(LauncherState.NORMAL) &&
            AbstractFloatingView.getTopOpenView(launcher) == null &&
            launcher.workspace != null

    private fun isEmptySpace(ev: MotionEvent): Boolean {
        val workspace = launcher.workspace ?: return false
        val coords = floatArrayOf(ev.x, ev.y)
        launcher.dragLayer.mapCoordInSelfToDescendant(workspace, coords)
        val page = workspace.getChildAt(workspace.currentPage) as? com.android.launcher3.CellLayout
            ?: return false
        val container = page.shortcutsAndWidgets
        val screenLocation = IntArray(2)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility != android.view.View.VISIBLE) continue
            child.getLocationOnScreen(screenLocation)
            val layerLocation = IntArray(2)
            launcher.dragLayer.getLocationOnScreen(layerLocation)
            val screenX = ev.x + layerLocation[0]
            val screenY = ev.y + layerLocation[1]
            if (screenX >= screenLocation[0] && screenX < screenLocation[0] + child.width &&
                screenY >= screenLocation[1] && screenY < screenLocation[1] + child.height
            ) return false
        }
        return true
    }

    private fun singleAllowed(right: Boolean): Boolean =
        launcher.workspace?.childCount == 1 && (!right || !feedEnabled)

    private fun span(ev: MotionEvent): Float =
        if (ev.pointerCount < 2) 0f else hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0))

    private fun reset() {
        active = false
        captured = false
        fingers = 0
        fired = false
        twoTapCandidate = false
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            reset()
            if (!workspaceReady()) return false
            startedOnEmptySpace = isEmptySpace(ev)
            active = true
            fingers = 1
            startX = ev.x
            startY = ev.y
            lastX = ev.x
            lastY = ev.y
            tapStart = ev.eventTime
            return false
        }
        if (!active) return false
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN && ev.pointerCount == 2) {
            fingers = 2
            startX = (ev.getX(0) + ev.getX(1)) / 2
            startY = (ev.getY(0) + ev.getY(1)) / 2
            startSpan = span(ev)
            twoTapCandidate = enabled[6]
            // Capture two-finger sequences only when a two-finger action is configured.
            captured = enabled.slice(2..6).any { it } || twoFingerUpEnabled || twoFingerDownEnabled
            return captured
        }
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && fingers == 1 && !captured && startedOnEmptySpace) {
            val dx = ev.x - startX
            val dy = ev.y - startY
            if (abs(dx) > slop && abs(dx) > abs(dy) * 1.3f &&
                ((dx < 0 && enabled[0] && singleAllowed(false)) ||
                    (dx > 0 && enabled[1] && singleAllowed(true)))
            ) {
                captured = true
                return true
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (!captured) reset()
        }
        return captured
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (!active || !captured) return false
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && fingers == 2 && ev.pointerCount >= 2) {
            lastX = (ev.getX(0) + ev.getX(1)) / 2
            lastY = (ev.getY(0) + ev.getY(1)) / 2
            val spanDelta = span(ev) - startSpan
            if (!fired && abs(spanDelta) >= pinchDistance &&
                abs(spanDelta) > hypot(lastX - startX, lastY - startY)
            ) {
                if (spanDelta < 0 && enabled[4]) { gestures.onPinchIn(); fired = true }
                if (spanDelta > 0 && enabled[5]) { gestures.onPinchOut(); fired = true }
            }
            val dx = lastX - startX
            val dy = lastY - startY
            // Fire during MOVE rather than waiting for a finger to lift.
            if (!fired && abs(dx) >= swipeDistance && abs(dx) > abs(dy) * 1.3f &&
                abs(spanDelta) < abs(dx)
            ) {
                if (dx < 0 && enabled[2]) { gestures.onTwoFingerSwipeLeft(); fired = true }
                if (dx > 0 && enabled[3]) { gestures.onTwoFingerSwipeRight(); fired = true }
            }
            if (!fired && abs(dy) >= swipeDistance && abs(dy) > abs(dx) * 1.3f &&
                abs(spanDelta) < abs(dy)
            ) {
                if (dy < 0 && twoFingerUpEnabled) { gestures.onTwoFingerSwipeUp(); fired = true }
                if (dy > 0 && twoFingerDownEnabled) { gestures.onTwoFingerSwipeDown(); fired = true }
            }
            if (!fired && hypot(dx, dy) > slop) twoTapCandidate = false
        }
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && fingers == 1) {
            lastX = ev.x
            lastY = ev.y
        }
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP && fingers == 2 && !fired) {
            // The final MOVE can be skipped on a fast flick; use the lift coordinates.
            if (ev.pointerCount >= 2) {
                lastX = (ev.getX(0) + ev.getX(1)) / 2
                lastY = (ev.getY(0) + ev.getY(1)) / 2
            }
            val dx = lastX - startX
            val dy = lastY - startY
            if (abs(dy) >= swipeDistance && abs(dy) > abs(dx) * 1.3f) {
                if (dy < 0 && twoFingerUpEnabled) { gestures.onTwoFingerSwipeUp(); fired = true }
                if (dy > 0 && twoFingerDownEnabled) { gestures.onTwoFingerSwipeDown(); fired = true }
            }
            if (!fired && abs(dx) >= swipeDistance && abs(dx) > abs(dy) * 1.3f) {
                if (dx < 0 && enabled[2]) { gestures.onTwoFingerSwipeLeft(); fired = true }
                if (dx > 0 && enabled[3]) { gestures.onTwoFingerSwipeRight(); fired = true }
            }
            if (!fired && twoTapCandidate && ev.eventTime - tapStart < 280L) {
                if (ev.eventTime - previousTwoTap < 360L) {
                    gestures.onTwoFingerDoubleTap()
                    previousTwoTap = 0L
                    fired = true
                } else previousTwoTap = ev.eventTime
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            if (!fired && fingers == 1 && abs(lastX - startX) >= swipeDistance &&
                abs(lastX - startX) > abs(lastY - startY) * 1.3f
            ) {
                if (lastX < startX && enabled[0] && singleAllowed(false)) gestures.onSwipeLeft()
                if (lastX > startX && enabled[1] && singleAllowed(true)) gestures.onSwipeRight()
            }
            reset()
        }
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL) reset()
        return true
    }
}
