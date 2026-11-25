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
    var showSearchDiagnostic by remember { mutableStateOf(false) }
    var searchLog by remember { mutableStateOf("") }
    var searchTraceEnabled by remember { mutableStateOf(app.lawnchair.search.PrimeSearchTrace.enabled) }
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
                label = stringResource(R.string.prime_diag_export),
                subtitle = stringResource(R.string.prime_diag_export_desc),
                onClick = { app.lawnchair.bugreport.PrimeWidgetLogExporter.export(context) },
            )
            ClickablePreference(
                label = stringResource(R.string.prime_diag_popup),
                subtitle = stringResource(R.string.prime_diag_popup_desc),
                onClick = {
                    popupLog = PrimeDebugLog.getPopupText(context)
                        .ifBlank { context.getString(R.string.prime_diag_no_trace) }
                    showPopupDiagnostic = true
                },
            )
            ClickablePreference(
                label = stringResource(R.string.prime_diag_search),
                subtitle = stringResource(R.string.prime_diag_search_desc),
                onClick = { searchLog = app.lawnchair.search.PrimeSearchTrace.dump(); showSearchDiagnostic = true },
            )
            ClickablePreference(
                label = stringResource(R.string.prime_diag_swipe),
                subtitle = stringResource(R.string.prime_diag_swipe_desc),
                onClick = { showSwipeDiagnostic = true },
            )
        }
    }

    if (showPopupDiagnostic) {
        AlertDialog(
            onDismissRequest = { showPopupDiagnostic = false },
            title = { Text(stringResource(R.string.prime_diag_popup)) },
            text = {
                Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(popupLog)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Prime popup diagnostic", popupLog))
                }) { Text(stringResource(R.string.prime_diag_copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, popupLog)
                    }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.prime_diag_share_title)))
                }) { Text(stringResource(R.string.prime_diag_share)) }
                TextButton(onClick = {
                    PrimeDebugLog.clearPopup(context)
                    popupLog = context.getString(R.string.prime_diag_cleared)
                }) { Text(stringResource(R.string.prime_diag_clear)) }
            },
        )
    }

    if (showSearchDiagnostic) {
        AlertDialog(
            onDismissRequest = { showSearchDiagnostic = false },
            title = { Text(stringResource(R.string.prime_diag_search)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Switch(
                        checked = searchTraceEnabled,
                        onCheckedChange = {
                            searchTraceEnabled = it
                            app.lawnchair.search.PrimeSearchTrace.enabled = it
                        },
                    )
                    Text(if (searchTraceEnabled) stringResource(R.string.prime_diag_capture_on) else stringResource(R.string.prime_diag_capture_off))
                    TextButton(onClick = { searchLog = app.lawnchair.search.PrimeSearchTrace.dump() }) {
                        Text(stringResource(R.string.prime_diag_refresh))
                    }
                    Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(searchLog)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Prime search diagnostic", searchLog))
                }) { Text(stringResource(R.string.prime_diag_copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.lawnchair.search.PrimeSearchTrace.clear()
                    searchLog = app.lawnchair.search.PrimeSearchTrace.dump()
                }) { Text(stringResource(R.string.prime_diag_clear)) }
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, searchLog)
                    }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.prime_diag_share_title)))
                }) { Text(stringResource(R.string.prime_diag_share)) }
            },
        )
    }

    if (showSwipeDiagnostic) {
        val log = ActivityAllAppsContainerView.getPrimeSwipeDebugLog()
        AlertDialog(
            onDismissRequest = { showSwipeDiagnostic = false },
            title = { Text(stringResource(R.string.prime_diag_prime_swipe)) },
            text = {
                Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(log)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Prime swipe diagnostic", log))
                }) { Text(stringResource(R.string.prime_diag_copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ActivityAllAppsContainerView.clearPrimeSwipeDebugLog()
                    showSwipeDiagnostic = false
                }) { Text(stringResource(R.string.prime_diag_clear)) }
            },
        )
    }
}
