package com.carpoolr.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

class AadhaarQrParserTest {

    @Test
    fun parsesSignedUidaiSecureQrV2() {
        val fields = listOf(
            "V2",
            "3",
            "482120261111123000123",
            "Chirag Rasane",
            "15-08-2004",
            "M",
            "", "Mumbai", "", "12", "Powai", "400076", "IIT Powai", "Maharashtra", "Main Rd", "Kurla"
        )
        val out = ByteArrayOutputStream()
        fields.forEach { f ->
            out.write(f.toByteArray(StandardCharsets.ISO_8859_1))
            out.write(0xFF)
        }
        // Simulated photo + 256-byte non-zero RSA signature tail
        out.write(ByteArray(64) { 0x42 })
        out.write(ByteArray(256) { ((it * 37 + 13) and 0xFF).toByte() })

        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(out.toByteArray()) }
        val decimalQr = BigInteger(1, gz.toByteArray()).toString(10)

        val parsed = AadhaarQrParser.parse(decimalQr).getOrThrow()
        assertEquals("4821", parsed.last4)
        assertEquals("Chirag Rasane", parsed.name)
        assertEquals("15/08/2004", parsed.dob)
        assertEquals("male", parsed.gender)
        assertTrue(parsed.signedSecureQr)
        assertNull(AadhaarQrParser.checkConsistency(parsed, "Chirag", "15/08/2004"))
    }

    @Test
    fun parsesMaskedXmlQrAndDetectsNameMismatch() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><PrintLetterBarcodeData uid="XXXXXXXX9134" name="Aarav Sharma" gender="M" dob="2001-04-09"/>"""
        val parsed = AadhaarQrParser.parse(xml).getOrThrow()
        assertEquals("9134", parsed.last4)
        assertEquals("Aarav Sharma", parsed.name)
        assertEquals("09/04/2001", parsed.dob)
        assertEquals("male", parsed.gender)
        assertTrue(AadhaarQrParser.checkConsistency(parsed, "Chirag Rasane", "09/04/2001") != null)
    }
}
