package app.lawnchair.data.wallpaper.model

import android.app.Application
import android.app.WallpaperManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.wallpaper.WallpaperManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {
    private val wallpaperManagerCompat = WallpaperManagerCompat.INSTANCE.get(application)

    val wallpapers: StateFlow<List<Wallpaper>>
        field = MutableStateFlow<List<Wallpaper>>(emptyList())

    private val mutex = Mutex()

    private val listener = object : WallpaperManagerCompat.OnColorsChangedListener {
        override fun onColorsChanged() {
            viewModelScope.launch {
                mutex.withLock {
                    saveWallpaper(wallpaperManagerCompat.wallpaperManager)
                }
            }
        }
    }

    init {
        loadTopWallpapers()
        wallpaperManagerCompat.addOnChangeListener(listener)
    }

    private suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        withContext(Dispatchers.IO) {
            wallpaperManagerCompat.service.saveWallpaper(wallpaperManager)
        }
        refreshWallpapers()
    }

    private suspend fun refreshWallpapers() {
        val topWallpapers = withContext(Dispatchers.IO) {
            var entries = wallpaperManagerCompat.service.dao.getTopWallpapers()
            if (entries.isEmpty()) {
                // A restored launcher can have a wallpaper but no local carousel history.
                wallpaperManagerCompat.service.saveWallpaper(wallpaperManagerCompat.wallpaperManager)
                entries = wallpaperManagerCompat.service.dao.getTopWallpapers()
            }
            entries
        }
        wallpapers.value = topWallpapers
    }

    private fun loadTopWallpapers() {
        viewModelScope.launch {
            mutex.withLock {
                refreshWallpapers()
            }
        }
    }

    suspend fun updateWallpaperRank(wallpaper: Wallpaper) {
        withContext(Dispatchers.IO) {
            wallpaperManagerCompat.service.updateWallpaperRank(wallpaper)
        }
        loadTopWallpapers()
    }
}
