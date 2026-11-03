package com.carpoolr.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.PoolRider
import com.carpoolr.app.places.DeviceLocation
import com.carpoolr.app.places.Place
import com.carpoolr.app.places.Places
import com.carpoolr.app.pool.FareSplit
import com.carpoolr.app.ui.components.GlassCard
import com.carpoolr.app.ui.components.pressScale
import com.carpoolr.app.ui.components.staggeredEntrance
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

private enum class Picking { Pickup, Drop }

/**
 * Instant pooling: pickup (your location by default) + drop, then "find a pool" matches you
 * right now with anyone whose trip runs along yours. While a trip is live the screen polls
 * the pool every 3 seconds. UI copy is lowercase; places keep their own casing.
 */
@Composable
fun HomeScreen(cardBackdrop: Backdrop, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickup by remember { mutableStateOf<Place?>(null) }
    var drop by remember { mutableStateOf<Place?>(null) }
    var locating by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    var trip by remember { mutableStateOf<List<PoolRider>?>(null) }   // null = loading
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun locate() {
        locating = true
        val loc = DeviceLocation.current(context)
        if (loc != null) {
            pickup = Places.reverse(loc.latitude, loc.longitude)
                ?: Place("current location", "", loc.latitude, loc.longitude)
        } else error = "turn on location, or tap pickup to search"
        locating = false
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) scope.launch { locate() } else error = "no location access. tap pickup to search"
    }

    // restore a live trip, and fill pickup from location
    LaunchedEffect(Unit) {
        trip = runCatching { Backend.myPool() }.getOrDefault(emptyList())
        if (pickup == null) {
            if (DeviceLocation.granted(context)) locate()
            else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // live trip: poll (this also retries the match on the server)
    val live = trip?.isNotEmpty() == true
    LaunchedEffect(live) {
        while (live) {
            delay(3000)
            runCatching { Backend.myPool() }.onSuccess { trip = it }
        }
    }

    picking?.let { which ->
        BackHandler { picking = null }
        PlaceSearch(
            title = if (which == Picking.Pickup) "pickup" else "drop",
            near = pickup,
            contentPadding = contentPadding,
            onUseLocation = if (which == Picking.Pickup) ({ picking = null; scope.launch { locate() }; Unit }) else null,
            onPick = { p -> if (which == Picking.Pickup) pickup = p else drop = p; picking = null; error = null },
            onClose = { picking = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val riders = trip
        if (riders != null && riders.isNotEmpty()) {
            item {
                PoolCard(cardBackdrop, riders, busy) {
                    busy = true
                    scope.launch {
                        runCatching { Backend.leavePool() }
                        trip = runCatching { Backend.myPool() }.getOrDefault(emptyList())
                        busy = false
                    }
                }
            }
            return@LazyColumn
        }
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
                    RouteField("pickup", if (locating) "finding you…" else "current location", pickup?.name, true) {
                        picking = Picking.Pickup
                    }
                    RouteField("drop", "enter destination", drop?.name, false) { picking = Picking.Drop }
                    Spacer(Modifier.height(4.dp))
                    PrimaryPill(if (busy) "finding…" else "find a pool", enabled = !busy) {
                        val p = pickup; val d = drop
                        if (p == null) { error = "set a pickup first"; return@PrimaryPill }
                        if (d == null) { error = "where are you going?"; picking = Picking.Drop; return@PrimaryPill }
                        busy = true; error = null
                        scope.launch {
                            runCatching { Backend.findPool(p.lat, p.lng, p.name, d.lat, d.lng, d.name) }
                                .onSuccess { trip = runCatching { Backend.myPool() }.getOrDefault(emptyList()) }
                                .onFailure { e -> error = "couldn't start: " + (e.message?.lineSequence()?.firstOrNull() ?: "").take(100).lowercase() }
                            busy = false
                        }
                    }
                    error?.let {
                        BasicText(it, style = TextStyle(CarpoolrColors.Raspberry, 14.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

/** Searching or pooled: who's in, where they're headed, and each share of the fare by distance. */
@Composable
private fun PoolCard(backdrop: Backdrop, riders: List<PoolRider>, busy: Boolean, onLeave: () -> Unit) {
    val first = riders.first()
    val pooled = first.status == "matched" && riders.size > 1
    val shares = remember(riders) {
        if (!pooled) emptyMap() else runCatching {
            FareSplit.split(100, riders.map { FareSplit.Rider(it.riderId, it.tripKm.coerceAtLeast(0.1)) })
        }.getOrDefault(emptyMap())
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedContent(pooled, label = "poolTitle") { isPooled ->
            BasicText(
                if (isPooled) "you're pooled" else "finding your pool",
                style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!pooled) {
                    var left by remember { mutableStateOf(minutesLeft(first.expiresAt)) }
                    LaunchedEffect(first.expiresAt) { while (true) { left = minutesLeft(first.expiresAt); delay(15_000) } }
                    BasicText(
                        "looking for riders heading your way" + (left?.let { " · $it min left" } ?: ""),
                        style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)
                    )
                }
                riders.forEach { r ->
                    RiderRow(r, shares[r.riderId])
                }
                if (pooled) BasicText(
                    "split by distance: whoever gets off first pays only for their stretch.",
                    style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
                )
                Spacer(Modifier.height(2.dp))
                PrimaryPill(
                    if (busy) "leaving…" else if (pooled) "leave pool" else "stop searching",
                    enabled = !busy,
                    color = CarpoolrColors.Cream.copy(alpha = 0.14f),
                    onClick = onLeave
                )
            }
        }
    }
}

@Composable
private fun RiderRow(r: PoolRider, sharePct: Int?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedRectangle(18.dp)).background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(Capsule())
                .background(if (r.isMe) CarpoolrColors.Raspberry else CarpoolrColors.GreenLight),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                (if (r.isMe) "you" else r.displayName ?: "r").take(1),
                style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                if (r.isMe) "you" else (r.displayName ?: "rider"),
                style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium)
            )
            BasicText(
                "${r.pickupLabel} → ${r.dropoffLabel}",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            BasicText("${"%.1f".format(r.tripKm)} km", style = TextStyle(CarpoolrColors.Cream, 14.sp, fontWeight = FontWeight.Medium))
            sharePct?.let { BasicText("$it% of fare", style = TextStyle(CarpoolrColors.CreamMuted, 12.sp)) }
        }
    }
}

/** Full-screen place search (results biased to [near]). */
@Composable
private fun PlaceSearch(
    title: String,
    near: Place?,
    contentPadding: PaddingValues,
    onUseLocation: (() -> Unit)?,
    onPick: (Place) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        loading = true
        runCatching { Places.search(query, near) }
            .onSuccess { results = it; failed = false }
            .onFailure { failed = true }
        loading = false
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, style = TextStyle(CarpoolrColors.Cream, 44.sp, fontFamily = Anton), modifier = Modifier.weight(1f))
                BasicText("close", style = TextStyle(CarpoolrColors.CreamMuted, 15.sp), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
        }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedRectangle(18.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                if (query.isEmpty()) BasicText("search a place", style = TextStyle(CarpoolrColors.CreamFaint, 17.sp))
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(CarpoolrColors.Cream, 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        }
        onUseLocation?.let { use ->
            item { ResultRow("use my current location", "", onClick = use) }
        }
        if (loading) item { Hint("searching…") }
        if (failed) item { Hint("couldn't reach place search. check your connection.") }
        if (!loading && !failed && query.trim().length >= 2 && results.isEmpty()) item { Hint("no places found") }
        items(results) { p -> ResultRow(p.name, p.detail) { onPick(p) } }
    }
}

@Composable
private fun Hint(text: String) =
    BasicText(text, style = TextStyle(CarpoolrColors.CreamMuted, 14.sp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))

@Composable
private fun ResultRow(name: String, detail: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().pressScale(source, 0.98f).clip(RoundedRectangle(16.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.06f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
        if (detail.isNotBlank()) BasicText(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(CarpoolrColors.CreamMuted, 13.sp))
    }
}

private fun minutesLeft(expiresAt: String?): Int? = runCatching {
    val end = OffsetDateTime.parse(expiresAt).toInstant()
    Duration.between(Instant.now(), end).toMinutes().toInt().coerceAtLeast(0)
}.getOrNull()

/** [value] is user-entered and shown exactly as typed; the placeholder is lowercase UI copy. */
@Composable
private fun RouteField(label: String, placeholder: String, value: String?, dotFilled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(RoundedRectangle(18.dp))
            .background(CarpoolrColors.Cream.copy(alpha = 0.08f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
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
                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
private fun PrimaryPill(
    text: String,
    enabled: Boolean = true,
    color: Color = CarpoolrColors.Raspberry,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.95f)
            .clip(Capsule())
            .background(if (enabled) color else color.copy(alpha = color.alpha * 0.5f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
    }
}
