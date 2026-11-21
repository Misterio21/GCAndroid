
package com.example.trackablescanner.scanner

class ScanConfirmation(
    private val requiredConfirmations: Int = 2,
    private val resetAfterEmptyScans: Int = 5
) {

    private var candidateCode: String? = null
    private var candidateCount = 0
    private var lastAcceptedCode: String? = null
    private var emptyScanCount = 0

    fun observe(detectedCodes: Set<String>): String? {

        if (detectedCodes.size != 1) {
            emptyScanCount++

            candidateCode = null
            candidateCount = 0

            // Stejný kód povolíme znovu až po delší době,
            // kdy kamera žádný jednoznačný kód nevidí.
            if (emptyScanCount >= resetAfterEmptyScans) {
                lastAcceptedCode = null
            }

            return null
        }

        emptyScanCount = 0

        val detectedCode = detectedCodes.first()

        if (detectedCode == candidateCode) {
            candidateCount++
        } else {
            candidateCode = detectedCode
            candidateCount = 1
        }

        if (
            candidateCount >= requiredConfirmations &&
            lastAcceptedCode != detectedCode
        ) {
            lastAcceptedCode = detectedCode
            return detectedCode
        }

        return null
    }
}
