package app.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.theme.ThemeAppearanceMode
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.theme.ThemeVariant
import app.lawnchair.ui.preferences.PreferenceActivity

/** Actions are performed only when the assigned gesture is triggered. */
class ToggleFlashlightGestureHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        try {
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            val enabled = TorchState.isEnabled(manager, cameraId)
            manager.setTorchMode(cameraId, !enabled)
            TorchState.set(cameraId, !enabled)
        } catch (_: Exception) {
            Toast.makeText(context, "Lampe torche indisponible", Toast.LENGTH_SHORT).show()
        }
    }

    private object TorchState {
        private val states = mutableMapOf<String, Boolean>()
        private var registered = false
        @Synchronized fun isEnabled(manager: CameraManager, id: String): Boolean {
            if (!registered) {
                manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        set(cameraId, enabled)
                    }
                    override fun onTorchModeUnavailable(cameraId: String) {
                        set(cameraId, false)
                    }
                }, Handler(Looper.getMainLooper()))
                registered = true
            }
            return states[id] == true
        }
        @Synchronized fun set(id: String, enabled: Boolean) { states[id] = enabled }
    }
}

class ShowVolumeGestureHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        context.getSystemService(AudioManager::class.java)
            ?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }
}

class TogglePrimeThemeGestureHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val next = if (context.effectiveThemeVariant() == ThemeVariant.DARK) {
            ThemeAppearanceMode.LIGHT
        } else {
            ThemeAppearanceMode.DARK
        }
        PreferenceManager.getInstance(context).launcherTheme.set(next.storedValue)
        launcher.recreate()
    }
}

class OpenPrimeSettingsGestureHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        launcher.startActivity(Intent(launcher, PreferenceActivity::class.java))
    }
}
