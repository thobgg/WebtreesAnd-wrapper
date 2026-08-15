package de.bgghome.webtrees

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var container: FrameLayout

    /** Die Ansicht, die das WebView fürs Vollbild anliefert; null heißt: kein Vollbild. */
    private var vollbildAnsicht: View? = null
    private var vollbildRueckruf: WebChromeClient.CustomViewCallback? = null

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

        // Status- und Navigationsleiste bleiben aus. Der Browser kann Leisten
        // nur über die Vollbild-API loswerden und quittiert das jedes Mal mit
        // einem Hinweis; hier ist es einfach der Normalzustand, wie in jeder
        // Bildbetrachter-App. Ein Wischen vom Rand holt sie kurz zurück.
        systemleisten(sichtbar = false)

        // Nur im Debug-Build: macht die App über adb als Inspektionsziel sichtbar.
        // Gelesen wird das Flag aus der Installation, nicht aus BuildConfig — die
        // Klasse wird erst erzeugt, wenn man buildFeatures.buildConfig einschaltet.
        val debuggierbar = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggierbar)

        container = FrameLayout(this)

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

            // Ohne WebChromeClient läuft die Vollbild-Anfrage einer Seite ins Leere.
            // Fotogalerien und Videos brauchen sie, um die Leisten loszuwerden.
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (vollbildAnsicht != null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    vollbildAnsicht = view
                    vollbildRueckruf = callback
                    webView.visibility = View.GONE
                    container.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    // Im Vollbild gibt es nichts zu umpolstern, die Leisten sind weg.
                    container.setPadding(0, 0, 0, 0)
                    systemleisten(sichtbar = false)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                override fun onHideCustomView() = beendeVollbild()
            }

            // PDFs und andere Downloads an externen Viewer weitergeben
            setDownloadListener { url, _, _, _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        container.addView(webView)
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val types = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            val insets = if (vollbildAnsicht == null) {
                windowInsets.getInsets(types)
            } else {
                Insets.NONE
            }
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(windowInsets)
                .setInsets(types, Insets.NONE)
                .build()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // Im Vollbild ist die Zurück-Taste der Ausgang aus dem Vollbild,
                    // nicht aus der Seite.
                    vollbildAnsicht != null -> beendeVollbild()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
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
     * Nach jedem Rückkehren in den Vordergrund erneut ausblenden: Android zeigt
     * die Leisten unter anderem nach einem Dialog, einem App-Wechsel oder dem
     * Entsperren wieder an, und sie blieben sonst stehen.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) systemleisten(sichtbar = false)
    }

    /**
     * Räumt das Vollbild ab: Ansicht raus, WebView zurück, Leisten bleiben aus.
     * Wird sowohl vom WebView (Seite verlässt das Vollbild) als auch von der
     * Zurück-Taste aufgerufen und verträgt beides mehrfach.
     */
    private fun beendeVollbild() {
        val ansicht = vollbildAnsicht ?: return
        container.removeView(ansicht)
        vollbildAnsicht = null
        webView.visibility = View.VISIBLE
        vollbildRueckruf?.onCustomViewHidden()
        vollbildRueckruf = null
        // Ausgeblendet bleiben die Leisten ohnehin - nur der wache Bildschirm
        // war eine Zugabe des Vollbilds.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ViewCompat.requestApplyInsets(container)
    }

    /**
     * Status- und Navigationsleiste ein- oder ausblenden. Ausgeblendet kommen sie
     * auf ein Wischen vom Rand kurz zurück und verschwinden wieder von selbst.
     * Ausgeblendet ist der Normalzustand der App; sichtbar wird nur gebraucht,
     * falls das jemand später wieder zur Wahl stellen will.
     */
    private fun systemleisten(sichtbar: Boolean) {
        val steuerung = WindowCompat.getInsetsController(window, window.decorView)
        steuerung.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (sichtbar) {
            steuerung.show(WindowInsetsCompat.Type.systemBars())
        } else {
            steuerung.hide(WindowInsetsCompat.Type.systemBars())
        }
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
