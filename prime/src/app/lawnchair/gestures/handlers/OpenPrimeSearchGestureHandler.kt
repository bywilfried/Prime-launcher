package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.prime.search.PrimeSearchOverlay

class OpenPrimeSearchGestureHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        PrimeSearchOverlay.show(launcher)
    }
}
