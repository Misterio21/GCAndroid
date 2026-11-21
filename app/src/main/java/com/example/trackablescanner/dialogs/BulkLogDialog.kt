
package com.example.trackablescanner.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BulkLogDialog(
    trackableCount: Int,
    logDate: String,
    onLogDateChange: (String) -> Unit,
    logText: String,
    onLogTextChange: (String) -> Unit,
    submitting: Boolean,
    progress: Int,
    message: String,
    results: List<String>,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = {
            Text("Hromadné logování")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Log bude odeslán všem $trackableCount uloženým TB."
                )

                OutlinedTextField(
                    value = logDate,
                    onValueChange = onLogDateChange,
                    label = {
                        Text("Datum (RRRR-MM-DD)")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting
                )

                OutlinedTextField(
                    value = logText,
                    onValueChange = onLogTextChange,
                    label = {
                        Text("Text společného logu")
                    },
                    placeholder = {
                        Text("Napiš společný text pro všechny TB...")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    minLines = 4,
                    maxLines = 8,
                    enabled = !submitting
                )

                Text(
                    "Typ logu: Discovered It",
                    style = MaterialTheme.typography.bodySmall
                )

                if (submitting) {
                    LinearProgressIndicator(
                        progress = {
                            if (trackableCount == 0) 0f
                            else progress.toFloat() / trackableCount
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("$progress / $trackableCount TB")
                }

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (results.isNotEmpty()) {
                    HorizontalDivider()

                    Text(
                        "Výsledky",
                        style = MaterialTheme.typography.titleSmall
                    )

                    results.forEach { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!submitting && progress == 0) {
                TextButton(
                    onClick = onContinue
                ) {
                    Text("Pokračovat")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !submitting
            ) {
                Text(
                    if (progress > 0) "Zavřít" else "Zrušit"
                )
            }
        }
    )
}
