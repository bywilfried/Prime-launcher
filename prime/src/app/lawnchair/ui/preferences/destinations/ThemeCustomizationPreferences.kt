package app.lawnchair.ui.preferences.destinations

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.lawnchair.theme.ThemeShareFile
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.qsb.getHotseatBackgroundColor
import app.lawnchair.theme.ThemeColors
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.theme.ThemeProfile
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.navigation.ThemeColorSelection
import app.lawnchair.theme.ThemeColorRole
import app.lawnchair.theme.CategoryThemeColorOverrides
import app.lawnchair.theme.ThemeColorOverrides
import app.lawnchair.theme.ThemeVariant
import app.lawnchair.theme.effectiveThemeVariant
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

/**
 * Inventory-first editor for ThemeProfile colors.
 *
 * The rows intentionally exist before every runtime consumer is connected. This makes this screen
 * the checklist used to wire preview + runtime through the same ThemeColorRole resolver.
 * Persistence/export actions are intentionally disabled until the sparse override store is added.
 */
@Composable
fun ThemeCustomizationPreferences(modifier: Modifier = Modifier, openDrawerSection: Boolean = false, categoryTabId: String? = null, folderKey: String? = null, folderSection: String? = null) {
    val context = LocalContext.current
    var bulkResetRevision by remember { mutableStateOf(0) }
    // Save stable strings rather than Serializable enums: R8 may rename/remove the
    // synthetic enum values() method used by Android Parcel restoration.
    var variantName by rememberSaveable { mutableStateOf(context.effectiveThemeVariant().name) }
    val variant = ThemeVariant.valueOf(variantName)
    var expandedName by rememberSaveable(openDrawerSection, categoryTabId, folderKey) {
        mutableStateOf(if (folderKey != null) (folderSection ?: "HOME") else if (openDrawerSection || categoryTabId != null) ThemeColorRole.Section.DRAWER.name else null)
    }
    val expanded = expandedName?.let(ThemeColorRole.Section::valueOf)
    var confirmThemeReset by rememberSaveable { mutableStateOf(false) }
    var confirmVariantReset by rememberSaveable { mutableStateOf(false) }
    val profile = ThemeProfile.current(context)
    val overrides = remember(context) { ThemeColorOverrides(context) }
    val exportTheme = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ThemeShareFile.export(context, it) }
                ?: error("Unable to open destination")
        }.onSuccess {
            Toast.makeText(context, context.getString(com.android.launcher3.R.string.prime_theme_ui_exported), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Log.e("ThemeCustomization", "Theme export failed", it)
            Toast.makeText(context, context.getString(com.android.launcher3.R.string.prime_theme_ui_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val importTheme = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { ThemeShareFile.import(context, it) }
                ?: error("Unable to open theme file")
        }.onSuccess {
            bulkResetRevision++
            Toast.makeText(context, context.getString(com.android.launcher3.R.string.prime_theme_ui_imported), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Log.e("ThemeCustomization", "Theme import failed", it)
            Toast.makeText(context, context.getString(com.android.launcher3.R.string.prime_theme_ui_invalid), Toast.LENGTH_SHORT).show()
        }
    }


    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = if (folderKey != null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_folder) else if (categoryTabId == null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_customize) else androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_category),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = "Legacy") {
            PreferenceTemplate(
                title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_variant)) },
                endWidget = {
                    Row {
                        val light = variant == ThemeVariant.LIGHT
                        if (light) Button(onClick = { variantName = ThemeVariant.LIGHT.name }) { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_light)) }
                        else OutlinedButton(onClick = { variantName = ThemeVariant.LIGHT.name }) { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_light)) }
                        val dark = variant == ThemeVariant.DARK
                        if (dark) Button(onClick = { variantName = ThemeVariant.DARK.name }) { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_dark)) }
                        else OutlinedButton(onClick = { variantName = ThemeVariant.DARK.name }) { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_dark)) }
                    }
                },
            )
        }

        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_global),
            variant = variant,
            section = ThemeColorRole.Section.GLOBAL,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_home),
            variant = variant,
            section = ThemeColorRole.Section.HOME,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = "Dock",
            variant = variant,
            section = ThemeColorRole.Section.DOCK,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (folderKey != null) ThemeSection(
            title = androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_folder),
            categoryTabId = categoryTabId,
            folderKey = folderKey,
            variant = variant,
            section = ThemeColorRole.Section.valueOf(folderSection ?: "HOME"),
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (categoryTabId == null && folderKey == null) ThemeSection(
            title = "Prime Search",
            variant = variant,
            section = ThemeColorRole.Section.PRIME_SEARCH,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )
        if (folderKey == null) ThemeSection(
            title = androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_drawer),
            categoryTabId = categoryTabId,
            variant = variant,
            section = ThemeColorRole.Section.DRAWER,
            expanded = expanded,
            bulkResetRevision = bulkResetRevision,
            onToggle = { expandedName = if (expanded == it) null else it.name },
        )

        PreferenceGroup(heading = if (folderKey != null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_manage_folder) else if (categoryTabId == null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_manage_theme) else androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_manage_category)) {
            PreferenceTemplate(
                title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_variant)) },
                description = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_variant_desc)) },
                onClick = { confirmVariantReset = true },
            )
            PreferenceTemplate(
                title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_theme)) },
                description = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_theme_desc)) },
                onClick = { confirmThemeReset = true },
            )
            PreferenceTemplate(
                title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_export)) },
                description = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_export_desc)) },
                enabled = categoryTabId == null && folderKey == null,
                onClick = { exportTheme.launch("Prime-theme.primetheme") },
            )
            PreferenceTemplate(
                title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_import)) },
                description = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_import_desc)) },
                enabled = categoryTabId == null && folderKey == null,
                onClick = { importTheme.launch(arrayOf("*/*")) },
            )
        }
    }

    if (confirmVariantReset) {
        AlertDialog(
            onDismissRequest = { confirmVariantReset = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_variant_question)) },
            text = {
                Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_variant_message, if (variant == ThemeVariant.LIGHT) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_light) else androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_dark)))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).resetVariant(folderKey, profile, variant)
                    else if (categoryTabId == null) overrides.resetVariant(profile, variant)
                    else CategoryThemeColorOverrides(context).resetVariant(categoryTabId, profile, variant)
                    bulkResetRevision++
                    confirmVariantReset = false
                }) {
                    Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVariantReset = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_cancel))
                }
            },
        )
    }

    if (confirmThemeReset) {
        AlertDialog(
            onDismissRequest = { confirmThemeReset = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_theme_question)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_both)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).resetTheme(folderKey, profile)
                        else if (categoryTabId == null) overrides.resetTheme(profile)
                        else CategoryThemeColorOverrides(context).resetTheme(categoryTabId, profile)
                        bulkResetRevision++
                        confirmThemeReset = false
                    },
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmThemeReset = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeSection(
    title: String,
    section: ThemeColorRole.Section,
    variant: ThemeVariant,
    expanded: ThemeColorRole.Section?,
    bulkResetRevision: Int,
    onToggle: (ThemeColorRole.Section) -> Unit,
    categoryTabId: String? = null,
    folderKey: String? = null,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    PreferenceGroup {
        PreferenceTemplate(
            title = { Text(title) },
            description = { Text(if (expanded == section) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_hide) else androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_show)) },
            onClick = { onToggle(section) },
        )
        ExpandAndShrink(visible = expanded == section) {
            androidx.compose.foundation.layout.Column {
                rolesForUi(section).filter { role ->
                    (folderKey == null || role.id.contains("folder.")) &&
                    role != ThemeColorRole.TABS_CATEGORY_TEXT &&
                        role != ThemeColorRole.HOME_FOLDER_BORDER &&
                        role != ThemeColorRole.DOCK_FOLDER_BORDER &&
                        role != ThemeColorRole.DRAWER_FOLDER_BORDER
                }.forEach { role ->
                        val profile = ThemeProfile.current(context)
                        // Observe an immediate local reset so Compose redraws this row without
                        // requiring the user to leave and reopen the editor.
                        var resetRevision by remember(profile, variant, role, categoryTabId, folderKey) {
                            mutableStateOf(0)
                        }
                        val override = run {
                            bulkResetRevision // Recompose on variant/theme reset.
                            resetRevision // Recompose on individual reset.
                            if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).get(folderKey, profile, variant, role)
                            else if (categoryTabId == null) ThemeColorOverrides(context).get(profile, variant, role)
                            else CategoryThemeColorOverrides(context).get(categoryTabId, profile, variant, role)
                        }
                        val prefs2 = preferenceManager2()
                        val preview = when (role) {
                            // Preview the exact Dock QSB defaults, including the existing
                            // "apply accent color" switch and its separate border preference.
                            ThemeColorRole.HOME_SEARCH_BACKGROUND -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    getHotseatBackgroundColor(context, prefs2.themedHotseatQsb.asState().value)
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_SEARCH_ICON -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    ThemeColors.resolveLegacyToken(
                                        context,
                                        app.lawnchair.theme.color.tokens.ColorTokens.ColorAccent,
                                        variant,
                                    )
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_SEARCH_BORDER -> {
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    prefs2.strokeColorStyle.asState().value.colorPreferenceEntry.lightColor(context)
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> {
                                val legacy = prefs2.notificationDotColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> {
                                val legacy = prefs2.hotseatBackgroundColor.asState().value
                                ThemeColors.resolveDockBackgroundForVariant(context, legacy, variant)
                            }
                            ThemeColorRole.HOME_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.HOME_FOLDER_OPEN_BORDER,
                            ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.DOCK_FOLDER_OPEN_BORDER,
                            ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER,
                            ThemeColorRole.DRAWER_FOLDER_OPEN_BORDER -> {
                                val backgroundRole = when (role) {
                                    ThemeColorRole.HOME_FOLDER_CLOSED_BORDER -> ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND
                                    ThemeColorRole.HOME_FOLDER_OPEN_BORDER -> ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER -> ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_OPEN_BORDER -> ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER -> ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND
                                    else -> ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND
                                }
                                ThemeColors.resolveFolderBorderForVariant(
                                    context, role,
                                    ThemeColors.resolveTheme(context, profile, backgroundRole, variant),
                                    variant,
                                )
                            }
                            ThemeColorRole.HOME_FOLDER_OPEN_TEXT,
                            ThemeColorRole.DOCK_FOLDER_OPEN_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT -> {
                                val backgroundRole = when (role) {
                                    ThemeColorRole.HOME_FOLDER_OPEN_TEXT -> ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND
                                    ThemeColorRole.DOCK_FOLDER_OPEN_TEXT -> ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND
                                    else -> ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND
                                }
                                if (override == ColorOption.Default || override == ColorOption.LegacyBehavior) {
                                    ThemeColors.resolveOpenFolderTitleColorForVariant(
                                        context, role,
                                        ThemeColors.resolveTheme(context, profile, backgroundRole, variant),
                                        variant,
                                    )
                                } else ThemeColors.resolveTheme(context, profile, role, variant)
                            }
                            ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT -> {
                                val legacy = prefs2.notificationDotTextColor.asState().value
                                app.lawnchair.theme.NotificationDotThemeColors.resolve(context, role, legacy)
                                    .takeIf { it != 0 } ?: ThemeColors.official(context, profile, role, variant)
                            }
                            ThemeColorRole.HOME_ICON_TEXT,
                            ThemeColorRole.HOME_FOLDER_ICON_TEXT,
                            ThemeColorRole.HOME_FOLDER_CLOSED_TEXT,
                            ThemeColorRole.DOCK_ICON_TEXT,
                            ThemeColorRole.DOCK_FOLDER_ICON_TEXT,
                            ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT,
                            ThemeColorRole.DRAWER_ICON_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_ICON_TEXT,
                            ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT -> ThemeColors.resolveIconLabelColorForVariant(context, role,
                                if (variant == ThemeVariant.DARK) android.graphics.Color.BLACK else android.graphics.Color.WHITE, variant)
                            else -> ThemeColors.resolveTheme(context, profile, role, variant)
                        }
                        val categoryPreview = if (folderKey != null) ThemeColors.resolveForFolder(context, profile, role, variant, folderKey, categoryTabId)
                            else if (categoryTabId == null || override == ColorOption.Default) preview
                            else ThemeColors.resolveForCategory(context, profile, role, variant, categoryTabId)
                        val isInherited = override == ColorOption.Default
                        val rolePreferenceAlpha = if (isInherited) 0.58f else 1f
                        Box {
                        ColorPreference(
                            label = roleLabel(context, role),
                            selectedColor = override,
                            modifier = Modifier.graphicsLayer { alpha = rolePreferenceAlpha },
                            previewColor = ColorOption.CustomColor(categoryPreview),
                            iconColorPreview = (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT &&
                                (override == ColorOption.IconColor || override == ColorOption.Default || override == ColorOption.LegacyBehavior)) ||
                                (role == ThemeColorRole.HOME_SEARCH_ICON &&
                                    (override == ColorOption.Default || override == ColorOption.LegacyBehavior) &&
                                    !prefs2.themedHotseatQsb.asState().value),
                            // Always display the resolved color, not a generic "A" glyph.
                            dynamicContrastPreview = (override == ColorOption.Default || override == ColorOption.LegacyBehavior) &&
                                (role == ThemeColorRole.HOME_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.DOCK_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT ||
                                    role == ThemeColorRole.HOME_FOLDER_OPEN_HINT ||
                                    role == ThemeColorRole.DOCK_FOLDER_OPEN_HINT ||
                                    role == ThemeColorRole.DRAWER_FOLDER_OPEN_HINT),
                            automaticContrastPreview = (override == ColorOption.Default || override == ColorOption.LegacyBehavior || override == ColorOption.AutomaticBlackWhite) &&
                                (role == ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT ||
                                role == ThemeColorRole.HOME_ICON_TEXT ||
                                role == ThemeColorRole.HOME_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.HOME_FOLDER_CLOSED_TEXT ||
                                role == ThemeColorRole.DOCK_ICON_TEXT ||
                                role == ThemeColorRole.DOCK_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT ||
                                role == ThemeColorRole.DRAWER_TEXT ||
                                role == ThemeColorRole.DRAWER_FOLDER_ICON_TEXT ||
                                role == ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT),
                            description = if (override == ColorOption.LegacyBehavior) {
                                androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_legacy)
                            } else if (override == ColorOption.Default) {
                                if (role == ThemeColorRole.HOME_PAGE_INDICATOR_ACTIVE ||
                                    role == ThemeColorRole.HOME_PAGE_INDICATOR_INACTIVE)
                                    androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_contrast)
                                else if (folderKey != null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_parent) else if (categoryTabId != null) androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_parent_theme) else androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_theme_value)
                            } else {
                                override.colorPreferenceEntry.label()
                            },
                            onClick = {
                                navController.navigate(
                                    ThemeColorSelection(role.id, variant.name, categoryTabId, folderKey),
                                )
                            },
                        )
                        if (!isInherited) {
                            IconButton(
                                onClick = {
                                    if (folderKey != null) app.lawnchair.theme.FolderThemeColorOverrides(context).set(
                                        folderKey, profile, variant, role, ColorOption.Default,
                                    ) else if (categoryTabId == null) ThemeColorOverrides(context).set(
                                        profile, variant, role, ColorOption.Default,
                                    ) else CategoryThemeColorOverrides(context).set(
                                        categoryTabId, profile, variant, role, ColorOption.Default,
                                    )
                                    resetRevision++
                                },
                                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                                    .padding(end = 44.dp, top = 4.dp)
                                    .size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.android.launcher3.R.string.prime_theme_ui_reset_role, roleLabel(context, role)),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        }

                }
            }
        }
    }
}

/**
 * Category tabs belong to the Drawer in the public editor even though their pre-public internal IDs
 * still use the historical TABS section.
 */
private fun rolesForUi(section: ThemeColorRole.Section): List<ThemeColorRole> = when (section) {
    ThemeColorRole.Section.DRAWER -> ThemeColorRole.entries.filter {
        it.section == ThemeColorRole.Section.DRAWER || it.section == ThemeColorRole.Section.TABS
    }
    else -> ThemeColorRole.entries.filter { it.section == section }
}

private fun roleLabel(context: android.content.Context, role: ThemeColorRole): String = context.getString(when (role) {
    ThemeColorRole.PRIME_SEARCH_WINDOW_BACKGROUND -> com.android.launcher3.R.string.prime_role_prime_search_window_background
    ThemeColorRole.PRIME_SEARCH_BAR_BACKGROUND -> com.android.launcher3.R.string.prime_role_prime_search_bar_background
    ThemeColorRole.PRIME_SEARCH_TEXT -> com.android.launcher3.R.string.prime_role_prime_search_text
    ThemeColorRole.PRIME_SEARCH_HINT -> com.android.launcher3.R.string.prime_role_prime_search_hint
    ThemeColorRole.PRIME_SEARCH_ICON -> com.android.launcher3.R.string.prime_role_prime_search_icon
    ThemeColorRole.PRIME_SEARCH_BORDER -> com.android.launcher3.R.string.prime_role_prime_search_border
    ThemeColorRole.PRIME_SEARCH_TAB_ACTIVE -> com.android.launcher3.R.string.prime_role_prime_search_tab_active
    ThemeColorRole.PRIME_SEARCH_TAB_INACTIVE -> com.android.launcher3.R.string.prime_role_prime_search_tab_inactive
    ThemeColorRole.PRIME_SEARCH_RESULT_BACKGROUND -> com.android.launcher3.R.string.prime_role_prime_search_result_background
    ThemeColorRole.PRIME_SEARCH_CARD_BACKGROUND -> com.android.launcher3.R.string.prime_role_prime_search_card_background
    ThemeColorRole.PRIME_SEARCH_CARD_BORDER -> com.android.launcher3.R.string.prime_role_prime_search_card_border
    ThemeColorRole.PRIME_SEARCH_RESULT_TEXT -> com.android.launcher3.R.string.prime_role_prime_search_result_text
    ThemeColorRole.PRIME_SEARCH_APP_LABEL -> com.android.launcher3.R.string.prime_role_prime_search_app_label
    ThemeColorRole.PRIME_SEARCH_SETTINGS_ICON -> com.android.launcher3.R.string.prime_role_prime_search_settings_icon
    ThemeColorRole.GLOBAL_ACCENT -> com.android.launcher3.R.string.prime_role_global_accent
    ThemeColorRole.GLOBAL_SETTINGS_BACKGROUND -> com.android.launcher3.R.string.prime_role_global_settings_background
    ThemeColorRole.GLOBAL_SETTINGS_CARD_BACKGROUND -> com.android.launcher3.R.string.prime_role_global_settings_card_background
    ThemeColorRole.GLOBAL_NOTIFICATION_DOT -> com.android.launcher3.R.string.prime_role_global_notification_dot
    ThemeColorRole.GLOBAL_NOTIFICATION_DOT_TEXT -> com.android.launcher3.R.string.prime_role_global_notification_dot_text
    ThemeColorRole.HOME_ICON_TEXT -> com.android.launcher3.R.string.prime_role_home_icon_text
    ThemeColorRole.HOME_FOLDER_ICON_TEXT -> com.android.launcher3.R.string.prime_role_home_folder_icon_text
    ThemeColorRole.HOME_HOTSEAT_BACKGROUND -> com.android.launcher3.R.string.prime_role_home_hotseat_background
    ThemeColorRole.HOME_SEARCH_BACKGROUND -> com.android.launcher3.R.string.prime_role_home_search_background
    ThemeColorRole.HOME_SEARCH_TEXT -> com.android.launcher3.R.string.prime_role_home_search_text
    ThemeColorRole.HOME_SEARCH_HINT -> com.android.launcher3.R.string.prime_role_home_search_hint
    ThemeColorRole.HOME_SEARCH_ICON -> com.android.launcher3.R.string.prime_role_home_search_icon
    ThemeColorRole.HOME_SEARCH_BORDER -> com.android.launcher3.R.string.prime_role_home_search_border
    ThemeColorRole.HOME_FOLDER_CLOSED_BACKGROUND -> com.android.launcher3.R.string.prime_role_home_folder_closed_background
    ThemeColorRole.HOME_FOLDER_CLOSED_TEXT -> com.android.launcher3.R.string.prime_role_home_folder_closed_text
    ThemeColorRole.HOME_FOLDER_OPEN_BACKGROUND -> com.android.launcher3.R.string.prime_role_home_folder_open_background
    ThemeColorRole.HOME_FOLDER_OPEN_TEXT -> com.android.launcher3.R.string.prime_role_home_folder_open_text
    ThemeColorRole.HOME_FOLDER_OPEN_HINT -> com.android.launcher3.R.string.prime_role_home_folder_open_hint
    ThemeColorRole.HOME_PAGE_INDICATOR_ACTIVE -> com.android.launcher3.R.string.prime_role_home_page_indicator_active
    ThemeColorRole.HOME_PAGE_INDICATOR_INACTIVE -> com.android.launcher3.R.string.prime_role_home_page_indicator_inactive
    ThemeColorRole.HOME_FOLDER_PAGINATION -> com.android.launcher3.R.string.prime_role_home_folder_pagination
    ThemeColorRole.HOME_FOLDER_CLOSED_BORDER -> com.android.launcher3.R.string.prime_role_home_folder_closed_border
    ThemeColorRole.HOME_FOLDER_OPEN_BORDER -> com.android.launcher3.R.string.prime_role_home_folder_open_border
    ThemeColorRole.HOME_FOLDER_BORDER -> com.android.launcher3.R.string.prime_role_home_folder_border
    ThemeColorRole.HOME_POPUP_BACKGROUND -> com.android.launcher3.R.string.prime_role_home_popup_background
    ThemeColorRole.HOME_POPUP_TEXT -> com.android.launcher3.R.string.prime_role_home_popup_text
    ThemeColorRole.HOME_POPUP_ICON -> com.android.launcher3.R.string.prime_role_home_popup_icon
    ThemeColorRole.DOCK_ICON_TEXT -> com.android.launcher3.R.string.prime_role_dock_icon_text
    ThemeColorRole.DOCK_FOLDER_ICON_TEXT -> com.android.launcher3.R.string.prime_role_dock_folder_icon_text
    ThemeColorRole.DOCK_FOLDER_CLOSED_BACKGROUND -> com.android.launcher3.R.string.prime_role_dock_folder_closed_background
    ThemeColorRole.DOCK_FOLDER_CLOSED_TEXT -> com.android.launcher3.R.string.prime_role_dock_folder_closed_text
    ThemeColorRole.DOCK_FOLDER_OPEN_BACKGROUND -> com.android.launcher3.R.string.prime_role_dock_folder_open_background
    ThemeColorRole.DOCK_FOLDER_OPEN_TEXT -> com.android.launcher3.R.string.prime_role_dock_folder_open_text
    ThemeColorRole.DOCK_FOLDER_OPEN_HINT -> com.android.launcher3.R.string.prime_role_dock_folder_open_hint
    ThemeColorRole.DOCK_FOLDER_PAGINATION -> com.android.launcher3.R.string.prime_role_dock_folder_pagination
    ThemeColorRole.DOCK_FOLDER_CLOSED_BORDER -> com.android.launcher3.R.string.prime_role_dock_folder_closed_border
    ThemeColorRole.DOCK_FOLDER_OPEN_BORDER -> com.android.launcher3.R.string.prime_role_dock_folder_open_border
    ThemeColorRole.DOCK_FOLDER_BORDER -> com.android.launcher3.R.string.prime_role_dock_folder_border
    ThemeColorRole.DRAWER_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_background
    ThemeColorRole.DRAWER_TEXT -> com.android.launcher3.R.string.prime_role_drawer_text
    ThemeColorRole.DRAWER_HANDLE -> com.android.launcher3.R.string.prime_role_drawer_handle
    ThemeColorRole.DRAWER_ICON_TEXT -> com.android.launcher3.R.string.prime_role_drawer_icon_text
    ThemeColorRole.DRAWER_FOLDER_ICON_TEXT -> com.android.launcher3.R.string.prime_role_drawer_folder_icon_text
    ThemeColorRole.DRAWER_SEARCH_BACKGROUND_INACTIVE -> com.android.launcher3.R.string.prime_role_drawer_search_background_inactive
    ThemeColorRole.DRAWER_SEARCH_BACKGROUND_ACTIVE -> com.android.launcher3.R.string.prime_role_drawer_search_background_active
    ThemeColorRole.DRAWER_SEARCH_TEXT -> com.android.launcher3.R.string.prime_role_drawer_search_text
    ThemeColorRole.DRAWER_SEARCH_HINT -> com.android.launcher3.R.string.prime_role_drawer_search_hint
    ThemeColorRole.DRAWER_SEARCH_ICON -> com.android.launcher3.R.string.prime_role_drawer_search_icon
    ThemeColorRole.DRAWER_SEARCH_BORDER -> com.android.launcher3.R.string.prime_role_drawer_search_border
    ThemeColorRole.DRAWER_SEARCH_SELECTED_RESULT_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_search_selected_result_background
    ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_search_result_card_background
    ThemeColorRole.DRAWER_SEARCH_RESULT_CARD_BORDER -> com.android.launcher3.R.string.prime_role_drawer_search_result_card_border
    ThemeColorRole.DRAWER_SEARCH_RESULT_TEXT -> com.android.launcher3.R.string.prime_role_drawer_search_result_text
    ThemeColorRole.DRAWER_FOLDER_CLOSED_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_folder_closed_background
    ThemeColorRole.DRAWER_FOLDER_CLOSED_TEXT -> com.android.launcher3.R.string.prime_role_drawer_folder_closed_text
    ThemeColorRole.DRAWER_FOLDER_OPEN_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_folder_open_background
    ThemeColorRole.DRAWER_FOLDER_OPEN_TEXT -> com.android.launcher3.R.string.prime_role_drawer_folder_open_text
    ThemeColorRole.DRAWER_FOLDER_OPEN_HINT -> com.android.launcher3.R.string.prime_role_drawer_folder_open_hint
    ThemeColorRole.DRAWER_FOLDER_PAGINATION -> com.android.launcher3.R.string.prime_role_drawer_folder_pagination
    ThemeColorRole.DRAWER_FOLDER_CLOSED_BORDER -> com.android.launcher3.R.string.prime_role_drawer_folder_closed_border
    ThemeColorRole.DRAWER_FOLDER_OPEN_BORDER -> com.android.launcher3.R.string.prime_role_drawer_folder_open_border
    ThemeColorRole.DRAWER_FOLDER_BORDER -> com.android.launcher3.R.string.prime_role_drawer_folder_border
    ThemeColorRole.DRAWER_PROFILE_TAB_SELECTED_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_profile_tab_selected_background
    ThemeColorRole.DRAWER_PROFILE_TAB_UNSELECTED_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_profile_tab_unselected_background
    ThemeColorRole.DRAWER_PROFILE_TAB_TEXT -> com.android.launcher3.R.string.prime_role_drawer_profile_tab_text
    ThemeColorRole.DRAWER_POPUP_BACKGROUND -> com.android.launcher3.R.string.prime_role_drawer_popup_background
    ThemeColorRole.DRAWER_POPUP_TEXT -> com.android.launcher3.R.string.prime_role_drawer_popup_text
    ThemeColorRole.DRAWER_POPUP_ICON -> com.android.launcher3.R.string.prime_role_drawer_popup_icon
    ThemeColorRole.TABS_CATEGORY_ACTIVE_BACKGROUND -> com.android.launcher3.R.string.prime_role_tabs_category_active_background
    ThemeColorRole.TABS_CATEGORY_INACTIVE_BACKGROUND -> com.android.launcher3.R.string.prime_role_tabs_category_inactive_background
    ThemeColorRole.TABS_CATEGORY_TEXT -> com.android.launcher3.R.string.prime_role_tabs_category_text
    ThemeColorRole.TABS_CATEGORY_ACTIVE_TEXT -> com.android.launcher3.R.string.prime_role_tabs_category_active_text
    ThemeColorRole.TABS_CATEGORY_INACTIVE_TEXT -> com.android.launcher3.R.string.prime_role_tabs_category_inactive_text
})
