package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import app.lawnchair.preferences.getAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.colorpreference.pickers.PresetsList
import app.lawnchair.ui.preferences.components.colorpreference.pickers.SwatchGrid
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.Chip
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.launch

@Composable
fun ColorSelection(
    label: String,
    preference: Preference<ColorOption, String, *>,
    modifier: Modifier = Modifier,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicColors,
    staticEntries: List<ColorPreferenceEntry<ColorOption>> = staticColors,
) {
    val adapter = preference.getAdapter()
    val appliedColor = adapter.state.value
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val navController = LocalNavController.current
    val selectedColor = remember { mutableIntStateOf(appliedColor.forCustomPicker(context)) }
    val selectedColorApplied = remember {
        derivedStateOf {
            appliedColor is ColorOption.CustomColor && appliedColor.color == selectedColor.intValue
        }
    }
    val defaultTabIndex = when {
        dynamicEntries.any { it.value == appliedColor } -> 0
        staticEntries.any { it.value == appliedColor } -> 0
        else -> 1
    }

    val onPresetClick = { option: ColorOption ->
        selectedColor.intValue = option.forCustomPicker(context)
        adapter.onChange(newValue = option)
    }

    val pagerState = rememberPagerState(
        initialPage = defaultTabIndex,
        pageCount = { 2 },
    )
    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            if (pagerState.currentPage == 0) {
                BottomSpacer()
                return@PreferenceLayout
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    enabled = !selectedColorApplied.value,
                    onClick = {
                        adapter.onChange(newValue = ColorOption.CustomColor(selectedColor.intValue))
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = R.string.action_apply))
                }
                BottomSpacer()
            }
        },
    ) {
        val scope = rememberCoroutineScope()
        val scrollToPage =
            { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Chip(
                    label = stringResource(id = R.string.presets),
                    onClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        scrollToPage(0)
                    },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 0,
                )
                Chip(
                    label = stringResource(id = R.string.custom),
                    onClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        scrollToPage(1)
                    },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 1,
                )
            }
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    0 -> {
                        Column {
                            PresetsList(
                                dynamicEntries = dynamicEntries,
                                onPresetClick = onPresetClick,
                                isPresetSelected = { it == appliedColor },
                            )
                            SwatchGrid(
                                modifier = Modifier.padding(top = 12.dp),
                                contentModifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 20.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ),
                                entries = staticEntries,
                                onSwatchClick = onPresetClick,
                                isSwatchSelected = { it == appliedColor },
                            )
                        }
                    }

                    1 -> {
                        CustomColorPicker(
                            selectedColor = selectedColor.intValue,
                            onSelect = { selectedColor.intValue = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrimeColorSelection(
    label: String,
    appliedColor: ColorOption,
    onApply: (ColorOption) -> Unit,
    modifier: Modifier = Modifier,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicColorsWithDefault,
    staticEntries: List<ColorPreferenceEntry<ColorOption>> = staticColors,
    defaultPreviewColor: Int? = null,
    defaultDynamicColor: ColorOption.DynamicColor? = null,
    automaticLegacyDefault: Boolean = false,
) {
    val context = LocalContext.current
    val themeFallbackColor = defaultPreviewColor ?: MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val displayedDynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicEntries.map { entry ->
        if (entry.value == ColorOption.Default) {
            ColorPreferenceEntry<ColorOption>(
                value = ColorOption.Default,
                label = if (automaticLegacyDefault) "Automatique (Legacy) — selon l'icône" else entry.label,
                lightColor = { themeFallbackColor },
                darkColor = { themeFallbackColor },
            )
        } else {
            entry
        }
    }
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val navController = LocalNavController.current
    var currentAppliedColor by remember(appliedColor) { mutableStateOf(appliedColor) }
    val selectedColor = remember(appliedColor, defaultPreviewColor) {
        mutableIntStateOf(
            if (appliedColor == ColorOption.Default && defaultPreviewColor != null) {
                defaultPreviewColor
            } else {
                appliedColor.forCustomPicker(context)
            },
        )
    }
    val selectedColorApplied = remember {
        derivedStateOf {
            currentAppliedColor is ColorOption.CustomColor &&
                (currentAppliedColor as ColorOption.CustomColor).color == selectedColor.intValue
        }
    }
    val defaultTabIndex = when (currentAppliedColor) {
        is ColorOption.CustomColor -> 1
        is ColorOption.DynamicColor -> 2
        else -> 0
    }
    val pagerState = rememberPagerState(initialPage = if (defaultTabIndex == 0) 0 else 1, pageCount = { 2 })
    val customMode = remember(appliedColor) { mutableIntStateOf(if (appliedColor is ColorOption.DynamicColor) 1 else 0) }
    // Keep the dynamic editor anchored to the semantic theme recipe until the user actually
    // applies a dynamic override. This avoids the generic Accent1/500 fallback when Default or
    // a fixed custom color is currently active.
    val dynamicEditorInitial = remember(appliedColor, defaultDynamicColor) {
        (appliedColor as? ColorOption.DynamicColor) ?: defaultDynamicColor
    }
    val onPresetClick = { option: ColorOption ->
        selectedColor.intValue = if (option == ColorOption.Default) {
            themeFallbackColor
        } else {
            option.forCustomPicker(context)
        }
        currentAppliedColor = option
        onApply(option)
    }

    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            if (pagerState.currentPage != 1 || customMode.intValue != 0) {
                BottomSpacer()
                return@PreferenceLayout
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Button(
                    // Allow explicitly reapplying a color even when it matches the
                    // currently selected swatch. This also gives users a clear way
                    // to confirm a custom hex value after choosing a preset.
                    enabled = true,
                    onClick = {
                        val option = ColorOption.CustomColor(selectedColor.intValue)
                        currentAppliedColor = option
                        onApply(option)
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(text = stringResource(id = R.string.action_apply)) }
                BottomSpacer()
            }
        },
    ) {
        val scope = rememberCoroutineScope()
        val scrollToPage = { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Chip(
                    label = stringResource(id = R.string.presets),
                    onClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        scrollToPage(0)
                    },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 0,
                )
                Chip(
                    label = stringResource(id = R.string.custom),
                    onClick = {
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        scrollToPage(1)
                    },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 1,
                )
            }
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    0 -> Column {
                        PresetsList(
                            dynamicEntries = displayedDynamicEntries,
                            onPresetClick = onPresetClick,
                            isPresetSelected = { it == currentAppliedColor },
                        )
                        SwatchGrid(
                            modifier = Modifier.padding(top = 12.dp),
                            contentModifier = Modifier.padding(
                                start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp,
                            ),
                            entries = staticEntries,
                            onSwatchClick = onPresetClick,
                            isSwatchSelected = { it == currentAppliedColor },
                        )
                    }
                    1 -> Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Chip(
                                label = "Fixe",
                                onClick = { customMode.intValue = 0 },
                                currentOffset = customMode.intValue.toFloat(),
                                page = 0,
                            )
                            Chip(
                                label = "Dynamique",
                                onClick = { customMode.intValue = 1 },
                                currentOffset = customMode.intValue.toFloat(),
                                page = 1,
                            )
                        }
                        if (customMode.intValue == 0) {
                            CustomColorPicker(
                                selectedColor = selectedColor.intValue,
                                onSelect = { selectedColor.intValue = it },
                            )
                        } else {
                            PrimeDynamicColorPicker(
                                // When no dynamic override exists yet, start from the semantic
                                // theme recipe instead of the generic Accent1/500 fallback.
                                // Seed from the persisted dynamic override when there is one; otherwise
                                // always open on the semantic theme recipe supplied by the caller.
                                // Use appliedColor (the route snapshot), not the mutable preset state:
                                // visiting Presets must not silently replace the editor's baseline.
                                initial = dynamicEditorInitial,
                                onApply = { option ->
                                    currentAppliedColor = option
                                    onApply(option)
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ColorOption.forCustomPicker(context: Context): Int {
    val color = colorPreferenceEntry.lightColor(context)
    if (color == 0) {
        return Color.BLACK
    }
    return color
}
