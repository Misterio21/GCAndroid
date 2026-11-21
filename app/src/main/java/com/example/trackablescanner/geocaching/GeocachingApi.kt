
package com.example.trackablescanner.geocaching

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeocachingApi {

    private const val BASE_URL =
        "https://www.geocaching.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getCsrfToken(cookie: String): String =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("$BASE_URL/api/auth/csrf")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", cookie)
                .get()
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    throw IOException(
                        "Geocaching.com vrátil HTTP ${response.code}"
                    )
                }

                val responseBody = response.body?.string()
                    ?: throw IOException("Prázdná odpověď serveru.")

                val json = JSONObject(responseBody)

                val token = json.optString("csrfToken")

                if (token.isBlank()) {
                    throw IOException(
                        "V odpovědi chybí CSRF token."
                    )
                }

                token
            }
        }
}
