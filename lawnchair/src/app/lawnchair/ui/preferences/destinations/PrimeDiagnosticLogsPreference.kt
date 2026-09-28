package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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

    PreferenceLayout(
        label = stringResource(R.string.prime_diagnostic_logs),
        backArrowVisible = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = {
                    PrimeDebugLog.clear()
                    logs.value = PrimeDebugLog.getText()
                },
            ) {
                Text(stringResource(R.string.prime_diagnostic_logs_clear))
            }
            SelectionContainer {
                Text(
                    text = logs.value,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
