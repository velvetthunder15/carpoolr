package com.carpoolr.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpiValidatorTest {
    @Test
    fun recognizesKnownIndianHandles() {
        val gpay = UpiValidator.verify("Chirag@okaxis")
        assertTrue(gpay is UpiCheck.Valid)
        gpay as UpiCheck.Valid
        assertEquals("chirag@okaxis", gpay.normalized)
        assertEquals("google pay · axis", gpay.providerLabel)
        assertTrue(gpay.knownHandle)

        val phonepe = UpiValidator.verify("9876543210@ybl") as UpiCheck.Valid
        assertEquals("phonepe · yes bank", phonepe.providerLabel)
    }

    @Test
    fun rejectsEmailAddressesAndMalformedVpas() {
        val email = UpiValidator.verify("chirag@gmail.com")
        assertTrue(email is UpiCheck.Invalid)
        assertTrue((email as UpiCheck.Invalid).reason.contains("email"))

        assertTrue(UpiValidator.verify("missingat") is UpiCheck.Invalid)
        assertTrue(UpiValidator.verify("bad..dots@okicici") is UpiCheck.Invalid)
    }

    @Test
    fun validatesAdultDobAndIndianPhone() {
        val today = LocalDate.of(2026, 11, 11)
        val ok = UpiValidator.parseAdultDob("15/8/2004", today).getOrThrow()
        assertEquals("15/08/2004", ok.first)
        assertEquals(22, ok.second)
        assertEquals("15/08/2005", UpiValidator.parseAdultDob("15082005", today).getOrThrow().first)

        assertTrue(UpiValidator.parseAdultDob("01/01/2012", today).isFailure)
        assertEquals("+91 98765 43210", UpiValidator.normalizeIndianPhone("9876543210"))
        assertEquals("+91 98765 43210", UpiValidator.normalizeIndianPhone("+91-98765-43210"))
        assertNull(UpiValidator.normalizeIndianPhone("12345"))
    }
}
