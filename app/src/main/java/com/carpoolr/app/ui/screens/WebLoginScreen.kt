package com.carpoolr.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.carpoolr.app.R
import com.carpoolr.app.connect.Provider
import com.carpoolr.app.ui.components.GlassIcon
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** Full-screen sign-in on the provider's own mobile site. carpoolr never sees the password. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(provider: Provider, onDone: () -> Unit, onCancel: () -> Unit) {
    val ref = remember { WebViewRef() }
    BackHandler {
        val w = ref.view
        if (w != null && w.canGoBack()) w.goBack() else onCancel()
    }
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
                    .clickable(interactionSource = backSource, indication = null, onClick = onCancel)
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
                    .clickable(interactionSource = doneSource, indication = null, onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) { BasicText("done", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)) }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Drop the WebView marker so sites serve their normal mobile sign-in.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(provider.loginUrl ?: "about:blank")
                    ref.view = this
                }
            },
            onRelease = {
                ref.view = null
                it.destroy()
            }
        )
    }
}

private class WebViewRef {
    var view: WebView? = null
}
