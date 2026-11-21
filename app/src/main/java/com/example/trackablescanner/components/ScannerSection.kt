
package com.example.trackablescanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trackablescanner.scanner.CameraScanner

@Composable
fun ScannerSection(
    scanning: Boolean,
    cameraPermissionGranted: Boolean,
    enabled: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCodeDetected: (String) -> Unit
) {
    if (scanning && cameraPermissionGranted) {
        CameraScanner(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            onCodeDetected = onCodeDetected
        )

        Text(
            text = "Umístěte kód dovnitř zeleného rámečku.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = onStopClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text("Zastavit skenování")
        }
    } else {
        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null
            )

            Spacer(Modifier.width(8.dp))

            Text("Spustit skener")
        }
    }
}
