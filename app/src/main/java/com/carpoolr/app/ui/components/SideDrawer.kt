package com.carpoolr.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop
import com.carpoolr.app.connect.Provider
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Glass panel that springs in from the right (profile) or the left.
 * Always composed so it can animate out; it does nothing while hidden.
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
                .background(Color.Black.copy(alpha = 0.35f))
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
                .fillMaxWidth(0.78f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
                .graphicsLayer {
                    val dir = if (fromStart) -1f else 1f
                    translationX = dir * (1f - progress) * (size.width + 24.dp.toPx())
                }
        ) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxSize(), corner = 32.dp) {
                // swallow taps so they don't fall through to the scrim
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

private data class ProfileItem(val label: String, val icon: Int)

private val AccountItems = listOf(
    ProfileItem("ride history", R.drawable.ic_history)
)

private val SafetyItems = listOf(
    ProfileItem("notifications", R.drawable.ic_bell)
)

private val SupportItems = listOf(
    ProfileItem("help", R.drawable.ic_help),
    ProfileItem("about", R.drawable.ic_info),
    ProfileItem("sign out", R.drawable.ic_logout)
)

/** Everything that used to live in the menu now lives here. */
@Composable
fun ProfileContent(
    onClose: () -> Unit,
    isConnected: (Provider) -> Boolean,
    onConnect: (Provider) -> Unit,
    onSignOut: () -> Unit,
    name: String = "",
    upi: String = "",
    onSaveProfile: (String, String) -> Unit = { _, _ -> },
    onRow: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedRectangle(28.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.14f))
                    .padding(12.dp)
            ) { GlassIcon(R.drawable.ic_person, null, size = 28) }
            Spacer(Modifier.width(12.dp))
            BasicText(
                "your profile",
                style = TextStyle(CarpoolrColors.Cream, 26.sp, fontFamily = Anton),
                modifier = Modifier.weight(1f)
            )
            CloseButton(onClose)
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            var i = 0
            ProfileEditor(name, upi, onSaveProfile, Modifier.staggeredEntrance(i++, 32L))
            AccountItems.forEach { item -> DrawerRow(item, Modifier.staggeredEntrance(i++, 32L)) { onRow(item.label) } }

            SectionLabel("connections", Modifier.staggeredEntrance(i++, 32L))
            Provider.entries.forEach { p ->
                ConnectionRow(p, isConnected(p), { onConnect(p) }, Modifier.staggeredEntrance(i++, 32L))
            }

            Divider()
            SafetyItems.forEach { item -> DrawerRow(item, Modifier.staggeredEntrance(i++, 32L)) { onRow(item.label) } }
            Divider()
            SupportItems.forEach { item ->
                DrawerRow(
                    item,
                    Modifier.staggeredEntrance(i++, 32L),
                    onClick = if (item.label == "sign out") onSignOut else ({ onRow(item.label) })
                )
            }
        }
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
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = TextStyle(CarpoolrColors.CreamMuted, 13.sp, fontWeight = FontWeight.Medium),
        modifier = modifier.padding(start = 12.dp, top = 18.dp, bottom = 4.dp)
    )
}

/** A ride app link. uber/ola sign in on the web; rapido can only be opened. */
@Composable
private fun ConnectionRow(p: Provider, connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        GlassIcon(R.drawable.ic_link, null, size = 22, tint = CarpoolrColors.CreamMuted)
        Spacer(Modifier.width(14.dp))
        BasicText(
            p.label,
            style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        when {
            !p.signsInOnWeb -> {
                BasicText("open app", style = TextStyle(CarpoolrColors.CreamMuted, 13.sp))
                Spacer(Modifier.width(6.dp))
                GlassIcon(R.drawable.ic_external, null, size = 16, tint = CarpoolrColors.CreamMuted)
            }
            connected -> {
                BasicText("connected", style = TextStyle(CarpoolrColors.Cream, 13.sp))
                Spacer(Modifier.width(6.dp))
                GlassIcon(R.drawable.ic_check, null, size = 16, tint = CarpoolrColors.Raspberry)
            }
            else -> Box(
                Modifier
                    .clip(Capsule())
                    .background(CarpoolrColors.Raspberry)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) { BasicText("connect", style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Bold)) }
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

/** Name your pool sees, and the UPI id riders pay you back on when you book. */
@Composable
private fun ProfileEditor(name: String, upi: String, onSave: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var n by remember(name) { mutableStateOf(name) }
    var u by remember(upi) { mutableStateOf(upi) }
    val dirty = n.trim() != name || u.trim() != upi
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditField("name", "how your pool sees you", n) { n = it.take(30) }
        EditField("upi id", "you@okaxis, so riders can pay you", u) { u = it.trim().take(60) }
        if (dirty) Box(
            Modifier.clip(Capsule()).background(CarpoolrColors.Raspberry)
                .clickable { onSave(n.trim(), u.trim()) }
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) { BasicText("save", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)) }
    }
}

@Composable
private fun EditField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(16.dp)).background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        BasicText(label, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
        Box {
            if (value.isEmpty()) BasicText(hint, style = TextStyle(CarpoolrColors.CreamFaint, 15.sp))
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
