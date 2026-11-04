package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    PreferenceScaffold(
        label = stringResource(R.string.pick_shortcut_for_gesture),
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { padding ->
        when {
            shortcuts == null -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            shortcuts!!.isEmpty() -> Text(
                text = stringResource(R.string.no_app_shortcuts_available),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyColumn(contentPadding = padding) {
                items(shortcuts!!, key = { "${it.userHandle}:${it.`package`}:${it.id}" }) { shortcut ->
                    ListItem(
                        headlineContent = { Text(shortcut.shortLabel.toString()) },
                        supportingContent = { Text(shortcut.`package`) },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(shortcut) },
                    )
                }
            }
        }
    }
}
