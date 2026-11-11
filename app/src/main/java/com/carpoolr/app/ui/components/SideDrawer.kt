package com.carpoolr.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.connect.Provider
import com.carpoolr.app.data.Profile
import com.carpoolr.app.profile.AadhaarQrData
import com.carpoolr.app.profile.AadhaarQrParser
import com.carpoolr.app.profile.AadhaarScanner
import com.carpoolr.app.profile.AvatarCodec
import com.carpoolr.app.profile.UpiCheck
import com.carpoolr.app.profile.UpiValidator
import com.carpoolr.app.ui.screens.payUpi
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full-screen liquid-glass sheet that springs in from the right (profile) or the left.
 */
@Composable
fun SideSheet(
    visible: Boolean,
    fromStart: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "sheet"
    )
    BackHandler(enabled = visible, onBack = onDismiss)
    if (progress <= 0.001f && !visible) return

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Box(
            Modifier
                .align(if (fromStart) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(10.dp)
                .graphicsLayer {
                    val dir = if (fromStart) -1f else 1f
                    translationX = dir * (1f - progress) * (size.width + 24.dp.toPx())
                }
        ) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxSize(), corner = 28.dp, contentPadding = 16.dp) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) { content() }
            }
        }
    }
}

private val GENDERS = listOf("female", "male", "non-binary", "prefer not to say")

/**
 * Option B: All-in-one single-scroll inline profile sheet with avatar picker, rider details,
 * DOB 18+ check, UPI VPA verification, UIDAI Secure QR Aadhaar verification, and ride-app links.
 */
@Composable
fun ProfileContent(
    onClose: () -> Unit,
    isConnected: (Provider) -> Boolean,
    onConnect: (Provider) -> Unit,
    onDisconnect: (Provider) -> Unit = {},
    onSignOut: () -> Unit,
    profile: Profile? = null,
    saving: Boolean = false,
    onSaveProfile: (Profile) -> Unit = {},
    onRow: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var dob by remember(profile) { mutableStateOf(profile?.dob.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phone.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender.orEmpty()) }
    var orgTag by remember(profile) { mutableStateOf(profile?.orgTag.orEmpty()) }
    var emergencyName by remember(profile) { mutableStateOf(profile?.emergencyName.orEmpty()) }
    var emergencyPhone by remember(profile) { mutableStateOf(profile?.emergencyPhone.orEmpty()) }
    var upi by remember(profile) { mutableStateOf(profile?.upiId.orEmpty()) }
    var upiVerified by remember(profile) { mutableStateOf(profile?.upiVerified == true && !profile.upiId.isNullOrBlank()) }
    var aadhaarLast4 by remember(profile) { mutableStateOf(profile?.aadhaarLast4) }
    var aadhaarVerified by remember(profile) { mutableStateOf(profile?.aadhaarVerified == true && !profile.aadhaarLast4.isNullOrBlank()) }
    var avatarData by remember(profile) { mutableStateOf(profile?.avatarData) }

    var showPhotoMenu by remember { mutableStateOf(false) }
    var upiNote by remember(profile) {
        mutableStateOf<Pair<String, Boolean>?>(
            if (profile?.upiVerified == true && !profile.upiId.isNullOrBlank()) {
                when (val c = UpiValidator.verify(profile.upiId)) {
                    is UpiCheck.Valid -> "✓ ${c.providerLabel}" to true
                    is UpiCheck.Invalid -> null
                }
            } else null
        )
    }
    var aadhaarNote by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingQr by remember { mutableStateOf<AadhaarQrData?>(null) }
    var manageProvider by remember { mutableStateOf<Provider?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val avatarBitmap = remember(avatarData) { AvatarCodec.decodeImageBitmap(avatarData) }

    val pickAvatarGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.Default) { AvatarCodec.encodeUri(context, uri) }
                if (encoded != null) {
                    avatarData = encoded
                    showPhotoMenu = false
                    formError = null
                } else {
                    formError = "couldn't load that photo"
                }
            }
        }
    }

    val takeAvatarCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            scope.launch {
                avatarData = withContext(Dispatchers.Default) { AvatarCodec.encodeBitmap(bmp) }
                showPhotoMenu = false
                formError = null
            }
        }
    }

    fun handleScannedAadhaarQr(rawQr: String) {
        AadhaarQrParser.parse(rawQr)
            .onSuccess { qr ->
                if (qr.dob != null && qr.dob.length == 10) {
                    val dobRes = UpiValidator.parseAdultDob(qr.dob)
                    if (dobRes.isFailure) {
                        aadhaarNote = (dobRes.exceptionOrNull()?.message ?: "riders must be 18+") to false
                        return@onSuccess
                    }
                }
                if (name.isBlank()) name = qr.name
                if (dob.isBlank() && qr.dob != null && qr.dob.length == 10) dob = qr.dob
                if (gender.isBlank() && qr.gender != null) gender = qr.gender

                val mismatch = AadhaarQrParser.checkConsistency(qr, name, dob)
                if (mismatch != null) {
                    pendingQr = qr
                    aadhaarNote = mismatch to false
                } else {
                    pendingQr = null
                    aadhaarLast4 = qr.last4
                    aadhaarVerified = true
                    aadhaarNote = "✓ uidai qr matched ${qr.name.lowercase()}" to true
                }
            }
            .onFailure { e ->
                aadhaarNote = (e.message ?: "couldn't verify aadhaar qr") to false
            }
    }

    val pickAadhaarGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            AadhaarScanner.scanFromImageUri(
                context = context,
                uri = uri,
                onQr = ::handleScannedAadhaarQr,
                onError = { msg -> aadhaarNote = msg to false }
            )
        }
    }

    val joinedText = remember(profile?.createdAt) {
        profile?.createdAt?.let { raw ->
            runCatching {
                " · joined " + OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM yyyy")).lowercase()
            }.getOrNull()
        }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "your profile",
                style = TextStyle(CarpoolrColors.Cream, 28.sp, fontFamily = Anton),
                modifier = Modifier.weight(1f)
            )
            CloseButton(onClose)
        }
        Spacer(Modifier.height(10.dp))

        // Single-scroll inline Option B body
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Hero card: Avatar + Stats + Badges
            SectionBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clickable { showPhotoMenu = !showPhotoMenu }
                    ) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(Capsule())
                                .background(CarpoolrColors.GreenLight)
                                .border(1.5.dp, CarpoolrColors.Cream.copy(alpha = 0.25f), Capsule()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                BasicText(
                                    initialsOf(name),
                                    style = TextStyle(CarpoolrColors.Cream, 22.sp, fontFamily = Anton)
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(Capsule())
                                .background(CarpoolrColors.Raspberry),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("+", style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val ageSuffix = UpiValidator.parseAdultDob(dob).getOrNull()?.second?.let { " · $it" }.orEmpty()
                        BasicText(
                            (name.trim().ifBlank { "add your name" }) + ageSuffix,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            "${profile?.poolsCount ?: 0} pools · ${"%.1f".format(profile?.kmShared ?: 0.0)} km$joinedText",
                            style = TextStyle(CarpoolrColors.CreamMuted, 12.sp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(
                                text = if (upiVerified) "✓ upi" else "upi unverified",
                                verified = upiVerified
                            )
                            StatusBadge(
                                text = if (aadhaarVerified && !aadhaarLast4.isNullOrBlank()) "✓ aadhaar · $aadhaarLast4" else "aadhaar unverified",
                                verified = aadhaarVerified && !aadhaarLast4.isNullOrBlank()
                            )
                        }
                    }
                }
                if (showPhotoMenu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlinePill("gallery", CarpoolrColors.Raspberry) {
                            pickAvatarGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        InlinePill("camera", CarpoolrColors.Cream.copy(alpha = 0.14f)) {
                            takeAvatarCamera.launch(null)
                        }
                        if (avatarData != null) {
                            InlinePill("remove", CarpoolrColors.Cream.copy(alpha = 0.10f)) {
                                avatarData = null
                                showPhotoMenu = false
                            }
                        }
                    }
                }
            }

            // 2. Personal details grid (Name, DOB, Phone, Gender, Campus/Work, Emergency)
            SectionBox {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        label = "full name",
                        hint = "matches id / upi",
                        value = name,
                        onChange = { name = it.take(40); formError = null },
                        modifier = Modifier.weight(1f)
                    )
                    CompactField(
                        label = "date of birth (18+)",
                        hint = "dd/mm/yyyy",
                        value = dob,
                        onChange = {
                            dob = it.filter { c -> c.isDigit() || c == '/' || c == '-' || c == '.' }.take(10)
                            formError = null
                        },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        label = "mobile number",
                        hint = "+91 98765 43210",
                        value = phone,
                        onChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(16); formError = null },
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.weight(1f)
                    )
                    CompactField(
                        label = "campus / workplace",
                        hint = "iitb / koramangala",
                        value = orgTag,
                        onChange = { orgTag = it.take(45); formError = null },
                        modifier = Modifier.weight(1f)
                    )
                }
                BasicText("gender", style = TextStyle(CarpoolrColors.CreamMuted, 11.5.sp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GENDERS.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { g ->
                                val active = gender == g
                                InlinePill(
                                    text = g,
                                    color = if (active) CarpoolrColors.Raspberry else CarpoolrColors.Cream.copy(alpha = 0.09f)
                                ) { gender = if (active) "" else g }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        label = "emergency contact",
                        hint = "name",
                        value = emergencyName,
                        onChange = { emergencyName = it.take(35); formError = null },
                        modifier = Modifier.weight(0.9f)
                    )
                    CompactField(
                        label = "emergency phone",
                        hint = "+91 98201 11223",
                        value = emergencyPhone,
                        onChange = { emergencyPhone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(16); formError = null },
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }

            // 3. Inline UPI VPA + UIDAI Aadhaar QR verification card
            SectionBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        label = "upi id (vpa) · riders pay splits here",
                        hint = "you@okaxis",
                        value = upi,
                        onChange = {
                            upi = it.trim().take(70)
                            upiVerified = false
                            upiNote = null
                            formError = null
                        },
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.weight(1f)
                    )
                    InlinePill(
                        text = if (upiVerified) "✓ verified" else "verify",
                        color = if (upiVerified) CarpoolrColors.Cream.copy(alpha = 0.16f) else CarpoolrColors.Raspberry
                    ) {
                        when (val res = UpiValidator.verify(upi)) {
                            is UpiCheck.Valid -> {
                                upi = res.normalized
                                upiVerified = true
                                upiNote = "✓ ${res.providerLabel}" to true
                            }
                            is UpiCheck.Invalid -> {
                                upiVerified = false
                                upiNote = res.reason to false
                            }
                        }
                    }
                }
                upiNote?.let { (msg, ok) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(
                            msg,
                            style = TextStyle(if (ok) CarpoolrColors.CreamMuted else CarpoolrColors.Raspberry, 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        if (ok && upi.isNotBlank()) {
                            BasicText(
                                "test ₹1 in upi app →",
                                style = TextStyle(CarpoolrColors.Cream, 12.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.clickable {
                                    payUpi(context, upi, name.ifBlank { "carpoolr rider" }, 1)
                                }
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(CarpoolrColors.Cream.copy(alpha = 0.08f)))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(
                            if (aadhaarVerified && !aadhaarLast4.isNullOrBlank()) "aadhaar · xxxx-xxxx-$aadhaarLast4"
                            else "aadhaar verification",
                            style = TextStyle(CarpoolrColors.Cream, 14.sp, fontWeight = FontWeight.Medium)
                        )
                        BasicText(
                            if (aadhaarVerified) "uidai qr verified · 12-digit number never stored"
                            else "scan mAadhaar, pvc card, or e-aadhaar qr",
                            style = TextStyle(CarpoolrColors.CreamMuted, 11.5.sp)
                        )
                    }
                    InlinePill(
                        text = if (aadhaarVerified) "re-scan" else "scan qr",
                        color = CarpoolrColors.Raspberry
                    ) {
                        AadhaarScanner.scanWithCamera(
                            context = context,
                            onQr = ::handleScannedAadhaarQr,
                            onError = { msg -> aadhaarNote = msg to false }
                        )
                    }
                    InlinePill(
                        text = "qr image",
                        color = CarpoolrColors.Cream.copy(alpha = 0.12f)
                    ) {
                        pickAadhaarGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                aadhaarNote?.let { (msg, ok) ->
                    BasicText(
                        msg,
                        style = TextStyle(if (ok) CarpoolrColors.CreamMuted else CarpoolrColors.Raspberry, 12.sp)
                    )
                }
                pendingQr?.let { qr ->
                    InlinePill("use aadhaar details (${qr.name.lowercase()}) & verify", CarpoolrColors.Cream.copy(alpha = 0.16f)) {
                        name = qr.name
                        if (qr.dob != null && qr.dob.length == 10) dob = qr.dob
                        if (qr.gender != null) gender = qr.gender
                        aadhaarLast4 = qr.last4
                        aadhaarVerified = true
                        pendingQr = null
                        aadhaarNote = "✓ uidai qr matched ${qr.name.lowercase()}" to true
                    }
                }
            }

            // 4. Ride-app connections 3-column grid + disconnect option
            SectionBox {
                BasicText("ride apps", style = TextStyle(CarpoolrColors.CreamMuted, 11.5.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Provider.entries.forEach { p ->
                        val connected = isConnected(p)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedRectangle(15.dp))
                                .background(CarpoolrColors.Cream.copy(alpha = if (connected) 0.12f else 0.07f))
                                .clickable {
                                    if (connected) manageProvider = if (manageProvider == p) null else p
                                    else onConnect(p)
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            BasicText(p.label, style = TextStyle(CarpoolrColors.Cream, 14.sp, fontWeight = FontWeight.Bold))
                            BasicText(
                                if (connected) "connected ✓" else "connect →",
                                style = TextStyle(if (connected) CarpoolrColors.CreamMuted else CarpoolrColors.Raspberry, 11.5.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
                manageProvider?.let { sel ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicText(
                            "${sel.label} session",
                            style = TextStyle(CarpoolrColors.CreamMuted, 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        InlinePill("re-sign in", CarpoolrColors.Cream.copy(alpha = 0.12f)) {
                            manageProvider = null
                            onConnect(sel)
                        }
                        InlinePill("disconnect", CarpoolrColors.Raspberry.copy(alpha = 0.85f)) {
                            onDisconnect(sel)
                            manageProvider = null
                        }
                    }
                }
            }

            // 5. Compact utility row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InlinePill("rides", CarpoolrColors.Cream.copy(alpha = 0.10f)) { onRow("ride history") }
                InlinePill("alerts", CarpoolrColors.Cream.copy(alpha = 0.10f)) { onRow("notifications") }
                InlinePill("help", CarpoolrColors.Cream.copy(alpha = 0.10f)) { onRow("help") }
                InlinePill("sign out", CarpoolrColors.Cream.copy(alpha = 0.10f), onClick = onSignOut)
            }
        }

        formError?.let { err ->
            Spacer(Modifier.height(6.dp))
            BasicText(err, style = TextStyle(CarpoolrColors.Raspberry, 13.sp, fontWeight = FontWeight.Medium))
        }

        Spacer(Modifier.height(10.dp))
        val saveSource = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxWidth()
                .pressScale(saveSource, 0.96f)
                .clip(Capsule())
                .background(if (!saving) CarpoolrColors.Raspberry else CarpoolrColors.Raspberry.copy(alpha = 0.45f))
                .clickable(interactionSource = saveSource, indication = null, enabled = !saving) {
                    val cleanName = name.trim()
                    if (cleanName.length < 2) {
                        formError = "enter your full name"
                        return@clickable
                    }
                    val cleanDob = if (dob.isBlank()) null else {
                        val r = UpiValidator.parseAdultDob(dob)
                        if (r.isFailure) {
                            formError = r.exceptionOrNull()?.message ?: "invalid date of birth"
                            return@clickable
                        }
                        r.getOrThrow().first
                    }
                    val cleanPhone = if (phone.isBlank()) null else {
                        UpiValidator.normalizeIndianPhone(phone) ?: run {
                            formError = "enter a valid 10-digit indian mobile number"
                            return@clickable
                        }
                    }
                    val cleanEmPhone = if (emergencyPhone.isBlank()) null else {
                        UpiValidator.normalizeIndianPhone(emergencyPhone) ?: run {
                            formError = "check emergency contact phone number"
                            return@clickable
                        }
                    }
                    var cleanUpi: String? = null
                    var finalUpiVerified = false
                    if (upi.isNotBlank()) {
                        when (val c = UpiValidator.verify(upi)) {
                            is UpiCheck.Valid -> {
                                cleanUpi = c.normalized
                                finalUpiVerified = true
                                upi = c.normalized
                                upiVerified = true
                            }
                            is UpiCheck.Invalid -> {
                                formError = c.reason
                                return@clickable
                            }
                        }
                    }
                    formError = null
                    onSaveProfile(
                        Profile(
                            displayName = cleanName,
                            upiId = cleanUpi,
                            upiVerified = finalUpiVerified,
                            phone = cleanPhone,
                            dob = cleanDob,
                            gender = gender.ifBlank { null },
                            orgTag = orgTag.trim().ifBlank { null },
                            emergencyName = emergencyName.trim().ifBlank { null },
                            emergencyPhone = cleanEmPhone,
                            aadhaarLast4 = aadhaarLast4,
                            aadhaarVerified = aadhaarVerified && !aadhaarLast4.isNullOrBlank(),
                            avatarData = avatarData,
                            createdAt = profile?.createdAt,
                            poolsCount = profile?.poolsCount ?: 0,
                            kmShared = profile?.kmShared ?: 0.0
                        )
                    )
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                if (saving) "saving…" else "save profile",
                style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".lowercase()
        parts.size == 1 -> parts.first().take(2).lowercase()
        else -> "me"
    }
}

@Composable
private fun SectionBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(22.dp))
            .background(CarpoolrColors.GreenDeep.copy(alpha = 0.42f))
            .border(1.dp, CarpoolrColors.Cream.copy(alpha = 0.12f), RoundedRectangle(22.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun StatusBadge(text: String, verified: Boolean) {
    Box(
        Modifier
            .clip(Capsule())
            .background(if (verified) CarpoolrColors.Cream.copy(alpha = 0.16f) else CarpoolrColors.Raspberry.copy(alpha = 0.25f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        BasicText(text, style = TextStyle(CarpoolrColors.Cream, 11.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun InlinePill(text: String, color: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(color)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicText(text, style = TextStyle(Color.White, 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun CompactField(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier
            .clip(RoundedRectangle(15.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.CreamMuted, 11.sp))
        Box {
            if (value.isEmpty()) {
                BasicText(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.CreamFaint, 14.sp))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(CarpoolrColors.Cream, 14.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source, 0.85f)
            .clip(RoundedRectangle(20.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.12f))
            .clickable(interactionSource = source, indication = null, onClick = onClose)
            .padding(8.dp)
    ) { GlassIcon(R.drawable.ic_close, "close", size = 20) }
}
