package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.PrimeDebugLog
import com.android.launcher3.R

@Composable
fun PrimeDiagnosticLogsPreference() {
    val logs = remember { mutableStateOf(PrimeDebugLog.getText()) }
    val iconsOnly = remember { mutableStateOf(false) }
    val visibleLogs = if (iconsOnly.value) {
        logs.value.lineSequence().filter { it.contains("PrimeIconCompare") }.joinToString("\\n")
            .ifEmpty { "Aucun diagnostic d’icône pour le moment. Ouvrez le tiroir ou la recherche, puis appuyez sur Actualiser." }
    } else {
        logs.value
    }

    PreferenceLayout(
        label = stringResource(R.string.prime_diagnostic_logs),
        backArrowVisible = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row {
                Button(onClick = { logs.value = PrimeDebugLog.getText() }) {
                    Text("Actualiser")
                }
                Button(
                onClick = {
                    PrimeDebugLog.clear()
                    logs.value = PrimeDebugLog.getText()
                },
            ) {
                Text(stringResource(R.string.prime_diagnostic_logs_clear))
                }
            }
            Row {
                Button(onClick = { iconsOnly.value = true }) { Text("Icônes") }
                Button(onClick = { iconsOnly.value = false }) { Text("Tous les journaux") }
            }
            SelectionContainer {
                Text(
                    text = visibleLogs,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
    }
}
