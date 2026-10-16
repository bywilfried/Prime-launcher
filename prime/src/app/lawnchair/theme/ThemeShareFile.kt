package app.lawnchair.theme

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import com.patrykmichalik.opto.core.setBlocking
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Portable global theme settings. Does not include folder/category-specific overrides. */
object ThemeShareFile {
    private const val FORMAT = "prime-theme"
    private const val VERSION = 1
    private const val OVERRIDES = "prime_theme_color_overrides"

    fun export(context: Context, output: OutputStream) {
        val profile = ThemeProfile.current(context)
        val manager = PreferenceManager2.getInstance(context)
        val colors = JSONObject()
        val prefix = "${profile.id.value}."
        context.getSharedPreferences(OVERRIDES, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (key.startsWith(prefix) && value is String) colors.put(key, value)
        }
        val document = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("profile", profile.id.value)
            .put("colorStyle", manager.colorStyle.firstCached().toString())
            .put("palette", manager.accentColor.firstCached().toString())
            .put("overrides", colors)
        output.write(document.toString(2).toByteArray(Charsets.UTF_8))
    }

    fun import(context: Context, input: InputStream) {
        val document = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(document.getString("format") == FORMAT) { "Not a Prime theme" }
        require(document.getInt("version") == VERSION) { "Unsupported theme version" }
        val profileId = document.getString("profile")
        val profile = ThemeProfile.byId(ThemeProfileId(profileId))
            ?: error("Theme profile is not supported: $profileId")
        val style = document.getString("colorStyle")
        val palette = document.getString("palette")
        val colors = document.getJSONObject("overrides")
        val values = mutableMapOf<String, String>()
        val prefix = "$profileId."
        val keys = colors.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key.startsWith(prefix)) { "Theme override belongs to a different profile" }
            val value = colors.getString(key)
            ColorOption.fromString(value)
            values[key] = value
        }
        val manager = PreferenceManager2.getInstance(context)
        val prefs = context.getSharedPreferences(OVERRIDES, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        values.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Failed to save theme colors" }
        ThemeProfile.select(context, profile)
        manager.colorStyle.setBlocking(ColorStyle.fromString(style))
        manager.accentColor.setBlocking(ColorOption.fromString(palette))
        ThemeColorInvalidation.invalidate()
        app.lawnchair.preferences2.ReloadHelper(context).recreate()
    }
}
