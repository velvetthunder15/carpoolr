package com.carpoolr.app.connect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Ride apps the user links to carpoolr.
 * Uber, Ola and Rapido sign in on their own mobile sites inside an in-app WebView. Cookies and
 * DOM storage persist, so later fare reads ride on the user's own session.
 */
enum class Provider(
    val label: String,
    val loginUrl: String?,
    val cookieUrl: String?,
    val appPackage: String
) {
    // Must enter via m.uber.com/go/home so m.uber.com sets the HttpOnly OAuth `state` cookie before auth.uber.com/v2.
    Uber("uber", "https://m.uber.com/go/home", "https://m.uber.com", "com.ubercab"),
    Ola("ola", "https://book.olacabs.com/", "https://book.olacabs.com", "com.olacabs.customer"),
    // Must enter via root `/` (not `/verify-phonenumber` directly, which crashes Rapido's Angular guard before init).
    Rapido("rapido", "https://m.rapido.bike/", "https://m.rapido.bike", "com.rapido.passenger");

    val signsInOnWeb: Boolean get() = loginUrl != null
}

/** Strips both "; wv" and "Version/4.0 " so sites treat WebView as standard Chrome Mobile on Android. */
internal fun cleanMobileChromeUa(raw: String): String = raw
    .replace("; wv)", ")")
    .replace("; wv", "")
    .replace(Regex("""Version/\d+\.\d+\s+"""), "")

private val UBER_SESSION_COOKIE_NAMES = setOf("sid", "csid", "fsid")

/**
 * Returns active authenticated cookie `name=value` entries across Uber's web origins,
 * ignoring `deleted` placeholders and pre-auth `jwt-session` / `state` cookies.
 */
internal fun uberAuthCookies(): Set<String> {
    val cm = CookieManager.getInstance()
    val out = HashSet<String>()
    val urls = listOf("https://m.uber.com", "https://uber.com", "https://riders.uber.com", "https://auth.uber.com")
    for (u in urls) {
        cm.getCookie(u)?.split(';')?.forEach { raw ->
            val trimmed = raw.trim()
            val name = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=', "").trim()
            if (name.lowercase() in UBER_SESSION_COOKIE_NAMES &&
                value.length > 8 &&
                !value.equals("deleted", ignoreCase = true)
            ) {
                out += "${name.lowercase()}=$value"
            }
        }
    }
    return out
}

internal fun hasUberSessionCookie(): Boolean = uberAuthCookies().isNotEmpty()

class ConnectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("connections", Context.MODE_PRIVATE)

    fun isConnected(p: Provider): Boolean = prefs.getBoolean(p.name, false)

    /** Set when the login page itself confirmed a signed-in session. */
    fun markConnected(p: Provider) {
        CookieManager.getInstance().flush()
        prefs.edit().putBoolean(p.name, true).apply()
    }

    /** Fallback check when WebLoginActivity returns TappedDone. */
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
        val urls = buildSet {
            p.cookieUrl?.let(::add)
            p.loginUrl?.let { add(Uri.parse(it).let { u -> "${u.scheme}://${u.host}" }) }
            when (p) {
                Provider.Uber -> {
                    add("https://uber.com")
                    add("https://m.uber.com")
                    add("https://auth.uber.com")
                    add("https://riders.uber.com")
                }
                Provider.Ola -> {
                    add("https://book.olacabs.com")
                    add("https://olacabs.com")
                    add("https://accounts.olacabs.com")
                }
                Provider.Rapido -> {
                    add("https://m.rapido.bike")
                    add("https://rapido.bike")
                }
            }
        }
        val cm = CookieManager.getInstance()
        val ws = runCatching { WebStorage.getInstance() }.getOrNull()
        for (url in urls) {
            runCatching { ws?.deleteOrigin(url) }
            val rootDomain = Uri.parse(url).host?.let { h ->
                val parts = h.split('.')
                if (parts.size >= 2) "." + parts.takeLast(2).joinToString(".") else null
            }
            cm.getCookie(url)?.split(";")?.forEach { pair ->
                val name = pair.substringBefore("=").trim()
                if (name.isNotEmpty()) {
                    cm.setCookie(url, "$name=; Path=/; Max-Age=0")
                    cm.setCookie(url, "$name=; Path=/; Max-Age=0; Secure")
                    if (rootDomain != null) {
                        cm.setCookie(url, "$name=; Domain=$rootDomain; Path=/; Max-Age=0")
                        cm.setCookie(url, "$name=; Domain=$rootDomain; Path=/; Max-Age=0; Secure")
                    }
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
