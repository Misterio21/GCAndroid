
package com.example.trackablescanner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoginSection(
    loginMessage: String,
    enabled: Boolean,
    onLoginClick: () -> Unit,
    onVerifyClick: () -> Unit
) {
    Button(
        onClick = onLoginClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Text("Přihlásit ke Geocaching.com")
    }

    OutlinedButton(
        onClick = onVerifyClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Text("Ověřit spojení")
    }

    Text(
        text = loginMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
