package app.lawnchair.prime.webapps

import android.content.Context
import android.net.Uri
import com.android.launcher3.model.PrimeWebIconCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prime-owned web shortcuts. These are not WebAPKs: the browser remains the
 * runtime, while Prime owns the shortcut metadata and drawer membership.
 */
data class PrimeWebApp(val name: String, val url: String)

class PrimeWebAppsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences(
        "prime_web_apps", Context.MODE_PRIVATE
    )

    fun getAll(): List<PrimeWebApp> = runCatching {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val name = entry.optString("name")
            val url = entry.optString("url")
            if (name.isBlank() || validateUrl(url) == null) null else PrimeWebApp(name, url)
        }.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun add(name: String, rawUrl: String): Boolean {
        val url = validateUrl(rawUrl) ?: return false
        val label = name.trim().take(80)
        if (label.isEmpty()) return false
        val apps = getAll().filterNot { it.url == url } + PrimeWebApp(label, url)
        persist(apps)
        PrimeWebIconCache.fetch(appContext, url)
        return true
    }

    fun remove(url: String) {
        persist(getAll().filterNot { it.url == url })
        PrimeWebIconCache.remove(appContext, url)
    }

    private fun persist(apps: List<PrimeWebApp>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(JSONObject().put("name", app.name).put("url", app.url))
        }
        preferences.edit().putString("entries", array.toString()).apply()
    }

    companion object {
        fun validateUrl(input: String): String? {
            val raw = input.trim()
            if (raw.isBlank()) return null
            val candidate = if ("://" in raw) raw else "https://$raw"
            val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.encodedUserInfo != null) return null
            if (uri.port !in -1..65535 || uri.port == 0) return null
            return uri.toString()
        }
    }
}
