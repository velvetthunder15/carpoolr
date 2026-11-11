package com.carpoolr.app.connect

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import android.webkit.JavascriptInterface
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.carpoolr.app.R
import com.carpoolr.app.ui.components.GlassIcon
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import org.json.JSONObject
import org.json.JSONTokener
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * The booker books the cab on the ride app's own mobile site (their saved session), with the
 * pool's route prefilled. carpoolr reads the fares the page shows, from its network responses
 * and its visible text, and the booker taps the one they booked. If mobile web blocks booking,
 * "open app" copies the drop, opens the native ride app, and keeps the fare bar ready here.
 */
class BookActivity : ComponentActivity() {

    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val fares = mutableStateListOf<FareOption>()
    private var closed = false

    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
    }

    private fun scanDom(view: WebView?) {
        if (closed || view == null) return
        view.evaluateJavascript("(function(){return document.body?document.body.innerText.slice(0,30000):''})()") { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return@evaluateJavascript
            add(FareParser.fromText(text))
        }
    }

    private val scan = object : Runnable {
        override fun run() {
            if (closed) return
            scanDom(web)
            main.postDelayed(this, 1500)
        }
    }

    private fun add(found: List<FareOption>) = main.post {
        found.forEach { f ->
            val i = fares.indexOfFirst { it.label.equals(f.label, true) }
            if (i >= 0) fares[i] = f else if (fares.size < 12) fares += f
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun onData(url: String?, body: String?) {
            if (body == null || body.length > 2_000_000) return
            if (!HAS_FARE_HINT.containsMatchIn(body)) return
            add(FareParser.fromJson(body))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val provider = Provider.valueOf(intent.getStringExtra(EXTRA_PROVIDER) ?: run { finish(); return })
        val pickupLabel = intent.getStringExtra("plabel").orEmpty()
        val dropLabel = intent.getStringExtra("dlabel").orEmpty()
        val url = bookingUrl(
            provider,
            intent.getDoubleExtra("plat", 0.0), intent.getDoubleExtra("plng", 0.0), pickupLabel,
            intent.getDoubleExtra("dlat", 0.0), intent.getDoubleExtra("dlng", 0.0), dropLabel
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else { setResult(Activity.RESULT_CANCELED); finish() }
            }
        })

        setContent {
            var typed by remember { mutableStateOf("") }
            var picked by remember { mutableStateOf<FareOption?>(null) }
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
                        "book on ${provider.label}",
                        style = TextStyle(CarpoolrColors.Cream, 22.sp, fontFamily = Anton),
                        modifier = Modifier.weight(1f)
                    )
                    val appSource = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .pressScale(appSource, 0.92f)
                            .clip(Capsule())
                            .background(CarpoolrColors.Cream.copy(alpha = 0.14f))
                            .clickable(appSource, null) {
                                copyToClipboard(dropLabel)
                                Toast.makeText(
                                    this@BookActivity,
                                    "drop copied · book in ${provider.label}, then type fare below",
                                    Toast.LENGTH_LONG
                                ).show()
                                openProviderApp(this@BookActivity, provider)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText("open app", style = TextStyle(CarpoolrColors.Cream, 13.sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(5.dp))
                        GlassIcon(R.drawable.ic_external, null, size = 14, tint = CarpoolrColors.Cream)
                    }
                }
                if (pickupLabel.isNotBlank() || dropLabel.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(CarpoolrColors.GreenDeep.copy(alpha = 0.55f))
                            .clickable {
                                copyToClipboard(dropLabel.ifBlank { pickupLabel })
                                Toast.makeText(this@BookActivity, "copied destination", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            "${pickupLabel.ifBlank { "pickup" }} → ${dropLabel.ifBlank { "drop" }}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(CarpoolrColors.CreamMuted, 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText("copy drop", style = TextStyle(CarpoolrColors.Cream, 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress in 1..99) Box(
                        Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(CarpoolrColors.Raspberry)
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx -> buildWebView(ctx, url) { progress = it } }
                )
                Column(
                    Modifier.fillMaxWidth().background(CarpoolrColors.GreenDeep).navigationBarsPadding().imePadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicText(
                        if (fares.isEmpty()) "book your ride above. fares show up here as the page loads."
                        else "tap the ride you booked",
                        style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
                    )
                    if (fares.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fares) { f ->
                            val on = picked == f
                            Box(
                                Modifier.clip(Capsule())
                                    .background(if (on) CarpoolrColors.Raspberry else CarpoolrColors.Cream.copy(alpha = 0.12f))
                                    .clickable { picked = f; typed = f.rupees.toString() }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) { BasicText("${f.label}  ₹${f.rupees}", style = TextStyle(ComposeColor.White, 14.sp, fontWeight = FontWeight.Medium)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).clip(RoundedRectangle(16.dp)).background(CarpoolrColors.Cream.copy(alpha = 0.10f))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (typed.isEmpty()) BasicText("₹ fare you paid", style = TextStyle(CarpoolrColors.CreamFaint, 16.sp))
                            BasicTextField(
                                typed, { v -> typed = v.filter(Char::isDigit).take(6); picked = null }, singleLine = true,
                                textStyle = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        val fare = typed.toIntOrNull()
                        Box(
                            Modifier.clip(Capsule())
                                .background(if (fare != null && fare > 0) CarpoolrColors.Raspberry else CarpoolrColors.Raspberry.copy(alpha = 0.4f))
                                .clickable(enabled = fare != null && fare > 0) {
                                    closed = true
                                    CookieManager.getInstance().flush()
                                    setResult(Activity.RESULT_OK, Intent()
                                        .putExtra(EXTRA_FARE, fare)
                                        .putExtra(EXTRA_LABEL, picked?.label)
                                        .putExtra(EXTRA_PROVIDER, provider.name))
                                    finish()
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) { BasicText("booked", style = TextStyle(ComposeColor.White, 15.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("destination", text))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(ctx: Context, url: String, onProgress: (Int) -> Unit): WebView = WebView(ctx).apply {
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
        addJavascriptInterface(Bridge(), "CarpoolrBridge")
        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, setOf("https://*.uber.com", "https://*.olacabs.com", "https://*.rapido.bike"))
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(view, request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!docStart) view.evaluateJavascript(HOOK_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                scanDom(view)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                scanDom(view)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!closed) runCatching { view.reload() }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val granted = ContextCompat.checkSelfPermission(this@BookActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this@BookActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                    private fun take(v: WebView, uri: Uri) {
                        if (taken) return
                        taken = true
                        handleUrl(view, uri, forceLoad = true)
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
        loadUrl(url)
        main.postDelayed(scan, 2000)
    }

    /**
     * Keeps http(s) and ride-app links inside WebView, while letting UPI / payment apps open externally
     * when the booker pays for the cab.
     */
    private fun handleUrl(view: WebView, uri: Uri, forceLoad: Boolean = false): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme == "http" || scheme == "https") {
            if (forceLoad) view.loadUrl(uri.toString())
            return forceLoad
        }
        if (scheme in RIDE_SCHEMES) return true
        if (scheme in PAYMENT_SCHEMES) {
            val external = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(external) }
                .onFailure { if (scheme == "upi") Toast.makeText(this, "no upi app found", Toast.LENGTH_SHORT).show() }
            return true
        }
        if (scheme == "intent") {
            val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return true
            parsed.addCategory(Intent.CATEGORY_BROWSABLE)
            parsed.component = null
            parsed.selector = null
            val fallback = parsed.getStringExtra("browser_fallback_url")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val targetPkg = parsed.`package`
            val targetScheme = parsed.data?.scheme?.lowercase()
            if (targetPkg == packageName) return true
            if (targetPkg in RIDE_PACKAGES || targetScheme in RIDE_SCHEMES) {
                if (fallback != null) view.loadUrl(fallback)
                return true
            }
            runCatching { startActivity(parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { if (fallback != null) view.loadUrl(fallback) }
            return true
        }
        return true
    }

    override fun onDestroy() {
        closed = true
        main.removeCallbacksAndMessages(null)
        web?.destroy(); web = null
        super.onDestroy()
    }

    class Input(
        val provider: Provider,
        val pickupLat: Double, val pickupLng: Double, val pickupLabel: String,
        val dropLat: Double, val dropLng: Double, val dropLabel: String
    )
    data class Result(val fare: Int, val label: String?, val provider: Provider)

    class Contract : ActivityResultContract<Input, Result?>() {
        override fun createIntent(context: Context, input: Input): Intent =
            Intent(context, BookActivity::class.java)
                .putExtra(EXTRA_PROVIDER, input.provider.name)
                .putExtra("plat", input.pickupLat).putExtra("plng", input.pickupLng).putExtra("plabel", input.pickupLabel)
                .putExtra("dlat", input.dropLat).putExtra("dlng", input.dropLng).putExtra("dlabel", input.dropLabel)

        override fun parseResult(resultCode: Int, intent: Intent?): Result? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val fare = intent.getIntExtra(EXTRA_FARE, -1).takeIf { it > 0 } ?: return null
            val p = intent.getStringExtra(EXTRA_PROVIDER)?.let(Provider::valueOf) ?: return null
            return Result(fare, intent.getStringExtra(EXTRA_LABEL), p)
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_FARE = "fare"
        const val EXTRA_LABEL = "label"

        private val RIDE_SCHEMES = setOf("uber", "olacabs", "rapido")
        private val RIDE_PACKAGES = setOf("com.ubercab", "com.olacabs.customer", "com.rapido.passenger")
        private val PAYMENT_SCHEMES = setOf(
            "upi", "tez", "gpay", "phonepe", "paytmmp", "bhim", "credpay", "amazonpay", "mobikwik", "freecharge"
        )
        private val HAS_FARE_HINT = Regex("(\u20B9|fare|price|estimate|payable|INR)", RegexOption.IGNORE_CASE)

        fun bookingUrl(p: Provider, plat: Double, plng: Double, plabel: String, dlat: Double, dlng: Double, dlabel: String): String {
            fun place(lat: Double, lng: Double, label: String) =
                JSONObject().put("latitude", lat).put("longitude", lng).put("addressLine1", label).toString()
            return when (p) {
                Provider.Uber -> "https://m.uber.com/go/product-selection?pickup=" + Uri.encode(place(plat, plng, plabel)) +
                    "&drop%5B0%5D=" + Uri.encode(place(dlat, dlng, dlabel))
                Provider.Ola -> "https://book.olacabs.com/?pickup_name=${Uri.encode(plabel)}&lat=$plat&lng=$plng" +
                    "&drop_lat=$dlat&drop_lng=$dlng&drop_name=${Uri.encode(dlabel)}"
                Provider.Rapido -> "https://m.rapido.bike/unup-home"
            }
        }

        /** Copies every fetch/XHR response that mentions a fare or price to the app. */
        private const val HOOK_JS = """(function(){if(window.__cpHook)return;window.__cpHook=1;
var send=function(u,t){try{if(t&&/(\u20B9|fare|price|estimate|payable|INR)/i.test(t))CarpoolrBridge.onData(String(u),t)}catch(e){}};
var f=window.fetch;if(f){window.fetch=function(){var a=arguments;return f.apply(this,a).then(function(r){try{r.clone().text().then(function(t){send(a[0]&&a[0].url||a[0],t)})}catch(e){}return r})}}
var o=XMLHttpRequest.prototype.open,s=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__u=u;return o.apply(this,arguments)};
XMLHttpRequest.prototype.send=function(){var x=this;x.addEventListener('load',function(){try{if(!x.responseType||x.responseType==='text')send(x.__u,x.responseText)}catch(e){}});return s.apply(this,arguments)}})();"""
    }
}
