package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pick a published Android app shortcut for a Prime Launcher Action. */
@Composable
fun PickShortcutForGesture() {
    val activity = LocalContext.current as Activity
    val launcherApps = activity.getSystemService(LauncherApps::class.java)
    val packageManager = activity.packageManager
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val shortcuts by produceState<List<ShortcutInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            if (launcherApps == null || !launcherApps.hasShortcutHostPermission()) {
                emptyList()
            } else {
                runCatching {
                    launcherApps.profiles.flatMap { user ->
                        launcherApps.getShortcuts(
                            LauncherApps.ShortcutQuery().apply {
                                setQueryFlags(
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
                                )
                            },
                            user,
                        ).orEmpty()
                    }.filter { it.isEnabled }
                        .distinctBy { "${it.userHandle}:${it.`package`}:${it.id}" }
                        .sortedWith(compareBy({ it.`package` }, { it.shortLabel.toString() }))
                }.getOrDefault(emptyList())
            }
        }
    }

    fun onSelect(shortcut: ShortcutInfo) {
        val config: GestureHandlerConfig = GestureHandlerConfig.OpenApp(
            appName = shortcut.shortLabel.toString(),
            target = OpenAppTarget.Shortcut(
                intent = Intent(),
                user = shortcut.userHandle,
                packageName = shortcut.`package`,
                id = shortcut.id,
            ),
        )
        activity.setResult(
            Activity.RESULT_OK,
            Intent().putExtra("config", kotlinxJson.encodeToString<GestureHandlerConfig>(config)),
        )
        activity.finish()
    }

    // Group by app and profile; search both application and shortcut names.
    val groups = remember(shortcuts, search) {
        val needle = java.text.Normalizer.normalize(
            search.trim(), java.text.Normalizer.Form.NFD,
        ).replace(Regex("\\p{M}+"), "").lowercase()
        fun matches(value: String) = java.text.Normalizer.normalize(
            value, java.text.Normalizer.Form.NFD,
        ).replace(Regex("\\p{M}+"), "").lowercase().contains(needle)

        shortcuts.orEmpty().groupBy { "${it.userHandle}:${it.`package`}" }
            .mapNotNull { (key, list) ->
                val packageName = list.first().`package`
                val appName = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0),
                    ).toString()
                }.getOrDefault(packageName)
                val filtered = if (needle.isEmpty() || matches(appName)) list else
                    list.filter { matches(it.shortLabel.toString()) || matches(it.longLabel?.toString().orEmpty()) }
                if (filtered.isEmpty()) null else Triple(key, appName, filtered)
            }.sortedBy { it.second.lowercase() }
    }

    PreferenceScaffold(
        label = stringResource(R.string.pick_shortcut_for_gesture),
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Rechercher une application ou un raccourci") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (shortcuts == null) {
                item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
            } else if (groups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_app_shortcuts_available),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            groups.forEach { (key, appName, matches) ->
                val packageName = matches.first().`package`
                val isExpanded = key in expanded || search.isNotBlank()
                item(key = "app:$key") {
                    ListItem(
                        leadingContent = {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                update = { image ->
                                    image.setImageDrawable(runCatching {
                                        packageManager.getApplicationIcon(packageName)
                                    }.getOrNull())
                                },
                                modifier = Modifier.size(42.dp),
                            )
                        },
                        headlineContent = { Text(appName) },
                        supportingContent = { Text("${matches.size} raccourci(s)") },
                        trailingContent = { Text(if (isExpanded) "▾" else "▸") },
                        modifier = Modifier.fillMaxWidth().clickable {
                            expanded = if (key in expanded) expanded - key else expanded + key
                        },
                    )
                }
                if (isExpanded) {
                    items(matches, key = { "shortcut:${it.userHandle}:${it.`package`}:${it.id}" }) { shortcut ->
                        ListItem(
                            leadingContent = {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { image ->
                                        image.setImageDrawable(runCatching {
                                            launcherApps?.getShortcutIconDrawable(
                                                shortcut, activity.resources.displayMetrics.densityDpi,
                                            ) ?: packageManager.getApplicationIcon(packageName)
                                        }.getOrNull())
                                    },
                                    modifier = Modifier.size(40.dp),
                                )
                            },
                            headlineContent = { Text(shortcut.shortLabel.toString(), maxLines = 2) },
                            supportingContent = { Text(appName, maxLines = 1) },
                            trailingContent = {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { image ->
                                        image.setImageDrawable(runCatching {
                                            packageManager.getApplicationIcon(packageName)
                                        }.getOrNull())
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(shortcut) },
                        )
                    }
                }
            }
        }
    }
}
