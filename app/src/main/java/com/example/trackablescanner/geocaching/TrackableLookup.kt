
package com.example.trackablescanner.geocaching

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URL

data class TrackableLookupResult(
    val trackingCode: String,
    val tbCode: String,
    val name: String,
    val owner: String,
    val trackableType: String,
    val goal: String,
    val description: String
)

object TrackableLookup {

    private const val BASE_URL = "https://www.geocaching.com"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun findTrackable(
        trackingCode: String,
        cookie: String
    ): TrackableLookupResult = withContext(Dispatchers.IO) {

        val normalizedCode = trackingCode.trim().uppercase()

        if (!normalizedCode.matches(Regex("[A-Z0-9]{6}"))) {
            throw IOException("Tracking kód musí mít přesně 6 znaků.")
        }

        if (cookie.isBlank()) {
            throw IOException("Chybí přihlašovací cookies.")
        }

        val pageUrl =
            "$BASE_URL/track/details.aspx?tracker=$normalizedCode"

        // 1. Načtení vyhledávacího formuláře
        val getRequest = Request.Builder()
            .url(pageUrl)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "text/html")
            .get()
            .build()

        val html = client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Chyba při načítání formuláře: HTTP ${response.code}")
            }

            response.body?.string()
                ?: throw IOException("Prázdná odpověď serveru.")
        }

        val document = Jsoup.parse(html, pageUrl)

        val form = document.selectFirst("form")
            ?: throw IOException("Vyhledávací formulář nebyl nalezen.")

        // 2. Načtení skrytých ASP.NET polí
        val fields = linkedMapOf<String, String>()

        for (input in form.select("input[type=hidden][name]")) {
            fields[input.attr("name")] = input.attr("value")
        }

        // 3. Doplnění tracking kódu
        fields["ctl00\$ContentBody\$txtTrackingNumber"] = normalizedCode
        fields["ctl00\$ContentBody\$btnLookupCode"] = "Track"

        val action = form.attr("action")
        val postUrl = if (action.isBlank()) {
            pageUrl
        } else {
            URL(URL(pageUrl), action).toString()
        }

        val formBody = FormBody.Builder().apply {
            fields.forEach { (name, value) ->
                add(name, value)
            }
        }.build()

        // 4. Odeslání formuláře
        val postRequest = Request.Builder()
            .url(postUrl)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "text/html")
            .header("Referer", pageUrl)
            .post(formBody)
            .build()

        val resultHtml = client.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Vyhledávání selhalo: HTTP ${response.code}")
            }

            response.body?.string()
                ?: throw IOException("Prázdná odpověď serveru.")
        }

        // 5. Získání TB identifikátoru
        val resultDocument = Jsoup.parse(resultHtml)

        val identifier = resultDocument
            .selectFirst("span.CoordInfoCode")
            ?.text()
            ?.trim()
            .orEmpty()

        if (!identifier.startsWith("TB")) {
            throw IOException(
                "Trackable nebyl nalezen nebo server nevrátil jeho identifikátor."
            )
        }

        // 6. Načtení detailů trackable
        val detailsUrl =
            "$BASE_URL/track/details.aspx?tracker=$identifier"

        val detailsRequest = Request.Builder()
            .url(detailsUrl)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "text/html")
            .get()
            .build()

        val detailsHtml = client.newCall(detailsRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Načtení detailů selhalo: HTTP ${response.code}")
            }

            response.body?.string()
                ?: throw IOException("Prázdná odpověď detailů.")
        }

        val detailsDocument = Jsoup.parse(detailsHtml)

        // 7. Extrakce údajů podle pycaching
        fun getText(id: String): String {
            return detailsDocument.getElementById(id)
                ?.text()
                ?.trim()
                ?: ""
        }

        val name = getText("ctl00_ContentBody_lbHeading")
        val owner = getText("ctl00_ContentBody_BugDetails_BugOwner")
        val type = detailsDocument
            .getElementById("ctl00_ContentBody_BugTypeImage")
            ?.attr("alt")
            ?.trim()
            .orEmpty()

        val goal = getText("TrackableGoal")
        val description = getText("TrackableDetails")

        if (name.isBlank()) {
            throw IOException("Nepodařilo se načíst název trackable.")
        }

        TrackableLookupResult(
            trackingCode = normalizedCode,
            tbCode = identifier,
            name = name,
            owner = owner,
            trackableType = type,
            goal = goal,
            description = description
        )
    }
}
