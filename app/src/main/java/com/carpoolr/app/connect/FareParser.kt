package com.carpoolr.app.connect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt

/** A ride option seen on the ride app's page: "Uber Go" for ₹212. */
data class FareOption(val label: String, val rupees: Int)

/**
 * Reads fares off Uber / Ola / Rapido pages without knowing their exact formats:
 * from the JSON their web apps fetch (any ₹ string next to a product name), and from the
 * visible page text (a ₹ line under a short name line).
 */
object FareParser {
    private val RUPEES = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""")
    private val LABEL_KEYS = listOf("displayName", "productName", "vehicleViewName", "title", "name", "serviceName", "categoryName", "label")
    private val FARE_KEY = Regex("(fare|price|amount|estimate|cost)", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun amount(s: String): Int? = RUPEES.find(s)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        ?.takeIf { it in 15.0..50000.0 }?.roundToInt()

    fun fromJson(text: String): List<FareOption> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<FareOption>()
        walk(root, out)
        return out.distinctBy { it.label.lowercase() to it.rupees }
    }

    private fun walk(e: JsonElement, out: MutableList<FareOption>) {
        when (e) {
            is JsonObject -> {
                val label = LABEL_KEYS.firstNotNullOfOrNull { k ->
                    (e[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it.length <= 40 && amount(it) == null }
                }
                if (label != null) {
                    val fare = e.entries.firstNotNullOfOrNull { (k, v) ->
                        val s = (v as? JsonPrimitive)?.contentOrNull ?: return@firstNotNullOfOrNull null
                        if (FARE_KEY.containsMatchIn(k) || s.trimStart().startsWith("₹")) amount(s) else null
                    } ?: e.entries.firstNotNullOfOrNull { (k, v) ->
                        // fare nested one level down: { fare: { display: "₹212" } }
                        if (FARE_KEY.containsMatchIn(k) && v is JsonObject)
                            v.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.let(::amount) } else null
                    }
                    if (fare != null) out += FareOption(label, fare)
                }
                e.values.forEach { walk(it, out) }
            }
            is JsonArray -> e.forEach { walk(it, out) }
            else -> Unit
        }
    }

    fun fromText(pageText: String): List<FareOption> {
        val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<FareOption>()
        lines.forEachIndexed { i, line ->
            val fare = amount(line) ?: return@forEachIndexed
            val inline = line.substringBefore("₹").trim().takeIf { it.length in 2..30 && it.any(Char::isLetter) }
            val label = inline ?: (i - 1 downTo maxOf(0, i - 3)).asSequence().map { lines[it] }
                .firstOrNull { it.length in 2..30 && it.any(Char::isLetter) && amount(it) == null && !it.contains("min", true) }
            if (label != null) out += FareOption(label, fare)
        }
        return out.distinctBy { it.label.lowercase() to it.rupees }
    }
}
