package app.lawnchair.backup

import android.content.Context
import app.lawnchair.theme.ThemeColorInvalidation
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Versioned snapshot of Prime's independent theme preference stores. */
internal object PrimeThemeBackup {
    private val stores = listOf(
        "prime_theme_profile",
        "prime_theme_color_overrides",
        "prime_category_theme_color_overrides",
        "prime_folder_theme_color_overrides",
    )

    fun write(context: Context, output: OutputStream) {
        val data = JSONObject().put("version", 1)
        val preferences = JSONObject()
        stores.forEach { name ->
            val entries = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                when (value) {
                    is String, is Boolean, is Int, is Long, is Float -> entries.put(key, value)
                }
            }
            preferences.put(name, entries)
        }
        data.put("preferences", preferences)
        output.write(data.toString().toByteArray(Charsets.UTF_8))
    }

    fun restore(context: Context, input: InputStream) {
        val data = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(data.getInt("version") == 1) { "Unsupported Prime theme backup version" }
        val preferences = data.getJSONObject("preferences")
        // Parse and validate every store before modifying any saved settings.
        val decoded = stores.associateWith { name ->
            val entries = preferences.getJSONObject(name)
            val values = linkedMapOf<String, Any>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = entries.get(key)
                require(value is String || value is Boolean || value is Number) { "Invalid theme preference" }
                values[key] = value
            }
            values
        }
        decoded.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Number -> editor.putDoubleCompat(key, value)
                }
            }
            check(editor.commit()) { "Failed restoring $name" }
        }
        ThemeColorInvalidation.invalidate()
    }

    private fun android.content.SharedPreferences.Editor.putDoubleCompat(key: String, value: Number) {
        putLong(key, value.toLong())
    }
}
