package app.lawnchair.data.wallpaper.model

import android.app.Application
import android.app.WallpaperManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val loading: StateFlow<Boolean>
        field = MutableStateFlow(true)

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
        wallpaperManagerCompat.service.saveWallpaper(wallpaperManager)
        refreshWallpapers()
    }

    private suspend fun refreshWallpapers() {
        // Always try to capture the currently active wallpaper, even when
        // historical entries already exist. This also seeds an empty history.
        wallpaperManagerCompat.service.saveWallpaper(wallpaperManagerCompat.wallpaperManager)
        wallpapers.value = wallpaperManagerCompat.service.dao.getTopWallpapers()
        loading.value = false
    }

    private fun loadTopWallpapers() {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    refreshWallpapers()
                } finally {
                    loading.value = false
                }
            }
        }
    }

    suspend fun saveCurrentWallpaperBeforeSwitch() {
        mutex.withLock {
            wallpaperManagerCompat.service.saveWallpaper(wallpaperManagerCompat.wallpaperManager)
        }
    }

    suspend fun updateWallpaperRank(wallpaper: Wallpaper) {
        wallpaperManagerCompat.service.updateWallpaperRank(wallpaper)
        loadTopWallpapers()
    }
}
