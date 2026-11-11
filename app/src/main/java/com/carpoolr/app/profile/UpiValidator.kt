package com.carpoolr.app.profile

import java.time.LocalDate
import java.time.Period

sealed interface UpiCheck {
    data class Valid(
        val normalized: String,
        val providerLabel: String,
        val knownHandle: Boolean
    ) : UpiCheck

    data class Invalid(val reason: String) : UpiCheck
}

/**
 * Zero-cost offline UPI VPA & profile field validator following NPCI handle conventions.
 */
object UpiValidator {

    private val LOCAL_PART = Regex("""^[a-z0-9][a-z0-9._-]{0,62}[a-z0-9]$""")
    private val HANDLE_PART = Regex("""^[a-z][a-z0-9]{1,31}$""")
    private val INDIAN_MOBILE = Regex("""^(?:(?:\+?91|0)[\s-]?)?([6-9]\d{9})$""")
    private val DOB_PATTERN = Regex("""^(\d{1,2})[/.\-\s](\d{1,2})[/.\-\s](\d{4})$""")
    private val DOB_COMPACT = Regex("""^(\d{2})(\d{2})(\d{4})$""")

    private val KNOWN_HANDLES: Map<String, String> = mapOf(
        // Google Pay
        "okaxis" to "google pay · axis",
        "okhdfcbank" to "google pay · hdfc",
        "okicici" to "google pay · icici",
        "oksbi" to "google pay · sbi",
        // PhonePe
        "ybl" to "phonepe · yes bank",
        "ibl" to "phonepe · icici",
        "axl" to "phonepe · axis",
        // Paytm
        "paytm" to "paytm",
        "ptyes" to "paytm · yes bank",
        "ptaxis" to "paytm · axis",
        "pthdfc" to "paytm · hdfc",
        "ptsbi" to "paytm · sbi",
        // BHIM / Amazon / CRED / Super.money / Navi / Slice / Jupiter / FamPay / WhatsApp
        "upi" to "bhim upi",
        "apl" to "amazon pay",
        "yapl" to "amazon pay · yes bank",
        "rapl" to "amazon pay · rbl",
        "tapicici" to "cred · icici",
        "credaxis" to "cred · axis",
        "superyes" to "super.money · yes bank",
        "superaxis" to "super.money · axis",
        "naviaxis" to "navi · axis",
        "sliceaxis" to "slice · axis",
        "slc" to "slice",
        "jupiteraxis" to "jupiter · axis",
        "fbl" to "federal bank / jupiter",
        "federal" to "federal bank",
        "yespop" to "pop · yes bank",
        "oneyes" to "onecard · yes bank",
        "niyoicici" to "niyo · icici",
        "fam" to "fampay",
        "freecharge" to "freecharge · axis",
        "fc" to "freecharge",
        "ikwik" to "mobikwik",
        "mobikwik" to "mobikwik",
        "jio" to "jiopay",
        "airtel" to "airtel payments bank",
        "airtelpaymentsbank" to "airtel payments bank",
        "waicici" to "whatsapp · icici",
        "wahdfcbank" to "whatsapp · hdfc",
        "wasbi" to "whatsapp · sbi",
        "waaxis" to "whatsapp · axis",
        // Direct Bank Handles
        "sbi" to "state bank of india",
        "hdfcbank" to "hdfc bank",
        "icici" to "icici bank",
        "axisbank" to "axis bank",
        "axisb" to "axis bank",
        "kotak" to "kotak bank",
        "kmbl" to "kotak bank",
        "indus" to "indusind bank",
        "yesbank" to "yes bank",
        "yesg" to "yes bank",
        "idfcfirst" to "idfc first bank",
        "idfcbank" to "idfc first bank",
        "barodampay" to "bank of baroda",
        "pnb" to "punjab national bank",
        "cnrb" to "canara bank",
        "uboi" to "union bank",
        "unionbank" to "union bank",
        "rbl" to "rbl bank",
        "hsbc" to "hsbc",
        "sc" to "standard chartered",
        "scbl" to "standard chartered",
        "dbs" to "dbs bank",
        "equitas" to "equitas bank",
        "aubank" to "au small finance bank",
        "au" to "au small finance bank",
        "db" to "deutsche bank",
        "boi" to "bank of india",
        "cbin" to "central bank of india",
        "indianbank" to "indian bank",
        "mahb" to "bank of maharashtra",
        "kvb" to "karur vysya bank",
        "sib" to "south indian bank",
        "cub" to "city union bank",
        "uco" to "uco bank",
        "bandhan" to "bandhan bank",
        "indie" to "indusind indie",
        "postbank" to "india post payments bank"
    )

    fun verify(raw: String): UpiCheck {
        val cleaned = raw.trim().lowercase()
        if (cleaned.isEmpty()) return UpiCheck.Invalid("enter your upi id")
        if (cleaned.any(Char::isWhitespace)) return UpiCheck.Invalid("upi id can't contain spaces")
        val parts = cleaned.split('@')
        if (parts.size != 2) return UpiCheck.Invalid("upi id needs one @ (like name@okaxis)")
        val (local, handle) = parts
        if (handle.contains('.')) {
            return UpiCheck.Invalid("that looks like an email · enter your upi handle (like @okaxis or @ybl)")
        }
        if (!LOCAL_PART.matches(local) || local.contains("..")) {
            return UpiCheck.Invalid("the part before @ doesn't look valid")
        }
        if (!HANDLE_PART.matches(handle)) {
            return UpiCheck.Invalid("the bank handle after @ doesn't look valid")
        }
        val known = KNOWN_HANDLES[handle]
        if (known != null) {
            return UpiCheck.Valid(cleaned, known, knownHandle = true)
        }
        if (handle.length < 3) {
            return UpiCheck.Invalid("unknown 2-letter handle @$handle")
        }
        return UpiCheck.Valid(cleaned, "@$handle upi handle", knownHandle = false)
    }

    /** Validates DD/MM/YYYY, DD-MM-YYYY, or DDMMYYYY and enforces age 18..100. Returns normalized DD/MM/YYYY + age. */
    fun parseAdultDob(raw: String, today: LocalDate = LocalDate.now()): Result<Pair<String, Int>> {
        val trimmed = raw.trim()
        val m = DOB_PATTERN.matchEntire(trimmed) ?: DOB_COMPACT.matchEntire(trimmed)
            ?: return Result.failure(IllegalArgumentException("use dd/mm/yyyy (like 15/08/2004)"))
        val day = m.groupValues[1].toIntOrNull() ?: return Result.failure(IllegalArgumentException("invalid day"))
        val month = m.groupValues[2].toIntOrNull() ?: return Result.failure(IllegalArgumentException("invalid month"))
        val year = m.groupValues[3].toIntOrNull() ?: return Result.failure(IllegalArgumentException("invalid year"))
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("that calendar date doesn't exist"))
        val age = Period.between(date, today).years
        if (age < 18) return Result.failure(IllegalArgumentException("riders must be 18 or older"))
        if (age > 100 || date.isAfter(today)) return Result.failure(IllegalArgumentException("check your birth year"))
        val formatted = "%02d/%02d/%04d".format(day, month, year)
        return Result.success(formatted to age)
    }

    /** Formats an Indian mobile number as +91 XXXXX XXXXX, or returns null when invalid. */
    fun normalizeIndianPhone(raw: String): String? {
        val compact = raw.trim().replace(" ", "").replace("-", "")
        val ten = INDIAN_MOBILE.matchEntire(compact)?.groupValues?.get(1) ?: return null
        return "+91 ${ten.substring(0, 5)} ${ten.substring(5)}"
    }
}
