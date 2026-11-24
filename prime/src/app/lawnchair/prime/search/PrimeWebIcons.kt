package app.lawnchair.prime.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors

/** Optional first-party favicon retrieval. Never sends search terms. */
internal object PrimeWebIcons {
    fun localResource(engine: PrimeSearchResultPreferences.WebEngine): Int = when (engine) {
        PrimeSearchResultPreferences.WebEngine.GOOGLE,
        PrimeSearchResultPreferences.WebEngine.MODE_AI -> com.android.launcher3.R.drawable.ic_super_g_color
        PrimeSearchResultPreferences.WebEngine.DUCKDUCKGO -> com.android.launcher3.R.drawable.ic_duckduckgo
        PrimeSearchResultPreferences.WebEngine.BRAVE,
        PrimeSearchResultPreferences.WebEngine.BRAVE_BROWSER -> com.android.launcher3.R.drawable.ic_brave
        PrimeSearchResultPreferences.WebEngine.BING -> com.android.launcher3.R.drawable.ic_bing
        PrimeSearchResultPreferences.WebEngine.ECOSIA -> com.android.launcher3.R.drawable.ic_ecosia
        PrimeSearchResultPreferences.WebEngine.STARTPAGE -> com.android.launcher3.R.drawable.ic_startpage
        PrimeSearchResultPreferences.WebEngine.YOUTUBE -> com.android.launcher3.R.drawable.ic_youtube
        PrimeSearchResultPreferences.WebEngine.WIKIPEDIA -> com.android.launcher3.R.drawable.ic_wikipedia
        PrimeSearchResultPreferences.WebEngine.GITHUB -> com.android.launcher3.R.drawable.ic_github
        PrimeSearchResultPreferences.WebEngine.KAGI -> com.android.launcher3.R.drawable.ic_kagi
        PrimeSearchResultPreferences.WebEngine.FDROID -> com.android.launcher3.R.drawable.ic_prime_fdroid
        else -> 0
    }

    private val worker = Executors.newFixedThreadPool(2)
    private val pending = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()
    private val attempted = mutableMapOf<String, Long>()
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_AGE = 30L * 24 * 60 * 60 * 1000
    private const val RETRY = 60L * 60 * 1000

    fun bind(view: ImageView, engine: PrimeSearchResultPreferences.WebEngine,
             surface: PrimeSearchResultPreferences.Surface, hasLocalIcon: Boolean) {
        load(view.context, engine, surface, hasLocalIcon) { bitmap ->
            view.post { view.background = null; view.setImageBitmap(bitmap) }
        }
    }

    fun load(sourceContext: android.content.Context, engine: PrimeSearchResultPreferences.WebEngine,
             surface: PrimeSearchResultPreferences.Surface, hasLocalIcon: Boolean, apply: (Bitmap) -> Unit) {
        val context = sourceContext.applicationContext
        val online = PrimeSearchResultPreferences.downloadWebIcons(context, surface)
        val host = android.net.Uri.parse(engine.url).host ?: return
        val file = File(File(context.noBackupFilesDir, "web-icons"), engine.name + ".png")
        val callback: (Bitmap?) -> Unit = { bitmap ->
            if (bitmap != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (PrimeSearchResultPreferences.downloadWebIcons(context, surface) || !hasLocalIcon) {
                    apply(bitmap)
                }
            }
        }
        worker.execute {
            val cached = BitmapFactory.decodeFile(file.absolutePath)
            if (online || !hasLocalIcon) callback(cached)
            if (!online || (cached != null && System.currentTimeMillis() - file.lastModified() < MAX_AGE)) return@execute
            synchronized(pending) {
                pending[engine.name]?.let { it.add(callback); return@execute }
                val now = System.currentTimeMillis()
                if (now - (attempted[engine.name] ?: 0L) < RETRY) return@execute
                attempted[engine.name] = now
                pending[engine.name] = mutableListOf(callback)
            }
            val bitmap = runCatching {
                if (!PrimeSearchResultPreferences.downloadWebIcons(context, surface)) return@runCatching null
                val connection = URL("https://$host/favicon.ico").openConnection() as HttpsURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    if (connection.responseCode != 200) return@runCatching null
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_BYTES) return@runCatching null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return@runCatching null
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                    file.parentFile?.mkdirs()
                    val atomic = android.util.AtomicFile(file)
                    val stream = atomic.startWrite()
                    try {
                        check(decoded.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        atomic.finishWrite(stream)
                    } catch (error: Exception) {
                        atomic.failWrite(stream)
                        throw error
                    }
                    decoded
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            val listeners = synchronized(pending) { pending.remove(engine.name).orEmpty() }
            listeners.forEach { it(bitmap) }
        }
    }
}
