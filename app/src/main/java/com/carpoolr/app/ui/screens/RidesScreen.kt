package com.carpoolr.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.ChatMessage
import com.carpoolr.app.data.PastRide
import com.carpoolr.app.pool.FareSplit
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Finished pools, newest first, with post-ride UPI settlement and read-only pool chat. */
@Composable
fun RidesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rides by remember { mutableStateOf<List<PastRide>?>(null) }
    var chatRide by remember { mutableStateOf<PastRide?>(null) }
    var busyPool by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { rides = runCatching { Backend.myRides() }.getOrDefault(emptyList()) }

    chatRide?.let { active ->
        BackHandler { chatRide = null }
        PastRideChat(ride = active, contentPadding = contentPadding, onClose = { chatRide = null })
        return
    }

    val list = rides ?: return
    if (list.isEmpty()) {
        PlaceholderScreen("no rides yet", "pools you finish will show up here.", contentPadding)
        return
    }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM, h:mm a") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BasicText("rides", style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton), modifier = Modifier.padding(horizontal = 20.dp))
        }
        items(list, key = { it.poolId }) { r ->
            PastRideCard(
                ride = r,
                fmt = fmt,
                busy = busyPool == r.poolId,
                onPayUpi = { upi, booker, amount -> payUpi(context, upi, booker, amount) },
                onMarkPaid = {
                    busyPool = r.poolId
                    scope.launch {
                        runCatching { Backend.markPaid(r.poolId) }.onFailure { e -> toast(context, e) }
                        rides = runCatching { Backend.myRides() }.getOrDefault(list)
                        busyPool = null
                    }
                },
                onOpenChat = { chatRide = r }
            )
        }
    }
}

@Composable
private fun PastRideCard(
    ride: PastRide,
    fmt: DateTimeFormatter,
    busy: Boolean,
    onPayUpi: (String, String, Int) -> Unit,
    onMarkPaid: () -> Unit,
    onOpenChat: () -> Unit
) {
    val cleanLegs = remember(ride.legs) {
        ride.legs.distinctBy { it.riderId }.map { FareSplit.Leg(it.riderId, it.boardKm, it.alightKm) }
    }
    val shares = remember(ride.fareRupees, cleanLegs) {
        val fare = ride.fareRupees
        if (fare != null && fare > 0 && cleanLegs.isNotEmpty()) {
            runCatching { FareSplit.splitLegs(fare, cleanLegs) }.getOrDefault(emptyMap())
        } else emptyMap()
    }
    val meId = ride.legs.firstOrNull { it.isMe }?.riderId
    val myShare = meId?.let { shares[it] }
    val unpaidOwed = ride.fareRupees != null && !ride.isBooker && !ride.paid && (myShare ?: 0) > 0
    val unpaidOthers = remember(ride.legs, ride.isBooker, ride.fareRupees) {
        if (ride.isBooker && ride.fareRupees != null) ride.legs.filter { !it.isMe && !it.isBooker && !it.paid } else emptyList()
    }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedRectangle(20.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "${ride.pickupLabel} → ${ride.dropoffLabel}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            SmallActionChip("chat", CarpoolrColors.Cream.copy(alpha = 0.12f), enabled = true, onClick = onOpenChat)
        }

        val date = ride.doneAt?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(fmt).lowercase() }.getOrNull()
        }
        val fareSummary = when {
            myShare != null && ride.fareRupees != null ->
                "your share ₹$myShare (₹${ride.fareRupees}${ride.provider?.let { " on $it" } ?: ""})" +
                    if (!ride.isBooker && ride.paid) " · paid" else ""
            ride.fareRupees != null ->
                "₹${ride.fareRupees} total" + (ride.provider?.let { " on $it" } ?: "")
            else -> null
        }
        BasicText(
            listOfNotNull(date, "${ride.riders} riders", "${"%.1f".format(ride.tripKm)} km", fareSummary).joinToString(" · "),
            style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
        )

        if (ride.isBooker && ride.fareRupees != null && ride.legs.size > 1) {
            val statusText = if (unpaidOthers.isEmpty()) "everyone paid"
            else "unpaid: " + unpaidOthers.joinToString(", ") { leg ->
                leg.displayName + (shares[leg.riderId]?.let { " ₹$it" } ?: "")
            }
            BasicText(
                statusText,
                style = TextStyle(
                    if (unpaidOthers.isEmpty()) CarpoolrColors.CreamMuted else CarpoolrColors.Raspberry,
                    13.sp,
                    fontWeight = if (unpaidOthers.isEmpty()) FontWeight.Normal else FontWeight.Medium
                )
            )
        }

        if (unpaidOwed && myShare != null) {
            val upi = ride.bookerUpi?.takeIf { it.isNotBlank() }
            val bookerLabel = ride.bookerName ?: "booker"
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SmallActionChip(
                        text = if (upi != null) "pay ₹$myShare to $bookerLabel" else "$bookerLabel has no upi id",
                        color = CarpoolrColors.Raspberry,
                        enabled = upi != null && !busy,
                        fillWidth = true
                    ) { onPayUpi(upi!!, bookerLabel, myShare) }
                }
                SmallActionChip(
                    text = if (busy) "saving…" else "i've paid",
                    color = CarpoolrColors.Cream.copy(alpha = 0.14f),
                    enabled = !busy,
                    onClick = onMarkPaid
                )
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    text: String,
    color: Color,
    enabled: Boolean,
    fillWidth: Boolean = false,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(if (enabled) color else color.copy(alpha = color.alpha * 0.45f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun PastRideChat(ride: PastRide, contentPadding: PaddingValues, onClose: () -> Unit) {
    var messages by remember(ride.poolId) { mutableStateOf<List<ChatMessage>?>(null) }
    LaunchedEffect(ride.poolId) {
        messages = runCatching { Backend.rideMessages(ride.poolId) }.getOrDefault(emptyList())
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                "pool chat",
                style = TextStyle(CarpoolrColors.Cream, 34.sp, fontFamily = Anton),
                modifier = Modifier.weight(1f)
            )
            BasicText(
                "close",
                style = TextStyle(CarpoolrColors.CreamMuted, 15.sp),
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp)
            )
        }
        BasicText(
            "${ride.pickupLabel} → ${ride.dropoffLabel} · read-only",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(CarpoolrColors.CreamMuted, 13.sp),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        val list = messages
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (list == null) {
                item { BasicText("loading…", style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)) }
            } else if (list.isEmpty()) {
                item { BasicText("no messages from this ride.", style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)) }
            } else {
                items(list, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedRectangle(18.dp))
                                .background(if (m.isMe) CarpoolrColors.Raspberry else CarpoolrColors.Cream.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            if (!m.isMe) BasicText(m.displayName, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
                            BasicText(m.body, style = TextStyle(Color.White, 15.sp))
                        }
                    }
                }
            }
        }
    }
}
