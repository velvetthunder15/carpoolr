package com.carpoolr.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
    @SerialName("trip_km") val tripKm: Double
)
