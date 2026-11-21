
package com.example.trackablescanner.ui.viewmodel

import com.example.trackablescanner.data.TrackableEntity
import com.example.trackablescanner.geocaching.TrackableLookupResult

data class TrackableScannerUiState(

    val trackables: List<TrackableEntity> = emptyList(),

    // Připojení ke Geocaching.com
    val cookie: String? = null,
    val csrfToken: String? = null,
    val isLoggedIn: Boolean = false,
    val loginMessage: String = "",
    val connectionMessage: String = "",
    val isCheckingConnection: Boolean = false,

    // Vyhledávání trackable
    val lookupResult: TrackableLookupResult? = null,
    val isLookingUp: Boolean = false,
    val lookupMessage: String = "",

    // Obecné zprávy
    val message: String = "",

    // Hromadné logování
    val showBulkLogDialog: Boolean = false,
    val showBulkLogConfirmDialog: Boolean = false,
    val bulkLogDate: String = "",
    val bulkLogText: String = "",
    val bulkLogSubmitting: Boolean = false,
    val bulkLogProgress: Int = 0,
    val bulkLogMessage: String = "",
    val bulkLogResults: List<String> = emptyList()
)
