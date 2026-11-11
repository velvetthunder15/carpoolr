package com.carpoolr.app.auth

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Coordinates the brief window during SignInScreen when carpoolr listens for an incoming
 * 6-digit email OTP notification. Outside sign-in, [awaitingEmail] is null and notifications
 * are ignored immediately.
 */
object OtpWatcher {
    @Volatile var awaitingEmail: String? = null
        private set
    @Volatile var startedAtMs: Long = 0L
        private set

    private val _codes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val codes: SharedFlow<String> = _codes.asSharedFlow()

    fun start(email: String) {
        awaitingEmail = email.trim()
        startedAtMs = System.currentTimeMillis()
        EmailOtpNotificationService.scanActive()
    }

    fun stop() {
        awaitingEmail = null
    }

    internal fun emit(code: String) {
        if (awaitingEmail != null) _codes.tryEmit(code)
    }
}

/**
 * Picks up 6-digit sign-in codes from email notifications (Gmail, Outlook, ProtonMail, Samsung Email, etc.)
 * only while the rider is actively on the OTP step of SignInScreen.
 */
class EmailOtpNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        if (OtpWatcher.awaitingEmail != null) scanActiveNotifications()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        inspect(sbn)
    }

    private fun scanActiveNotifications() {
        runCatching { activeNotifications?.forEach(::inspect) }
    }

    private fun inspect(sbn: StatusBarNotification) {
        val targetEmail = OtpWatcher.awaitingEmail ?: return
        if (sbn.packageName == packageName) return
        // Ignore stale notifications that arrived well before the user requested a code.
        if (sbn.postTime < OtpWatcher.startedAtMs - 60_000L) return

        val extras = sbn.notification?.extras ?: return
        val combined = extractNotificationText(extras)
        if (combined.isBlank()) return

        val code = extractEmailOtp(combined, targetEmail) ?: return
        OtpWatcher.emit(code)
    }

    companion object {
        @Volatile private var instance: EmailOtpNotificationService? = null

        private val OTP_CONTEXT = Regex(
            """\b(carpoolr|supabase|verification|verify|otp|code|passcode|sign[\s-]?in|log[\s-]?in|magic\s+link)\b""",
            RegexOption.IGNORE_CASE
        )
        private val NEAR_CODE = Regex(
            """(?:code|otp|passcode|pin|token)(?:\s+is|\s*:)?\s*(\d{6})(?!\d)|(?<!\d)(\d{6})\s+(?:is\s+your|verification|sign[\s-]?in|login|code)""",
            RegexOption.IGNORE_CASE
        )
        private val SIX_DIGITS = Regex("""(?<!\d)(\d{6})(?!\d)""")

        internal fun scanActive() {
            instance?.scanActiveNotifications()
        }

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun openNotificationAccessSettings(context: Context) {
            val cn = ComponentName(context, EmailOtpNotificationService::class.java).flattenToString()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                putExtra(":settings:fragment_args_key", cn)
                putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", cn) })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        fun openAppInfoSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        internal fun extractNotificationText(extras: Bundle): String {
            val parts = ArrayList<String>(8)
            fun addSeq(key: String) {
                extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
            addSeq(Notification.EXTRA_TITLE)
            addSeq(Notification.EXTRA_SUB_TEXT)
            addSeq(Notification.EXTRA_BIG_TEXT)
            addSeq(Notification.EXTRA_TEXT)
            addSeq(Notification.EXTRA_SUMMARY_TEXT)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
                line?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
            return parts.joinToString("\n")
        }

        /**
         * Extracts a 6-digit OTP from notification text only when auth/email context is present,
         * preferring digits directly adjacent to "code" / "otp" phrasing.
         */
        internal fun extractEmailOtp(text: String, targetEmail: String? = null): String? {
            val hasContext = OTP_CONTEXT.containsMatchIn(text) ||
                (!targetEmail.isNullOrBlank() && text.contains(targetEmail, ignoreCase = true) && text.contains("code", ignoreCase = true))
            if (!hasContext) return null

            NEAR_CODE.find(text)?.let { m ->
                val hit = m.groupValues[1].ifEmpty { m.groupValues[2] }
                if (hit.length == 6) return hit
            }
            return SIX_DIGITS.find(text)?.groupValues?.get(1)
        }
    }
}
