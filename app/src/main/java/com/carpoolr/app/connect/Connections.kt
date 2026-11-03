package com.carpoolr.app.connect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager

/**
 * Ride apps the user links to carpoolr.
 * Uber, Ola and Rapido sign in on their own mobile sites inside an in-app WebView. Cookies
 * persist, so later fare reads ride on the user's own session.
 * Rapido's web app (m.rapido.bike) signs in with a phone OTP.
 */
enum class Provider(
    val label: String,
    val loginUrl: String?,
    val cookieUrl: String?,
    val appPackage: String
) {
    // Uber: login-redirect sends straight to auth.uber.com/v2 (phone/email), then back to m.uber.com.
    Uber("uber", "https://m.uber.com/go/login-redirect/?previousPathname=%2Fgo%2Fhome", "https://m.uber.com", "com.ubercab"),
    Ola("ola", "https://book.olacabs.com/", "https://book.olacabs.com", "com.olacabs.customer"),
    // Rapido PWA's own phone-number login route; it stores {"data": token} in localStorage "token".
    Rapido("rapido", "https://m.rapido.bike/verify-phonenumber", "https://m.rapido.bike", "com.rapido.passenger");

    val signsInOnWeb: Boolean get() = loginUrl != null
}

class ConnectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("connections", Context.MODE_PRIVATE)

    fun isConnected(p: Provider): Boolean = prefs.getBoolean(p.name, false)

    /** Set when the login page itself confirmed a signed-in session. */
    fun markConnected(p: Provider) {
        CookieManager.getInstance().flush()
        prefs.edit().putBoolean(p.name, true).apply()
    }

    /** Called when the user finishes a web sign-in. True only if the site actually set cookies. */
    fun markSignedIn(p: Provider): Boolean {
        val url = p.cookieUrl ?: return false
        CookieManager.getInstance().flush()
        val hasCookies = !CookieManager.getInstance().getCookie(url).isNullOrBlank()
        prefs.edit().putBoolean(p.name, hasCookies).apply()
        return hasCookies
    }

    fun disconnect(p: Provider) {
        prefs.edit().putBoolean(p.name, false).apply()
        val url = p.cookieUrl ?: return
        val cm = CookieManager.getInstance()
        cm.getCookie(url)?.split(";")?.forEach { pair ->
            val name = pair.substringBefore("=").trim()
            if (name.isNotEmpty()) cm.setCookie(url, "$name=; Max-Age=0")
        }
        cm.flush()
    }
}

/** Opens the provider's app, or its Play Store page if it isn't installed. */
fun openProviderApp(context: Context, p: Provider) {
    val launch = context.packageManager.getLaunchIntentForPackage(p.appPackage)
    val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${p.appPackage}"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${p.appPackage}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
