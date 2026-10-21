/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.gestures.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.os.ResultReceiver
import android.app.role.RoleManager
import android.os.Process
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.icons.LauncherIcons
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.util.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.addIf
import app.lawnchair.util.ProvideLifecycleState

class LawnchairShortcutActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                val windowSizeClass = calculateWindowSizeClass(this)

                val isExpandedScreen =
                    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
                        windowSizeClass.heightSizeClass in
                        setOf(WindowHeightSizeClass.Expanded, WindowHeightSizeClass.Medium)

                ProvideLifecycleState {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.addIf(isExpandedScreen) { requiredWidth(640.dp) },
                        ) {
                            val context = LocalContext.current

                            @Suppress("DEPRECATION")
                            val resultReceiver = intent.getParcelableExtra<ResultReceiver>(EXTRA_RESULT_RECEIVER)
                            CreateActionsScreen(
                                onSelect = {
                                    if (resultReceiver != null) {
                                        val bundle = Bundle().apply {
                                            putString(EXTRA_HANDLER, GestureHandlerConfig.toString(it))
                                        }
                                        resultReceiver.send(RESULT_OK, bundle)
                                    } else {
                                        // A shortcut configuration started for result by Prime's drag/drop
                                        // flow must return its result to the caller so the
                                        // launcher can honor the exact drop coordinates.
                                        // HOME role is not required for this contract.
                                        if (callingActivity != null) {
                                            returnShortcutToPrime(context, it)
                                        } else if (isPrimeDefaultLauncher(context)) {
                                            saveChanges(context, it)
                                        } else {
                                            addDirectlyToPrime(context, it)
                                        }
                                    }
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isPrimeDefaultLauncher(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java)
        return roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }

    // The Android shortcut-result contract belongs to the current HOME app.
    // When another HOME app is selected, write only to Prime's own workspace.
    private fun addDirectlyToPrime(context: Context, config: GestureHandlerConfig) {
        val appContext = context.applicationContext
        Executors.MODEL_EXECUTOR.execute {
            try {
                val app = LauncherAppState.getInstance(appContext)
                val model = app.model
                val idp = app.invariantDeviceProfile
                val columns = idp.numColumns
                val rows = idp.numRows
                if (columns <= 0 || rows <= 0) return@execute

                val occupied = HashSet<String>()
                val screens = sortedSetOf<Int>()
                model.modelDbController.query(
                    arrayOf(
                        LauncherSettings.Favorites.SCREEN,
                        LauncherSettings.Favorites.CELLX,
                        LauncherSettings.Favorites.CELLY,
                        LauncherSettings.Favorites.SPANX,
                        LauncherSettings.Favorites.SPANY,
                    ),
                    LauncherSettings.Favorites.CONTAINER + "=?",
                    arrayOf(LauncherSettings.Favorites.CONTAINER_DESKTOP.toString()),
                    LauncherSettings.Favorites.SCREEN + " ASC",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val screen = cursor.getInt(0)
                        screens.add(screen)
                        val x = cursor.getInt(1)
                        val y = cursor.getInt(2)
                        val spanX = cursor.getInt(3).coerceAtLeast(1)
                        val spanY = cursor.getInt(4).coerceAtLeast(1)
                        for (dx in 0 until spanX) for (dy in 0 until spanY) {
                            occupied.add("$screen:${x + dx}:${y + dy}")
                        }
                    }
                }
                if (screens.isEmpty()) screens.add(0)
                var chosen: Triple<Int, Int, Int>? = null
                for (screen in screens) {
                    for (y in 0 until rows) for (x in 0 until columns) {
                        if ("$screen:$x:$y" !in occupied && chosen == null) {
                            chosen = Triple(screen, x, y)
                        }
                    }
                    if (chosen != null) break
                }
                // Do not overwrite occupied cells or fabricate a new workspace page.
                val (screen, x, y) = chosen ?: return@execute
                val actionIntent = Intent(appContext, RunHandlerActivity::class.java).apply {
                    action = START_ACTION
                    putExtra(EXTRA_HANDLER, GestureHandlerConfig.toString(config))
                }
                val info = WorkspaceItemInfo().apply {
                    itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                    title = config.getDisplayLabel(appContext)
                    intent = actionIntent
                    user = Process.myUserHandle()
                }
                LauncherIcons.obtain(appContext).use { icons ->
                    val glyph = config.getIcon(appContext).loadDrawable(appContext)
                    if (glyph != null) {
                        val adaptive = android.graphics.drawable.AdaptiveIconDrawable(
                            android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE),
                            android.graphics.drawable.InsetDrawable(glyph, 0.20f),
                        )
                        // Only Prime-generated action shortcuts use icon-shape
                        // foreground fitting. Never modify AppWidgetHostView or
                        // third-party RemoteViews, even for 1x1 widgets.
                        val shape = PreferenceManager2.getInstance(appContext)
                            .iconShape.firstCached()
                        info.bitmap = if (shape != null) {
                            icons.createPrimeIconBitmap(adaptive, info.user, shape)
                        } else {
                            icons.createBadgedIconBitmap(
                                adaptive, BaseIconFactory.IconOptions(),
                            )
                        }
                    }
                }
                model.getWriter(false, CellPosMapper.DEFAULT, null).addItemToDatabase(
                    info, LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, x, y,
                )
                model.forceReload()
            } catch (e: Exception) {
                android.util.Log.e("PrimeActions", "Failed to add action to Prime workspace", e)
            }
        }
    }

    // ACTION_CREATE_SHORTCUT expects the legacy result extras. Android's
    // ShortcutManager.createShortcutResultIntent() can return null when a
    // different package (Prime Release) holds the HOME role.
    // Return the action directly to the calling Prime Debug launcher instead.
    private fun returnShortcutToPrime(context: Context, selectedHandler: GestureHandlerConfig) {
        val launchIntent = Intent(context, RunHandlerActivity::class.java).apply {
            action = START_ACTION
            putExtra(EXTRA_HANDLER, GestureHandlerConfig.toString(selectedHandler))
        }
        val icon = selectedHandler.getWorkspaceIcon(context).loadDrawable(context)
        val size = (context.resources.displayMetrics.density * 96).toInt().coerceAtLeast(1)
        val shape = PreferenceManager2.getInstance(context).iconShape.firstCached()
        val bitmap = if (icon != null && shape != null) {
            // Legacy ACTION_CREATE_SHORTCUT results also need the same fitted
            // foreground as directly added Prime actions.
            LauncherIcons.obtain(context).use { icons ->
                val adaptive = android.graphics.drawable.AdaptiveIconDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE),
                    android.graphics.drawable.InsetDrawable(icon, 0.20f),
                )
                icons.createPrimeIconBitmap(adaptive, Process.myUserHandle(), shape).icon
            }
        } else {
            android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888,
            ).also { result ->
                if (icon != null) {
                    val canvas = android.graphics.Canvas(result)
                    icon.setBounds(0, 0, size, size)
                    icon.draw(canvas)
                }
            }
        }
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, selectedHandler.getDisplayLabel(context).toString())
                putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun saveChanges(context: Context, selectedHandler: GestureHandlerConfig) {
        val shortcutManager =
            ContextCompat.getSystemService(applicationContext, ShortcutManager::class.java)
                ?: return

        val shortcutInfo = ShortcutInfo.Builder(
            context,
            "$GESTURE_SHORTCUT_ID_PREFIX:${GestureHandlerConfig.toString(selectedHandler)}",
        )
            .apply {
                setShortLabel(selectedHandler.getDisplayLabel(context))
                setIcon(selectedHandler.getWorkspaceIcon(context))
                setIntent(
                    Intent(context, RunHandlerActivity::class.java).apply {
                        action = START_ACTION
                        putExtra(EXTRA_HANDLER, GestureHandlerConfig.toString(selectedHandler))
                    },
                )
            }.build()

        val intent = shortcutManager.createShortcutResultIntent(shortcutInfo)
        setResult(RESULT_OK, intent)
    }

    companion object {
        const val START_ACTION = "app.lawnchair.START_ACTION"
        const val EXTRA_HANDLER = "app.lawnchair.EXTRA_HANDLER"
        const val EXTRA_RESULT_RECEIVER = "app.lawnchair.EXTRA_RESULT_RECEIVER"
        const val GESTURE_SHORTCUT_ID_PREFIX = "gesture:"

        fun shouldSkipShortcutBadge(context: Context, si: ShortcutInfo): Boolean {
            val value = context.packageName == si.`package` &&
                si.id.startsWith(GESTURE_SHORTCUT_ID_PREFIX)

            return value
        }
    }
}
