package app.lawnchair.ui.preferences.components.search

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import app.lawnchair.prime.search.PrimeSearchPermissions
import app.lawnchair.prime.search.PrimeSourceSelection
import app.lawnchair.prime.search.PrimeTasksSearch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.prime.search.PrimeCalendarSearch
import app.lawnchair.prime.search.PrimeSearchResultPreferences as Prefs
import kotlin.math.roundToInt

/** Shared category controls; every read/write is scoped to the requested search surface. */
@Composable
internal fun SharedCategoryOptions(category: Prefs.Category, surface: Prefs.Surface, permissionRefresh: Int = 0) {
    val context = LocalContext.current
    Column {
        when (category) {
            Prefs.Category.CALENDAR, Prefs.Category.CONTACTS, Prefs.Category.REMINDERS -> {
                val kind = when (category) {
                    Prefs.Category.CALENDAR -> "calendars"
                    Prefs.Category.REMINDERS -> "tasks"
                    else -> "contacts"
                }
                if (category == Prefs.Category.REMINDERS) {
                    var showCompleted by remember(surface) { mutableStateOf(Prefs.showCompletedTasks(context, surface)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Afficher les tâches terminées", modifier = Modifier.weight(1f))
                        Switch(checked = showCompleted, onCheckedChange = {
                            showCompleted = it
                            Prefs.setShowCompletedTasks(context, surface, it)
                        })
                    }
                }
                if (category == Prefs.Category.CALENDAR) {
                    var futureOnly by remember(surface) { mutableStateOf(PrimeCalendarSearch.futureOnly(context, surface)) }
                    Row {
                        Text("Événements futurs uniquement", modifier = Modifier.weight(1f))
                        Switch(checked = futureOnly, onCheckedChange = {
                            futureOnly = it
                            PrimeCalendarSearch.setFutureOnly(context, it, surface)
                        })
                    }
                }
                if (PrimeSearchPermissions.granted(context, category)) {
                    val sources = remember(category, permissionRefresh) {
                        when (kind) {
                            "calendars" -> PrimeSourceSelection.calendars(context)
                            "tasks" -> PrimeTasksSearch.lists(context)
                            else -> PrimeSourceSelection.contactAccounts(context)
                        }
                    }
                    var selectedIds by remember(surface, category, permissionRefresh) {
                        mutableStateOf(PrimeSourceSelection.selected(context, kind, surface))
                    }
                    Text(when (kind) {
                        "calendars" -> "Agendas utilisés pour la recherche"
                        "tasks" -> "Listes Tasks.org utilisées pour la recherche"
                        else -> "Comptes de contacts utilisés pour la recherche"
                    })
                    if (sources.isEmpty()) Text("Aucune source disponible sur cet appareil.")
                    Column(modifier = Modifier.fillMaxWidth()) {
                        sources.forEach { source ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedIds?.contains(source.id) ?: true,
                                    onCheckedChange = { enabled ->
                                        PrimeSourceSelection.setSelected(context, kind, source.id, enabled, sources, surface)
                                        selectedIds = PrimeSourceSelection.selected(context, kind, surface)
                                    },
                                )
                                Text(source.label)
                            }
                        }
                    }
                    TextButton(onClick = {
                        PrimeSourceSelection.reset(context, kind, surface)
                        selectedIds = null
                    }) { Text("Tout sélectionner") }
                }
            }
            Prefs.Category.FILES -> {
                var limit by remember(surface) { mutableStateOf(Prefs.maxFileResults(context, surface)) }
                Text("Nombre maximal de fichiers : $limit")
                Slider(value = limit.toFloat(), onValueChange = {
                    limit = it.roundToInt().coerceIn(3, 100)
                    Prefs.setMaxFileResults(context, limit, surface)
                }, valueRange = 3f..100f)
                Prefs.FileOption.entries.forEach { option ->
                    var active by remember(surface, option) { mutableStateOf(Prefs.fileOptionEnabled(context, option, surface)) }
                    Row {
                        Text(option.label, modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = {
                            active = it
                            Prefs.setFileOptionEnabled(context, option, it, surface)
                        })
                    }
                }
                Text("Les fichiers accessibles dépendent des autorisations Android.")
            }
            Prefs.Category.WEB -> {
                var count by remember(surface) { mutableStateOf(Prefs.webSuggestionCount(context, surface)) }
                Text("Nombre de suggestions : $count")
                Slider(value = count.toFloat(), onValueChange = {
                    count = it.roundToInt().coerceIn(0, 8)
                    Prefs.setWebSuggestionCount(context, count, surface)
                }, valueRange = 0f..8f, steps = 7)
                var provider by remember(surface) { mutableStateOf(Prefs.suggestionProvider(context, surface)) }
                Text("Fournisseur de suggestions")
                Prefs.SuggestionProvider.entries.forEach { option ->
                    Row {
                        RadioButton(selected = provider == option, onClick = {
                            provider = option
                            Prefs.setSuggestionProvider(context, option, surface)
                        })
                        Text(option.title)
                    }
                }
                Text("Les termes saisis sont transmis au fournisseur sélectionné.")
            }
            Prefs.Category.WEB_SHORTCUTS -> {
                Prefs.WebEngine.entries.forEach { engine ->
                    var active by remember(surface, engine) { mutableStateOf(engine in Prefs.enabledWebEngines(context, surface)) }
                    Row {
                        Text(engine.title, modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = {
                            active = it
                            Prefs.setWebEngineEnabled(context, engine, it, surface)
                        })
                    }
                }
            }
            Prefs.Category.NOTIFICATIONS -> {
                var retention by remember(surface) { mutableStateOf(Prefs.notificationRetention(context, surface)) }
                Text("Conserver les notifications pendant :")
                Prefs.NotificationRetention.entries.forEach { option ->
                    Row {
                        RadioButton(selected = retention == option, onClick = {
                            retention = option
                            Prefs.setNotificationRetention(context, option, surface)
                        })
                        Text(option.label)
                    }
                }
            }
            else -> Unit
        }
    }
}

/** One category card design for both surfaces, including reorder gestures and actions. */
@Composable
internal fun SharedCategoryCard(
    category: Prefs.Category,
    index: Int,
    lastIndex: Int,
    enabled: Boolean,
    needsPermission: Boolean,
    onSettings: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onPermission: () -> Unit,
    dragState: CategoryDragState,
) {
    val dragging = dragState.active == category
    val rowPixels = with(LocalDensity.current) { 112.dp.toPx() }
    val targetIndex = (dragState.origin + (dragState.offset / rowPixels).roundToInt()).coerceIn(0, lastIndex)
    val displacement = if (dragState.active != null && !dragging) {
        when {
            index > dragState.origin && index <= targetIndex -> -rowPixels
            index < dragState.origin && index >= targetIndex -> rowPixels
            else -> 0f
        }
    } else 0f
    val shift by animateFloatAsState(displacement, label = "categoryShift")
    val lift by animateFloatAsState(if (dragging) 1.06f else 1f, label = "categoryLift")
    PreferenceTemplate(
        modifier = Modifier.zIndex(if (dragging) 10f else 0f).graphicsLayer {
            translationY = if (dragging) dragState.offset else shift
            scaleX = lift
            scaleY = lift
            shadowElevation = if (dragging) 12.dp.toPx() else 0f
        },
        title = { Text(category.title) },
        description = {
            Text(
                if (needsPermission) PrimeSearchPermissions.explanation(category) else category.description,
                color = if (needsPermission) androidx.compose.ui.graphics.Color.Gray else androidx.compose.ui.graphics.Color.Unspecified,
            )
        },
        endWidget = {
            Row {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Paramètres de ${category.title}")
                }
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Maintenir puis glisser pour réorganiser",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        .pointerInput(category, index, lastIndex) {
                            var distance = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    distance = 0f
                                    dragState.origin = index
                                    dragState.offset = 0f
                                    dragState.active = category
                                },
                                onDragEnd = {
                                    val destination = (dragState.origin + (dragState.offset / rowPixels).roundToInt()).coerceIn(0, lastIndex)
                                    dragState.active = null
                                    dragState.offset = 0f
                                    onMove(index, destination)
                                },
                                onDragCancel = {
                                    distance = 0f
                                    dragState.offset = 0f
                                    dragState.active = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    distance += amount.y
                                    dragState.offset = distance
                                },
                            )
                        },
                )
                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter")
                }
                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre")
                }
                Switch(
                    checked = enabled && !needsPermission,
                    onCheckedChange = { value ->
                        if (value && needsPermission) onPermission() else onEnabled(value)
                    },
                )
            }
        },
        onClick = {
            if (needsPermission) onPermission() else onEnabled(!enabled)
        },
    )
}

internal class CategoryDragState {
    var active by mutableStateOf<Prefs.Category?>(null)
    var origin by mutableStateOf(0)
    var offset by mutableStateOf(0f)
}

/** Identical settings dialog shell on both search surfaces. */
@Composable
internal fun SharedCategorySettingsDialog(
    category: Prefs.Category,
    surface: Prefs.Surface,
    permissionRefresh: Int,
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(category.title) },
        text = {
            Column {
                Text(category.description)
                SharedCategoryOptions(category, surface, permissionRefresh)
                extra()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}
