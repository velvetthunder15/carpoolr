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
 * Reads fares off Uber / Ola / Rapido pages from network JSON and visible DOM text.
 * Filters out ETA lines, promo badges ("10% off"), taglines, and strikethrough original fares.
 */
object FareParser {
    private val RUPEES = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""")
    private val LABEL_KEYS = listOf("displayName", "productName", "vehicleViewName", "title", "name", "serviceName", "categoryName", "label")
    private val FARE_KEY = Regex("(fare|price|amount|estimate|cost|payable)", RegexOption.IGNORE_CASE)
    private val NUMERIC_FARE_KEY = Regex("^(fare|final_?fare|total_?fare|estimated_?fare|payable_?amount|final_?amount|price|estimated_?price)$", RegexOption.IGNORE_CASE)
    private val E5_FARE_KEY = Regex("(fare|amount|estimate|price).*e5$", RegexOption.IGNORE_CASE)

    private val ETA_OR_DIST = Regex("""\b(\d+\s*(min|mins|minute|minutes|hr|hrs|sec|km|m)\b|away\b|\d{1,2}:\d{2}\s*(am|pm)?\b)""", RegexOption.IGNORE_CASE)
    private val PROMO_OR_BADGE = Regex("""(%|\boff\b|\bsave\b|\bpromo\b|\bdiscount|\bfaster\b|\bcheaper\b|\bbest\s+value\b|\bapplied\b)""", RegexOption.IGNORE_CASE)
    private val UI_CHROME = Regex("""^(choose|select|book|confirm|payment|cash|personal|business|offers?|coupons?|pickup|drop|total|fare|wallet|upi|riding|recommended)\b""", RegexOption.IGNORE_CASE)
    private val TAGLINE_PROSE = Regex("""\b(affordable|compact|comfortable|comfy|doorstep|haggling|everyday|economical|drivers|top-quality|ac\s+cabs|rides\s+for|spacious)\b""", RegexOption.IGNORE_CASE)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun amount(s: String): Int? = RUPEES.find(s)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        ?.takeIf { it in 15.0..50000.0 }?.roundToInt()

    internal fun isProductLabel(s: String): Boolean {
        val t = s.trim()
        if (t.length !in 2..28) return false
        if (!t.any(Char::isLetter)) return false
        if (t.contains(',')) return false
        if (amount(t) != null) return false
        if (t.split(Regex("\\s+")).size > 4) return false
        if (ETA_OR_DIST.containsMatchIn(t)) return false
        if (PROMO_OR_BADGE.containsMatchIn(t)) return false
        if (UI_CHROME.containsMatchIn(t)) return false
        if (TAGLINE_PROSE.containsMatchIn(t)) return false
        return true
    }

    fun fromJson(text: String): List<FareOption> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<FareOption>()
        walk(root, out)
        return out.distinctBy { it.label.lowercase() }
    }

    private fun walk(e: JsonElement, out: MutableList<FareOption>) {
        when (e) {
            is JsonObject -> {
                val label = LABEL_KEYS.firstNotNullOfOrNull { k ->
                    (e[k] as? JsonPrimitive)?.contentOrNull?.takeIf(::isProductLabel)
                }
                if (label != null) {
                    val fare = extractFare(e)
                    if (fare != null) out += FareOption(label, fare)
                }
                e.values.forEach { walk(it, out) }
            }
            is JsonArray -> e.forEach { walk(it, out) }
            else -> Unit
        }
    }

    private fun extractFare(e: JsonObject): Int? {
        // 1. Direct string field carrying ₹ / Rs / INR
        e.entries.firstNotNullOfOrNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNull ?: return@firstNotNullOfOrNull null
            if (FARE_KEY.containsMatchIn(k) || s.trimStart().startsWith("₹")) amount(s) else null
        }?.let { return it }

        // 2. Nested 1-level object carrying ₹ / Rs / INR: { fare: { display: "₹212" } }
        e.entries.firstNotNullOfOrNull { (k, v) ->
            if (FARE_KEY.containsMatchIn(k) && v is JsonObject)
                v.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.let(::amount) }
            else null
        }?.let { return it }

        // 3. Micro-unit E5 numeric fields (e.g. fareAmountE5 = 21200000 -> ₹212)
        e.entries.firstNotNullOfOrNull { (k, v) ->
            if (!E5_FARE_KEY.containsMatchIn(k)) return@firstNotNullOfOrNull null
            (v as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }?.div(100_000.0)
                ?.takeIf { it in 15.0..50000.0 }?.roundToInt()
        }?.let { return it }

        // 4. Plain numeric rupee fields (direct or 1-level nested)
        e.entries.firstNotNullOfOrNull { (k, v) ->
            when {
                NUMERIC_FARE_KEY.matches(k) && v is JsonPrimitive ->
                    v.contentOrNull?.toDoubleOrNull()?.takeIf { it in 15.0..15000.0 }?.roundToInt()
                FARE_KEY.containsMatchIn(k) && v is JsonObject ->
                    v.entries.firstNotNullOfOrNull { (nk, nv) ->
                        if (NUMERIC_FARE_KEY.matches(nk) && nv is JsonPrimitive)
                            nv.contentOrNull?.toDoubleOrNull()?.takeIf { it in 15.0..15000.0 }?.roundToInt()
                        else null
                    }
                else -> null
            }
        }?.let { return it }

        return null
    }

    fun fromText(pageText: String): List<FareOption> {
        val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<FareOption>()
        lines.forEachIndexed { i, line ->
            val fare = amount(line) ?: return@forEachIndexed
            val rawInline = line.replace(RUPEES, " ").trim().trim('-', '·', ':', '•').trim()
            val inline = rawInline.takeIf(::isProductLabel)
            // Skip consecutive bare price line (strikethrough original price right after discounted price)
            if (inline == null && i > 0 && amount(lines[i - 1]) != null) return@forEachIndexed
            val label = inline ?: (i - 1 downTo maxOf(0, i - 5)).asSequence()
                .map { lines[it] }
                .firstOrNull(::isProductLabel)
            if (label != null) out += FareOption(label, fare)
        }
        return out.distinctBy { it.label.lowercase() }
    }
}
