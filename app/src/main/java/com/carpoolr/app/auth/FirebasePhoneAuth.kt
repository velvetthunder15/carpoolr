package com.carpoolr.app.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Firebase Phone Authentication gate run after Supabase sign-in and before opening CarpoolrApp.
 */
object FirebasePhoneAuth {
    private const val PREFS = "firebase_phone_gate"

    fun isConfigured(context: Context): Boolean = runCatching {
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }.getOrDefault(false)

    fun verifiedPhone(context: Context, supabaseUserId: String?): String? {
        if (supabaseUserId.isNullOrBlank()) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("verified_$supabaseUserId", null)?.takeIf { it.isNotBlank() }
    }

    fun isVerified(context: Context, supabaseUserId: String?): Boolean =
        verifiedPhone(context, supabaseUserId) != null

    fun markVerified(context: Context, supabaseUserId: String?, formattedPhone: String) {
        if (supabaseUserId.isNullOrBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("verified_$supabaseUserId", formattedPhone)
            .apply()
    }

    fun signOut(context: Context) {
        if (isConfigured(context)) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
    }

    fun sendOtp(
        activity: Activity,
        e164Phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        onCodeSent: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
        onInstantVerified: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured(activity)) {
            onError("firebase is not configured yet · add app/google-services.json")
            return
        }
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrElse {
            onError("couldn't start firebase phone auth")
            return
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { res ->
                        onInstantVerified(res.user?.phoneNumber ?: e164Phone)
                    }
                    .addOnFailureListener { e ->
                        onError(friendlyError(e))
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(friendlyError(e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId, token)
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resendToken != null) builder.setForceResendingToken(resendToken)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyOtp(
        context: Context,
        verificationId: String,
        code: String,
        fallbackPhone: String,
        onVerified: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured(context)) {
            onError("firebase is not configured yet")
            return
        }
        val credential = runCatching { PhoneAuthProvider.getCredential(verificationId, code) }.getOrElse {
            onError("invalid code format")
            return
        }
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { res ->
                onVerified(res.user?.phoneNumber ?: fallbackPhone)
            }
            .addOnFailureListener { e ->
                onError(friendlyError(e))
            }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "wrong or expired otp code"
        is FirebaseTooManyRequestsException -> "too many sms requests · wait a few minutes and retry"
        else -> {
            val m = e.message.orEmpty()
            when {
                m.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
                    "enable billing or add a test phone number in firebase auth console"
                m.contains("app is not authorized", ignoreCase = true) || m.contains("SHA", ignoreCase = true) ->
                    "register the apk sha-1 & sha-256 in firebase project settings"
                else -> "couldn't verify phone: " + m.lineSequence().firstOrNull().orEmpty().take(100).lowercase()
            }
        }
    }
}
