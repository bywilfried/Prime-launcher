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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.rememberTransformAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.prime.drawer.PrimeDrawerTabsRepository
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppDrawerHapticFeedbackPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
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
        Column {
            DrawerLayoutPreference(drawerListAdapter, drawerTabsAdapter)
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
                    ColorPreference(
                        preference = prefs.drawerTabsColor,
                    )
                    SwitchPreference(
                        label = stringResource(id = R.string.apps_in_folder_label),
                        description = stringResource(id = R.string.apps_in_folder_description),
                        adapter = prefs.primeHideFolderApps.getAdapter(),
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
            ColorPreference(preference = prefs2.appDrawerBackgroundColor)
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = prefs.drawerOpacity.getAdapter(),
                step = 0.1f,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            ColorPreference(preference = prefs2.workProfileTabBackgroundColor)
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
            val drawerColumnsAdapter = prefs2.drawerColumns.getAdapter()
            val drawerColumnsUnfoldedAdapter = prefs2.drawerColumnsUnfolded.getAdapter()
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
                adapter = prefs2.drawerCellHeightFactor.getAdapter(),
                label = stringResource(id = R.string.row_height_label),
                valueRange = 0.3F..1.5F,
                step = 0.1F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = prefs2.drawerLeftRightMarginFactor.getAdapter(),
                label = stringResource(id = R.string.app_drawer_indent_label),
                valueRange = 0.0F..1.5F,
                step = 0.05F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = prefs2.drawerPaddingTopFactor.getAdapter(),
                label = stringResource(id = R.string.top_padding_label),
                valueRange = 1.0F..2.0F,
                step = 0.05F,
                showAsPercentage = true,
            )
        }
        val showDrawerLabels = prefs2.showIconLabelsInDrawer.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_sizes),
                adapter = prefs2.drawerIconSizeFactor.getAdapter(),
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
                    adapter = prefs2.drawerIconLabelSizeFactor.getAdapter(),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            ExpandAndShrink(
                visible = showDrawerLabels.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs2.twoLineAllApps.getAdapter(),
                    label = stringResource(R.string.twoline_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_remember_position_title),
                description = stringResource(id = R.string.pref_all_apps_remember_position_description),
                adapter = prefs2.rememberPosition.getAdapter(),
            )
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_show_scrollbar_title),
                adapter = prefs2.showScrollbar.getAdapter(),
            )
        }
    }
}

@Composable
private fun DrawerLayoutPreference(
    drawerListAdapter: PreferenceAdapter<Boolean>,
    drawerTabsAdapter: PreferenceAdapter<Boolean>,
) {
    val layoutModeAdapter = rememberTransformAdapter(
        adapter = drawerListAdapter,
        transformGet = { drawerList ->
            when {
                drawerTabsAdapter.state.value -> DrawerLayoutMode.TABS
                drawerList -> DrawerLayoutMode.DEFAULT
                else -> DrawerLayoutMode.CADDY
            }
        },
        transformSet = { mode ->
            drawerTabsAdapter.onChange(mode == DrawerLayoutMode.TABS)
            mode != DrawerLayoutMode.CADDY
        },
    )
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
                DrawerLayoutMode.entries.forEach { mode ->
                    SwitchPreferencePreviewCard(
                        label = when (mode) {
                            DrawerLayoutMode.DEFAULT -> stringResource(id = R.string.feed_default)
                            DrawerLayoutMode.TABS -> stringResource(id = R.string.drawer_tabs)
                            DrawerLayoutMode.CADDY -> stringResource(id = R.string.caddy_beta)
                        },
                        isSelected = layoutModeAdapter.state.value == mode,
                        onClick = { layoutModeAdapter.onChange(mode) },
                        modifier = Modifier.width(cardWidth),
                    ) { DrawerLayoutPreview(mode) }
                }
            }
        }
    }
}

@Composable
private fun DrawerLayoutPreview(mode: DrawerLayoutMode) {
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
            if (mode == DrawerLayoutMode.TABS) {
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
                repeat(if (mode == DrawerLayoutMode.CADDY) 2 else 4) {
                    Box(
                        modifier = Modifier
                            .size(if (mode == DrawerLayoutMode.CADDY) 16.dp * scale else iconSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
            }
        }
    }
}

private enum class DrawerLayoutMode { DEFAULT, TABS, CADDY }
