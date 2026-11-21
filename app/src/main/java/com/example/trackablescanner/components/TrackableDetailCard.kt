
package com.example.trackablescanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trackablescanner.geocaching.TrackableLookupResult

@Composable
fun TrackableDetailCard(
    result: TrackableLookupResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.titleLarge
            )

            HorizontalDivider()

            DetailRow("Tracking kód", result.trackingCode)
            DetailRow("TB identifikátor", result.tbCode)
            DetailRow("Vlastník", result.owner)
            DetailRow("Typ", result.trackableType)

            if (result.goal.isNotBlank()) {
                HorizontalDivider()

                Text(
                    text = "Cíl trackable",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = result.goal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (result.description.isNotBlank()) {
                HorizontalDivider()

                Text(
                    text = "Popis",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = result.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    if (value.isNotBlank()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
