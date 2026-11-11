package com.carpoolr.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * carpoolr's own Supabase project (ref tomkixjzyeaslofybxrw, ap-south-1).
 * The anon key is public by design; row level security guards the data.
 */
object Backend {
    private const val URL = "https://tomkixjzyeaslofybxrw.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRvbWtpeGp6eWVhc2xvZnlieHJ3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM3MDkxNTEsImV4cCI6MjEwOTI4NTE1MX0.1tCafouYacU1G_ScbWnq6r4YtRzBVdpOIrESeoYg_EY"

    val client: SupabaseClient by lazy {
        createSupabaseClient(URL, ANON_KEY) {
            install(Auth) {
                // Magic links in the sign-in email open carpoolr://auth
                scheme = "carpoolr"
                host = "auth"
            }
            install(Postgrest)
            install(Functions)
        }
    }

    /** Emails a 6-digit code (and a sign-in link) to [email]. Creates the account on first use. */
    suspend fun sendCode(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email
            createUser = true
        }
    }

    suspend fun verifyCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    }

    suspend fun signOut() = client.auth.signOut()

    /** My live trip and everyone pooled with me (empty when I have none). Also retries the match. */
    suspend fun myPool(): List<PoolRider> =
        client.postgrest.rpc("my_pool").decodeList<PoolRider>()

    suspend fun leavePool() {
        client.postgrest.rpc("leave_pool")
    }

    /** Booker only: the fare the ride app charged, which locks the pool. */
    suspend fun setBooking(fareRupees: Int, provider: String) {
        client.postgrest.rpc("set_booking", buildJsonObject { put("p_fare", fareRupees); put("p_provider", provider) })
    }

    suspend fun markPaid(poolId: String? = null) {
        if (poolId == null) client.postgrest.rpc("mark_paid")
        else client.postgrest.rpc("mark_paid", buildJsonObject { put("p_pool", poolId) })
    }
    suspend fun finishPool() { client.postgrest.rpc("finish_pool") }

    suspend fun myRides(): List<PastRide> = client.postgrest.rpc("my_rides").decodeList<PastRide>()

    suspend fun rideMessages(poolId: String): List<ChatMessage> =
        client.postgrest.rpc("ride_messages", buildJsonObject { put("p_pool", poolId) }).decodeList<ChatMessage>()

    suspend fun messages(after: Long): List<ChatMessage> =
        client.postgrest.rpc("pool_messages", buildJsonObject { put("p_after", after) }).decodeList<ChatMessage>()

    suspend fun sendChat(body: String) {
        client.postgrest.rpc("send_chat", buildJsonObject { put("p_body", body) })
    }

    suspend fun myProfile(): Profile? = client.postgrest.rpc("my_profile").decodeList<Profile>().firstOrNull()

    suspend fun updateProfile(p: Profile) {
        val fullParams = buildJsonObject {
            put("p_name", p.displayName.orEmpty())
            put("p_upi", p.upiId.orEmpty())
            put("p_upi_verified", p.upiVerified)
            put("p_phone", p.phone.orEmpty())
            put("p_dob", p.dob.orEmpty())
            put("p_gender", p.gender.orEmpty())
            put("p_org_tag", p.orgTag.orEmpty())
            put("p_emergency_name", p.emergencyName.orEmpty())
            put("p_emergency_phone", p.emergencyPhone.orEmpty())
            put("p_aadhaar_last4", p.aadhaarLast4.orEmpty())
            put("p_aadhaar_verified", p.aadhaarVerified)
            put("p_avatar_data", p.avatarData.orEmpty())
        }
        runCatching { client.postgrest.rpc("update_profile", fullParams) }.onFailure { e ->
            // Graceful fallback if migration 0006 has not been applied on the server yet
            if (e.message.orEmpty().contains("PGRST202") || e.message.orEmpty().contains("update_profile")) {
                client.postgrest.rpc(
                    "update_profile",
                    buildJsonObject {
                        put("p_name", p.displayName.orEmpty())
                        put("p_upi", p.upiId.orEmpty())
                    }
                )
            } else throw e
        }
    }

    suspend fun poolAvatars(poolId: String): Map<String, String> =
        runCatching {
            client.postgrest.rpc("pool_avatars", buildJsonObject { put("p_pool", poolId) })
                .decodeList<RiderAvatar>()
                .associate { it.riderId to it.avatarData }
        }.getOrDefault(emptyMap())

    /** Google Places through our edge function (key stays on the server). */
    suspend fun searchPlaces(query: String, lat: Double?, lng: Double?): List<PlaceHit> {
        val body = buildJsonObject {
            put("q", query); if (lat != null && lng != null) { put("lat", lat); put("lng", lng) }
        }
        val res = client.functions.invoke("places", body)
        if (res.status.value !in 200..299) throw IllegalStateException("places ${res.status.value}")
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<PlacesResponse>(res.bodyAsText()).places
    }

    /** Instant match: joins a pool with riders heading the same way right now, or starts searching. */
    suspend fun findPool(
        pickupLat: Double, pickupLng: Double, pickupLabel: String,
        dropLat: Double, dropLng: Double, dropLabel: String
    ): PoolMatch {
        val params = buildJsonObject {
            put("p_pickup_lat", pickupLat); put("p_pickup_lng", pickupLng); put("p_pickup_label", pickupLabel)
            put("p_drop_lat", dropLat); put("p_drop_lng", dropLng); put("p_drop_label", dropLabel)
        }
        return client.postgrest.rpc("find_pool", params).decodeList<PoolMatch>().first()
    }
}

@Serializable
data class PoolMatch(
    @SerialName("request_id") val requestId: String,
    @SerialName("pool_id") val poolId: String? = null,
    val status: String
)

@Serializable
data class PoolRider(
    @SerialName("request_id") val requestId: String,
    @SerialName("pool_id") val poolId: String? = null,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("rider_id") val riderId: String,
    @SerialName("is_me") val isMe: Boolean,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("pickup_label") val pickupLabel: String,
    @SerialName("dropoff_label") val dropoffLabel: String,
    @SerialName("pickup_lat") val pickupLat: Double,
    @SerialName("pickup_lng") val pickupLng: Double,
    @SerialName("drop_lat") val dropLat: Double,
    @SerialName("drop_lng") val dropLng: Double,
    @SerialName("trip_km") val tripKm: Double,
    @SerialName("board_km") val boardKm: Double = 0.0,
    @SerialName("alight_km") val alightKm: Double = 0.0,
    @SerialName("is_booker") val isBooker: Boolean = false,
    @SerialName("upi_id") val upiId: String? = null,
    val paid: Boolean = false,
    @SerialName("pool_status") val poolStatus: String? = null,
    @SerialName("fare_rupees") val fareRupees: Int? = null,
    val provider: String? = null,
    @SerialName("aadhaar_verified") val aadhaarVerified: Boolean = false,
    @SerialName("upi_verified") val upiVerified: Boolean = false,
    @SerialName("org_tag") val orgTag: String? = null
)

@Serializable
data class RideLeg(
    @SerialName("rider_id") val riderId: String,
    @SerialName("is_me") val isMe: Boolean = false,
    @SerialName("display_name") val displayName: String = "rider",
    @SerialName("board_km") val boardKm: Double = 0.0,
    @SerialName("alight_km") val alightKm: Double = 0.0,
    val paid: Boolean = false,
    @SerialName("is_booker") val isBooker: Boolean = false
)

@Serializable
data class PastRide(
    @SerialName("pool_id") val poolId: String,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("pickup_label") val pickupLabel: String,
    @SerialName("dropoff_label") val dropoffLabel: String,
    @SerialName("trip_km") val tripKm: Double,
    val riders: Int,
    @SerialName("fare_rupees") val fareRupees: Int? = null,
    val provider: String? = null,
    @SerialName("is_booker") val isBooker: Boolean = false,
    val paid: Boolean = true,
    @SerialName("booker_name") val bookerName: String? = null,
    @SerialName("booker_upi") val bookerUpi: String? = null,
    val legs: List<RideLeg> = emptyList()
)

@Serializable
data class ChatMessage(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("is_me") val isMe: Boolean,
    @SerialName("display_name") val displayName: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class Profile(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("upi_verified") val upiVerified: Boolean = false,
    val phone: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    @SerialName("org_tag") val orgTag: String? = null,
    @SerialName("emergency_name") val emergencyName: String? = null,
    @SerialName("emergency_phone") val emergencyPhone: String? = null,
    @SerialName("aadhaar_last4") val aadhaarLast4: String? = null,
    @SerialName("aadhaar_verified") val aadhaarVerified: Boolean = false,
    @SerialName("avatar_data") val avatarData: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("pools_count") val poolsCount: Int = 0,
    @SerialName("km_shared") val kmShared: Double = 0.0
)

@Serializable
data class RiderAvatar(
    @SerialName("rider_id") val riderId: String,
    @SerialName("avatar_data") val avatarData: String
)

@Serializable
data class PlaceHit(val name: String, val address: String = "", val lat: Double, val lng: Double)

@Serializable
data class PlacesResponse(val places: List<PlaceHit> = emptyList())
