
package com.example.trackablescanner.geocaching

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class GeocachingLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView

    companion object {
        const val EXTRA_COOKIE = "geocaching_cookie"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createLayout()
        configureWebView()

        webView.loadUrl("https://www.geocaching.com/")
    }

    private fun createLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        statusText = TextView(this).apply {
            text = "Přihlas se ke svému účtu Geocaching.com."
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }

        webView = WebView(this)

        val confirmButton = Button(this).apply {
            text = "Použít přihlášení"

            setOnClickListener {
                finishLogin()
            }
        }

        layout.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        layout.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        layout.addView(
            confirmButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                statusText.text =
                    "Stránka načtena. Přihlas se a potom stiskni tlačítko."
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun finishLogin() {
        val cookieManager = CookieManager.getInstance()

        cookieManager.flush()

        val cookies = cookieManager.getCookie(
            "https://www.geocaching.com"
        )

        if (cookies.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Nepodařilo se získat cookies.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (!cookies.contains("gspkauth=")) {
            Toast.makeText(
                this,
                "Nenašel jsem přihlašovací cookie. " +
                        "Zkontroluj, zda jsi přihlášen.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val result = intent.apply {
            putExtra(EXTRA_COOKIE, cookies)
        }

        setResult(Activity.RESULT_OK, result)
        finish()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }
}
