package app.lawnchair.prime.search

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import app.lawnchair.LawnchairLauncher

/**
 * Initial floating search surface. It intentionally does not transition to All Apps.
 * Search results and configurable appearance will be added in the next integration step.
 */
object PrimeSearchOverlay {
    private var activeDialog: Dialog? = null

    fun show(launcher: LawnchairLauncher) {
        if (activeDialog?.isShowing == true) return
        val density = launcher.resources.displayMetrics.density
        val inset = (16 * density).toInt()
        val input = EditText(launcher).apply {
            isSingleLine = true
            hint = "Prime Search"
            setPadding(inset, inset, inset, inset)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }
        val container = FrameLayout(launcher).apply {
            setPadding(inset, inset, inset, inset)
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        val dialog = Dialog(launcher).apply {
            setContentView(container)
            setCanceledOnTouchOutside(true)
            setOnDismissListener { activeDialog = null }
        }
        activeDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        container.translationY = -120 * density
        container.animate().translationY(0f).setDuration(220).start()
        input.requestFocus()
        input.post {
            (launcher.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun dismiss() {
        activeDialog?.dismiss()
    }
}
