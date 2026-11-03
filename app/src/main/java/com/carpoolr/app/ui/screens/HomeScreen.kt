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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Placeholder rows to show the layout. Real pools come from Supabase matching later. */
private data class SamplePool(val from: String, val to: String, val time: String, val seats: String, val perHead: String)

private val SamplePools = listOf(
    SamplePool("MIT Gate 2", "Mangaluru Airport", "Today, 6:30 PM", "2 of 4 seats", "₹380 / head"),
    SamplePool("Tiger Circle", "Udupi Bus Stand", "Today, 7:15 PM", "1 of 3 seats", "₹90 / head"),
    SamplePool("Block-9", "Malpe Beach", "Sat, 4:00 PM", "3 of 4 seats", "₹110 / head"),
    SamplePool("MIT Gate 2", "Mangaluru Central", "Sun, 9:00 AM", "2 of 4 seats", "₹340 / head")
)

@Composable
fun HomeScreen(cardBackdrop: Backdrop, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp).staggeredEntrance(0)) {
                BasicText("Where to?", style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton))
                BasicText(
                    "Share the ride, split the fare.",
                    style = TextStyle(CarpoolrColors.CreamMuted, 15.sp)
                )
            }
        }
        item {
            GlassCard(
                backdrop = cardBackdrop,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggeredEntrance(1)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteField(label = "Pickup", value = "MIT Gate 2, Manipal", dotFilled = true)
                    RouteField(label = "Drop", value = "Choose destination", dotFilled = false)
                    Spacer(Modifier.height(4.dp))
                    PrimaryPill("Find a pool")
                }
            }
        }
        item {
            BasicText(
                "Pools leaving soon",
                style = TextStyle(CarpoolrColors.Cream, 22.sp, fontFamily = Anton),
                modifier = Modifier.padding(start = 20.dp, top = 10.dp).staggeredEntrance(2)
            )
        }
        itemsIndexed(SamplePools) { i, pool ->
            PoolCard(pool, cardBackdrop, Modifier.padding(horizontal = 16.dp).staggeredEntrance(3 + i))
        }
    }
}

@Composable
private fun RouteField(label: String, value: String, dotFilled: Boolean) {
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
                .background(if (dotFilled) CarpoolrColors.Cream else CarpoolrColors.CreamFaint)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText(label, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
            BasicText(value, style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
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
            .background(CarpoolrColors.Cream)
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(CarpoolrColors.Green, 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PoolCard(pool: SamplePool, backdrop: Backdrop, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    GlassCard(
        backdrop = backdrop,
        corner = 24.dp,
        contentPadding = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clickable(interactionSource = source, indication = null, onClick = {})
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    "${pool.from} → ${pool.to}",
                    style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(4.dp))
                BasicText("${pool.time} · ${pool.seats}", style = TextStyle(CarpoolrColors.CreamMuted, 13.sp))
            }
            Spacer(Modifier.width(12.dp))
            BasicText(pool.perHead, style = TextStyle(CarpoolrColors.Cream, 18.sp, fontFamily = Anton))
        }
    }
}
