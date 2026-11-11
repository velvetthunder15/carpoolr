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
    // Open auth.uber.com/v2 directly with m.uber.com/ as next_url (avoids /go/login-redirect white screen).
    Uber("uber", "https://auth.uber.com/v2/?next_url=https%3A%2F%2Fm.uber.com%2F", "https://m.uber.com", "com.ubercab"),
    Ola("ola", "https://book.olacabs.com/", "https://book.olacabs.com", "com.olacabs.customer"),
    // Rapido PWA's own phone-number login route; it stores {"data": token} in localStorage "token".
    Rapido("rapido", "https://m.rapido.bike/verify-phonenumber", "https://m.rapido.bike", "com.rapido.passenger");

    val signsInOnWeb: Boolean get() = loginUrl != null
}

/** Strips both "; wv" and "Version/4.0 " so sites treat WebView as standard Chrome Mobile on Android. */
internal fun cleanMobileChromeUa(raw: String): String = raw
    .replace("; wv)", ")")
    .replace("; wv", "")
    .replace(Regex("""Version/\d+\.\d+\s+"""), "")

internal fun cookieNamesFor(vararg urls: String): Set<String> {
    val cm = CookieManager.getInstance()
    val out = HashSet<String>()
    for (u in urls) {
        cm.getCookie(u)?.split(';')?.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty()) out += name
        }
    }
    return out
}

/** True once auth.uber.com has issued an authenticated .uber.com session cookie (HttpOnly sid / jwt-session). */
internal fun hasUberSessionCookie(): Boolean {
    val names = cookieNamesFor("https://m.uber.com", "https://auth.uber.com", "https://riders.uber.com")
    return names.any { it.equals("sid", ignoreCase = true) || it.equals("jwt-session", ignoreCase = true) }
}

class ConnectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("connections", Context.MODE_PRIVATE)

    fun isConnected(p: Provider): Boolean = prefs.getBoolean(p.name, false)

    /** Set when the login page itself confirmed a signed-in session. */
    fun markConnected(p: Provider) {
        CookieManager.getInstance().flush()
        prefs.edit().putBoolean(p.name, true).apply()
    }

    /** Called when the user manually taps "done" on a web sign-in. */
    fun markSignedIn(p: Provider): Boolean {
        val url = p.cookieUrl ?: return false
        CookieManager.getInstance().flush()
        val ok = when (p) {
            Provider.Uber -> hasUberSessionCookie()
            else -> !CookieManager.getInstance().getCookie(url).isNullOrBlank()
        }
        prefs.edit().putBoolean(p.name, ok).apply()
        return ok
    }

    fun disconnect(p: Provider) {
        prefs.edit().putBoolean(p.name, false).apply()
        val url = p.cookieUrl ?: return
        val cm = CookieManager.getInstance()
        val rootDomain = Uri.parse(url).host?.let { h ->
            val parts = h.split('.')
            if (parts.size >= 2) "." + parts.takeLast(2).joinToString(".") else null
        }
        cm.getCookie(url)?.split(";")?.forEach { pair ->
            val name = pair.substringBefore("=").trim()
            if (name.isNotEmpty()) {
                cm.setCookie(url, "$name=; Path=/; Max-Age=0")
                if (rootDomain != null) {
                    cm.setCookie(url, "$name=; Domain=$rootDomain; Path=/; Max-Age=0; Secure")
                }
            }
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
