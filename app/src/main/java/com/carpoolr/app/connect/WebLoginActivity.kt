package com.carpoolr.app.connect

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
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
 * place, and Uber's HttpOnly session cookie is detected as soon as OTP/login completes.
 */
class WebLoginActivity : ComponentActivity() {

    private var web: WebView? = null
    private lateinit var provider: Provider
    private val main = Handler(Looper.getMainLooper())
    private var finished = false
    private var leftLoginPage = false
    private var initialUberSession: String? = null
    private var baseline: Set<String>? = null

    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
    }

    /** Polls the page and CookieManager for a signed-in session and closes on its own once found. */
    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            web?.let { checkSignedIn(it) }
            main.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
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
                                CookieManager.getInstance().flush()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) { BasicText("done", style = TextStyle(ComposeColor.White, 15.sp, fontWeight = FontWeight.Bold)) }
                }
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
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = cleanMobileChromeUa(userAgentString)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            if (provider == Provider.Uber) initialUberSession = currentUberSessionToken()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(view, request.url)

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    noteUberHost(url)
                    checkSignedIn(view)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    noteUberHost(url)
                    checkSignedIn(view)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    noteUberHost(url)
                    checkSignedIn(view)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (!finished) runCatching { view.reload() }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    val granted = ContextCompat.checkSelfPermission(this@WebLoginActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this@WebLoginActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) callback.invoke(origin, true, false)
                    else {
                        geoOrigin = origin; geoCallback = callback
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }

                @SuppressLint("SetJavaScriptEnabled")
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    val popup = WebView(view.context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = cleanMobileChromeUa(settings.userAgentString)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }
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
            main.postDelayed(poll, 1200)
        }

    private fun noteUberHost(url: String?) {
        val host = url?.let { Uri.parse(it).host }?.lowercase() ?: return
        if (host == "auth.uber.com" || host == "login.uber.com") leftLoginPage = true
    }

    private fun currentUberSessionToken(): String? {
        val cm = CookieManager.getInstance()
        return listOf("https://m.uber.com", "https://auth.uber.com", "https://riders.uber.com")
            .asSequence()
            .flatMap { u -> cm.getCookie(u).orEmpty().split(';').asSequence() }
            .map { it.trim() }
            .firstOrNull {
                val k = it.substringBefore('=').trim()
                val v = it.substringAfter('=', "").trim()
                v.length > 8 && (k.equals("sid", ignoreCase = true) || k.equals("jwt-session", ignoreCase = true))
            }
    }

    /** Provider-specific check for a live session; closes the page by itself when signed in. */
    private fun checkSignedIn(view: WebView) {
        if (finished) return
        val host = Uri.parse(view.url ?: return).host?.lowercase() ?: return
        when (provider) {
            Provider.Uber -> {
                val token = currentUberSessionToken()
                // Close immediately once auth.uber.com sets a fresh .uber.com sid/jwt-session cookie,
                // or once an already-valid session redirects onto Uber's rider web host.
                val freshCookie = token != null && token != initialUberSession
                val landedSignedIn = leftLoginPage && host in UBER_APP_HOSTS && token != null
                if (freshCookie || landedSignedIn) signedIn()
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
        main.postDelayed({ finish() }, 350)
    }

    /** Keeps http(s) in the page; recovers from post-auth app handoff schemes instead of leaving a white page. */
    private fun handleUrl(view: WebView, uri: Uri, forceLoad: Boolean = false): Boolean {
        val scheme = uri.scheme?.lowercase()
        return when (scheme) {
            "http", "https" -> {
                if (forceLoad) view.loadUrl(uri.toString())
                forceLoad
            }
            "intent" -> {
                val fallback = runCatching {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                }.getOrNull()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                if (fallback != null) {
                    view.loadUrl(fallback)
                } else if (provider == Provider.Uber) {
                    recoverUberPostLogin(view)
                }
                true
            }
            "uber" -> {
                if (provider == Provider.Uber) recoverUberPostLogin(view)
                true
            }
            else -> true
        }
    }

    private fun recoverUberPostLogin(view: WebView) {
        CookieManager.getInstance().flush()
        if (currentUberSessionToken() != null) {
            signedIn()
        } else {
            view.loadUrl("https://m.uber.com/")
        }
    }

    override fun onDestroy() {
        finished = true
        main.removeCallbacksAndMessages(null)
        web?.destroy()
        web = null
        super.onDestroy()
    }

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

        private val UBER_APP_HOSTS = setOf("m.uber.com", "riders.uber.com", "www.uber.com")

        /** Names of cookies and storage keys that look like a login session, comma-joined. */
        private const val SESSION_KEYS_JS =
            "(function(){var r=/(token|auth|jwt|access)/i,x=/(branch|_ga|gtm|recaptcha|analytic|clevertap|firebase|ab_|exp)/i,o=[];" +
            "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ls:'+k)}}catch(e){}" +
            "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);var v=sessionStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ss:'+k)}}catch(e){}" +
            "document.cookie.split(';').forEach(function(c){var n=c.split('=')[0].trim();if(r.test(n)&&!x.test(n))o.push('ck:'+n)});return o.join(',')})()"
    }
}
