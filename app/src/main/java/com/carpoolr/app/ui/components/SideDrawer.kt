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
    ProfileItem("edit profile", R.drawable.ic_person),
    ProfileItem("ride history", R.drawable.ic_history),
    ProfileItem("saved places", R.drawable.ic_pin),
    ProfileItem("payments", R.drawable.ic_wallet)
)

private val SafetyItems = listOf(
    ProfileItem("trusted contacts", R.drawable.ic_users),
    ProfileItem("safety", R.drawable.ic_shield),
    ProfileItem("notifications", R.drawable.ic_bell),
    ProfileItem("settings", R.drawable.ic_settings)
)

private val SupportItems = listOf(
    ProfileItem("help", R.drawable.ic_help),
    ProfileItem("about", R.drawable.ic_info),
    ProfileItem("sign out", R.drawable.ic_logout)
)

/** Everything that used to live in the menu now lives here. */
@Composable
fun ProfileContent(onClose: () -> Unit) {
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
            listOf(AccountItems, SafetyItems, SupportItems).forEachIndexed { group, items ->
                if (group > 0) {
                    Box(
                        Modifier
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CarpoolrColors.Cream.copy(alpha = 0.10f))
                    )
                }
                items.forEach { item ->
                    DrawerRow(item, Modifier.staggeredEntrance(i++, 32L))
                }
            }
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
private fun DrawerRow(item: ProfileItem, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clip(RoundedRectangle(18.dp))
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIcon(item.icon, null, size = 22, tint = CarpoolrColors.CreamMuted)
        Spacer(Modifier.width(14.dp))
        BasicText(item.label, style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
    }
}
