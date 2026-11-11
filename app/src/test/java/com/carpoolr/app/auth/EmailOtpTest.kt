package com.carpoolr.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailOtpTest {
    @Test
    fun extractsSupabaseEmailCode() {
        val notif = "Confirm your sign in\nYour verification code is 482910. Enter it in carpoolr."
        assertEquals("482910", EmailOtpNotificationService.extractEmailOtp(notif, "rider@gmail.com"))
    }

    @Test
    fun prefersCodeAdjacentToKeywordOverOrderNumbers() {
        val notif = "Ref 991204 · carpoolr sign-in\n830412 is your verification code"
        assertEquals("830412", EmailOtpNotificationService.extractEmailOtp(notif, "rider@gmail.com"))
    }

    @Test
    fun ignoresUnrelatedSixDigitEmails() {
        val notif = "Amazon.in shipped order 554321\nArriving tomorrow by 9pm"
        assertNull(EmailOtpNotificationService.extractEmailOtp(notif, "rider@gmail.com"))
    }
}
