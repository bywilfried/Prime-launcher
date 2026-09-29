package app.lawnchair.prime.drawer

import android.content.Context
import android.util.TypedValue
import androidx.core.content.edit
import app.lawnchair.preferences.CallbackPrefEntry
import app.lawnchair.preferences.PrefEntry
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
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
    private val tabsRepository = PrimeDrawerTabsRepository(this.context)

    fun get(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode = PrimeDrawerMode.current(legacy),
    ): PrimeDrawerModeProfile {
        val root = readRoot()
        val stored = root.optJSONObject(mode.storageKey)
        if (stored != null) {
            val profile = stored.toProfile(gridOption)
            return if (stored.has("predictionsEnabled")) {
                profile
            } else {
                profile.copy(predictionsEnabled = mode != PrimeDrawerMode.TABS)
            }
        }
        return legacyProfile(gridOption).copy(predictionsEnabled = mode != PrimeDrawerMode.TABS)
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

    fun reset(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode,
    ): PrimeDrawerModeProfile {
        val profile = defaultProfile(gridOption).copy(predictionsEnabled = mode != PrimeDrawerMode.TABS)
        val root = readRoot()
        root.put(mode.storageKey, profile.toJson())
        prefs.edit { putString(PREF_MODE_PROFILES, root.toString()) }
        return profile
    }

    fun <T> preference(
        gridOption: InvariantDeviceProfile.GridOption,
        mode: PrimeDrawerMode,
        key: String,
        read: (PrimeDrawerModeProfile) -> T,
        write: (PrimeDrawerModeProfile, T) -> PrimeDrawerModeProfile,
        invalidate: (T) -> Unit = { ReloadHelper(context).reloadGrid() },
    ): PrefEntry<T> = CallbackPrefEntry(
        key = "$PREF_MODE_PROFILES/${mode.storageKey}/$key",
        defaultValue = read(legacyProfile(gridOption)),
        getter = { read(get(gridOption, mode)) },
        setter = { newValue ->
            update(gridOption, mode, { profile -> write(profile, newValue) }, { invalidate(newValue) })
        },
    )

    private fun defaultProfile(grid: InvariantDeviceProfile.GridOption): PrimeDrawerModeProfile {
        fun floatResource(id: Int): Float = TypedValue().also {
            context.resources.getValue(id, it, true)
        }.float

        return PrimeDrawerModeProfile(
            drawerOpacity = .5f,
            drawerColumns = grid.numAllAppsColumns,
            drawerColumnsUnfolded = grid.numAllAppsColumns + 2,
            drawerIconSize = floatResource(com.android.launcher3.R.dimen.config_default_drawer_icon_size_factor),
            showLabels = context.resources.getBoolean(com.android.launcher3.R.bool.config_default_show_icon_labels_in_drawer),
            labelSize = floatResource(com.android.launcher3.R.dimen.config_default_drawer_icon_label_size_factor),
            twoLineLabels = context.resources.getBoolean(com.android.launcher3.R.bool.config_default_enable_two_line_allapps),
            rowHeight = floatResource(com.android.launcher3.R.dimen.config_default_drawer_cell_height_factor),
            horizontalMargin = floatResource(com.android.launcher3.R.dimen.config_default_drawer_left_right_factor),
            topPadding = floatResource(com.android.launcher3.R.dimen.config_default_drawer_padding_top),
            rememberPosition = context.resources.getBoolean(com.android.launcher3.R.bool.config_default_remember_position),
            showScrollbar = context.resources.getBoolean(com.android.launcher3.R.bool.config_default_show_scrollbar),
            predictionsEnabled = true,
            predictionMode = legacy2.predictionMode.firstCached().toString(),
            predictionUseWeightedUsageStats = legacy2.lawnchairPredictorUseWeightedUsageStats.firstCached(),
            hideFolderApps = true,
            appDrawerBackgroundColor = ColorOption.fromString(
                context.getString(com.android.launcher3.R.string.config_default_app_drawer_bg_color),
            ),
            defaultDrawerTextColor = null,
            defaultTabsColor = null,
            workProfileTabsColor = null,
        )
    }

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
            predictionsEnabled = legacy2.enableGlobalPrediction.firstCached(),
            predictionMode = legacy2.predictionMode.firstCached().toString(),
            predictionUseWeightedUsageStats = legacy2.lawnchairPredictorUseWeightedUsageStats.firstCached(),
            hideFolderApps = legacy.folderApps.get(),
            appDrawerBackgroundColor = legacy2.appDrawerBackgroundColor.firstCached(),
            defaultDrawerTextColor = tabsRepository.getConfiguration().defaultDrawerTextColor,
            defaultTabsColor = null,
            workProfileTabsColor = null,
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
        put("predictionsEnabled", predictionsEnabled)
        put("predictionMode", predictionMode)
        put("predictionUseWeightedUsageStats", predictionUseWeightedUsageStats)
        put("hideFolderApps", hideFolderApps)
        put("appDrawerBackgroundColor", appDrawerBackgroundColor.toString())
        put("defaultDrawerTextColor", defaultDrawerTextColor ?: JSONObject.NULL)
        put("defaultTabsColor", defaultTabsColor ?: JSONObject.NULL)
        put("workProfileTabsColor", workProfileTabsColor ?: JSONObject.NULL)
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
        predictionsEnabled = optBoolean("predictionsEnabled", legacy2.enableGlobalPrediction.firstCached()),
        predictionMode = optString("predictionMode", legacy2.predictionMode.firstCached().toString()),
        predictionUseWeightedUsageStats = optBoolean("predictionUseWeightedUsageStats", legacy2.lawnchairPredictorUseWeightedUsageStats.firstCached()),
        hideFolderApps = optBoolean("hideFolderApps", legacy.folderApps.get()),
        appDrawerBackgroundColor = ColorOption.fromString(optString("appDrawerBackgroundColor", legacy2.appDrawerBackgroundColor.firstCached().toString())),
        defaultDrawerTextColor = if (has("defaultDrawerTextColor") && !isNull("defaultDrawerTextColor")) getInt("defaultDrawerTextColor") else tabsRepository.getConfiguration().defaultDrawerTextColor,
        defaultTabsColor = if (has("defaultTabsColor") && !isNull("defaultTabsColor")) getInt("defaultTabsColor") else null,
        workProfileTabsColor = if (has("workProfileTabsColor") && !isNull("workProfileTabsColor")) getInt("workProfileTabsColor") else null,
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
    val predictionsEnabled: Boolean,
    val predictionMode: String,
    val predictionUseWeightedUsageStats: Boolean,
    val hideFolderApps: Boolean,
    val appDrawerBackgroundColor: ColorOption,
    val defaultDrawerTextColor: Int?,
    val defaultTabsColor: Int?,
    val workProfileTabsColor: Int?,
)
