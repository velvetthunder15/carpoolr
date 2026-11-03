package com.carpoolr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.ui.components.GlassCard
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.components.staggeredEntrance
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Instant pooling: say where you're going, and get matched with people heading
 * the same way right now. There are no scheduled pools.
 * UI copy is lowercase. Only places the user enters keep their own casing.
 */
@Composable
fun HomeScreen(cardBackdrop: Backdrop, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BasicText(
                "where to?",
                style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton),
                modifier = Modifier.padding(horizontal = 20.dp).staggeredEntrance(0)
            )
        }
        item {
            GlassCard(
                backdrop = cardBackdrop,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggeredEntrance(1)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteField(label = "pickup", placeholder = "current location", value = null, dotFilled = true)
                    RouteField(label = "drop", placeholder = "enter destination", value = null, dotFilled = false)
                    Spacer(Modifier.height(4.dp))
                    PrimaryPill("pool me now")
                }
            }
        }
    }
}

/** [value] is user-entered and shown exactly as typed; the placeholder is lowercase UI copy. */
@Composable
private fun RouteField(label: String, placeholder: String, value: String?, dotFilled: Boolean) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(RoundedRectangle(18.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(Capsule())
                .background(if (dotFilled) CarpoolrColors.Raspberry else CarpoolrColors.CreamFaint)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText(label, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
            BasicText(
                value ?: placeholder,
                style = TextStyle(
                    if (value == null) CarpoolrColors.CreamMuted else CarpoolrColors.Cream,
                    16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun PrimaryPill(text: String) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(CarpoolrColors.Raspberry)
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
    }
}
