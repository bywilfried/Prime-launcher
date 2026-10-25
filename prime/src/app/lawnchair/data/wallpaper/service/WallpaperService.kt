package app.lawnchair.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.util.bitmapToByteArray
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@LauncherAppSingleton
class WallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    val dao = AppDatabase.Companion.INSTANCE.get(context).wallpaperDao()

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        com.android.launcher3.PrimeDebugLog.wallpaper(context, "capture started")
        try {
            val wallpaperDrawable = wallpaperManager.drawable
            com.android.launcher3.PrimeDebugLog.wallpaper(
                context, "drawable type=" + wallpaperDrawable.javaClass.simpleName,
            )
            val currentBitmap = (wallpaperDrawable as BitmapDrawable).toBitmap()
            com.android.launcher3.PrimeDebugLog.wallpaper(
                context, "bitmap decoded dimensions=" + currentBitmap.width + "x" + currentBitmap.height,
            )
            val byteArray = bitmapToByteArray(currentBitmap)
            com.android.launcher3.PrimeDebugLog.wallpaper(context, "bitmap encoded bytes=" + byteArray.size)
            saveWallpaper(byteArray)
            com.android.launcher3.PrimeDebugLog.wallpaper(context, "capture finished")
        } catch (e: Exception) {
            com.android.launcher3.PrimeDebugLog.wallpaper(
                context, "capture failed type=" + e.javaClass.simpleName + " message=" + e.message,
            )
            Log.e("WallpaperChange", "Error detecting wallpaper change", e)
        }
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) {
        val timestamp = System.currentTimeMillis()

        val checksum = calculateChecksum(imageData)

        val existingWallpapers = dao.getTopWallpapers()
        com.android.launcher3.PrimeDebugLog.wallpaper(context, "history count=" + existingWallpapers.size)

        if (existingWallpapers.any { it.checksum == checksum }) {
            com.android.launcher3.PrimeDebugLog.wallpaper(context, "wallpaper already saved")
            Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
            return
        }
        val imagePath = saveImageToAppStorage(imageData)
        com.android.launcher3.PrimeDebugLog.wallpaper(context, "image saved exists=" + File(imagePath).exists())
        if (existingWallpapers.size < 4) {
            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = existingWallpapers.size,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
            com.android.launcher3.PrimeDebugLog.wallpaper(context, "database insert succeeded")
        } else {
            val lowestRankedWallpaper = existingWallpapers.minByOrNull { it.timestamp }

            if (lowestRankedWallpaper != null) {
                dao.deleteWallpaper(lowestRankedWallpaper.id)
                deleteWallpaperFile(lowestRankedWallpaper.imagePath)
            }

            for (wallpaper in existingWallpapers) {
                if (wallpaper.rank >= (lowestRankedWallpaper?.rank ?: 0)) {
                    dao.updateRank(wallpaper.rank)
                }
            }

            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = 0,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
        }
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) {
        val topWallpapers = dao.getTopWallpapers()
        val currentTime = System.currentTimeMillis()

        dao.updateWallpaper(selectedWallpaper.id, rank = 0, timestamp = currentTime)

        for (wallpaper in topWallpapers) {
            if (wallpaper.id != selectedWallpaper.id) {
                dao.updateRank(wallpaper.rank)
            }
        }
    }

    fun getTopWallpapers(): List<Wallpaper> = runBlocking {
        val wallpapers = dao.getTopWallpapers()
        wallpapers.ifEmpty { emptyList() }
    }

    private fun deleteWallpaperFile(imagePath: String) {
        val file = File(imagePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageHash = imageData.hashCode().toString()
        val imageFile = File(storageDir, "wallpaper_$imageHash.jpg")

        if (!imageFile.exists()) {
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }
        }

        return imageFile.absolutePath
    }

    override fun close() {
        TODO("Not yet implemented")
    }
    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}
