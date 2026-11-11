package com.carpoolr.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.auth.FirebasePhoneAuth
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.Profile
import com.carpoolr.app.profile.ProfileStore
import com.carpoolr.app.profile.UpiValidator
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.google.firebase.auth.PhoneAuthProvider
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mandatory Firebase SMS OTP gate before entering CarpoolrApp.
 */
@Composable
fun PhoneVerifyScreen(
    activity: Activity,
    userId: String?,
    onVerified: () -> Unit,
    onSkipUnconfigured: () -> Unit
) {
    val configured = remember { FirebasePhoneAuth.isConfigured(activity) }
    val scope = rememberCoroutineScope()

    var digits by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown--
        }
    }

    fun finishVerification(rawPhone: String) {
        val normalized = UpiValidator.normalizeIndianPhone(rawPhone) ?: "+91 ${digits.take(5)} ${digits.drop(5)}"
        FirebasePhoneAuth.markVerified(activity, userId, normalized)
        scope.launch {
            val base = ProfileStore.loadCached(activity, userId) ?: Profile()
            ProfileStore.persist(activity, base.copy(phone = normalized))
            busy = false
            onVerified()
        }
    }

    fun requestOtp() {
        val normalized = UpiValidator.normalizeIndianPhone(digits)
        if (normalized == null) {
            error = "enter a valid 10-digit indian mobile number"
            return
        }
        val e164 = "+91" + digits.filter(Char::isDigit).takeLast(10)
        busy = true
        error = null
        FirebasePhoneAuth.sendOtp(
            activity = activity,
            e164Phone = e164,
            resendToken = resendToken,
            onCodeSent = { vId, token ->
                verificationId = vId
                resendToken = token
                cooldown = 30
                busy = false
            },
            onInstantVerified = { phone ->
                finishVerification(phone)
            },
            onError = { msg ->
                busy = false
                error = msg
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CarpoolrColors.Green)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.ic_route_mark_color),
            contentDescription = null,
            modifier = Modifier.size(width = 56.dp, height = 35.dp)
        )
        Spacer(Modifier.height(14.dp))
        BasicText("verify phone", style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton))
        Spacer(Modifier.height(6.dp))
        BasicText(
            "confirm your mobile number with a one-time sms code before pooling.",
            style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)
        )
        Spacer(Modifier.height(24.dp))

        if (verificationId == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("+91", style = TextStyle(CarpoolrColors.CreamMuted, 17.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (digits.isEmpty()) {
                        BasicText("98765 43210", style = TextStyle(CarpoolrColors.CreamFaint, 17.sp))
                    }
                    BasicTextField(
                        value = digits,
                        onValueChange = {
                            digits = it.filter(Char::isDigit).take(10)
                            error = null
                        },
                        singleLine = true,
                        textStyle = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            VerifyPill(
                text = if (busy) "sending sms…" else "send otp",
                enabled = !busy && digits.length == 10 && configured,
                onClick = ::requestOtp
            )
            if (!configured) {
                Spacer(Modifier.height(12.dp))
                BasicText(
                    "firebase config (app/google-services.json) hasn't been added yet.",
                    style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
                )
                Spacer(Modifier.height(10.dp))
                VerifyPill(
                    text = "continue while setting up firebase",
                    enabled = true,
                    color = CarpoolrColors.Cream.copy(alpha = 0.14f),
                    onClick = onSkipUnconfigured
                )
            }
        } else {
            BasicText(
                "sms sent to +91 ${digits.take(5)} ${digits.drop(5)}",
                style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                if (code.isEmpty()) {
                    BasicText("6-digit sms otp", style = TextStyle(CarpoolrColors.CreamFaint, 17.sp))
                }
                BasicTextField(
                    value = code,
                    onValueChange = { v ->
                        val clean = v.filter(Char::isDigit).take(6)
                        code = clean
                        error = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            VerifyPill(
                text = if (busy) "verifying…" else "verify & open carpoolr",
                enabled = !busy && code.length == 6
            ) {
                val vId = verificationId ?: return@VerifyPill
                busy = true
                error = null
                FirebasePhoneAuth.verifyOtp(
                    context = activity,
                    verificationId = vId,
                    code = code,
                    fallbackPhone = "+91$digits",
                    onVerified = ::finishVerification,
                    onError = { msg ->
                        busy = false
                        error = msg
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BasicText(
                    if (cooldown > 0) "resend in ${cooldown}s" else "resend code",
                    style = TextStyle(
                        if (cooldown > 0 || busy) CarpoolrColors.CreamFaint else CarpoolrColors.Cream,
                        14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.clickable(enabled = cooldown == 0 && !busy, onClick = ::requestOtp)
                )
                BasicText(
                    "change number",
                    style = TextStyle(CarpoolrColors.CreamMuted, 14.sp),
                    modifier = Modifier.clickable {
                        verificationId = null
                        code = ""
                        error = null
                    }
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            BasicText(it, style = TextStyle(CarpoolrColors.Raspberry, 14.sp, fontWeight = FontWeight.Medium))
        }

        Spacer(Modifier.height(24.dp))
        BasicText(
            "sign out",
            style = TextStyle(CarpoolrColors.CreamFaint, 13.sp),
            modifier = Modifier.clickable {
                FirebasePhoneAuth.signOut(activity)
                scope.launch { runCatching { Backend.signOut() } }
            }
        )
    }
}

@Composable
private fun VerifyPill(
    text: String,
    enabled: Boolean,
    color: Color = CarpoolrColors.Raspberry,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(if (enabled) color else color.copy(alpha = color.alpha * 0.45f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
    }
}
