
package com.example.trackablescanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackablescanner.data.TrackableDatabase
import com.example.trackablescanner.data.TrackableRepository
import com.example.trackablescanner.geocaching.GeocachingApi
import com.example.trackablescanner.geocaching.TrackableLog
import com.example.trackablescanner.geocaching.TrackableLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TrackableScannerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: TrackableRepository

    private val _uiState = MutableStateFlow(TrackableScannerUiState())
    val uiState: StateFlow<TrackableScannerUiState> = _uiState.asStateFlow()

    private val lookupInProgress = AtomicBoolean(false)

    init {
        val dao = TrackableDatabase
            .getDatabase(application)
            .trackableDao()

        repository = TrackableRepository(dao)

        observeTrackables()
    }

    private fun updateState(
        update: TrackableScannerUiState.() -> TrackableScannerUiState
    ) {
        _uiState.value = _uiState.value.update()
    }

    private fun observeTrackables() {
        viewModelScope.launch {
            repository.allTrackables.collectLatest { trackables ->
                updateState {
                    copy(trackables = trackables)
                }
            }
        }
    }

    // --------------------------------------------------
    // PŘIHLÁŠENÍ
    // --------------------------------------------------

    fun setCookie(cookie: String) {
        updateState {
            copy(
                cookie = cookie,
                isLoggedIn = cookie.isNotBlank(),
                loginMessage = if (cookie.isNotBlank()) {
                    "Přihlášení proběhlo."
                } else {
                    "Uživatel není přihlášen."
                },
                csrfToken = null
            )
        }
    }

    fun logout() {
        updateState {
            copy(
                cookie = null,
                csrfToken = null,
                isLoggedIn = false,
                loginMessage = "Byl jsi odhlášen.",
                connectionMessage = ""
            )
        }
    }

    // --------------------------------------------------
    // SMAZÁNÍ VŠECH TRACKABLES
    // --------------------------------------------------

    fun deleteAllTrackables() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteAll()
                }

                updateState {
                    copy(
                        message = "Seznam byl vymazán.",
                        lookupResult = null
                    )
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        message = "Chyba při mazání seznamu: ${e.message}"
                    )
                }
            }
        }
    }

    // --------------------------------------------------
    // OVĚŘENÍ PŘIPOJENÍ
    // --------------------------------------------------

    fun verifyConnection() {
        val cookie = _uiState.value.cookie

        if (cookie.isNullOrBlank()) {
            updateState {
                copy(
                    connectionMessage = "Nejsi přihlášen.",
                    isCheckingConnection = false
                )
            }
            return
        }

        viewModelScope.launch {
            updateState {
                copy(
                    isCheckingConnection = true,
                    connectionMessage = ""
                )
            }

            try {
                val token = withContext(Dispatchers.IO) {
                    GeocachingApi.getCsrfToken(cookie)
                }

                updateState {
                    copy(
                        csrfToken = token,
                        connectionMessage = if (token.isNotBlank()) {
                            "Připojení ke Geocaching.com je funkční."
                        } else {
                            "Nepodařilo se získat CSRF token."
                        }
                    )
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        csrfToken = null,
                        connectionMessage = "Chyba připojení: ${e.message}"
                    )
                }
            } finally {
                updateState {
                    copy(isCheckingConnection = false)
                }
            }
        }
    }

    // --------------------------------------------------
    // VYHLEDÁNÍ TRACKABLE
    // --------------------------------------------------

    fun lookupTrackable(code: String) {
        val normalizedCode = code.trim().uppercase(Locale.ROOT)

        if (!normalizedCode.matches(Regex("[A-Z0-9]{6}"))) {
            updateState {
                copy(lookupMessage = "Neplatný tracking code.")
            }
            return
        }

        val cookie = _uiState.value.cookie

        if (cookie.isNullOrBlank()) {
            updateState {
                copy(lookupMessage = "Nejdříve se přihlas.")
            }
            return
        }

        if (!lookupInProgress.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch {
            updateState {
                copy(
                    isLookingUp = true,
                    lookupMessage = "",
                    lookupResult = null
                )
            }

            try {
                val result = TrackableLookup.findTrackable(
                    trackingCode = normalizedCode,
                    cookie = cookie
                )

                updateState {
                    copy(
                        lookupResult = result,
                        lookupMessage = ""
                    )
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        lookupMessage = "Chyba při vyhledávání: ${e.message}"
                    )
                }
            } finally {
                lookupInProgress.set(false)

                updateState {
                    copy(isLookingUp = false)
                }
            }
        }
    }

    // --------------------------------------------------
    // PŘIDÁNÍ TRACKABLE
    // --------------------------------------------------

    fun addTrackable(code: String) {
        val normalizedCode = code.trim().uppercase(Locale.ROOT)

        if (!normalizedCode.matches(Regex("[A-Z0-9]{6}"))) {
            updateState {
                copy(message = "Neplatný tracking code.")
            }
            return
        }

        val cookie = _uiState.value.cookie

        if (cookie.isNullOrBlank()) {
            updateState {
                copy(message = "Nejdříve se přihlas ke Geocaching.com.")
            }
            return
        }

        viewModelScope.launch {
            updateState {
                copy(message = "Ověřuji trackable $normalizedCode...")
            }

            try {
                // Nejdříve ověříme, zda trackable skutečně existuje.
                TrackableLookup.findTrackable(
                    trackingCode = normalizedCode,
                    cookie = cookie
                )

                // Teprve potom jej uložíme do databáze.
                val added = withContext(Dispatchers.IO) {
                    repository.addTrackable(normalizedCode)
                }

                updateState {
                    copy(
                        message = if (added) {
                            "Trackable $normalizedCode byl přidán."
                        } else {
                            "Trackable $normalizedCode už v seznamu existuje."
                        },
                        lookupResult = null
                    )
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        message = "Trackable $normalizedCode nebyl přidán: ${e.message}"
                    )
                }
            }
        }
    }

    // --------------------------------------------------
    // SMAZÁNÍ JEDNOHO TRACKABLE
    // --------------------------------------------------

    fun removeTrackable(code: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteTrackable(code)
                }

                updateState {
                    copy(message = "Trackable $code byl odstraněn.")
                }
            } catch (e: Exception) {
                updateState {
                    copy(message = "Chyba při mazání: ${e.message}")
                }
            }
        }
    }

    // --------------------------------------------------
    // OBECNÉ ZPRÁVY
    // --------------------------------------------------

    fun clearMessage() {
        updateState {
            copy(message = "")
        }
    }

    fun clearLookupResult() {
        updateState {
            copy(
                lookupResult = null,
                lookupMessage = ""
            )
        }
    }

    // --------------------------------------------------
    // HROMADNÉ LOGOVÁNÍ
    // --------------------------------------------------

    fun openBulkLogDialog() {
        updateState {
            copy(
                showBulkLogDialog = true,
                bulkLogDate = LocalDate.now().toString(),
                bulkLogMessage = "",
                bulkLogResults = emptyList(),
                bulkLogProgress = 0
            )
        }
    }

    fun closeBulkLogDialog() {
        if (_uiState.value.bulkLogSubmitting) return

        updateState {
            copy(showBulkLogDialog = false)
        }
    }

    fun updateBulkLogDate(date: String) {
        updateState {
            copy(bulkLogDate = date)
        }
    }

    fun updateBulkLogText(text: String) {
        updateState {
            copy(bulkLogText = text)
        }
    }

    fun validateBulkLog() {
        val state = _uiState.value

        try {
            LocalDate.parse(state.bulkLogDate)

            if (state.bulkLogText.isBlank()) {
                updateState {
                    copy(bulkLogMessage = "Vyplň text logu.")
                }
                return
            }

            if (state.trackables.isEmpty()) {
                updateState {
                    copy(bulkLogMessage = "Seznam trackables je prázdný.")
                }
                return
            }

            if (state.cookie.isNullOrBlank()) {
                updateState {
                    copy(bulkLogMessage = "Nejdříve se přihlas.")
                }
                return
            }

            updateState {
                copy(
                    bulkLogMessage = "",
                    showBulkLogConfirmDialog = true
                )
            }
        } catch (_: DateTimeParseException) {
            updateState {
                copy(
                    bulkLogMessage = "Zadej platné datum ve formátu RRRR-MM-DD."
                )
            }
        }
    }

    fun closeBulkLogConfirmDialog() {
        updateState {
            copy(showBulkLogConfirmDialog = false)
        }
    }

    fun submitBulkLogs() {
        val initialState = _uiState.value

        if (initialState.bulkLogSubmitting) return

        val cookie = initialState.cookie

        if (cookie.isNullOrBlank()) {
            updateState {
                copy(bulkLogMessage = "Nejdříve se přihlas.")
            }
            return
        }

        val trackables = initialState.trackables
        val logText = initialState.bulkLogText

        val logDate = try {
            LocalDate.parse(initialState.bulkLogDate)
        } catch (_: DateTimeParseException) {
            updateState {
                copy(bulkLogMessage = "Neplatné datum.")
            }
            return
        }

        if (logText.isBlank()) {
            updateState {
                copy(bulkLogMessage = "Text logu nesmí být prázdný.")
            }
            return
        }

        if (trackables.isEmpty()) {
            updateState {
                copy(bulkLogMessage = "Seznam trackables je prázdný.")
            }
            return
        }

        viewModelScope.launch {
            updateState {
                copy(
                    showBulkLogConfirmDialog = false,
                    bulkLogSubmitting = true,
                    bulkLogProgress = 0,
                    bulkLogMessage = "Zahajuji logování...",
                    bulkLogResults = emptyList()
                )
            }

            val results = mutableListOf<String>()

            try {
                // Získání CSRF tokenu.
                val token = _uiState.value.csrfToken
                    ?.takeIf { it.isNotBlank() }
                    ?: withContext(Dispatchers.IO) {
                        GeocachingApi.getCsrfToken(cookie)
                    }

                if (token.isBlank()) {
                    throw IllegalStateException(
                        "Nepodařilo se získat CSRF token."
                    )
                }

                updateState {
                    copy(csrfToken = token)
                }

                // Postupné zpracování všech trackables.
                for ((index, trackable) in trackables.withIndex()) {
                    val code = trackable.code

                    try {
                        updateState {
                            copy(
                                bulkLogMessage = "Vyhledávám $code..."
                            )
                        }

                        // Získání skutečného TB identifikátoru.
                        val lookupResult = TrackableLookup.findTrackable(
                            trackingCode = code,
                            cookie = cookie
                        )

                        updateState {
                            copy(
                                bulkLogMessage = "Odesílám log pro $code..."
                            )
                        }

                        // Odeslání logu.
                        TrackableLog.submitDiscoveredLog(
                            cookie = cookie,
                            csrfToken = token,
                            referenceCode = lookupResult.tbCode,
                            trackingCode = lookupResult.trackingCode,
                            logText = logText,
                            logDate = logDate
                        )

                        // Z databáze odstraníme pouze úspěšně zalogovaný TB.
                        withContext(Dispatchers.IO) {
                            repository.deleteTrackable(code)
                        }

                        results.add(
                            "✓ $code – úspěšně zalogováno"
                        )
                    } catch (e: Exception) {
                        // Při chybě zůstává trackable v databázi.
                        results.add(
                            "✗ $code – ${e.message ?: "Neznámá chyba"}"
                        )
                    }

                    updateState {
                        copy(
                            bulkLogProgress = index + 1,
                            bulkLogResults = results.toList()
                        )
                    }

                    // Čtyřsekundová pauza mezi jednotlivými logy.
                    if (index < trackables.lastIndex) {
                        delay(4000)
                    }
                }

                val successCount = results.count {
                    it.startsWith("✓")
                }

                val errorCount = results.size - successCount

                updateState {
                    copy(
                        bulkLogMessage = "Dokončeno. Úspěšně: $successCount, chyby: $errorCount."
                    )
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        bulkLogMessage = "Chyba hromadného logování: ${e.message}"
                    )
                }
            } finally {
                updateState {
                    copy(bulkLogSubmitting = false)
                }
            }
        }
    }
}
