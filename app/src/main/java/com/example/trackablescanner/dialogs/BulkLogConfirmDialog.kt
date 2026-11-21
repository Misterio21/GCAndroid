
package com.example.trackablescanner.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
@Composable
fun BulkLogConfirmDialog(
    trackableCount: Int,
    logDate: String,
    logText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Odeslat log všem TB?")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Počet trackables: $trackableCount")
                Text("Datum: $logDate")
                Text("Typ: Discovered It")

                HorizontalDivider()

                Text("Společný text:")
                Text(logText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Odeslat všem")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Zpět")
            }
        }
    )
}
