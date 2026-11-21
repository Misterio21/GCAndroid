
package com.example.trackablescanner.geocaching

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object TrackableLog {

    private const val BASE_URL = "https://www.geocaching.com"

    private const val LOG_URL =
        "$BASE_URL/api/live/v1/trpc/web.logs.createTrackableLog"

    private const val LOG_TYPE_DISCOVERED_IT = 48

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun submitDiscoveredLog(
        cookie: String,
        csrfToken: String,
        referenceCode: String,
        trackingCode: String,
        logText: String,
        logDate: LocalDate = LocalDate.now()
    ): String = withContext(Dispatchers.IO) {

        if (cookie.isBlank()) {
            throw IOException("Chybí přihlašovací cookies.")
        }

        if (csrfToken.isBlank()) {
            throw IOException("Chybí CSRF token.")
        }

        if (!referenceCode.startsWith("TB")) {
            throw IOException("Neplatný TB identifikátor.")
        }

        if (!trackingCode.matches(Regex("[A-Z0-9]{6}")) &&
            !trackingCode.matches(Regex("[a-zA-Z0-9]{6}"))
        ) {
            throw IOException("Neplatný tracking kód.")
        }

        if (logText.isBlank()) {
            throw IOException("Text logu nesmí být prázdný.")
        }

        // Datum v poledne UTC, stejně jako v Python implementaci
        val formattedDate = logDate
            .atTime(12, 0)
            .atZone(java.time.ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            ))

        // Sestavení JSON payloadu
        val bodyJson = JSONObject().apply {

            put("referenceCode", referenceCode)

            put("body", JSONObject().apply {
                put("cannotDelete", false)
                put("images", JSONArray())
                put("logDate", formattedDate)
                put("logText", logText)
                put("logType", LOG_TYPE_DISCOVERED_IT)
                put("trackingCode", trackingCode.uppercase())
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        val requestBody = bodyJson
            .toString()
            .toRequestBody(mediaType)

        val request = Request.Builder()
            .url(LOG_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .header("Origin", BASE_URL)
            .header(
                "Referer",
                "$BASE_URL/live/trackable/$referenceCode/log"
            )
            .header("Cookie", cookie)
            .header("csrf-token", csrfToken)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->

            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException(
                    "Geocaching.com vrátil HTTP ${response.code}: " +
                            responseBody.take(500)
                )
            }

            if (responseBody.isBlank()) {
                throw IOException("Server vrátil prázdnou odpověď.")
            }

            // Kontrola případné tRPC chyby
            try {
                val json = JSONObject(responseBody)

                if (json.has("error")) {
                    val error = json.optJSONObject("error")
                    val message = error
                        ?.optJSONObject("json")
                        ?.optString("message")
                        ?: "Server odmítl vytvoření logu."

                    throw IOException(message)
                }
            } catch (e: IOException) {
                throw e
            } catch (_: Exception) {
                // Odpověď nemusí být ve formátu JSON.
            }

            "Požadavek na vytvoření logu byl úspěšně přijat."
        }
    }
}
