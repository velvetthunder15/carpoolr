package com.carpoolr.app.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Patterns
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.auth.EmailOtpNotificationService
import com.carpoolr.app.auth.OtpWatcher
import com.carpoolr.app.data.Backend
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch

private val PLAIN_SIX_DIGITS = Regex("""(?<!\d)(\d{6})(?!\d)""")

/** Google OAuth + Email OTP sign-in (with automatic email notification OTP pickup). */
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notifAccess by remember { mutableStateOf(EmailOtpNotificationService.isEnabled(context)) }
    val scope = rememberCoroutineScope()

    fun verify(otp: String) {
        if (busy || otp.length != 6) return
        busy = true
        error = null
        scope.launch {
            runCatching { Backend.verifyCode(email, otp) }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    error = if (msg.contains("expired", true) || msg.contains("invalid", true)) {
                        "wrong or expired code"
                    } else {
                        "couldn't sign in: " + msg.lineSequence().firstOrNull().orEmpty().take(120).lowercase()
                    }
                }
            busy = false
        }
    }

    LaunchedEffect(windowFocused, codeSent) {
        if (windowFocused) {
            googleBusy = false
            notifAccess = EmailOtpNotificationService.isEnabled(context)
            if (codeSent && notifAccess) OtpWatcher.rescan()
        }
    }

    DisposableEffect(codeSent, email) {
        if (codeSent) OtpWatcher.start(email) else OtpWatcher.stop()
        onDispose { OtpWatcher.stop() }
    }

    LaunchedEffect(codeSent, email) {
        if (!codeSent) return@LaunchedEffect
        OtpWatcher.codes.collect { detected ->
            if (detected.length == 6 && !busy) {
                code = detected
                verify(detected)
            }
        }
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
            painterResource(R.drawable.ic_route_mark_color), null,
            Modifier.size(width = 64.dp, height = 40.dp)
        )
        Spacer(Modifier.height(16.dp))
        BasicText("carpoolr", style = TextStyle(CarpoolrColors.Cream, 52.sp, fontFamily = Anton))
        Spacer(Modifier.height(26.dp))

        if (!codeSent) {
            GoogleSignInPill(
                busy = googleBusy,
                enabled = !busy && !googleBusy
            ) {
                googleBusy = true
                error = null
                scope.launch {
                    runCatching { Backend.signInWithGoogle() }
                        .onFailure { e ->
                            googleBusy = false
                            error = "couldn't open google sign-in: " +
                                e.message.orEmpty().lineSequence().firstOrNull().orEmpty().take(100).lowercase()
                        }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(CarpoolrColors.Cream.copy(alpha = 0.14f)))
                BasicText(
                    "or with email",
                    style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(CarpoolrColors.Cream.copy(alpha = 0.14f)))
            }
            Spacer(Modifier.height(18.dp))

            Field(email, { email = it.trim(); error = null }, "email", KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            Pill(if (busy) "sending…" else "send code", enabled = !busy && !googleBusy) {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    error = "that email doesn't look right"; return@Pill
                }
                busy = true
                scope.launch {
                    runCatching { Backend.sendCode(email) }
                        .onSuccess {
                            OtpWatcher.start(email, resetClock = true)
                            codeSent = true
                            error = null
                        }
                        .onFailure { e ->
                            val msg = e.message.orEmpty()
                            if (msg.contains("seconds", true) || msg.contains("rate", true)) {
                                codeSent = true
                                error = "a code was just sent. use the newest one in your inbox."
                            } else {
                                error = "couldn't send the code: " + msg.lineSequence().firstOrNull().orEmpty().take(120).lowercase()
                            }
                        }
                    busy = false
                }
            }
        } else {
            BasicText("code sent to $email", style = TextStyle(CarpoolrColors.CreamMuted, 14.sp))
            Spacer(Modifier.height(4.dp))
            if (notifAccess) {
                BasicText(
                    "waiting for email notification · auto-fills when it arrives",
                    style = TextStyle(CarpoolrColors.CreamFaint, 12.sp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Field(
                value = code,
                onChange = { v ->
                    val clean = v.filter(Char::isDigit).take(6)
                    code = clean
                    error = null
                },
                placeholder = "6-digit code",
                type = KeyboardType.NumberPassword
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallChip("paste code") {
                    val clip = readClipboardSixDigits(context)
                    if (clip != null) {
                        code = clip
                        verify(clip)
                    } else {
                        error = "no 6-digit code found on clipboard"
                    }
                }
                if (!notifAccess) {
                    SmallChip("auto-read notifications") {
                        EmailOtpNotificationService.openNotificationAccessSettings(context)
                    }
                }
            }
            if (!notifAccess && Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "sideloaded apk? tap here for app info → ⋮ → allow restricted settings first",
                    style = TextStyle(CarpoolrColors.CreamFaint, 12.sp),
                    modifier = Modifier.clickable { EmailOtpNotificationService.openAppInfoSettings(context) }
                )
            }
            Spacer(Modifier.height(14.dp))
            Pill(if (busy) "checking…" else "sign in", enabled = !busy && code.length == 6) {
                verify(code)
            }
            Spacer(Modifier.height(14.dp))
            BasicText(
                "use a different email",
                style = TextStyle(CarpoolrColors.CreamMuted, 14.sp),
                modifier = Modifier.clickable { codeSent = false; code = ""; error = null }
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            BasicText(it, style = TextStyle(CarpoolrColors.Raspberry, 14.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun GoogleSignInPill(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(if (enabled) CarpoolrColors.Cream else CarpoolrColors.Cream.copy(alpha = 0.55f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoogleGMark()
        Spacer(Modifier.width(10.dp))
        BasicText(
            if (busy) "opening google…" else "continue with google",
            style = TextStyle(CarpoolrColors.GreenDeep, 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun GoogleGMark() {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 3.2.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(Color(0xFFEA4335), startAngle = -145f, sweepAngle = 105f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFFFBBC05), startAngle = 135f, sweepAngle = 80f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFF34A853), startAngle = 45f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFF4285F4), startAngle = -12f, sweepAngle = 60f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, size.height * 0.5f),
            end = Offset(size.width - inset * 0.6f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Butt
        )
    }
}

private fun readClipboardSixDigits(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val desc = cm.primaryClipDescription ?: return null
    if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
        return null
    }
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    return PLAIN_SIX_DIGITS.find(text)?.groupValues?.get(1)
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(CarpoolrColors.Cream.copy(alpha = 0.12f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        BasicText(text, style = TextStyle(CarpoolrColors.Cream, 12.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, type: KeyboardType) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        if (value.isEmpty()) BasicText(placeholder, style = TextStyle(CarpoolrColors.CreamFaint, 17.sp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(CarpoolrColors.Raspberry),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(if (enabled) CarpoolrColors.Raspberry else CarpoolrColors.Raspberry.copy(alpha = 0.45f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) { BasicText(text, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold)) }
}
