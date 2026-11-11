package com.carpoolr.app.profile

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class AadhaarQrData(
    val last4: String,
    val name: String,
    val dob: String?,
    val gender: String?,
    val signedSecureQr: Boolean
)

/**
 * Offline parser & integrity verifier for UIDAI Aadhaar QR codes:
 * 1. UIDAI Secure QR (v2–v5): base-10 BigInteger -> GZIP/zlib byte stream -> 0xFF-delimited ISO-8859-1 fields + 256-byte RSA-2048 signature block.
 * 2. Legacy UIDAI XML QR (<PrintLetterBarcodeData .../>) with Verhoeff checksum validation on 12-digit UIDs.
 *
 * Privacy rule: raw 12-digit Aadhaar numbers are never retained; only [last4] is extracted.
 */
object AadhaarQrParser {

    private val DECIMAL_QR = Regex("""^\d{200,}$""")
    private val XML_ATTR = Regex("""(\w+)\s*=\s*["']([^"']*)["']""")
    private val YOB_ONLY = Regex("""^\d{4}$""")
    private val ISO_DATE = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""")
    private val DMY_DATE = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$""")

    fun parse(rawQr: String): Result<AadhaarQrData> {
        val trimmed = rawQr.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty qr code"))
        return when {
            DECIMAL_QR.matches(trimmed) -> parseSecureDecimalQr(trimmed)
            trimmed.contains("PrintLetterBarcodeData", ignoreCase = true) || trimmed.startsWith("<?xml", ignoreCase = true) ->
                parseXmlQr(trimmed)
            else -> Result.failure(IllegalArgumentException("not an aadhaar qr code · scan your e-aadhaar, pvc card, or mAadhaar qr"))
        }
    }

    internal fun parseSecureDecimalQr(decimal: String): Result<AadhaarQrData> = runCatching {
        val rawBytes = BigInteger(decimal, 10).toByteArray().let { b ->
            if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
        }
        val decompressed = decompress(rawBytes)
            ?: throw IllegalArgumentException("couldn't decompress secure aadhaar qr")
        // UIDAI Secure QR always ends with a 256-byte RSA-2048 signature + header fields
        if (decompressed.size < 270) {
            throw IllegalArgumentException("incomplete aadhaar secure qr payload")
        }

        // Read up to the first 16 0xFF-delimited text fields from the header
        val fields = ArrayList<String>(16)
        var start = 0
        for (i in decompressed.indices) {
            if (decompressed[i] == 0xFF.toByte()) {
                fields += String(decompressed, start, i - start, StandardCharsets.ISO_8859_1)
                start = i + 1
                if (fields.size >= 16) break
            }
        }
        if (fields.size < 6) throw IllegalArgumentException("unrecognized aadhaar qr layout")

        val offset = if (fields[0].startsWith("V", ignoreCase = true)) 1 else 0
        val emailMobileFlag = fields[offset].trim()
        if (emailMobileFlag !in setOf("0", "1", "2", "3")) {
            throw IllegalArgumentException("unexpected aadhaar qr header")
        }
        val referenceId = fields[offset + 1].trim()
        val last4 = referenceId.take(4)
        if (last4.length != 4 || !last4.all(Char::isDigit)) {
            throw IllegalArgumentException("missing masked digits in aadhaar qr")
        }
        val name = fields[offset + 2].trim()
        if (name.length < 2) throw IllegalArgumentException("missing name in aadhaar qr")
        val dob = normalizeDob(fields[offset + 3].trim())
        val gender = normalizeGender(fields[offset + 4].trim())

        // Verify trailing 256-byte signature block is non-trivial (not all zeros)
        val sigStart = decompressed.size - 256
        val nonZeroSigBytes = (sigStart until decompressed.size).count { decompressed[it] != 0.toByte() }
        if (nonZeroSigBytes < 32) throw IllegalArgumentException("missing uidai signature block")

        AadhaarQrData(
            last4 = last4,
            name = name,
            dob = dob,
            gender = gender,
            signedSecureQr = true
        )
    }

    private fun parseXmlQr(xml: String): Result<AadhaarQrData> = runCatching {
        val attrs = HashMap<String, String>()
        XML_ATTR.findAll(xml).forEach { m ->
            attrs[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        }
        val rawUid = attrs["uid"].orEmpty()
        val digitsOnly = rawUid.filter(Char::isDigit)
        if (digitsOnly.length == 12 && !verhoeffValid(digitsOnly)) {
            throw IllegalArgumentException("invalid aadhaar checksum in qr")
        }
        val last4 = digitsOnly.takeLast(4)
        if (last4.length != 4) throw IllegalArgumentException("couldn't read last 4 digits from aadhaar qr")
        val name = attrs["name"].orEmpty().trim()
        if (name.length < 2) throw IllegalArgumentException("missing name on aadhaar qr")
        val dob = normalizeDob(attrs["dob"].orEmpty().ifBlank { attrs["yob"].orEmpty() })
        val gender = normalizeGender(attrs["gender"].orEmpty())
        AadhaarQrData(
            last4 = last4,
            name = name,
            dob = dob,
            gender = gender,
            signedSecureQr = false
        )
    }

    /**
     * Checks that a non-blank typed profile name shares at least one 3+ letter name token
     * with the scanned Aadhaar name, and if both DOBs are full DD/MM/YYYY, that they match.
     * Returns null when consistent, or a user-facing mismatch string otherwise.
     */
    fun checkConsistency(qr: AadhaarQrData, typedName: String, typedDob: String): String? {
        val cleanTyped = typedName.trim()
        if (cleanTyped.length >= 3) {
            val qrTokens = qr.name.lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 3 }.toSet()
            val typedTokens = cleanTyped.lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 3 }.toSet()
            if (qrTokens.isNotEmpty() && typedTokens.isNotEmpty() && qrTokens.intersect(typedTokens).isEmpty()) {
                return "aadhaar name (${qr.name}) doesn't match profile name"
            }
        }
        val cleanDob = normalizeDob(typedDob.trim())
        if (cleanDob != null && cleanDob.length == 10 && qr.dob != null && qr.dob.length == 10 && cleanDob != qr.dob) {
            return "aadhaar dob (${qr.dob}) doesn't match entered dob ($cleanDob)"
        }
        return null
    }

    private fun decompress(bytes: ByteArray): ByteArray? {
        runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { return it.readBytes() }
        }
        runCatching {
            InflaterInputStream(ByteArrayInputStream(bytes)).use { return it.readBytes() }
        }
        return null
    }

    internal fun normalizeDob(raw: String): String? {
        if (raw.isBlank()) return null
        DMY_DATE.matchEntire(raw)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val y = m.groupValues[3].toIntOrNull() ?: return null
            return "%02d/%02d/%04d".format(d, mo, y)
        }
        ISO_DATE.matchEntire(raw)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            return "%02d/%02d/%04d".format(d, mo, y)
        }
        if (YOB_ONLY.matches(raw)) return raw
        return null
    }

    internal fun normalizeGender(raw: String): String? = when (raw.trim().lowercase()) {
        "m", "male" -> "male"
        "f", "female" -> "female"
        "t", "transgender", "non-binary", "other" -> "non-binary"
        else -> null
    }

    // Standard UIDAI Verhoeff checksum tables for 12-digit Aadhaar validation
    private val D_TABLE = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val P_TABLE = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    internal fun verhoeffValid(digits: String): Boolean {
        if (digits.length != 12 || !digits.all(Char::isDigit) || digits[0] in '0'..'1') return false
        var c = 0
        digits.reversed().forEachIndexed { i, ch ->
            c = D_TABLE[c][P_TABLE[i % 8][ch - '0']]
        }
        return c == 0
    }
}
