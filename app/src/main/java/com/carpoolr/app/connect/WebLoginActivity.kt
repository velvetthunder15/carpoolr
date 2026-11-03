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
import android.webkit.CookieManager
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val provider = Provider.valueOf(intent.getStringExtra(EXTRA_PROVIDER) ?: run { finish(); return })

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
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

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
        web?.destroy()
        web = null
        super.onDestroy()
    }

    /** Launch contract: input is the provider, result is true when the user tapped done. */
    class Contract : ActivityResultContract<Provider, Boolean>() {
        override fun createIntent(context: Context, input: Provider): Intent =
            Intent(context, WebLoginActivity::class.java).putExtra(EXTRA_PROVIDER, input.name)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
    }
}
