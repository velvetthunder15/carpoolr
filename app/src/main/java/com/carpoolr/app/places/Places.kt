package com.carpoolr.app.places

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/** A place the rider picked: [name] is shown as-is, [detail] is the area under it. */
data class Place(val name: String, val detail: String, val lat: Double, val lng: Double)

/**
 * Place search + reverse lookup on Photon (photon.komoot.io, OpenStreetMap data, no key).
 * Results are biased toward the rider's position.
 */
object Places {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, near: Place?): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = near?.let { "&lat=${it.lat}&lon=${it.lng}" } ?: ""
        parse(get("https://photon.komoot.io/api/?q=$q&limit=8&lang=en$bias"))
    }

    suspend fun reverse(lat: Double, lng: Double): Place? = withContext(Dispatchers.IO) {
        runCatching { parse(get("https://photon.komoot.io/reverse?lat=$lat&lon=$lng&lang=en")).firstOrNull() }
            .getOrNull()?.copy(lat = lat, lng = lng)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "carpoolr-android/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun parse(body: String): List<Place> =
        json.parseToJsonElement(body).jsonObject["features"]?.jsonArray?.mapNotNull { f ->
            val o = f.jsonObject
            val coords = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val p = o["properties"]?.jsonObject ?: return@mapNotNull null
            fun s(k: String) = p.str(k)
            val street = listOfNotNull(s("housenumber"), s("street")).joinToString(" ").ifBlank { null }
            val name = s("name") ?: street ?: s("district") ?: s("city") ?: return@mapNotNull null
            val detail = listOfNotNull(
                if (s("name") != null) street else null,
                s("district") ?: s("locality"), s("city") ?: s("county"), s("state")
            ).distinct().filter { it != name }.joinToString(", ")
            Place(name, detail, coords[1].jsonPrimitive.double, coords[0].jsonPrimitive.double)
        }?.distinctBy { it.name + it.detail } ?: emptyList()

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

object DeviceLocation {
    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Current fix, or the freshest last-known one. Null if location is off or denied. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!granted(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000) return last
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return last
        }
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { cont.resume(it) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, { cont.resume(it) }, android.os.Looper.getMainLooper())
                }
            }
        }
        return fresh ?: last
    }
}
