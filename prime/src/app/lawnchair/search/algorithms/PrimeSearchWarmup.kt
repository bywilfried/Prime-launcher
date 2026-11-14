package app.lawnchair.search.algorithms

import android.content.Context
import app.lawnchair.prime.search.PrimeSearchResultPreferences
import app.lawnchair.search.algorithms.engine.provider.apps.AppSearchProvider
import com.android.launcher3.LauncherAppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shared warm-up for Prime Search and drawer search.
 * Only prepare data for an enabled category on at least one search surface.
 * Never start a search or load results from contacts/files/network at startup.
 */
object PrimeSearchWarmup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    @Synchronized
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        pending?.cancel()
        val appsEnabled = PrimeSearchResultPreferences.Surface.entries.any { surface ->
            PrimeSearchResultPreferences.enabled(
                appContext, PrimeSearchResultPreferences.Category.APPS, surface
            )
        }
        if (!appsEnabled) return
        pending = LauncherAppState.getInstance(appContext).model.runWhenModelLoaded(scope) { apps ->
            scope.launch(Dispatchers.Default) {
                // Check again in case the user disabled both app sections while loading.
                if (PrimeSearchResultPreferences.Surface.entries.any { surface ->
                        PrimeSearchResultPreferences.enabled(
                            appContext, PrimeSearchResultPreferences.Category.APPS, surface
                        )
                    }) {
                    AppSearchProvider.warmUp(apps)
                }
            }
        }
    }
}
