package com.carpoolr.app.profile

import com.carpoolr.app.data.Profile
import com.carpoolr.app.data.ProfileRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreTest {
    @Test
    fun preservesVerifiedUpiAndRichFieldsOn0004Server() {
        val cached = Profile(
            displayName = "Chirag Rasane",
            upiId = "chirag@okaxis",
            upiVerified = true,
            phone = "+91 98765 43210",
            dob = "15/08/2005",
            aadhaarLast4 = "4821",
            aadhaarVerified = true
        )
        // Server on migration 0004 omits upi_verified (null) and rich columns
        val remote0004 = ProfileRow(
            displayName = "Chirag Rasane",
            upiId = "chirag@okaxis",
            upiVerified = null
        )
        val merged = ProfileStore.merge(remote0004, cached, alreadySynced = false)
        assertTrue(merged.profile!!.upiVerified)
        assertEquals("chirag@okaxis", merged.profile.upiId)
        assertEquals("+91 98765 43210", merged.profile.phone)
        assertTrue(merged.profile.aadhaarVerified)
        assertFalse(merged.needsRemoteSync)
    }

    @Test
    fun promotesPre0006VerifiedStateWhenMigration0006FirstGoesLive() {
        val cached = Profile(
            displayName = "Chirag Rasane",
            upiId = "chirag@okaxis",
            upiVerified = true,
            phone = "+91 98765 43210"
        )
        // Right after 0006 runs, upi_verified defaults to false on existing row
        val remote0006Fresh = ProfileRow(
            displayName = "Chirag Rasane",
            upiId = "chirag@okaxis",
            upiVerified = false
        )
        val merged = ProfileStore.merge(remote0006Fresh, cached, alreadySynced = false)
        assertTrue(merged.profile!!.upiVerified)
        assertEquals("+91 98765 43210", merged.profile.phone)
        assertTrue(merged.needsRemoteSync)
    }

    @Test
    fun respectsClearedFieldsOnceSyncedTo0006() {
        val cached = Profile(
            displayName = "Chirag Rasane",
            upiId = "chirag@okaxis",
            upiVerified = true
        )
        val remoteCleared = ProfileRow(
            displayName = "Chirag Rasane",
            upiId = null,
            upiVerified = false
        )
        val merged = ProfileStore.merge(remoteCleared, cached, alreadySynced = true)
        assertFalse(merged.profile!!.upiVerified)
        assertEquals(null, merged.profile.upiId)
        assertFalse(merged.needsRemoteSync)
    }
}
