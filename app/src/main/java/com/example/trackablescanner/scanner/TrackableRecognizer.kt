
package com.example.trackablescanner.scanner

import com.google.mlkit.vision.text.Text
import java.util.Locale

class TrackableRecognizer {

    private val codeRegex =
        Regex("(?<![A-Z0-9])[A-Z0-9]{6}(?![A-Z0-9])")

    fun findCodes(
        result: Text
    ): Set<String> {

        val detectedCodes = mutableSetOf<String>()

        result.textBlocks.forEach { block ->

            block.lines.forEach { line ->

                val text = line.text
                    .uppercase(Locale.ROOT)
                    .replace(Regex("[\\s-]"), "")

                val matches = codeRegex.findAll(text)

                matches.forEach { match ->

                    val code = match.value

                    if (isValidFormat(code)) {
                        detectedCodes.add(code)
                    }
                }
            }
        }

        return detectedCodes
    }

    private fun isValidFormat(code: String): Boolean {

        if (code.length != 6) {
            return false
        }

        return code.all {
            it in 'A'..'Z' || it in '0'..'9'
        }
    }
}
