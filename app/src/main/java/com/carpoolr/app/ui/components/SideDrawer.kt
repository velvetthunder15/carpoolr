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

private val DrawerItems = listOf("My pools", "Saved places", "Payments", "Safety", "Settings", "Help")

/**
 * Glass panel that springs in from the left (menu) or right (profile).
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

@Composable
fun MenuContent(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("carpoolr", style = TextStyle(CarpoolrColors.Cream, 30.sp, fontFamily = Anton))
            Spacer(Modifier.weight(1f))
            CloseButton(onClose)
        }
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DrawerItems.forEachIndexed { i, label ->
                DrawerRow(label, Modifier.staggeredEntrance(i, 40L))
            }
        }
    }
}

@Composable
fun ProfileContent(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            CloseButton(onClose)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .clip(RoundedRectangle(40.dp))
                .background(CarpoolrColors.Cream.copy(alpha = 0.14f))
                .padding(18.dp)
        ) { GlassIcon(R.drawable.ic_person, null, size = 44) }
        Spacer(Modifier.height(16.dp))
        BasicText("Your profile", style = TextStyle(CarpoolrColors.Cream, 28.sp, fontFamily = Anton))
        Spacer(Modifier.height(6.dp))
        BasicText(
            "Sign-in and verified college ID come next.",
            style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Edit profile", "Ride history", "Trusted contacts").forEachIndexed { i, label ->
                DrawerRow(label, Modifier.staggeredEntrance(i, 40L))
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
    ) { GlassIcon(R.drawable.ic_close, "Close", size = 20) }
}

@Composable
private fun DrawerRow(label: String, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clip(RoundedRectangle(18.dp))
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        BasicText(label, style = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Medium))
    }
}

