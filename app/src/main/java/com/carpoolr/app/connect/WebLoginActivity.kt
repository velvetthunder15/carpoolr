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
import android.widget.Toast
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
import androidx.compose.runtime.key
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
 * Full-screen sign-in on Uber / Ola / Rapido's mobile web app:
 * - Uber: enters via m.uber.com/go/home so OAuth `state` cookie is minted first, then detects `sid`/`csid`/`fsid`.
 * - Rapido: enters via m.rapido.bike/ so Angular initializes guards before `/verify-phonenumber` and reads `localStorage.token`.
 * - Ola: opens Ola's Polymer `ola-login` modal automatically and reads `ola-app.user.isLoggedIn`.
 */
class WebLoginActivity : ComponentActivity() {

    private var web: WebView? = null
    private lateinit var provider: Provider
    private val main = Handler(Looper.getMainLooper())
    private var finished = false
    private var leftLoginPage = false
    private var initialUberCookies: Set<String> = emptySet()
    private var uberAuthCookieSeenAtMs: Long = 0L
    private var baseline: Set<String>? = null
    private var onCrashRecreate: (() -> Unit)? = null

    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
    }

    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            web?.let { checkSignedIn(it, manualTap = false) }
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
            var webEpoch by remember { mutableIntStateOf(0) }
            onCrashRecreate = { webEpoch++ }

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
                    val reloadSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(reloadSource, 0.90f)
                            .clip(Capsule())
                            .background(CarpoolrColors.Cream.copy(alpha = 0.12f))
                            .clickable(reloadSource, null) { web?.reload() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) { BasicText("reload", style = TextStyle(CarpoolrColors.Cream, 13.sp, fontWeight = FontWeight.Medium)) }
                    Spacer(Modifier.width(8.dp))
                    val doneSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(doneSource, 0.92f)
                            .clip(Capsule())
                            .background(CarpoolrColors.Raspberry)
                            .clickable(doneSource, null) { onDoneClicked() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) { BasicText("done", style = TextStyle(ComposeColor.White, 15.sp, fontWeight = FontWeight.Bold)) }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress in 1..99) Box(
                        Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(CarpoolrColors.Raspberry)
                    )
                }
                key(webEpoch) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding().imePadding(),
                        factory = { ctx -> buildWebView(ctx, provider) { progress = it } }
                    )
                }
            }
        }
    }

    private fun onDoneClicked() {
        val view = web ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        CookieManager.getInstance().flush()
        checkSignedIn(view, manualTap = true) { ok ->
            if (ok) {
                signedIn()
            } else {
                Toast.makeText(
                    this,
                    "complete otp sign-in on the page first (or tap ← to cancel)",
                    Toast.LENGTH_SHORT
                ).show()
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
                // Standard Chrome mobile viewport (1:1 CSS width instead of shrunk 980px overview).
                loadWithOverviewMode = false
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
            if (provider == Provider.Uber) initialUberCookies = uberAuthCookies()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(view, request.url)

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    noteUberHost(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    noteUberHost(url)
                    if (provider == Provider.Ola) promptOlaLoginModal(view)
                    checkSignedIn(view, manualTap = false)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    noteUberHost(url)
                    if (provider == Provider.Ola) promptOlaLoginModal(view)
                    checkSignedIn(view, manualTap = false)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (!finished) {
                        runCatching { (view.parent as? ViewGroup)?.removeView(view); view.destroy() }
                        if (web === view) web = null
                        main.post { onCrashRecreate?.invoke() }
                    }
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
            main.removeCallbacks(poll)
            main.postDelayed(poll, 1200)
        }

    private fun noteUberHost(url: String?) {
        val u = url?.let(Uri::parse) ?: return
        val host = u.host?.lowercase() ?: return
        val path = u.path.orEmpty()
        if (host == "auth.uber.com" || host == "login.uber.com" || path.startsWith("/go/login-redirect")) {
            leftLoginPage = true
        }
    }

    /** Auto-opens Ola's Polymer `ola-login` modal once `<ola-app>` finishes loading unauthenticated. */
    private fun promptOlaLoginModal(view: WebView) {
        view.evaluateJavascript(
            "(function(){try{var a=document.querySelector('ola-app');" +
                "if(a&&a.profileLoaded&&a.user&&!a.user.isLoggedIn&&!window.__cpOlaModal){" +
                "window.__cpOlaModal=1;a.dispatchEvent(new CustomEvent('open-modal'," +
                "{detail:{pageName:'ola-login',modalTitle:'LOG IN',hideHeaderFlag:true},bubbles:true,composed:true}));" +
                "}}catch(e){}})()",
            null
        )
    }

    /**
     * Checks whether the provider session is now authenticated.
     * If [onResult] is non-null (manual "done" tap), invokes it with true/false; otherwise auto-closes when true.
     */
    private fun checkSignedIn(view: WebView, manualTap: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        if (finished) return
        val currentUri = view.url?.let(Uri::parse)
        val host = currentUri?.host?.lowercase().orEmpty()
        val path = currentUri?.path.orEmpty()

        fun report(ok: Boolean) {
            if (onResult != null) onResult(ok)
            else if (ok) signedIn()
        }

        when (provider) {
            Provider.Uber -> {
                val cookies = uberAuthCookies()
                val freshCookies = cookies - initialUberCookies
                val onAuthHost = host == "auth.uber.com" || host == "login.uber.com"
                val onRedirectInterstitial = path.startsWith("/go/login-redirect")

                if (freshCookies.isNotEmpty() && uberAuthCookieSeenAtMs == 0L) {
                    uberAuthCookieSeenAtMs = System.currentTimeMillis()
                }
                // Wait for /go/login-redirect to finish exchanging tokens onto m.uber.com/go/home,
                // unless auth.uber.com already minted fresh sid/csid/fsid and stalled >2.5s on redirect.
                val settledAfterRedirect = !onAuthHost && !onRedirectInterstitial &&
                    ((freshCookies.isNotEmpty()) || (leftLoginPage && host in UBER_APP_HOSTS && cookies.isNotEmpty()))
                val stalledWithFreshCookie = freshCookies.isNotEmpty() &&
                    uberAuthCookieSeenAtMs > 0L &&
                    System.currentTimeMillis() - uberAuthCookieSeenAtMs > 2500L

                if (settledAfterRedirect || stalledWithFreshCookie) {
                    report(true)
                    return
                }
                // Secondary DOM check when user is already on m.uber.com/go/home (especially on manual "done" tap)
                if (host in UBER_APP_HOSTS && !onRedirectInterstitial) {
                    view.evaluateJavascript(UBER_DOM_SIGNED_IN_JS) { raw ->
                        val domSignedIn = raw == "true"
                        report(domSignedIn || (manualTap && cookies.isNotEmpty()))
                    }
                } else {
                    report(manualTap && cookies.isNotEmpty() && !onAuthHost)
                }
            }
            Provider.Rapido -> {
                if (!host.endsWith("rapido.bike")) {
                    report(false)
                    return
                }
                view.evaluateJavascript(RAPIDO_SIGNED_IN_JS) { raw ->
                    report(raw == "true")
                }
            }
            Provider.Ola -> {
                if (!host.endsWith("olacabs.com")) {
                    report(false)
                    return
                }
                view.evaluateJavascript(OLA_SIGNED_IN_JS) { raw ->
                    if (raw == "true") {
                        report(true)
                    } else {
                        view.evaluateJavascript(SESSION_KEYS_JS) { keysRaw ->
                            val keys = keysRaw.trim('"').split(',').filter { it.isNotBlank() }.toSet()
                            val before = baseline
                            if (before == null) {
                                baseline = keys
                                report(false)
                            } else {
                                report((keys - before).isNotEmpty())
                            }
                        }
                    }
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
        main.postDelayed({ finish() }, 250)
    }

    private fun handleUrl(view: WebView, uri: Uri, forceLoad: Boolean = false): Boolean {
        val scheme = uri.scheme?.lowercase()
        return when (scheme) {
            "http", "https" -> {
                if (forceLoad) view.loadUrl(uri.toString())
                forceLoad
            }
            "intent" -> {
                val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
                val fallback = parsed?.getStringExtra("browser_fallback_url")
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                val dataUrl = parsed?.dataString?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                when {
                    fallback != null -> view.loadUrl(fallback)
                    dataUrl != null -> view.loadUrl(dataUrl)
                    provider == Provider.Uber -> recoverUberPostLogin(view)
                }
                true
            }
            "uber" -> {
                if (provider == Provider.Uber) recoverUberPostLogin(view)
                true
            }
            "rapido" -> {
                if (provider == Provider.Rapido) checkSignedIn(view, manualTap = false)
                true
            }
            "olacabs" -> {
                if (provider == Provider.Ola) checkSignedIn(view, manualTap = false)
                true
            }
            else -> true
        }
    }

    private fun recoverUberPostLogin(view: WebView) {
        CookieManager.getInstance().flush()
        val cookies = uberAuthCookies()
        if ((cookies - initialUberCookies).isNotEmpty() || (leftLoginPage && cookies.isNotEmpty())) {
            signedIn()
        } else {
            view.loadUrl("https://m.uber.com/go/home")
        }
    }

    override fun onDestroy() {
        finished = true
        onCrashRecreate = null
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

        private const val RAPIDO_SIGNED_IN_JS =
            "(function(){try{" +
                "var raw=localStorage.getItem('token');" +
                "if(raw&&raw!=='null'){try{var t=JSON.parse(raw);if(t&&(t.data||(typeof t==='string'&&t.length>10)))return true}catch(e){if(raw.length>15)return true}}" +
                "var p=location.pathname||'';" +
                "if(p.indexOf('/home')===0&&!document.querySelector('app-authentication')){" +
                "var u=localStorage.getItem('user')||localStorage.getItem('profile');if(u&&u.length>10)return true}" +
                "return false}catch(e){return false}})()"

        private const val OLA_SIGNED_IN_JS =
            "(function(){try{var a=document.querySelector('ola-app');return !!(a&&a.user&&a.user.isLoggedIn)}catch(e){return false}})()"

        private const val UBER_DOM_SIGNED_IN_JS =
            "(function(){try{" +
                "if(location.hostname.indexOf('auth.uber.com')>=0||location.pathname.indexOf('login')>=0)return false;" +
                "var t=(document.body&&document.body.innerText||'').toLowerCase();" +
                "if(t.indexOf('sign in')>=0&&t.indexOf('continue')>=0&&t.length<400)return false;" +
                "return t.indexOf('where to')>=0||t.indexOf('pickup')>=0||t.indexOf('activity')>=0||t.indexOf('suggestions')>=0" +
                "}catch(e){return false}})()"

        private const val SESSION_KEYS_JS =
            "(function(){var r=/(token|auth|jwt|access|owt|session)/i,x=/(branch|_ga|gtm|recaptcha|analytic|clevertap|firebase|ab_|exp| boomr|xsrf|csrf)/i,o=[];" +
                "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ls:'+k)}}catch(e){}" +
                "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);var v=sessionStorage.getItem(k);if(r.test(k)&&!x.test(k)&&v&&v.length>8)o.push('ss:'+k)}}catch(e){}" +
                "document.cookie.split(';').forEach(function(c){var n=c.split('=')[0].trim();if(r.test(n)&&!x.test(n))o.push('ck:'+n)});return o.join(',')})()"
    }
}
