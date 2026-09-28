package app.lawnchair.prime.drawer

import android.content.Context
import androidx.core.content.edit
import app.lawnchair.preferences.CallbackPrefEntry
import app.lawnchair.preferences.PrefEntry
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.ENABLE_TWOLINE_ALLAPPS_TOGGLE
import org.json.JSONObject

/**
 * Prime-owned per-mode drawer settings.
 *
 * Lawnchair preferences remain the migration/default source. Once a mode profile exists it is
 * independent, so editing Tabs cannot silently change Default or Caddy.
 */
class PrimeDrawerModePreferences(context: Context) {
    private val context = context.applicationContext
    private val prefs = LauncherPrefs.getPrefs(this.context)
    private val legacy = PreferenceManager.getInstance(this.context)
    private val legacy2 = PreferenceManager2.INSTANCE.get(this.context)!!

    fun get(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode = PrimeDrawerMode.current(legacy),
    ): PrimeDrawerModeProfile {
        val root = readRoot()
        val stored = root.optJSONObject(mode.storageKey)
        if (stored != null) return stored.toProfile(gridOption)
        return legacyProfile(gridOption)
    }

    fun update(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode = PrimeDrawerMode.current(legacy),
        transform: (PrimeDrawerModeProfile) -> PrimeDrawerModeProfile,
        invalidate: () -> Unit = { ReloadHelper(context).reloadGrid() },
    ) {
        val root = readRoot()
        root.put(mode.storageKey, transform(get(gridOption, mode)).toJson())
        prefs.edit { putString(PREF_MODE_PROFILES, root.toString()) }
        invalidate()
    }

    fun hasStoredProfile(mode: PrimeDrawerMode): Boolean =
        readRoot().has(mode.storageKey)

    fun <T> preference(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode,
        key: String,
        read: (PrimeDrawerModeProfile) -> T,
        write: (PrimeDrawerModeProfile, T) -> PrimeDrawerModeProfile,
        invalidate: () -> Unit = { ReloadHelper(context).reloadGrid() },
    ): PrefEntry<T> = CallbackPrefEntry(
        key = "$PREF_MODE_PROFILES/${mode.storageKey}/$key",
        defaultValue = read(legacyProfile(gridOption)),
        getter = { read(get(gridOption, mode)) },
        setter = { newValue ->
            update(gridOption, mode, { profile -> write(profile, newValue) }, invalidate)
        },
    )

    private fun legacyProfile(grid: InvariantDeviceProfile.GridOption): PrimeDrawerModeProfile {
        return PrimeDrawerModeProfile(
            drawerOpacity = legacy.drawerOpacity.get(),
            drawerColumns = legacy2.drawerColumns.firstCached(gridOption = grid),
            drawerColumnsUnfolded = legacy2.drawerColumnsUnfolded.firstCached(gridOption = grid),
            drawerIconSize = legacy2.drawerIconSizeFactor.firstCached(),
            showLabels = legacy2.showIconLabelsInDrawer.firstCached(),
            labelSize = legacy2.drawerIconLabelSizeFactor.firstCached(),
            twoLineLabels = legacy2.twoLineAllApps.firstCached(),
            rowHeight = legacy2.drawerCellHeightFactor.firstCached(),
            horizontalMargin = legacy2.drawerLeftRightMarginFactor.firstCached(),
            topPadding = legacy2.drawerPaddingTopFactor.firstCached(),
            rememberPosition = legacy2.rememberPosition.firstCached(),
            showScrollbar = legacy2.showScrollbar.firstCached(),
            hideFolderApps = legacy.primeHideFolderApps.get(),
        )
    }

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(prefs.getString(PREF_MODE_PROFILES, "{}")) }
            .getOrElse { JSONObject() }

    private fun PrimeDrawerModeProfile.toJson() = JSONObject().apply {
        put("drawerOpacity", drawerOpacity.toDouble())
        put("drawerColumns", drawerColumns)
        put("drawerColumnsUnfolded", drawerColumnsUnfolded)
        put("drawerIconSize", drawerIconSize.toDouble())
        put("showLabels", showLabels)
        put("labelSize", labelSize.toDouble())
        put("twoLineLabels", twoLineLabels)
        put("rowHeight", rowHeight.toDouble())
        put("horizontalMargin", horizontalMargin.toDouble())
        put("topPadding", topPadding.toDouble())
        put("rememberPosition", rememberPosition)
        put("showScrollbar", showScrollbar)
        put("hideFolderApps", hideFolderApps)
    }

    private fun JSONObject.toProfile(grid: InvariantDeviceProfile.GridOption) = PrimeDrawerModeProfile(
        drawerOpacity = optDouble("drawerOpacity", legacy.drawerOpacity.get().toDouble()).toFloat(),
        drawerColumns = optInt("drawerColumns", legacy2.drawerColumns.firstCached(gridOption = grid)),
        drawerColumnsUnfolded = optInt(
            "drawerColumnsUnfolded",
            legacy2.drawerColumnsUnfolded.firstCached(gridOption = grid),
        ),
        drawerIconSize = optDouble("drawerIconSize", legacy2.drawerIconSizeFactor.firstCached().toDouble()).toFloat(),
        showLabels = optBoolean("showLabels", legacy2.showIconLabelsInDrawer.firstCached()),
        labelSize = optDouble("labelSize", legacy2.drawerIconLabelSizeFactor.firstCached().toDouble()).toFloat(),
        twoLineLabels = optBoolean("twoLineLabels", legacy2.twoLineAllApps.firstCached()),
        rowHeight = optDouble("rowHeight", legacy2.drawerCellHeightFactor.firstCached().toDouble()).toFloat(),
        horizontalMargin = optDouble(
            "horizontalMargin",
            legacy2.drawerLeftRightMarginFactor.firstCached().toDouble(),
        ).toFloat(),
        topPadding = optDouble("topPadding", legacy2.drawerPaddingTopFactor.firstCached().toDouble()).toFloat(),
        rememberPosition = optBoolean("rememberPosition", legacy2.rememberPosition.firstCached()),
        showScrollbar = optBoolean("showScrollbar", legacy2.showScrollbar.firstCached()),
        hideFolderApps = optBoolean("hideFolderApps", legacy.primeHideFolderApps.get()),
    )

    companion object {
        private const val PREF_MODE_PROFILES = "prime_drawer_mode_profiles_v1"
    }
}

data class PrimeDrawerModeProfile(
    val drawerOpacity: Float,
    val drawerColumns: Int,
    val drawerColumnsUnfolded: Int,
    val drawerIconSize: Float,
    val showLabels: Boolean,
    val labelSize: Float,
    val twoLineLabels: Boolean,
    val rowHeight: Float,
    val horizontalMargin: Float,
    val topPadding: Float,
    val rememberPosition: Boolean,
    val showScrollbar: Boolean,
    val hideFolderApps: Boolean,
)
