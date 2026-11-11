package com.carpoolr.app.profile

import android.content.Context
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.Profile
import com.carpoolr.app.data.ProfileRow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Per-user local cache + serialized server sync for [Profile].
 * Ensures verified UPI VPAs, photos, and personal details persist immediately (0ms) on verify/save,
 * never race with drawer close/reopen fetches, and survive even before migration 0006 runs on Supabase.
 */
object ProfileStore {
    private const val PREFS = "profile_store"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private fun keyFor(userId: String?): String = userId?.takeIf { it.isNotBlank() } ?: "current"

    fun loadCached(context: Context, userId: String? = Backend.currentUserId()): Profile? {
        val k = keyFor(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("profile_$k", null) ?: return null
        return runCatching { json.decodeFromString(Profile.serializer(), raw) }.getOrNull()
    }

    fun saveCached(context: Context, profile: Profile, userId: String? = Backend.currentUserId(), syncedRichSchema: Boolean? = null) {
        val k = keyFor(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = runCatching { json.encodeToString(Profile.serializer(), profile) }.getOrNull() ?: return
        prefs.edit().apply {
            putString("profile_$k", encoded)
            if (syncedRichSchema != null) putBoolean("synced_$k", syncedRichSchema)
        }.apply()
    }

    private fun isSyncedRichSchema(context: Context, userId: String?): Boolean {
        val k = keyFor(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("synced_$k", false)
    }

    fun clearAll(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    /**
     * Writes locally right away, then pushes to Supabase under [mutex] so a concurrent drawer reopen
     * never overwrites an in-flight verification or profile edit.
     */
    suspend fun persist(context: Context, updated: Profile): Result<Profile> {
        val uid = Backend.currentUserId()
        saveCached(context, updated, uid, syncedRichSchema = false)
        return mutex.withLock {
            runCatching {
                val richOk = Backend.updateProfile(updated)
                saveCached(context, updated, uid, syncedRichSchema = richOk)
                updated
            }
        }
    }

    /**
     * Fetches `my_profile()` under [mutex] and merges with local per-user state.
     */
    suspend fun syncFromServer(context: Context): Profile? = mutex.withLock {
        val uid = Backend.currentUserId()
        val cached = loadCached(context, uid)
        val alreadySynced = isSyncedRichSchema(context, uid)
        val remote = runCatching { Backend.myProfileRow() }.getOrElse { return@withLock cached }
        val res = merge(remote, cached, alreadySynced)
        val merged = res.profile ?: return@withLock null
        if (res.needsRemoteSync) {
            val ok = runCatching { Backend.updateProfile(merged) }.getOrDefault(false)
            saveCached(context, merged, uid, syncedRichSchema = ok)
        } else {
            val richLive = remote?.upiVerified != null
            saveCached(context, merged, uid, syncedRichSchema = if (richLive) true else null)
        }
        merged
    }

    data class MergeResult(val profile: Profile?, val needsRemoteSync: Boolean)

    /**
     * Merges the server's `my_profile()` row with local per-user state.
     * - When `remote.upiVerified == null`, the server is still on migration 0004 (only `display_name` + `upi_id`),
     *   so local `upiVerified` and rich profile fields are preserved.
     * - When `remote.upiVerified != null`, migration 0006 is live: if local changes were saved while on 0004
     *   (`[alreadySynced]` == false), they are merged once and flagged with `needsRemoteSync = true`.
     */
    internal fun merge(remote: ProfileRow?, cached: Profile?, alreadySynced: Boolean): MergeResult {
        if (remote == null) return MergeResult(cached, needsRemoteSync = false)

        // Migration 0004 server: only display_name and upi_id exist remotely
        if (remote.upiVerified == null) {
            val remoteUpi = remote.upiId?.takeIf { it.isNotBlank() }
            val effectiveUpi = remoteUpi ?: cached?.upiId?.takeIf { it.isNotBlank() }
            val verified = !effectiveUpi.isNullOrBlank() &&
                cached?.upiVerified == true &&
                cached.upiId?.equals(effectiveUpi, ignoreCase = true) == true
            return MergeResult(
                profile = Profile(
                    displayName = remote.displayName ?: cached?.displayName,
                    upiId = effectiveUpi,
                    upiVerified = verified,
                    phone = cached?.phone,
                    dob = cached?.dob,
                    gender = cached?.gender,
                    orgTag = cached?.orgTag,
                    emergencyName = cached?.emergencyName,
                    emergencyPhone = cached?.emergencyPhone,
                    aadhaarLast4 = cached?.aadhaarLast4,
                    aadhaarVerified = cached?.aadhaarVerified == true && !cached.aadhaarLast4.isNullOrBlank(),
                    avatarData = cached?.avatarData,
                    createdAt = cached?.createdAt,
                    poolsCount = cached?.poolsCount ?: 0,
                    kmShared = cached?.kmShared ?: 0.0
                ),
                needsRemoteSync = false
            )
        }

        val remoteProfile = Profile(
            displayName = remote.displayName,
            upiId = remote.upiId,
            upiVerified = remote.upiVerified && !remote.upiId.isNullOrBlank(),
            phone = remote.phone,
            dob = remote.dob,
            gender = remote.gender,
            orgTag = remote.orgTag,
            emergencyName = remote.emergencyName,
            emergencyPhone = remote.emergencyPhone,
            aadhaarLast4 = remote.aadhaarLast4,
            aadhaarVerified = remote.aadhaarVerified == true && !remote.aadhaarLast4.isNullOrBlank(),
            avatarData = remote.avatarData,
            createdAt = remote.createdAt,
            poolsCount = remote.poolsCount,
            kmShared = remote.kmShared
        )

        if (cached == null || alreadySynced) {
            return MergeResult(remoteProfile, needsRemoteSync = false)
        }

        // First load after migration 0006 goes live: promote any pre-0006 local fields to server
        val effectiveUpi = remoteProfile.upiId ?: cached.upiId
        val effectiveUpiVerified = !effectiveUpi.isNullOrBlank() && (
            remoteProfile.upiVerified ||
                (cached.upiVerified && cached.upiId?.equals(effectiveUpi, ignoreCase = true) == true)
            )
        val effectiveLast4 = remoteProfile.aadhaarLast4 ?: cached.aadhaarLast4
        val effectiveAadhaarVerified = !effectiveLast4.isNullOrBlank() &&
            (remoteProfile.aadhaarVerified || cached.aadhaarVerified)

        val merged = remoteProfile.copy(
            displayName = remoteProfile.displayName ?: cached.displayName,
            upiId = effectiveUpi,
            upiVerified = effectiveUpiVerified,
            phone = remoteProfile.phone ?: cached.phone,
            dob = remoteProfile.dob ?: cached.dob,
            gender = remoteProfile.gender ?: cached.gender,
            orgTag = remoteProfile.orgTag ?: cached.orgTag,
            emergencyName = remoteProfile.emergencyName ?: cached.emergencyName,
            emergencyPhone = remoteProfile.emergencyPhone ?: cached.emergencyPhone,
            aadhaarLast4 = effectiveLast4,
            aadhaarVerified = effectiveAadhaarVerified,
            avatarData = remoteProfile.avatarData ?: cached.avatarData
        )
        return MergeResult(merged, needsRemoteSync = merged != remoteProfile)
    }
}
