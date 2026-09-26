package app.lawnchair.ui.preferences.components.reorderable

import android.os.Build
import android.os.VibrationAttributes
import app.lawnchair.preferences.preferenceManager
import com.google.android.msdl.domain.InteractionProperties

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

enum class ReorderHapticFeedbackType {
    START,
    MOVE,
    END,
    CANCEL,
}

interface ReorderHapticFeedback {
    fun performHapticFeedback(type: ReorderHapticFeedbackType) {}
}

@Composable
fun rememberReorderHapticFeedback(): ReorderHapticFeedback {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val prefs = preferenceManager()

    val reorderHapticFeedback = remember {
        object : ReorderHapticFeedback {
            override fun performHapticFeedback(type: ReorderHapticFeedbackType) {
                val token = when (type) {
                    ReorderHapticFeedbackType.START -> MSDLToken.START
                    ReorderHapticFeedbackType.MOVE -> MSDLToken.DRAG_INDICATOR_DISCRETE
                    ReorderHapticFeedbackType.END -> MSDLToken.STOP
                    ReorderHapticFeedbackType.CANCEL -> MSDLToken.CANCEL
                }
                if (type == ReorderHapticFeedbackType.MOVE) {
                    val percent = prefs.primeHapticReorderMove.get().coerceIn(0, 100)
                    if (percent == 0) return
                    if (percent < 100 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        mMSDLPlayerWrapper.playToken(
                            token,
                            InteractionProperties.DynamicVibrationScale(
                                scale = percent / 100f,
                                vibrationAttributes = VibrationAttributes.Builder()
                                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                                    .build(),
                            ),
                        )
                        return
                    }
                }
                mMSDLPlayerWrapper.playToken(token)
            }
        }
    }

    return reorderHapticFeedback
}

@Composable
internal fun ObserveReorderHapticFeedback(interactionSource: MutableInteractionSource) {
    val haptic = rememberReorderHapticFeedback()
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.START)

                is DragInteraction.Stop ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.END)

                is DragInteraction.Cancel ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.CANCEL)
            }
        }
    }
}
