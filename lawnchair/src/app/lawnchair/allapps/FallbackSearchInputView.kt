package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.ThemeColors
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.util.EditTextExtensions.setCursorColor
import app.lawnchair.util.EditTextExtensions.setTextSelectHandleColor
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.allapps.ActivityAllAppsContainerView

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private var appsView: ActivityAllAppsContainerView<*>? = null
    var isResetting = false
        private set

    init {
        applyPrimeSearchTheme()
        val iconColor = resolvePrimeColor(ThemeColorRole.DRAWER_SEARCH_ICON)
        setCursorColor(iconColor)
        setTextSelectHandleColor(iconColor)
        highlightColor = ColorUtils.setAlphaComponent(iconColor, 82)
    }

    fun applyPrimeSearchTheme() {
        setTextColor(resolvePrimeColor(ThemeColorRole.DRAWER_SEARCH_TEXT))
        setHintTextColor(resolvePrimeColor(ThemeColorRole.DRAWER_SEARCH_HINT))
        val iconColor = resolvePrimeColor(ThemeColorRole.DRAWER_SEARCH_ICON)
        setCursorColor(iconColor)
        setTextSelectHandleColor(iconColor)
        highlightColor = ColorUtils.setAlphaComponent(iconColor, 82)
    }

    private fun resolvePrimeColor(role: ThemeColorRole): Int {
        val profile = ThemeProfile.current(context)
        val variant = context.effectiveThemeVariant()
        return ThemeColors.resolve(context, profile, role, variant)
    }

    override fun reset() {
        isResetting = true
        try {
            super.reset()
        } finally {
            isResetting = false
        }
    }

    fun initialize(appsView: ActivityAllAppsContainerView<*>?) {
        this.appsView = appsView
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        // Prefer the active apps list over appsView itself. appsView has
        // focusable=false and its search container is focusedByDefault, so
        // requestFocus() on appsView would re-focus the search field (e.g. when
        // switching Personal/Work tabs triggers resetSearch).
        val appsView = this.appsView
        val activeList = appsView?.activeRecyclerView
        if (activeList != null) {
            activeList.requestFocus()
        } else {
            appsView?.requestFocus()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyPrimeSearchTheme()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.RIGHT or Gravity.CENTER
        }
    }
}
