package com.carpoolr.app.connect

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.carpoolr.app.R
import com.carpoolr.app.ui.components.GlassIcon
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Full-screen sign-in on the provider's own mobile site, in its own activity so no liquid-glass
 * layers sit around the WebView. App links (uber://, intent://) stay on the web, popups open in
 * place, and "done" reports whether the site set login cookies.
 */
class WebLoginActivity : ComponentActivity() {

    private var web: WebView? = null
    private lateinit var provider: Provider
    private val main = Handler(Looper.getMainLooper())
    private var finished = false
    private var leftLoginPage = false       // uber: reached auth.uber.com at least once
    private var baseline: Set<String>? = null // ola: session-ish keys present before sign-in

    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
    }

    /** Polls the page for a signed-in session and closes on its own once it finds one. */
    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            web?.let { checkSignedIn(it) }
            main.postDelayed(this, 1200)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        provider = Provider.valueOf(intent.getStringExtra(EXTRA_PROVIDER) ?: run { finish(); return })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else { setResult(Activity.RESULT_CANCELED); finish() }
            }
        })

        setContent {
            var progress by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxSize().background(CarpoolrColors.Green)) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(backSource, 0.85f)
                            .clip(RoundedRectangle(20.dp))
                            .background(CarpoolrColors.Cream.copy(alpha = 0.12f))
                            .clickable(backSource, null) { setResult(Activity.RESULT_CANCELED); finish() }
                            .padding(8.dp)
                    ) { GlassIcon(R.drawable.ic_back, "back", size = 20) }
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        "sign in to ${provider.label}",
                        style = TextStyle(CarpoolrColors.Cream, 22.sp, fontFamily = Anton),
                        modifier = Modifier.weight(1f)
                    )
                    val doneSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(doneSource, 0.92f)
                            .clip(Capsule())
                            .background(CarpoolrColors.Raspberry)
                            .clickable(doneSource, null) {
                                // manual fallback; the store still checks for real cookies
                                CookieManager.getInstance().flush()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) { BasicText("done", style = TextStyle(ComposeColor.White, 15.sp, fontWeight = FontWeight.Bold)) }
                }
                // thin load bar
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress in 1..99) Box(
                        Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(CarpoolrColors.Raspberry)
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
                    factory = { ctx -> buildWebView(ctx, provider) { progress = it } }
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(ctx: Context, provider: Provider, onProgress: (Int) -> Unit): WebView =
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                databaseEnabled = true
                setGeolocationEnabled(true)
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                // Drop the WebView marker so sites serve their normal mobile sign-in.
                userAgentString = userAgentString.replace("; wv", "")
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(view, request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url != null && Uri.parse(url).host == "auth.uber.com") leftLoginPage = true
                    checkSignedIn(view)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    // SPA route changes (rapido, ola) don't fire onPageFinished
                    if (url != null && Uri.parse(url).host == "auth.uber.com") leftLoginPage = true
                    checkSignedIn(view)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

                // Rapido and Uber ask for location; without an answer their pages can sit blank.
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    val granted = ContextCompat.checkSelfPermission(this@WebLoginActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this@WebLoginActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) callback.invoke(origin, true, false)
                    else {
                        geoOrigin = origin; geoCallback = callback
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }

                // Popups (target=_blank, window.open) load in this same page.
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    val popup = WebView(view.context)
                    popup.webViewClient = object : WebViewClient() {
                        private var taken = false
                        private fun take(v: WebView, url: Uri) {
                            if (taken) return
                            taken = true
                            handleUrl(view, url, forceLoad = true)
                            v.stopLoading()
                            v.post { v.destroy() }
                        }
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            take(v, request.url); return true
                        }
                        override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            if (url != null && url != "about:blank") take(v, Uri.parse(url))
                        }
                    }
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
            web = this
            loadUrl(provider.loginUrl ?: "about:blank")
            main.postDelayed(poll, 2000)
        }

    /** Provider-specific check for a live session; closes the page by itself when signed in. */
    private fun checkSignedIn(view: WebView) {
        if (finished) return
        val host = Uri.parse(view.url ?: return).host ?: return
        when (provider) {
            Provider.Uber -> {
                // auth.uber.com sends you back to m.uber.com only after a successful sign-in
                if (leftLoginPage && host == "m.uber.com") signedIn()
            }
            Provider.Rapido -> if (host == "m.rapido.bike") {
                view.evaluateJavascript(
                    "(function(){try{var t=JSON.parse(localStorage.getItem('token')||'null');return !!(t&&t.data)}catch(e){return false}})()"
                ) { if (it == "true") signedIn() }
            }
            Provider.Ola -> if (host.endsWith("olacabs.com")) {
                view.evaluateJavascript(SESSION_KEYS_JS) { raw ->
                    val keys = raw.trim('"').split(',').filter { it.isNotBlank() }.toSet()
                    val before = baseline
                    if (before == null) baseline = keys
                    else if ((keys - before).isNotEmpty()) signedIn()
                }
            }
        }
    }

    private fun signedIn() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        ConnectionStore(this).markConnected(provider)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_AUTO, true))
        main.postDelayed({ finish() }, 400)
    }

    /** Keeps http(s) in the page; turns intent:// into its web fallback; ignores app-only schemes. */
    private fun handleUrl(view: WebView, uri: Uri, forceLoad: Boolean = false): Boolean {
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> { if (forceLoad) view.loadUrl(uri.toString()); forceLoad }
            "intent" -> {
                val fallback = runCatching {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                }.getOrNull()
                if (fallback != null) view.loadUrl(fallback)
                true
            }
            else -> true // uber://, olacabs://, rapido:// etc.: stay on the web sign-in
        }
    }

    override fun onDestroy() {
        finished = true
        main.removeCallbacksAndMessages(null)
        web?.destroy()
        web = null
        super.onDestroy()
    }

    /** Launch contract: input is the provider; result says how the page closed. */
    enum class Outcome { Cancelled, TappedDone, SignedIn }

    class Contract : ActivityResultContract<Provider, Outcome>() {
        override fun createIntent(context: Context, input: Provider): Intent =
            Intent(context, WebLoginActivity::class.java).putExtra(EXTRA_PROVIDER, input.name)

        override fun parseResult(resultCode: Int, intent: Intent?): Outcome = when {
            resultCode != Activity.RESULT_OK -> Outcome.Cancelled
            intent?.getBooleanExtra(EXTRA_AUTO, false) == true -> Outcome.SignedIn
            else -> Outcome.TappedDone
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_AUTO = "auto"

        /** Names of cookies and storage keys that look like a login session, comma-joined. */
        private const val SESSION_KEYS_JS =
            "(function(){var r=/(token|auth|jwt|access)/i,x=/(branch|_ga|gtm|recaptcha|analytic|clevertap|firebase|ab_|exp)/i,o=[];" +
            "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ls:'+k)}}catch(e){}" +
            "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);var v=sessionStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ss:'+k)}}catch(e){}" +
            "document.cookie.split(';').forEach(function(c){var n=c.split('=')[0].trim();if(r.test(n)&&!x.test(n))o.push('ck:'+n)});return o.join(',')})()"
    }
}
