package de.bgghome.webtrees

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val PREFS = "webtrees"
        private const val KEY_URL = "instance_url"
    }

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    val scheme = url.scheme ?: return false
                    if (scheme != "http" && scheme != "https") {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        return true
                    }
                    return false
                }

                // Nur der Hauptrahmen zählt: Ist die hinterlegte Adresse falsch oder
                // die Instanz nicht erreichbar, direkt wieder nach der Adresse fragen.
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        askForUrl(getString(R.string.url_unreachable))
                    }
                }
            }

            // PDFs und andere Downloads an externen Viewer weitergeben
            setDownloadListener { url, _, _, _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        val container = FrameLayout(this).apply {
            addView(webView)
        }
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val types = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            val insets = windowInsets.getInsets(types)
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(windowInsets)
                .setInsets(types, Insets.NONE)
                .build()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            return
        }

        val stored = prefs.getString(KEY_URL, null)
        if (stored.isNullOrBlank()) askForUrl(null) else webView.loadUrl(stored)
    }

    /**
     * Fragt nach der Adresse der webtrees-Instanz und merkt sie sich. Wird beim
     * ersten Start aufgerufen und erneut, wenn die gespeicherte Adresse nicht lädt.
     */
    private fun askForUrl(message: String?) {
        val input = EditText(this).apply {
            setHint(R.string.url_hint)
            setText(prefs.getString(KEY_URL, "").orEmpty())
            isSingleLine = true
        }
        val pad = (resources.displayMetrics.density * 20).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.url_prompt)
            .apply { if (message != null) setMessage(message) }
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = formatUrl(input.text.toString())
                if (url == null) {
                    askForUrl(getString(R.string.url_invalid))
                } else {
                    prefs.edit().putString(KEY_URL, url).apply()
                    webView.loadUrl(url)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (prefs.getString(KEY_URL, null).isNullOrBlank()) finish()
            }
            .show()
    }

    /**
     * Ergänzt ein fehlendes Schema, entfernt einen abschließenden Schrägstrich und
     * gibt null zurück, wenn daraus keine brauchbare Adresse wird.
     */
    private fun formatUrl(raw: String): String? {
        var candidate = raw.trim().trimEnd('/')
        if (candidate.isEmpty()) return null
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://$candidate"
        }
        val host = Uri.parse(candidate).host
        return if (host.isNullOrBlank() || !host.contains('.')) null else candidate
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}
