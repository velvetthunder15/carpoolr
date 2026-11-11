package com.carpoolr.app.ui.components

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Liquid-glass panel that springs in from the right (profile) or the left.
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
                .background(Color.Black.copy(alpha = 0.40f))
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
                .fillMaxWidth(0.86f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .graphicsLayer {
                    val dir = if (fromStart) -1f else 1f
                    translationX = dir * (1f - progress) * (size.width + 24.dp.toPx())
                }
        ) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxSize(), corner = 32.dp) {
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

private enum class ProfilePage { Menu, PersonalInfo, UpiPayments, Connections }

private data class ProfileItem(val label: String, val icon: Int)

private val AccountItems = listOf(
    ProfileItem("ride history", R.drawable.ic_history),
    ProfileItem("notifications", R.drawable.ic_bell)
)

private val SupportItems = listOf(
    ProfileItem("help", R.drawable.ic_help),
    ProfileItem("about", R.drawable.ic_info),
    ProfileItem("sign out", R.drawable.ic_logout)
)

private val GENDERS = listOf("female", "male", "non-binary", "prefer not to say")

/**
 * Clean profile drawer menu with dedicated sub-pages for:
 * 1. personal info (photo, name, dob, phone, gender, campus/workplace, emergency contact, aadhaar id)
 * 2. upi / payments (upi vpa verification, auto-save on verify, ₹1 app test)
 * 3. connections (uber, ola, rapido sign-in & management)
 * Plus full-width rows for ride history, notifications, help, about, and sign out.
 */
@Composable
fun ProfileContent(
    visible: Boolean = true,
    onClose: () -> Unit,
    isConnected: (Provider) -> Boolean,
    onConnect: (Provider) -> Unit,
    onDisconnect: (Provider) -> Unit = {},
    onSignOut: () -> Unit,
    profile: Profile? = null,
    saving: Boolean = false,
    onSaveProfile: (Profile, String?) -> Unit = { _, _ -> },
    onRow: (String) -> Unit = {}
) {
    var pageName by rememberSaveable { mutableStateOf(ProfilePage.Menu.name) }
    val page = ProfilePage.valueOf(pageName)

    LaunchedEffect(visible) {
        if (!visible) {
            kotlinx.coroutines.delay(220)
            pageName = ProfilePage.Menu.name
        }
    }

    BackHandler(enabled = page != ProfilePage.Menu) {
        pageName = ProfilePage.Menu.name
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != ProfilePage.Menu
            val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 420f)
            if (forward) {
                (slideInHorizontally(slide) { it / 3 } + fadeIn(spring(stiffness = 420f))) togetherWith
                    (slideOutHorizontally(slide) { -it / 4 } + fadeOut(spring(stiffness = 550f)))
            } else {
                (slideInHorizontally(slide) { -it / 4 } + fadeIn(spring(stiffness = 420f))) togetherWith
                    (slideOutHorizontally(slide) { it / 3 } + fadeOut(spring(stiffness = 550f)))
            }
        },
        label = "profilePage",
        modifier = Modifier.fillMaxSize()
    ) { target ->
        when (target) {
            ProfilePage.Menu -> ProfileMenuPage(
                profile = profile,
                isConnected = isConnected,
                onOpenPage = { pageName = it.name },
                onClose = onClose,
                onRow = onRow,
                onSignOut = onSignOut
            )
            ProfilePage.PersonalInfo -> PersonalInfoPage(
                profile = profile,
                saving = saving,
                onBack = { pageName = ProfilePage.Menu.name },
                onClose = onClose,
                onSave = { updated, msg, closeSubpage ->
                    onSaveProfile(updated, msg)
                    if (closeSubpage) pageName = ProfilePage.Menu.name
                }
            )
            ProfilePage.UpiPayments -> UpiPaymentsPage(
                profile = profile,
                saving = saving,
                onBack = { pageName = ProfilePage.Menu.name },
                onClose = onClose,
                onSave = { updated, msg -> onSaveProfile(updated, msg) }
            )
            ProfilePage.Connections -> ConnectionsPage(
                isConnected = isConnected,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onBack = { pageName = ProfilePage.Menu.name },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun ProfileMenuPage(
    profile: Profile?,
    isConnected: (Provider) -> Boolean,
    onOpenPage: (ProfilePage) -> Unit,
    onClose: () -> Unit,
    onRow: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val avatarBitmap = remember(profile?.avatarData) { AvatarCodec.decodeImageBitmap(profile?.avatarData) }
    val name = profile?.displayName?.trim().orEmpty()
    val upi = profile?.upiId?.trim().orEmpty()
    val upiVerified = profile?.upiVerified == true && upi.isNotEmpty()
    val aadhaarLast4 = profile?.aadhaarLast4?.takeIf { it.isNotBlank() }
    val aadhaarVerified = profile?.aadhaarVerified == true && aadhaarLast4 != null

    val ageSuffix = remember(profile?.dob) {
        profile?.dob?.let { UpiValidator.parseAdultDob(it).getOrNull()?.second }?.let { " · $it" }.orEmpty()
    }
    val joinedText = remember(profile?.createdAt) {
        profile?.createdAt?.let { raw ->
            runCatching {
                "joined " + OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM yyyy")).lowercase()
            }.getOrNull()
        }
    }

    val personalSubtitle = buildList {
        if (name.isNotEmpty()) add(name)
        profile?.phone?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (aadhaarVerified) add("id verified")
    }.joinToString(" · ").ifBlank { "photo, name, dob, phone & id" }

    val upiSubtitle = when {
        upiVerified -> "$upi · verified"
        upi.isNotEmpty() -> "$upi · tap to verify"
        else -> "add upi id to receive fare splits"
    }

    val connectedList = Provider.entries.filter { isConnected(it) }.map { it.label }
    val connectionsSubtitle = if (connectedList.isNotEmpty()) {
        "${connectedList.joinToString(", ")} connected"
    } else {
        "uber, ola & rapido"
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "your profile",
                style = TextStyle(CarpoolrColors.Cream, 26.sp, fontFamily = Anton),
                modifier = Modifier.weight(1f)
            )
            CloseButton(onClose)
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            var i = 0
            // Hero summary card (taps through to personal info)
            val heroSource = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .staggeredEntrance(i++, 28L)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .pressScale(heroSource, 0.98f)
                    .clip(RoundedRectangle(22.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                    .clickable(heroSource, null) { onOpenPage(ProfilePage.PersonalInfo) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(avatarBitmap = avatarBitmap, name = name, sizeDp = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(
                        (name.ifBlank { "set your name" }) + ageSuffix,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            if (name.isBlank()) CarpoolrColors.CreamMuted else CarpoolrColors.Cream,
                            17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val metaParts = buildList {
                        val pools = profile?.poolsCount ?: 0
                        if (pools > 0) add("$pools pools · ${"%.1f".format(profile?.kmShared ?: 0.0)} km")
                        profile?.orgTag?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (isEmpty() && joinedText != null) add(joinedText)
                    }
                    if (metaParts.isNotEmpty()) {
                        BasicText(
                            metaParts.joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp)
                        )
                    }
                    if (upiVerified || aadhaarVerified) {
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (upiVerified) StatusBadge("✓ upi", verified = true)
                            if (aadhaarVerified) StatusBadge("✓ id · $aadhaarLast4", verified = true)
                        }
                    }
                }
            }

            SettingNavRow(
                icon = R.drawable.ic_person,
                title = "personal info",
                subtitle = personalSubtitle,
                checked = aadhaarVerified,
                modifier = Modifier.staggeredEntrance(i++, 28L)
            ) { onOpenPage(ProfilePage.PersonalInfo) }

            SettingNavRow(
                icon = R.drawable.ic_wallet,
                title = "upi / payments",
                subtitle = upiSubtitle,
                checked = upiVerified,
                modifier = Modifier.staggeredEntrance(i++, 28L)
            ) { onOpenPage(ProfilePage.UpiPayments) }

            SettingNavRow(
                icon = R.drawable.ic_link,
                title = "connections",
                subtitle = connectionsSubtitle,
                checked = connectedList.isNotEmpty(),
                modifier = Modifier.staggeredEntrance(i++, 28L)
            ) { onOpenPage(ProfilePage.Connections) }

            Divider()

            AccountItems.forEach { item ->
                DrawerRow(item, Modifier.staggeredEntrance(i++, 28L)) { onRow(item.label) }
            }

            Divider()

            SupportItems.forEach { item ->
                DrawerRow(
                    item = item,
                    modifier = Modifier.staggeredEntrance(i++, 28L),
                    onClick = if (item.label == "sign out") onSignOut else ({ onRow(item.label) })
                )
            }
        }
    }
}

@Composable
private fun PersonalInfoPage(
    profile: Profile?,
    saving: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSave: (Profile, String?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var dob by remember(profile?.dob) { mutableStateOf(profile?.dob.orEmpty().filter(Char::isDigit).take(8)) }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
    var gender by remember(profile?.gender) { mutableStateOf(profile?.gender.orEmpty()) }
    var orgTag by remember(profile?.orgTag) { mutableStateOf(profile?.orgTag.orEmpty()) }
    var emergencyName by remember(profile?.emergencyName) { mutableStateOf(profile?.emergencyName.orEmpty()) }
    var emergencyPhone by remember(profile?.emergencyPhone) { mutableStateOf(profile?.emergencyPhone.orEmpty()) }
    var aadhaarLast4 by remember(profile?.aadhaarLast4) { mutableStateOf(profile?.aadhaarLast4) }
    var aadhaarVerified by remember(profile?.aadhaarVerified, profile?.aadhaarLast4) {
        mutableStateOf(profile?.aadhaarVerified == true && !profile.aadhaarLast4.isNullOrBlank())
    }
    var avatarData by remember(profile?.avatarData) { mutableStateOf(profile?.avatarData) }

    var verifiedQrSnapshot by remember { mutableStateOf<AadhaarQrData?>(null) }
    var aadhaarNote by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingQr by remember { mutableStateOf<AadhaarQrData?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val avatarBitmap = remember(avatarData) { AvatarCodec.decodeImageBitmap(avatarData) }

    fun currentDraftProfile(
        overrideAvatar: String? = avatarData,
        overrideLast4: String? = aadhaarLast4,
        overrideAadhaarOk: Boolean = aadhaarVerified,
        overrideName: String = name,
        overrideDob: String = dob,
        overrideGender: String = gender
    ): Profile {
        val base = profile ?: Profile()
        val formattedDob = UpiValidator.parseAdultDob(overrideDob).getOrNull()?.first
            ?: overrideDob.trim().ifBlank { base.dob }
        return base.copy(
            displayName = overrideName.trim().ifBlank { base.displayName },
            dob = formattedDob,
            gender = overrideGender.ifBlank { base.gender },
            aadhaarLast4 = overrideLast4,
            aadhaarVerified = overrideAadhaarOk && !overrideLast4.isNullOrBlank(),
            avatarData = overrideAvatar
        )
    }

    fun invalidateAadhaarIfEdited(newName: String, newDob: String) {
        if (!aadhaarVerified) return
        val snap = verifiedQrSnapshot
        if (snap != null) {
            if (AadhaarQrParser.checkConsistency(snap, newName, newDob) != null) {
                aadhaarVerified = false
                aadhaarNote = "name or dob changed · re-scan aadhaar qr to verify" to false
            }
        } else {
            val origName = profile?.displayName.orEmpty().trim()
            val origDobDigits = profile?.dob.orEmpty().filter(Char::isDigit).take(8)
            val newDobDigits = newDob.filter(Char::isDigit).take(8)
            if ((origName.isNotEmpty() && !origName.equals(newName.trim(), ignoreCase = true)) ||
                (origDobDigits.isNotEmpty() && origDobDigits != newDobDigits)
            ) {
                aadhaarVerified = false
                aadhaarNote = "details changed · re-scan aadhaar qr to keep id badge" to false
            }
        }
    }

    val pickAvatarGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.Default) { AvatarCodec.encodeUri(context, uri) }
                if (encoded != null) {
                    avatarData = encoded
                    formError = null
                    onSave(currentDraftProfile(overrideAvatar = encoded), null, false)
                } else {
                    formError = "couldn't load that photo"
                }
            }
        }
    }

    val takeAvatarCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.Default) { AvatarCodec.encodeBitmap(bmp) }
                avatarData = encoded
                formError = null
                onSave(currentDraftProfile(overrideAvatar = encoded), null, false)
            }
        }
    }

    fun handleScannedAadhaarQr(rawQr: String) {
        AadhaarQrParser.parse(rawQr)
            .onSuccess { qr ->
                if (qr.dob != null) {
                    if (qr.dob.length == 10) {
                        val dobRes = UpiValidator.parseAdultDob(qr.dob)
                        if (dobRes.isFailure) {
                            aadhaarNote = (dobRes.exceptionOrNull()?.message ?: "riders must be 18+") to false
                            return@onSuccess
                        }
                    } else if (qr.dob.length == 4) {
                        val yob = qr.dob.toIntOrNull()
                        if (yob != null && LocalDate.now().year - yob < 18) {
                            aadhaarNote = "riders must be 18 or older" to false
                            return@onSuccess
                        }
                    }
                }
                val candidateName = name.ifBlank { qr.name }
                val qrDobDigits = qr.dob?.takeIf { it.length == 10 }?.filter(Char::isDigit)?.take(8)
                val candidateDobDigits = if (dob.isBlank() && qrDobDigits != null) qrDobDigits else dob
                val candidateDobFormatted = UpiValidator.parseAdultDob(candidateDobDigits).getOrNull()?.first ?: candidateDobDigits
                val mismatch = AadhaarQrParser.checkConsistency(qr, candidateName, candidateDobFormatted)
                if (mismatch != null) {
                    pendingQr = qr
                    aadhaarNote = mismatch to false
                } else {
                    val nextGender = gender.ifBlank { qr.gender.orEmpty() }
                    name = candidateName
                    dob = candidateDobDigits
                    gender = nextGender
                    pendingQr = null
                    verifiedQrSnapshot = qr
                    aadhaarLast4 = qr.last4
                    aadhaarVerified = true
                    aadhaarNote = "✓ uidai qr matched ${qr.name.lowercase()}" to true
                    onSave(
                        currentDraftProfile(
                            overrideLast4 = qr.last4,
                            overrideAadhaarOk = true,
                            overrideName = candidateName,
                            overrideDob = candidateDobFormatted,
                            overrideGender = nextGender
                        ),
                        "aadhaar verified",
                        false
                    )
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

    Column(Modifier.fillMaxSize()) {
        SubpageHeader(title = "personal info", onBack = onBack, onClose = onClose)
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Photo row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(avatarBitmap = avatarBitmap, name = name, sizeDp = 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText("profile photo", style = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("gallery", CarpoolrColors.Raspberry) {
                            pickAvatarGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        ActionChip("camera", CarpoolrColors.Cream.copy(alpha = 0.14f)) {
                            takeAvatarCamera.launch(null)
                        }
                        if (avatarData != null) {
                            ActionChip("remove", CarpoolrColors.Cream.copy(alpha = 0.10f)) {
                                avatarData = null
                                onSave(currentDraftProfile(overrideAvatar = null), null, false)
                            }
                        }
                    }
                }
            }

            EditField(
                label = "full name",
                hint = "how your pool sees you",
                value = name,
                onChange = {
                    val v = it.take(40)
                    name = v
                    formError = null
                    invalidateAadhaarIfEdited(v, dob)
                }
            )
            DobField(
                digits = dob,
                onDigitsChange = { digits ->
                    dob = digits
                    formError = null
                    val checkStr = UpiValidator.parseAdultDob(digits).getOrNull()?.first ?: digits
                    invalidateAadhaarIfEdited(name, checkStr)
                },
                onPickCalendar = {
                    val maxAdult = LocalDate.now().minusYears(18)
                    val parsed = if (dob.length == 8) {
                        val d = dob.substring(0, 2).toIntOrNull() ?: 15
                        val m = dob.substring(2, 4).toIntOrNull() ?: 8
                        val y = dob.substring(4, 8).toIntOrNull() ?: (maxAdult.year - 2)
                        runCatching { LocalDate.of(y, m, d) }.getOrNull()
                    } else null
                    val init = parsed ?: maxAdult.minusYears(2)
                    DatePickerDialog(
                        context,
                        { _, year, monthZeroBased, dayOfMonth ->
                            val pickedDigits = "%02d%02d%04d".format(dayOfMonth, monthZeroBased + 1, year)
                            dob = pickedDigits
                            formError = null
                            val checkStr = "%02d/%02d/%04d".format(dayOfMonth, monthZeroBased + 1, year)
                            invalidateAadhaarIfEdited(name, checkStr)
                        },
                        init.year,
                        init.monthValue - 1,
                        init.dayOfMonth
                    ).apply {
                        datePicker.maxDate = maxAdult.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }
            )
            EditField(
                label = "mobile number",
                hint = "+91 98765 43210",
                value = phone,
                keyboardType = KeyboardType.Phone,
                onChange = {
                    phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(16)
                    formError = null
                }
            )

            SectionLabel("gender")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GENDERS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { g ->
                            val active = gender == g
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedRectangle(14.dp))
                                    .background(if (active) CarpoolrColors.Raspberry else CarpoolrColors.Cream.copy(alpha = 0.08f))
                                    .clickable { gender = if (active) "" else g }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    g,
                                    style = TextStyle(
                                        if (active) Color.White else CarpoolrColors.Cream,
                                        13.5.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            EditField(
                label = "campus / workplace",
                hint = "optional (like iit bombay or koramangala)",
                value = orgTag,
                onChange = { orgTag = it.take(45); formError = null }
            )

            SectionLabel("emergency contact")
            EditField(
                label = "contact name",
                hint = "optional",
                value = emergencyName,
                onChange = { emergencyName = it.take(35); formError = null }
            )
            EditField(
                label = "contact phone",
                hint = "+91 98201 11223",
                value = emergencyPhone,
                keyboardType = KeyboardType.Phone,
                onChange = {
                    emergencyPhone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(16)
                    formError = null
                }
            )

            SectionLabel("id verification")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(
                    if (aadhaarVerified && !aadhaarLast4.isNullOrBlank()) "aadhaar · xxxx-xxxx-$aadhaarLast4 (verified)"
                    else "aadhaar qr verification",
                    style = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium)
                )
                BasicText(
                    if (aadhaarVerified) "shows ✓ id badge on pool cards · 12-digit number is never stored"
                    else "scan mAadhaar, pvc card, or e-aadhaar qr for a verified rider badge",
                    style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(if (aadhaarVerified) "re-scan qr" else "scan qr", CarpoolrColors.Raspberry) {
                        AadhaarScanner.scanWithCamera(
                            context = context,
                            onQr = ::handleScannedAadhaarQr,
                            onError = { msg -> aadhaarNote = msg to false }
                        )
                    }
                    ActionChip("pick qr image", CarpoolrColors.Cream.copy(alpha = 0.14f)) {
                        pickAadhaarGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                aadhaarNote?.let { (msg, ok) ->
                    BasicText(
                        msg,
                        style = TextStyle(if (ok) CarpoolrColors.CreamMuted else CarpoolrColors.Raspberry, 12.5.sp)
                    )
                }
                pendingQr?.let { qr ->
                    ActionChip("use aadhaar details (${qr.name.lowercase()}) & verify", CarpoolrColors.Cream.copy(alpha = 0.16f)) {
                        val nextDobDigits = qr.dob?.takeIf { it.length == 10 }?.filter(Char::isDigit)?.take(8) ?: dob
                        val nextDobFormatted = UpiValidator.parseAdultDob(nextDobDigits).getOrNull()?.first ?: nextDobDigits
                        val nextGender = qr.gender ?: gender
                        name = qr.name
                        dob = nextDobDigits
                        gender = nextGender
                        aadhaarLast4 = qr.last4
                        aadhaarVerified = true
                        verifiedQrSnapshot = qr
                        pendingQr = null
                        aadhaarNote = "✓ uidai qr matched ${qr.name.lowercase()}" to true
                        onSave(
                            currentDraftProfile(
                                overrideLast4 = qr.last4,
                                overrideAadhaarOk = true,
                                overrideName = qr.name,
                                overrideDob = nextDobFormatted,
                                overrideGender = nextGender
                            ),
                            "aadhaar verified",
                            false
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        formError?.let { err ->
            Spacer(Modifier.height(6.dp))
            BasicText(err, style = TextStyle(CarpoolrColors.Raspberry, 13.sp, fontWeight = FontWeight.Medium))
        }

        Spacer(Modifier.height(10.dp))
        FullWidthPill(
            text = if (saving) "saving…" else "save personal info",
            enabled = !saving
        ) {
            val cleanName = name.trim()
            if (cleanName.isNotEmpty() && cleanName.length < 2) {
                formError = "enter at least 2 characters for your name"
                return@FullWidthPill
            }
            val cleanDob = if (dob.isBlank()) null else {
                val r = UpiValidator.parseAdultDob(dob)
                if (r.isFailure) {
                    formError = r.exceptionOrNull()?.message ?: "invalid date of birth"
                    return@FullWidthPill
                }
                r.getOrThrow().first
            }
            val cleanPhone = if (phone.isBlank()) null else {
                UpiValidator.normalizeIndianPhone(phone) ?: run {
                    formError = "enter a valid 10-digit indian mobile number"
                    return@FullWidthPill
                }
            }
            val cleanEmPhone = if (emergencyPhone.isBlank()) null else {
                UpiValidator.normalizeIndianPhone(emergencyPhone) ?: run {
                    formError = "check emergency contact phone number"
                    return@FullWidthPill
                }
            }
            formError = null
            val base = profile ?: Profile()
            onSave(
                base.copy(
                    displayName = cleanName.ifBlank { null },
                    phone = cleanPhone,
                    dob = cleanDob,
                    gender = gender.ifBlank { null },
                    orgTag = orgTag.trim().ifBlank { null },
                    emergencyName = emergencyName.trim().ifBlank { null },
                    emergencyPhone = cleanEmPhone,
                    aadhaarLast4 = aadhaarLast4,
                    aadhaarVerified = aadhaarVerified && !aadhaarLast4.isNullOrBlank(),
                    avatarData = avatarData
                ),
                "personal info saved",
                true
            )
        }
    }
}

@Composable
private fun UpiPaymentsPage(
    profile: Profile?,
    saving: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSave: (Profile, String?) -> Unit
) {
    val context = LocalContext.current
    val savedUpi = profile?.upiId?.trim().orEmpty()
    val savedVerified = profile?.upiVerified == true && savedUpi.isNotEmpty()

    var upi by remember(savedUpi) { mutableStateOf(savedUpi) }
    var verified by remember(savedUpi, savedVerified) { mutableStateOf(savedVerified) }
    var feedback by remember(savedUpi, savedVerified) {
        mutableStateOf<Pair<String, Boolean>?>(
            if (savedVerified) {
                when (val c = UpiValidator.verify(savedUpi)) {
                    is UpiCheck.Valid -> "verified · ${c.providerLabel}" to true
                    is UpiCheck.Invalid -> "verified upi id" to true
                }
            } else null
        )
    }

    Column(Modifier.fillMaxSize()) {
        SubpageHeader(title = "upi / payments", onBack = onBack, onClose = onClose)
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(
                "when you book a cab for the pool, co-riders pay their distance split directly to your upi id.",
                style = TextStyle(CarpoolrColors.CreamMuted, 13.5.sp)
            )

            EditField(
                label = "upi id (vpa)",
                hint = "you@okaxis or 9876543210@ybl",
                value = upi,
                keyboardType = KeyboardType.Email,
                onChange = { raw ->
                    val trimmed = raw.trim().take(70)
                    upi = trimmed
                    val stillMatchesSaved = savedVerified && trimmed.equals(savedUpi, ignoreCase = true)
                    verified = stillMatchesSaved
                    feedback = if (stillMatchesSaved) {
                        when (val c = UpiValidator.verify(trimmed)) {
                            is UpiCheck.Valid -> "verified · ${c.providerLabel}" to true
                            is UpiCheck.Invalid -> "verified upi id" to true
                        }
                    } else null
                }
            )

            feedback?.let { (msg, ok) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(16.dp))
                        .background(
                            if (ok) CarpoolrColors.Cream.copy(alpha = 0.10f)
                            else CarpoolrColors.Raspberry.copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ok) {
                        GlassIcon(R.drawable.ic_check, null, size = 18, tint = CarpoolrColors.Raspberry)
                        Spacer(Modifier.width(10.dp))
                    }
                    BasicText(
                        msg,
                        style = TextStyle(
                            if (ok) CarpoolrColors.Cream else CarpoolrColors.Raspberry,
                            13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            FullWidthPill(
                text = when {
                    saving -> "verifying…"
                    verified -> "✓ upi verified"
                    else -> "verify & save upi"
                },
                enabled = !saving && !verified,
                color = if (verified) CarpoolrColors.Cream.copy(alpha = 0.16f) else CarpoolrColors.Raspberry
            ) {
                when (val res = UpiValidator.verify(upi)) {
                    is UpiCheck.Valid -> {
                        upi = res.normalized
                        verified = true
                        feedback = "verified · ${res.providerLabel}" to true
                        val base = profile ?: Profile()
                        onSave(
                            base.copy(upiId = res.normalized, upiVerified = true),
                            "upi verified & saved"
                        )
                    }
                    is UpiCheck.Invalid -> {
                        verified = false
                        feedback = res.reason to false
                    }
                }
            }

            val validCheck = UpiValidator.verify(upi) as? UpiCheck.Valid
            if (validCheck != null) {
                FullWidthPill(
                    text = "test ₹1 in upi app",
                    enabled = true,
                    color = CarpoolrColors.Cream.copy(alpha = 0.12f)
                ) {
                    payUpi(
                        context,
                        validCheck.normalized,
                        profile?.displayName?.takeIf { it.isNotBlank() } ?: "carpoolr rider",
                        1
                    )
                }
            }

            if (savedUpi.isNotEmpty()) {
                BasicText(
                    "remove saved upi id",
                    style = TextStyle(CarpoolrColors.CreamMuted, 13.sp),
                    modifier = Modifier
                        .clickable {
                            upi = ""
                            verified = false
                            feedback = null
                            val base = profile ?: Profile()
                            onSave(base.copy(upiId = null, upiVerified = false), "upi id removed")
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionsPage(
    isConnected: (Provider) -> Boolean,
    onConnect: (Provider) -> Unit,
    onDisconnect: (Provider) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var manageProvider by remember { mutableStateOf<Provider?>(null) }

    Column(Modifier.fillMaxSize()) {
        SubpageHeader(title = "connections", onBack = onBack, onClose = onClose)
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText(
                "sign in once on a ride app's mobile site so carpoolr can prefill your pool's route and read live fares.",
                style = TextStyle(CarpoolrColors.CreamMuted, 13.5.sp),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Provider.entries.forEach { p ->
                val connected = isConnected(p)
                val expanded = manageProvider == p && connected
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(18.dp))
                        .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
                        .clickable {
                            if (connected) manageProvider = if (expanded) null else p
                            else onConnect(p)
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIcon(R.drawable.ic_car, null, size = 22, tint = CarpoolrColors.CreamMuted)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(
                                p.label,
                                style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium)
                            )
                            BasicText(
                                if (connected) "session active · tap to manage" else "not connected",
                                style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp)
                            )
                        }
                        if (connected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText("connected", style = TextStyle(CarpoolrColors.Cream, 13.sp))
                                Spacer(Modifier.width(6.dp))
                                GlassIcon(R.drawable.ic_check, null, size = 16, tint = CarpoolrColors.Raspberry)
                            }
                        } else {
                            Box(
                                Modifier
                                    .clip(Capsule())
                                    .background(CarpoolrColors.Raspberry)
                                    .padding(horizontal = 13.dp, vertical = 6.dp)
                            ) {
                                BasicText("connect", style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChip("re-sign in", CarpoolrColors.Cream.copy(alpha = 0.14f)) {
                                manageProvider = null
                                onConnect(p)
                            }
                            ActionChip("disconnect", CarpoolrColors.Raspberry) {
                                manageProvider = null
                                onDisconnect(p)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubpageHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val backSource = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(backSource, 0.85f)
                .clip(RoundedRectangle(20.dp))
                .background(CarpoolrColors.Cream.copy(alpha = 0.12f))
                .clickable(backSource, null, onClick = onBack)
                .padding(8.dp)
        ) { GlassIcon(R.drawable.ic_back, "back", size = 20) }
        Spacer(Modifier.width(12.dp))
        BasicText(
            title,
            style = TextStyle(CarpoolrColors.Cream, 24.sp, fontFamily = Anton),
            modifier = Modifier.weight(1f)
        )
        CloseButton(onClose)
    }
}

@Composable
private fun SettingNavRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clip(RoundedRectangle(18.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIcon(icon, null, size = 22, tint = CarpoolrColors.CreamMuted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
            BasicText(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (checked) {
            GlassIcon(R.drawable.ic_check, null, size = 16, tint = CarpoolrColors.Raspberry)
            Spacer(Modifier.width(6.dp))
        }
        GlassIcon(R.drawable.ic_chevron_right, null, size = 18, tint = CarpoolrColors.CreamFaint)
    }
}

@Composable
private fun DrawerRow(item: ProfileItem, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clip(RoundedRectangle(18.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIcon(item.icon, null, size = 22, tint = CarpoolrColors.CreamMuted)
        Spacer(Modifier.width(14.dp))
        BasicText(item.label, style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(CarpoolrColors.Cream.copy(alpha = 0.10f))
    )
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text,
        style = TextStyle(CarpoolrColors.CreamMuted, 12.5.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun AvatarCircle(
    avatarBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    name: String,
    sizeDp: Int
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(Capsule())
            .background(CarpoolrColors.GreenLight)
            .border(1.5.dp, CarpoolrColors.Cream.copy(alpha = 0.22f), Capsule()),
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
                style = TextStyle(CarpoolrColors.Cream, (sizeDp * 0.38f).sp, fontFamily = Anton)
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
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(color)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        BasicText(text, style = TextStyle(Color.White, 12.5.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun FullWidthPill(
    text: String,
    enabled: Boolean,
    color: Color = CarpoolrColors.Raspberry,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.96f)
            .clip(Capsule())
            .background(if (enabled) color else color.copy(alpha = color.alpha * 0.65f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold))
    }
}

private object DobVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text.take(8)
        val len = d.length
        val formatted = buildString {
            for (i in d.indices) {
                if (i == 2 || i == 4) append(" / ")
                append(d[i])
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, len)
                return when {
                    o <= 2 -> o
                    o <= 4 -> o + 3
                    else -> o + 6
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val raw = when {
                    offset <= 2 -> offset
                    offset <= 5 -> 2
                    offset <= 7 -> offset - 3
                    offset <= 10 -> 4
                    else -> offset - 6
                }
                return raw.coerceIn(0, len)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
private fun DobField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onPickCalendar: () -> Unit
) {
    val ageLabel = remember(digits) {
        if (digits.length == 8) {
            UpiValidator.parseAdultDob(digits).getOrNull()?.second?.let { " · $it yrs" }.orEmpty()
        } else ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(16.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                "date of birth (18+)$ageLabel",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(CarpoolrColors.CreamMuted, 12.sp)
            )
            Box {
                if (digits.isEmpty()) {
                    BasicText("dd / mm / yyyy", style = TextStyle(CarpoolrColors.CreamFaint, 15.sp))
                }
                BasicTextField(
                    value = digits,
                    onValueChange = { onDigitsChange(it.filter(Char::isDigit).take(8)) },
                    singleLine = true,
                    visualTransformation = DobVisualTransformation,
                    textStyle = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        ActionChip("calendar", CarpoolrColors.Cream.copy(alpha = 0.14f), onClick = onPickCalendar)
    }
}

@Composable
private fun EditField(
    label: String,
    hint: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(16.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
        Box {
            if (value.isEmpty()) {
                BasicText(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.CreamFaint, 15.sp))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium),
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
