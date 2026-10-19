package app.lawnchair.ui.preferences.components.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
