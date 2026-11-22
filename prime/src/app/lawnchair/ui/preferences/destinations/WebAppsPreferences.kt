package app.lawnchair.ui.preferences.destinations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout

/**
 * Native browser installation handoff for PWAs.
 *
 * A WebAPK must be installed by a supporting browser: a launcher cannot create
 * one from a URL. The browser owns the PWA profile, offline cache and runtime.
 */
@Composable
fun WebAppsPreferences() {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    PreferenceLayout(
        label = "Applications Web (PWA)",
        backArrowVisible = true,
    ) {
        PreferenceGroup {
            Text("Installer une application Web")
            Text(
                "Ouvrez le site dans un navigateur compatible, puis utilisez " +
                    "son menu « Installer l'application » ou « Ajouter à l'écran d'accueil »."
            )
            Button(onClick = { showAddDialog = true }) {
                Text("Installer une PWA depuis une URL")
            }
            Text(
                "Les WebAPK installés apparaissent comme des applications Android " +
                    "dans le tiroir. Leur mode hors ligne et leurs données restent " +
                    "gérés par le navigateur."
            )
            Text(
                "Attention : « Ajouter à l'écran d'accueil » peut créer un simple " +
                    "raccourci plutôt qu'une application installée. " +
                    "L'intégration de ces raccourcis au tiroir est prévue séparément."
            )
        }
    }

    if (showAddDialog) {
        val normalized = normalizeWebAppUrl(url)
        AlertDialog(
            onDismissRequest = { showAddDialog = false; error = null },
            title = { Text("Installer une application Web") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Saisissez l'adresse HTTPS de la PWA à installer.")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text("Adresse du site") },
                        placeholder = { Text("https://exemple.com") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) Text(error!!)
                    Text(
                        "Le navigateur décidera si le site est installable en WebAPK. " +
                            "Confirmez ensuite l'installation dans son menu."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalized != null,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                        try {
                            context.startActivity(intent)
                            showAddDialog = false
                            error = null
                        } catch (_: ActivityNotFoundException) {
                            error = "Aucun navigateur disponible pour ouvrir cette adresse."
                        } catch (_: SecurityException) {
                            error = "Le navigateur ne permet pas d'ouvrir cette adresse."
                        }
                    },
                ) { Text("Ouvrir dans le navigateur") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; error = null }) {
                    Text("Annuler")
                }
            },
        )
    }
}

/** Only accept web URLs; never forward arbitrary schemes to external apps. */
private fun normalizeWebAppUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
    if (uri.userInfo != null) return null
    return uri.toString()
}
