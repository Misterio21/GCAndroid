
package com.example.trackablescanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.trackablescanner.data.TrackableDatabase
import com.example.trackablescanner.data.TrackableRepository
import com.example.trackablescanner.geocaching.GeocachingApi
import com.example.trackablescanner.geocaching.GeocachingLoginActivity
import com.example.trackablescanner.geocaching.TrackableLookup
import com.example.trackablescanner.geocaching.TrackableLookupResult
import com.example.trackablescanner.geocaching.TrackableLog
import com.example.trackablescanner.scanner.CameraScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackableScannerApp() {

    val context = LocalContext.current

    val repository = remember {
        TrackableRepository(
            TrackableDatabase.getDatabase(context).trackableDao()
        )
    }

    val trackables by repository.allTrackables.collectAsState(
        initial = emptyList()
    )

    val coroutineScope = rememberCoroutineScope()

    // Kamera
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanning by remember { mutableStateOf(false) }

    // Ruční zadání
    var showManualDialog by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }

    // Zprávy
    var message by remember { mutableStateOf("") }

    // Geocaching.com přihlášení
    var geocachingCookie by remember {
        mutableStateOf<String?>(null)
    }

    var geocachingLoginMessage by remember {
        mutableStateOf("Nejsi přihlášen ke Geocaching.com.")
    }

    var csrfToken by remember {
        mutableStateOf<String?>(null)
    }

    // Detail trackable
    var lookupResult by remember {
        mutableStateOf<TrackableLookupResult?>(null)
    }

    var lookupLoading by remember {
        mutableStateOf(false)
    }

    var lookupMessage by remember {
        mutableStateOf("")
    }

    // Hromadné logování
    var showBulkLogDialog by remember {
        mutableStateOf(false)
    }

    var showBulkLogConfirmDialog by remember {
        mutableStateOf(false)
    }

    var bulkLogText by remember {
        mutableStateOf("")
    }

    var bulkLogDate by remember {
        mutableStateOf(LocalDate.now().toString())
    }

    var bulkLogSubmitting by remember {
        mutableStateOf(false)
    }

    var bulkLogProgress by remember {
        mutableIntStateOf(0)
    }

    var bulkLogResults by remember {
        mutableStateOf(emptyList<String>())
    }

    var bulkLogMessage by remember {
        mutableStateOf("")
    }

    val lookupInProgress = remember {
        AtomicBoolean(false)
    }

    // Přihlášení ke Geocaching.com
    val geocachingLoginLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == android.app.Activity.RESULT_OK) {

                val cookie = result.data?.getStringExtra(
                    GeocachingLoginActivity.EXTRA_COOKIE
                )

                if (!cookie.isNullOrBlank()) {
                    geocachingCookie = cookie
                    csrfToken = null

                    geocachingLoginMessage =
                        "Přihlašovací cookies byly získány."
                } else {
                    geocachingLoginMessage =
                        "Nepodařilo se získat přihlašovací cookies."
                }
            }
        }

    // Povolení kamery
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted
            scanning = granted

            if (!granted) {
                message = "Přístup ke kameře nebyl povolen."
            }
        }

    // Vyhledání trackable
    fun lookupTrackable(code: String) {

        val cookie = geocachingCookie

        if (cookie.isNullOrBlank()) {
            lookupMessage =
                "Nejdříve se přihlas ke Geocaching.com."
            return
        }

        val normalizedCode = code.trim().uppercase(Locale.ROOT)

        if (!normalizedCode.matches(Regex("[A-Z0-9]{6}"))) {
            lookupMessage = "Neplatný tracking kód."
            return
        }

        coroutineScope.launch {

            lookupLoading = true
            lookupResult = null
            lookupMessage = "Vyhledávám trackable $normalizedCode..."

            try {

                val result = TrackableLookup.findTrackable(
                    trackingCode = normalizedCode,
                    cookie = cookie
                )

                lookupResult = result
                lookupMessage = "Trackable byl úspěšně nalezen."

            } catch (e: Exception) {

                lookupResult = null

                lookupMessage =
                    "Vyhledávání selhalo: ${e.message ?: "Neznámá chyba."}"

            } finally {
                lookupLoading = false
            }
        }
    }

    // Přidání trackable do databáze


    fun addTrackable(code: String) {

        val normalizedCode = code.trim().uppercase(Locale.ROOT)

        if (!normalizedCode.matches(Regex("[A-Z0-9]{6}"))) {
            message = "Neplatný formát tracking kódu."
            return
        }

        val cookie = geocachingCookie

        if (cookie.isNullOrBlank()) {
            message = "Pro ověření existence TB se nejdříve přihlas ke Geocaching.com."
            return
        }

        // Okamžitá ochrana proti opakovanému volání.
        if (!lookupInProgress.compareAndSet(false, true)) {
            return
        }

        coroutineScope.launch {

            lookupLoading = true
            lookupResult = null
            lookupMessage = "Ověřuji tracking kód $normalizedCode..."

            try {

                val result = TrackableLookup.findTrackable(
                    trackingCode = normalizedCode,
                    cookie = cookie
                )

                val added = repository.addTrackable(normalizedCode)

                lookupResult = result

                if (added) {
                    message = "Trackable ${result.tbCode} byl úspěšně přidán."
                } else {
                    message = "Trackable ${result.tbCode} už v seznamu existuje."
                }

                lookupMessage = "Trackable byl úspěšně ověřen."

            } catch (e: Exception) {

                lookupResult = null

                lookupMessage =
                    "Trackable $normalizedCode se nepodařilo ověřit: " +
                            (e.message ?: "Neznámá chyba.")

                message = "Kód nebyl přidán do seznamu."

            } finally {

                lookupLoading = false
                lookupInProgress.set(false)
            }
        }
    }



    // Hromadné odeslání logů
    fun submitBulkLogs() {

        val cookie = geocachingCookie

        if (cookie.isNullOrBlank()) {
            bulkLogMessage =
                "Nejsi přihlášen ke Geocaching.com."
            return
        }

        // Zachytíme aktuální seznam kódů.
        val codes = trackables.map { it.code }

        if (codes.isEmpty()) {
            bulkLogMessage =
                "Seznam neobsahuje žádné trackables."
            return
        }

        val parsedDate = try {
            LocalDate.parse(bulkLogDate)
        } catch (_: DateTimeParseException) {
            bulkLogMessage = "Neplatné datum."
            return
        }

        if (bulkLogText.isBlank()) {
            bulkLogMessage = "Text logu nesmí být prázdný."
            return
        }

        if (bulkLogSubmitting) {
            return
        }

        coroutineScope.launch {

            bulkLogSubmitting = true
            bulkLogProgress = 0
            bulkLogResults = emptyList()
            bulkLogMessage = "Připravuji hromadné logování..."

            var successful = 0
            var failed = 0

            try {

                // Získání CSRF tokenu
                val token = csrfToken
                    ?: GeocachingApi.getCsrfToken(cookie)

                csrfToken = token

                // Postupné zpracování všech TB
                codes.forEachIndexed { index, code ->

                    bulkLogMessage =
                        "Zpracovávám ${index + 1} z ${codes.size}: $code"

                    try {

                        // Vyhledání TB identifikátoru
                        val result = TrackableLookup.findTrackable(
                            trackingCode = code,
                            cookie = cookie
                        )

                        // Odeslání logu
                        TrackableLog.submitDiscoveredLog(
                            cookie = cookie,
                            csrfToken = token,
                            referenceCode = result.tbCode,
                            trackingCode = result.trackingCode,
                            logText = bulkLogText,
                            logDate = parsedDate
                        )

                        // Odstranění úspěšně zalogovaného TB z databáze
                        repository.deleteTrackable(code)

                        successful++

                        bulkLogResults = bulkLogResults +
                                "✓ ${result.tbCode} – ${result.name} (odstraněno ze seznamu)"

                    } catch (e: Exception) {

                        failed++

                        bulkLogResults = bulkLogResults +
                                "✗ $code – ${e.message ?: "Neznámá chyba"}"
                    }

                    bulkLogProgress = index + 1

                    // Pauza mezi požadavky
                    if (index < codes.lastIndex) {
                        delay(4000)
                    }
                }

                bulkLogMessage =
                    "Dokončeno. Úspěšně: $successful, neúspěšně: $failed."

            } catch (e: Exception) {

                bulkLogMessage =
                    "Hromadné logování se nepodařilo spustit: " +
                            (e.message ?: "Neznámá chyba.")

            } finally {
                bulkLogSubmitting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Trackable Scanner")
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteAll()
                                message = "Seznam byl vymazán."
                                lookupResult = null
                            }
                        },
                        enabled = trackables.isNotEmpty() &&
                                !bulkLogSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Vymazat seznam"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "Naskenované trackables: ${trackables.size}",
                style = MaterialTheme.typography.titleMedium
            )

            // Přihlášení
            Button(
                onClick = {
                    val intent = Intent(
                        context,
                        GeocachingLoginActivity::class.java
                    )

                    geocachingLoginLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !bulkLogSubmitting
            ) {
                Text("Přihlásit ke Geocaching.com")
            }

            // Ověření spojení
            OutlinedButton(
                onClick = {

                    val cookie = geocachingCookie

                    if (cookie.isNullOrBlank()) {
                        geocachingLoginMessage =
                            "Nejdříve se přihlas ke Geocaching.com."
                        return@OutlinedButton
                    }

                    coroutineScope.launch {

                        geocachingLoginMessage =
                            "Ověřuji spojení s Geocaching.com..."

                        try {

                            val token =
                                GeocachingApi.getCsrfToken(cookie)

                            csrfToken = token

                            geocachingLoginMessage =
                                "CSRF token úspěšně získán."

                        } catch (e: Exception) {

                            csrfToken = null

                            geocachingLoginMessage =
                                "Ověření se nezdařilo: " +
                                        (e.message ?: "Neznámá chyba.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = geocachingCookie != null &&
                        !bulkLogSubmitting
            ) {
                Text("Ověřit spojení")
            }

            Text(
                text = geocachingLoginMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Kamera
            if (scanning && cameraPermissionGranted) {

                CameraScanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    onCodeDetected = { code ->
                        addTrackable(code)
                    }
                )

                Text(
                    text = "Umístěte kód dovnitř zeleného rámečku.",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        scanning = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zastavit skenování")
                }

            } else {

                Button(
                    onClick = {
                        if (cameraPermissionGranted) {
                            scanning = true
                        } else {
                            permissionLauncher.launch(
                                Manifest.permission.CAMERA
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !bulkLogSubmitting
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Spustit skener")
                }
            }

            // Ruční zadání
            OutlinedButton(
                onClick = {
                    manualCode = ""
                    showManualDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !bulkLogSubmitting
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null
                )

                Spacer(Modifier.width(8.dp))

                Text("Zadat kód ručně")
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Výsledek vyhledávání
            if (lookupLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (lookupMessage.isNotBlank()) {
                Text(
                    text = lookupMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            lookupResult?.let { result ->

                TrackableDetailCard(
                    result = result
                )
            }

            HorizontalDivider()

            Text(
                text = "Seznam kódů",
                style = MaterialTheme.typography.titleMedium
            )

            // Hromadné logování
            Button(
                onClick = {
                    bulkLogText = ""
                    bulkLogDate = LocalDate.now().toString()
                    bulkLogMessage = ""
                    bulkLogResults = emptyList()
                    bulkLogProgress = 0
                    showBulkLogDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = trackables.isNotEmpty() &&
                        !bulkLogSubmitting &&
                        geocachingCookie != null
            ) {
                Text(
                    "Hromadně logovat všechny TB (${trackables.size})"
                )
            }

            // Seznam uložených kódů
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
                                        lookupTrackable(trackable.code)
                                    },
                                    enabled = !lookupLoading &&
                                            geocachingCookie != null &&
                                            !bulkLogSubmitting
                                ) {
                                    Text("Detail")
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.deleteTrackable(
                                                trackable.code
                                            )
                                        }
                                    },
                                    enabled = !bulkLogSubmitting
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

    // Dialog pro ruční zadání
    if (showManualDialog) {

        AlertDialog(
            onDismissRequest = {
                showManualDialog = false
            },
            title = {
                Text("Ruční zadání kódu")
            },
            text = {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = {
                        manualCode = it.uppercase(Locale.ROOT)
                    },
                    label = {
                        Text("Tracking code")
                    },
                    placeholder = {
                        Text("Např. ABC123")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            addTrackable(manualCode)
                            showManualDialog = false
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addTrackable(manualCode)
                        showManualDialog = false
                    }
                ) {
                    Text("Přidat")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showManualDialog = false
                    }
                ) {
                    Text("Zrušit")
                }
            }
        )
    }

    // Dialog pro hromadné logování
    if (showBulkLogDialog) {

        AlertDialog(
            onDismissRequest = {
                if (!bulkLogSubmitting) {
                    showBulkLogDialog = false
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
                        "Log bude odeslán všem ${trackables.size} uloženým TB."
                    )

                    OutlinedTextField(
                        value = bulkLogDate,
                        onValueChange = {
                            bulkLogDate = it
                        },
                        label = {
                            Text("Datum (RRRR-MM-DD)")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !bulkLogSubmitting
                    )

                    OutlinedTextField(
                        value = bulkLogText,
                        onValueChange = {
                            bulkLogText = it
                        },
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
                        enabled = !bulkLogSubmitting
                    )

                    Text(
                        "Typ logu: Discovered It",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (bulkLogSubmitting) {

                        LinearProgressIndicator(
                            progress = {
                                if (trackables.isEmpty()) 0f
                                else bulkLogProgress.toFloat() /
                                        trackables.size
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "$bulkLogProgress / ${trackables.size} TB"
                        )
                    }

                    if (bulkLogMessage.isNotBlank()) {
                        Text(
                            text = bulkLogMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Výsledky jednotlivých logů
                    if (bulkLogResults.isNotEmpty()) {

                        HorizontalDivider()

                        Text(
                            "Výsledky",
                            style = MaterialTheme.typography.titleSmall
                        )

                        bulkLogResults.forEach { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {

                if (!bulkLogSubmitting &&
                    bulkLogProgress == 0
                ) {

                    TextButton(
                        onClick = {

                            try {
                                LocalDate.parse(bulkLogDate)

                                if (bulkLogText.isBlank()) {
                                    bulkLogMessage =
                                        "Vyplň text logu."
                                } else {
                                    showBulkLogConfirmDialog = true
                                }

                            } catch (_: DateTimeParseException) {
                                bulkLogMessage =
                                    "Zadej platné datum ve formátu RRRR-MM-DD."
                            }
                        }
                    ) {
                        Text("Pokračovat")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!bulkLogSubmitting) {
                            showBulkLogDialog = false
                        }
                    },
                    enabled = !bulkLogSubmitting
                ) {
                    Text(
                        if (bulkLogProgress > 0) "Zavřít" else "Zrušit"
                    )
                }
            }
        )
    }

    // Potvrzení hromadného odeslání
    if (showBulkLogConfirmDialog) {

        AlertDialog(
            onDismissRequest = {
                showBulkLogConfirmDialog = false
            },
            title = {
                Text("Odeslat log všem TB?")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        "Počet trackables: ${trackables.size}"
                    )

                    Text("Datum: $bulkLogDate")
                    Text("Typ: Discovered It")

                    HorizontalDivider()

                    Text("Společný text:")
                    Text(bulkLogText)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkLogConfirmDialog = false
                        submitBulkLogs()
                    }
                ) {
                    Text("Odeslat všem")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBulkLogConfirmDialog = false
                    }
                ) {
                    Text("Zpět")
                }
            }
        )
    }
}

@Composable
private fun TrackableDetailCard(
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

            DetailRow(
                label = "Tracking kód",
                value = result.trackingCode
            )

            DetailRow(
                label = "TB identifikátor",
                value = result.tbCode
            )

            DetailRow(
                label = "Vlastník",
                value = result.owner
            )

            DetailRow(
                label = "Typ",
                value = result.trackableType
            )

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
