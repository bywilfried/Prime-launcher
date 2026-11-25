package app.lawnchair

import com.android.app.animation.Interpolators
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.touch.AllAppsSwipeController
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun Launcher.animateToAllApps() {
    suspendCancellableCoroutine { cont ->
        // Button/shortcut opening is time-driven, unlike the finger-controlled swipe.
        // Keep button opening snappy; swipe-driven opening has its own timing.
        // Property interpolators shape the motion without an extra deceleration.
        val duration = 450L
        val config = StateAnimationConfig()
        AllAppsSwipeController.applyNormalToAllAppsAnimConfig(this, config)
        config.duration = duration

        val animation = stateManager
            .createAnimationToNewWorkspace(LauncherState.ALL_APPS, config)
        val anim = animation.animationPlayer
        anim.setFloatValues(0f, 1f)
        anim.duration = duration
        anim.interpolator = Interpolators.LINEAR
        anim.addListener(forEndCallback(Runnable { cont.resume(Unit) }))
        animation.dispatchOnStart()
        anim.start()
        cont.invokeOnCancellation { anim.cancel() }
    }
}
