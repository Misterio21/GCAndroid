
package com.example.trackablescanner.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit

import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.trackablescanner.geocaching.GeocachingLoginActivity
import com.example.trackablescanner.ui.components.LoginSection
import com.example.trackablescanner.ui.components.ScannerSection
import com.example.trackablescanner.ui.components.TrackableDetailCard
import com.example.trackablescanner.ui.dialogs.ManualCodeDialog
import com.example.trackablescanner.ui.dialogs.BulkLogDialog
import com.example.trackablescanner.ui.dialogs.BulkLogConfirmDialog
import com.example.trackablescanner.ui.viewmodel.TrackableScannerViewModel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackableScannerApp(
    scannerViewModel: TrackableScannerViewModel = viewModel()
) {

    val context = LocalContext.current

    // --------------------------------------------------
    // VIEWMODEL STATE
    // --------------------------------------------------

    val state by scannerViewModel.uiState.collectAsState()
    val trackables = state.trackables

    // --------------------------------------------------
    // LOKÁLNÍ UI STAV
    // --------------------------------------------------

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanning by remember {
        mutableStateOf(false)
    }

    var showManualDialog by remember {
        mutableStateOf(false)
    }

    var manualCode by remember {
        mutableStateOf("")
    }

    // --------------------------------------------------
    // LAUNCHERY
    // --------------------------------------------------

    val geocachingLoginLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

                val cookie = result.data?.getStringExtra(
                    GeocachingLoginActivity.EXTRA_COOKIE
                )

                if (!cookie.isNullOrBlank()) {
                    scannerViewModel.setCookie(cookie)
                }
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted
            scanning = granted

            if (!granted) {
                scannerViewModel.clearMessage()
            }
        }

    // --------------------------------------------------
    // HLAVNÍ OBRAZOVKA
    // --------------------------------------------------

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Trackable Scanner")
                },
                actions = {
                    IconButton(
                        onClick = {
                            scannerViewModel.deleteAllTrackables()
                        },
                        enabled = trackables.isNotEmpty() &&
                                !state.bulkLogSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Vymazat seznam"
                        )
                    }
                }
            )
        }
    ) { innerPadding: PaddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --------------------------------------------------
            // POČET TRACKABLES
            // --------------------------------------------------

            Text(
                text = "Naskenované trackables: ${trackables.size}",
                style = MaterialTheme.typography.titleMedium
            )

            // --------------------------------------------------
            // PŘIHLÁŠENÍ
            // --------------------------------------------------

            LoginSection(
                loginMessage = state.connectionMessage.ifBlank {
                    state.loginMessage
                },
                enabled = !state.bulkLogSubmitting,

                onLoginClick = {
                    val intent = Intent(
                        context,
                        GeocachingLoginActivity::class.java
                    )

                    geocachingLoginLauncher.launch(intent)
                },

                onVerifyClick = {
                    scannerViewModel.verifyConnection()
                }
            )

            HorizontalDivider()

            // --------------------------------------------------
            // KAMERA
            // --------------------------------------------------

            ScannerSection(
                scanning = scanning,
                cameraPermissionGranted = cameraPermissionGranted,
                enabled = !state.bulkLogSubmitting,

                onStartClick = {
                    if (cameraPermissionGranted) {
                        scanning = true
                    } else {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                },

                onStopClick = {
                    scanning = false
                },

                onCodeDetected = { code: String ->
                    scannerViewModel.addTrackable(code)
                }
            )

            // --------------------------------------------------
            // RUČNÍ ZADÁNÍ
            // --------------------------------------------------

            OutlinedButton(
                onClick = {
                    manualCode = ""
                    showManualDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.bulkLogSubmitting
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text("Zadat kód ručně")
            }

            // --------------------------------------------------
            // HLAVNÍ ZPRÁVA
            // --------------------------------------------------

            if (state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // --------------------------------------------------
            // VÝSLEDEK VYHLEDÁVÁNÍ
            // --------------------------------------------------

            if (state.isLookingUp) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.lookupMessage.isNotBlank()) {
                Text(
                    text = state.lookupMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.lookupResult?.let { result ->
                TrackableDetailCard(
                    result = result
                )
            }

            HorizontalDivider()

            // --------------------------------------------------
            // SEZNAM TRACKABLES
            // --------------------------------------------------

            Text(
                text = "Seznam kódů",
                style = MaterialTheme.typography.titleMedium
            )

            // Hromadné logování

            Button(
                onClick = {
                    scannerViewModel.openBulkLogDialog()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = trackables.isNotEmpty() &&
                        !state.bulkLogSubmitting &&
                        state.cookie != null
            ) {
                Text(
                    "Hromadně logovat všechny TB (${trackables.size})"
                )
            }

            // Prázdný seznam

            if (trackables.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Zatím nebyl naskenován žádný kód.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else {

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    trackables.forEach { trackable ->

                        val formattedDate = remember(trackable.addedAt) {
                            SimpleDateFormat(
                                "dd.MM.yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(trackable.addedAt))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),

                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {

                                    Text(
                                        text = trackable.code,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = "Přidáno: $formattedDate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        scannerViewModel.lookupTrackable(
                                            trackable.code
                                        )
                                    },
                                    enabled = !state.isLookingUp &&
                                            state.cookie != null &&
                                            !state.bulkLogSubmitting
                                ) {
                                    Text("Detail")
                                }

                                IconButton(
                                    onClick = {
                                        scannerViewModel.removeTrackable(
                                            trackable.code
                                        )
                                    },
                                    enabled = !state.bulkLogSubmitting
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Odstranit"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------
    // DIALOG PRO RUČNÍ ZADÁNÍ
    // --------------------------------------------------

    if (showManualDialog) {

        ManualCodeDialog(
            code = manualCode,

            onCodeChange = { newCode: String ->
                manualCode = newCode.uppercase(Locale.ROOT)
            },

            onDismiss = {
                showManualDialog = false
            },

            onConfirm = {
                scannerViewModel.addTrackable(manualCode)
                showManualDialog = false
            }
        )
    }

    // --------------------------------------------------
    // DIALOG PRO HROMADNÉ LOGOVÁNÍ
    // --------------------------------------------------

    if (state.showBulkLogDialog) {

        BulkLogDialog(
            trackableCount = trackables.size,

            logDate = state.bulkLogDate,
            onLogDateChange = { newDate: String ->
                scannerViewModel.updateBulkLogDate(newDate)
            },

            logText = state.bulkLogText,
            onLogTextChange = { newText: String ->
                scannerViewModel.updateBulkLogText(newText)
            },

            submitting = state.bulkLogSubmitting,
            progress = state.bulkLogProgress,
            message = state.bulkLogMessage,
            results = state.bulkLogResults,

            onContinue = {
                scannerViewModel.validateBulkLog()
            },

            onDismiss = {
                scannerViewModel.closeBulkLogDialog()
            }
        )
    }

    // --------------------------------------------------
    // POTVRZENÍ HROMADNÉHO ODESLÁNÍ
    // --------------------------------------------------

    if (state.showBulkLogConfirmDialog) {

        BulkLogConfirmDialog(
            trackableCount = trackables.size,
            logDate = state.bulkLogDate,
            logText = state.bulkLogText,

            onConfirm = {
                scannerViewModel.submitBulkLogs()
            },

            onDismiss = {
                scannerViewModel.closeBulkLogConfirmDialog()
            }
        )
    }
}
