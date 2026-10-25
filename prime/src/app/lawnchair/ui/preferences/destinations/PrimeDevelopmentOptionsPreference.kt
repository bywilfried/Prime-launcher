package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.PrimeDiagnosticLogs
import app.lawnchair.util.restartLauncher
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import android.content.Intent
import com.android.launcher3.PrimeDebugLog
import android.content.ClipData
import android.content.ClipboardManager

@Composable
fun PrimeDevelopmentOptionsPreference() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    var showSwipeDiagnostic by remember { mutableStateOf(false) }
    var showPopupDiagnostic by remember { mutableStateOf(false) }
    var popupLog by remember { mutableStateOf("") }
    PreferenceLayout(
        label = stringResource(R.string.prime_development_options),
        backArrowVisible = true,
    ) {
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.debug_restart_launcher),
                onClick = { restartLauncher(context) },
            )
            ClickablePreference(
                label = stringResource(R.string.prime_diagnostic_logs),
                subtitle = stringResource(R.string.prime_diagnostic_logs_description),
                onClick = { navController.navigate(PrimeDiagnosticLogs) },
            )
            ClickablePreference(
                label = "Exporter les logs des widgets",
                subtitle = "Récupérer et partager les journaux accessibles à Prime Launcher",
                onClick = { app.lawnchair.bugreport.PrimeWidgetLogExporter.export(context) },
            )
            ClickablePreference(
                label = "Diagnostic des menus contextuels",
                subtitle = "Afficher et partager les couleurs et fonds des menus de l’accueil",
                onClick = {
                    popupLog = PrimeDebugLog.getPopupText(context)
                        .ifBlank { "Aucune trace. Effectuez un appui long sur une zone vide de l’accueil, puis revenez ici." }
                    showPopupDiagnostic = true
                },
            )
            ClickablePreference(
                label = "Diagnostic swipe",
                subtitle = "Afficher les traces du swipe entre les catégories du tiroir",
                onClick = { showSwipeDiagnostic = true },
            )
        }
    }

    if (showPopupDiagnostic) {
        AlertDialog(
            onDismissRequest = { showPopupDiagnostic = false },
            title = { Text("Diagnostic des menus contextuels") },
            text = {
                Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(popupLog)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Prime popup diagnostic", popupLog))
                }) { Text("Copier") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, popupLog)
                    }
                    context.startActivity(Intent.createChooser(share, "Partager le diagnostic"))
                }) { Text("Partager") }
                TextButton(onClick = {
                    PrimeDebugLog.clearPopup(context)
                    popupLog = "Journal effacé. Effectuez un nouvel appui long sur une zone vide."
                }) { Text("Effacer") }
            },
        )
    }

    if (showSwipeDiagnostic) {
        val log = ActivityAllAppsContainerView.getPrimeSwipeDebugLog()
        AlertDialog(
            onDismissRequest = { showSwipeDiagnostic = false },
            title = { Text("Diagnostic swipe Prime") },
            text = {
                Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(log)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Prime swipe diagnostic", log))
                }) { Text("Copier") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ActivityAllAppsContainerView.clearPrimeSwipeDebugLog()
                    showSwipeDiagnostic = false
                }) { Text("Effacer") }
            },
        )
    }
}
