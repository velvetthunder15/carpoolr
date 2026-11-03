package com.carpoolr.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.carpoolr.app.data.Backend
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch

/** Email OTP: enter email, get a 6-digit code, type it in. The email's link also signs in. */
@Composable
fun SignInScreen() {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        Spacer(Modifier.height(28.dp))

        if (!codeSent) {
            Field(email, { email = it.trim(); error = null }, "email", KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            Pill(if (busy) "sending…" else "send code", enabled = !busy) {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    error = "that email doesn't look right"; return@Pill
                }
                busy = true
                scope.launch {
                    runCatching { Backend.sendCode(email) }
                        .onSuccess { codeSent = true; error = null }
                        .onFailure { error = "couldn't send the code. try again in a minute." }
                    busy = false
                }
            }
        } else {
            BasicText("code sent to $email", style = TextStyle(CarpoolrColors.CreamMuted, 14.sp))
            Spacer(Modifier.height(10.dp))
            Field(code, { v -> code = v.filter(Char::isDigit).take(6); error = null }, "6-digit code", KeyboardType.NumberPassword)
            Spacer(Modifier.height(14.dp))
            Pill(if (busy) "checking…" else "sign in", enabled = !busy && code.length == 6) {
                busy = true
                scope.launch {
                    runCatching { Backend.verifyCode(email, code) }
                        .onFailure { error = "wrong or expired code" }
                    busy = false
                }
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
