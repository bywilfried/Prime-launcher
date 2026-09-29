/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.PrefEntry
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.prime.drawer.PrimeDrawerMode
import app.lawnchair.prime.drawer.PrimeDrawerModePreferences
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppDrawerHapticFeedbackPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceModelList
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreferencePreviewCard
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.AppDrawerHiddenApps
import app.lawnchair.ui.preferences.navigation.PrimeDrawerCategories
import app.lawnchair.ui.preferences.navigation.PrimeDrawerDefaultColor
import app.lawnchair.ui.preferences.navigation.Predictions
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

@Composable
fun AppDrawerPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val resources = context.resources
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val drawerListAdapter = prefs.drawerList.getAdapter()
        val drawerTabsAdapter = prefs.drawerTabsEnabled.getAdapter()
        val activeDrawerMode = when {
            drawerTabsAdapter.state.value -> PrimeDrawerMode.TABS
            drawerListAdapter.state.value -> PrimeDrawerMode.DEFAULT
            else -> PrimeDrawerMode.CADDY
        }
        val modePreferences = PrimeDrawerModePreferences(context)
        val drawerGridOption = InvariantDeviceProfile.INSTANCE.get(context).closestProfile
        val modeProfile = modePreferences.get(drawerGridOption, activeDrawerMode)
        val nativeDrawerOpacity = prefs.drawerOpacity.getAdapter()
        val nativeDrawerColumns = prefs2.drawerColumns.getAdapter()
        val nativeDrawerColumnsUnfolded = prefs2.drawerColumnsUnfolded.getAdapter()
        val nativeDrawerIconSize = prefs2.drawerIconSizeFactor.getAdapter()
        val nativeShowLabels = prefs2.showIconLabelsInDrawer.getAdapter()
        val nativeLabelSize = prefs2.drawerIconLabelSizeFactor.getAdapter()
        val nativeDrawerBackgroundColor = prefs2.appDrawerBackgroundColor.getAdapter()
        val nativeWorkProfileTabsColor = prefs2.workProfileTabBackgroundColor.getAdapter()
        val nativeRowHeight = prefs2.drawerCellHeightFactor.getAdapter()
        val nativeHorizontalMargin = prefs2.drawerLeftRightMarginFactor.getAdapter()
        val nativeTopPadding = prefs2.drawerPaddingTopFactor.getAdapter()
        val nativeTwoLineLabels = prefs2.twoLineAllApps.getAdapter()
        val nativeHideFolderApps = prefs.folderApps.getAdapter()
        val nativeRememberPosition = prefs2.rememberPosition.getAdapter()
        val nativeShowScrollbar = prefs2.showScrollbar.getAdapter()
        fun <T> modePreference(
            key: String,
            read: (app.lawnchair.prime.drawer.PrimeDrawerModeProfile) -> T,
            write: (app.lawnchair.prime.drawer.PrimeDrawerModeProfile, T) -> app.lawnchair.prime.drawer.PrimeDrawerModeProfile,
            invalidate: (T) -> Unit = { ReloadHelper(context).reloadGrid() },
        ) = modePreferences.preference(drawerGridOption, activeDrawerMode, key, read, write, invalidate)
        LaunchedEffect(activeDrawerMode) {
            if (nativeDrawerBackgroundColor.state.value != modeProfile.appDrawerBackgroundColor) nativeDrawerBackgroundColor.onChange(modeProfile.appDrawerBackgroundColor)
            val workTabsColor = modeProfile.workProfileTabsColor
                ?.let { app.lawnchair.theme.color.ColorOption.CustomColor(it) }
                ?: app.lawnchair.theme.color.ColorOption.SystemAccent
            if (nativeWorkProfileTabsColor.state.value != workTabsColor) nativeWorkProfileTabsColor.onChange(workTabsColor)
            if (nativeDrawerOpacity.state.value != modeProfile.drawerOpacity) nativeDrawerOpacity.onChange(modeProfile.drawerOpacity)
            if (nativeDrawerColumns.state.value != modeProfile.drawerColumns) nativeDrawerColumns.onChange(modeProfile.drawerColumns)
            if (nativeDrawerColumnsUnfolded.state.value != modeProfile.drawerColumnsUnfolded) nativeDrawerColumnsUnfolded.onChange(modeProfile.drawerColumnsUnfolded)
            if (nativeDrawerIconSize.state.value != modeProfile.drawerIconSize) nativeDrawerIconSize.onChange(modeProfile.drawerIconSize)
            if (nativeShowLabels.state.value != modeProfile.showLabels) nativeShowLabels.onChange(modeProfile.showLabels)
            if (nativeLabelSize.state.value != modeProfile.labelSize) nativeLabelSize.onChange(modeProfile.labelSize)
            if (nativeRowHeight.state.value != modeProfile.rowHeight) nativeRowHeight.onChange(modeProfile.rowHeight)
            if (nativeHorizontalMargin.state.value != modeProfile.horizontalMargin) nativeHorizontalMargin.onChange(modeProfile.horizontalMargin)
            if (nativeTopPadding.state.value != modeProfile.topPadding) nativeTopPadding.onChange(modeProfile.topPadding)
            if (nativeTwoLineLabels.state.value != modeProfile.twoLineLabels) nativeTwoLineLabels.onChange(modeProfile.twoLineLabels)
            if (nativeHideFolderApps.state.value != modeProfile.hideFolderApps) nativeHideFolderApps.onChange(modeProfile.hideFolderApps)
            if (nativeRememberPosition.state.value != modeProfile.rememberPosition) nativeRememberPosition.onChange(modeProfile.rememberPosition)
            if (nativeShowScrollbar.state.value != modeProfile.showScrollbar) nativeShowScrollbar.onChange(modeProfile.showScrollbar)
        }
        Column {
            DrawerLayoutPreference(
                activeMode = activeDrawerMode,
                onModeChange = { mode ->
                    drawerTabsAdapter.onChange(mode == PrimeDrawerMode.TABS)
                    drawerListAdapter.onChange(mode != PrimeDrawerMode.CADDY)
                    ReloadHelper(context).reloadGrid()
                },
            )
            ExpandAndShrink(visible = drawerListAdapter.state.value && !drawerTabsAdapter.state.value) {
                AppDrawerFolderPreferenceItem()
            }
            ExpandAndShrink(visible = drawerTabsAdapter.state.value) {
                PreferenceGroup(heading = stringResource(id = R.string.prime_tabs_settings)) {
                    NavigationActionPreference(
                        label = stringResource(id = R.string.prime_categories_manage),
                        destination = PrimeDrawerCategories,
                    )
                    ListPreference(
                        adapter = prefs.drawerTabsOpenMode.getAdapter(),
                        label = stringResource(id = R.string.prime_tabs_open_behavior),
                        entries = listOf(
                            ListPreferenceEntry("first") {
                                stringResource(id = R.string.prime_tabs_open_first)
                            },
                            ListPreferenceEntry("last") {
                                stringResource(id = R.string.prime_tabs_open_last)
                            },
                            ListPreferenceEntry("default") {
                                stringResource(id = R.string.prime_tabs_open_default)
                            },
                        ),
                    )
                    SwitchPreference(
                        label = stringResource(id = R.string.prime_tabs_swipe_enabled),
                        adapter = prefs.drawerTabsSwipeEnabled.getAdapter(),
                    )
                    val hideAllAdapter = prefs.drawerTabsHideAll.getAdapter()
                    val hideUnclassifiedAdapter = prefs.drawerTabsHideUnclassified.getAdapter()
                    val hasUserTabs = PrimeDrawerTabsRepository(context)
                        .getConfiguration()
                        .tabs
                        .any { !it.isSystem }
                    val unclassifiedVisible = !hideUnclassifiedAdapter.state.value
                    val canHideAll = hasUserTabs || unclassifiedVisible
                    SwitchPreference(
                        checked = canHideAll && hideAllAdapter.state.value,
                        onCheckedChange = hideAllAdapter::onChange,
                        label = stringResource(id = R.string.prime_tabs_hide_all),
                        enabled = canHideAll,
                    )
                    SwitchPreference(
                        checked = hideUnclassifiedAdapter.state.value,
                        onCheckedChange = { hide ->
                            hideUnclassifiedAdapter.onChange(hide)
                            if (hide && !hasUserTabs && hideAllAdapter.state.value) {
                                hideAllAdapter.onChange(false)
                            }
                        },
                        label = stringResource(id = R.string.prime_tabs_hide_unclassified),
                    )
                }
            }
        }
        val hiddenApps = prefs2.hiddenApps.getAdapter().state.value
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.hidden_apps_label),
                destination = AppDrawerHiddenApps,
                subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenApps.size, hiddenApps.size),
            )
            SearchBarPreference(SearchRoute.DRAWER_SEARCH, showLabel = false)
            NavigationActionPreference(
                label = stringResource(R.string.suggestion_pref_screen_title),
                destination = Predictions,
            )
            AppDrawerHapticFeedbackPreference()
        }
        PreferenceGroup(heading = stringResource(R.string.style)) {
            val navController = app.lawnchair.ui.preferences.LocalNavController.current
            val drawerBackgroundModel = ColorPreferenceModelList.INSTANCE.get(context)[prefs2.appDrawerBackgroundColor.key.name]
            ColorPreference(
                label = stringResource(id = drawerBackgroundModel.labelRes),
                selectedColor = modeProfile.appDrawerBackgroundColor,
                onClick = {
                    navController.navigate(
                        PrimeDrawerDefaultColor(activeDrawerMode.storageKey, "background", resources.getString(drawerBackgroundModel.labelRes)),
                    )
                },
            )
            if (drawerTabsAdapter.state.value) {
                val drawerTabsColorModel = ColorPreferenceModelList.INSTANCE.get(context)[prefs2.drawerTabsColor.key.name]
                ColorPreference(
                    label = stringResource(id = drawerTabsColorModel.labelRes),
                    selectedColor = modeProfile.defaultTabsColor
                        ?.let { app.lawnchair.theme.color.ColorOption.CustomColor(it) }
                        ?: app.lawnchair.theme.color.ColorOption.Default,
                    onClick = {
                        navController.navigate(
                            PrimeDrawerDefaultColor(activeDrawerMode.storageKey, "tabs", resources.getString(drawerTabsColorModel.labelRes)),
                        )
                    },
                )
            }
            ColorPreference(
                label = "Couleur du texte par défaut du drawer",
                selectedColor = modeProfile.defaultDrawerTextColor
                    ?.let { app.lawnchair.theme.color.ColorOption.CustomColor(it) }
                    ?: app.lawnchair.theme.color.ColorOption.Default,
                onClick = {
                    navController.navigate(
                        PrimeDrawerDefaultColor(activeDrawerMode.storageKey, "text", "Couleur du texte par défaut du drawer"),
                    )
                },
            )
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = modeBackedAdapter(modePreference("drawerOpacity", { it.drawerOpacity }, { profile, value -> profile.copy(drawerOpacity = value) }, { }), nativeDrawerOpacity),
                step = 0.1f,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            ColorPreference(
                label = "Couleur des onglets Personnel / Travail",
                selectedColor = modeProfile.workProfileTabsColor
                    ?.let { app.lawnchair.theme.color.ColorOption.CustomColor(it) }
                    ?: app.lawnchair.theme.color.ColorOption.Default,
                onClick = {
                    navController.navigate(
                        PrimeDrawerDefaultColor(activeDrawerMode.storageKey, "workTabs", "Couleur des onglets Personnel / Travail"),
                    )
                },
            )
            SwitchPreference(
                label = stringResource(id = R.string.work_profile_tab_container_background_label),
                adapter = prefs2.workProfileTabContainerBackground.getAdapter(),
            )
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_search_bar_background),
                adapter = prefs2.appDrawerSearchBarBackground.getAdapter(),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            val drawerColumnsAdapter = modeBackedAdapter(modePreference("drawerColumns", { it.drawerColumns }, { profile, value -> profile.copy(drawerColumns = value) }, { }), nativeDrawerColumns)
            val drawerColumnsUnfoldedAdapter = modeBackedAdapter(modePreference("drawerColumnsUnfolded", { it.drawerColumnsUnfolded }, { profile, value -> profile.copy(drawerColumnsUnfolded = value) }, { }), nativeDrawerColumnsUnfolded)
            if (isFoldable) {
                SliderPreference(
                    label = stringResource(id = R.string.state_folded, stringResource(id = R.string.app_drawer_columns)),
                    adapter = drawerColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                SliderPreference(
                    label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.app_drawer_columns)),
                    adapter = drawerColumnsUnfoldedAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                ExpandAndShrink(
                    visible = drawerColumnsAdapter.state.value > drawerColumnsUnfoldedAdapter.state.value,
                ) {
                    WarningPreference(
                        text = stringResource(id = R.string.foldable_columns_error),
                    )
                }
            } else {
                SliderPreference(
                    label = stringResource(id = R.string.app_drawer_columns),
                    adapter = drawerColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
            }
            SliderPreference(
                adapter = modeBackedAdapter(modePreference("rowHeight", { it.rowHeight }, { profile, value -> profile.copy(rowHeight = value) }, { }), nativeRowHeight),
                label = stringResource(id = R.string.row_height_label),
                valueRange = 0.3F..1.5F,
                step = 0.1F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = modeBackedAdapter(modePreference("horizontalMargin", { it.horizontalMargin }, { profile, value -> profile.copy(horizontalMargin = value) }, { }), nativeHorizontalMargin),
                label = stringResource(id = R.string.app_drawer_indent_label),
                valueRange = 0.0F..1.5F,
                step = 0.05F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = modeBackedAdapter(modePreference("topPadding", { it.topPadding }, { profile, value -> profile.copy(topPadding = value) }, { }), nativeTopPadding),
                label = stringResource(id = R.string.top_padding_label),
                valueRange = 1.0F..2.0F,
                step = 0.05F,
                showAsPercentage = true,
            )
        }
        val showDrawerLabels = modeBackedAdapter(modePreference("showLabels", { it.showLabels }, { profile, value -> profile.copy(showLabels = value) }, { }), nativeShowLabels)
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_sizes),
                adapter = modeBackedAdapter(modePreference("drawerIconSize", { it.drawerIconSize }, { profile, value -> profile.copy(drawerIconSize = value) }, { }), nativeDrawerIconSize),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = showDrawerLabels,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(
                visible = showDrawerLabels.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = modeBackedAdapter(modePreference("labelSize", { it.labelSize }, { profile, value -> profile.copy(labelSize = value) }, { }), nativeLabelSize),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            ExpandAndShrink(
                visible = showDrawerLabels.state.value,
            ) {
                SwitchPreference(
                    adapter = modeBackedAdapter(modePreference("twoLineLabels", { it.twoLineLabels }, { profile, value -> profile.copy(twoLineLabels = value) }, { }), nativeTwoLineLabels),
                    label = stringResource(R.string.twoline_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
            SwitchPreference(
                label = stringResource(id = R.string.apps_in_folder_label),
                description = stringResource(id = R.string.apps_in_folder_description),
                adapter = modeBackedAdapter(modePreference("hideFolderApps", { it.hideFolderApps }, { profile, value -> profile.copy(hideFolderApps = value) }, { }), nativeHideFolderApps),
            )
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_remember_position_title),
                description = stringResource(id = R.string.pref_all_apps_remember_position_description),
                adapter = modeBackedAdapter(modePreference("rememberPosition", { it.rememberPosition }, { profile, value -> profile.copy(rememberPosition = value) }, { }), nativeRememberPosition),
            )
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_show_scrollbar_title),
                adapter = modeBackedAdapter(modePreference("showScrollbar", { it.showScrollbar }, { profile, value -> profile.copy(showScrollbar = value) }, { }), nativeShowScrollbar),
            )
        }
        PreferenceGroup {
            ClickablePreference(
                label = "Réinitialiser les réglages du mode",
                subtitle = "Rétablit les réglages généraux de ce mode avec les valeurs Lawnchair par défaut.",
                confirmationText = "Réinitialiser les réglages de ce mode ? Les catégories et dossiers ne seront pas modifiés.",
                onClick = {
                    val resetProfile = modePreferences.reset(drawerGridOption, activeDrawerMode)
                    nativeDrawerBackgroundColor.onChange(resetProfile.appDrawerBackgroundColor)
                    nativeWorkProfileTabsColor.onChange(app.lawnchair.theme.color.ColorOption.SystemAccent)
                    nativeDrawerOpacity.onChange(resetProfile.drawerOpacity)
                    nativeDrawerColumns.onChange(resetProfile.drawerColumns)
                    nativeDrawerColumnsUnfolded.onChange(resetProfile.drawerColumnsUnfolded)
                    nativeDrawerIconSize.onChange(resetProfile.drawerIconSize)
                    nativeShowLabels.onChange(resetProfile.showLabels)
                    nativeLabelSize.onChange(resetProfile.labelSize)
                    nativeRowHeight.onChange(resetProfile.rowHeight)
                    nativeHorizontalMargin.onChange(resetProfile.horizontalMargin)
                    nativeTopPadding.onChange(resetProfile.topPadding)
                    nativeTwoLineLabels.onChange(resetProfile.twoLineLabels)
                    nativeHideFolderApps.onChange(resetProfile.hideFolderApps)
                    nativeRememberPosition.onChange(resetProfile.rememberPosition)
                    nativeShowScrollbar.onChange(resetProfile.showScrollbar)
                    ReloadHelper(context).reloadGrid()
                },
            )
        }
    }
}

@Composable
private fun DrawerLayoutPreference(
    activeMode: PrimeDrawerMode,
    onModeChange: (PrimeDrawerMode) -> Unit,
) {
    val maxPreviewHeight = LocalConfiguration.current.screenHeightDp.dp / 4
    val maxPreviewWidth = maxPreviewHeight * 4 / 3

    Column {
        app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading(stringResource(id = R.string.layout))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val spacing = 12.dp
            val availableCardWidth = (maxWidth - spacing * 2) / 3
            val cardWidth = minOf(availableCardWidth, maxPreviewWidth)

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top,
            ) {
                PrimeDrawerMode.entries.forEach { mode ->
                    SwitchPreferencePreviewCard(
                        label = when (mode) {
                            PrimeDrawerMode.DEFAULT -> stringResource(id = R.string.feed_default)
                            PrimeDrawerMode.TABS -> stringResource(id = R.string.drawer_tabs)
                            PrimeDrawerMode.CADDY -> stringResource(id = R.string.caddy_beta)
                        },
                        isSelected = activeMode == mode,
                        onClick = { onModeChange(mode) },
                        modifier = Modifier.width(cardWidth),
                    ) { DrawerLayoutPreview(mode) }
                }
            }
        }
    }
}

@Composable
private fun DrawerLayoutPreview(mode: PrimeDrawerMode) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The original preview was designed inside a 136 x 96 dp content area
        // (160 x 120 card minus 12 dp padding on every side). Scale every element
        // from that reference so three-column previews keep the same proportions.
        val scale = minOf(maxWidth / 136.dp, maxHeight / 96.dp)
        val barWidth = 108.dp * scale
        val barHeight = 18.dp * scale
        val iconSize = 20.dp * scale
        val iconSpacing = 8.dp * scale
        val tabWidth = 24.dp * scale
        val tabHeight = 10.dp * scale
        val tabSpacing = 4.dp * scale

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp * scale),
                    ),
            )
            if (mode == PrimeDrawerMode.TABS) {
                Row(horizontalArrangement = Arrangement.spacedBy(tabSpacing)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .height(tabHeight)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp * scale),
                                ),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(iconSpacing)) {
                repeat(if (mode == PrimeDrawerMode.CADDY) 2 else 4) {
                    Box(
                        modifier = Modifier
                            .size(if (mode == PrimeDrawerMode.CADDY) 16.dp * scale else iconSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
            }
        }
    }
}


@Composable
private fun <T> modeBackedAdapter(
    modePreference: PrefEntry<T>,
    nativeAdapter: PreferenceAdapter<T>,
): PreferenceAdapter<T> {
    val modeAdapter = androidx.compose.runtime.key(modePreference.key) {
        modePreference.getAdapter()
    }
    return remember(modePreference.key, modeAdapter, nativeAdapter) {
        object : PreferenceAdapter<T> {
            override val state = modeAdapter.state
            override fun onChange(newValue: T) {
                modeAdapter.onChange(newValue)
                nativeAdapter.onChange(newValue)
            }
        }
    }
}
