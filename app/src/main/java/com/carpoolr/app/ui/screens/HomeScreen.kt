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
import com.carpoolr.app.pool.MatchService
import com.carpoolr.app.connect.BookActivity
import com.carpoolr.app.connect.Provider
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

    val book = rememberLauncherForActivityResult(BookActivity.Contract()) { res ->
        if (res != null) {
            busy = true
            scope.launch {
                runCatching { Backend.setBooking(res.fare, res.provider.label) }.onFailure { e -> toast(context, e) }
                trip = runCatching { Backend.myPool() }.getOrDefault(emptyList())
                busy = false
            }
        }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                fun act(block: suspend () -> Unit) {
                    busy = true
                    scope.launch {
                        runCatching { block() }.onFailure { e -> toast(context, e) }
                        trip = runCatching { Backend.myPool() }.getOrDefault(emptyList())
                        busy = false
                    }
                }
                PoolCard(
                    cardBackdrop, riders, busy,
                    onLeave = { MatchService.stop(context); act { Backend.leavePool() } },
                    onBook = { p ->
                        val me = riders.first { it.isMe }
                        book.launch(BookActivity.Input(p, me.pickupLat, me.pickupLng, me.pickupLabel, me.dropLat, me.dropLng, me.dropoffLabel))
                    },
                    onPaid = { act { Backend.markPaid() } },
                    onFinish = { act { Backend.finishPool() } }
                )
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
                                .onSuccess { m ->
                                    trip = runCatching { Backend.myPool() }.getOrDefault(emptyList())
                                    if (m.status == "searching") {
                                        if (android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        MatchService.start(context)
                                    }
                                }
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

/** Searching, pooled, booked: who's in, who books, the fare, and each rider's share by distance. */
@Composable
private fun PoolCard(
    backdrop: Backdrop,
    riders: List<PoolRider>,
    busy: Boolean,
    onLeave: () -> Unit,
    onBook: (Provider) -> Unit,
    onPaid: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val first = riders.first()
    val pooled = first.status == "matched" && riders.size > 1
    val booked = first.poolStatus == "booked" && first.fareRupees != null
    val me = riders.firstOrNull { it.isMe } ?: first
    val booker = riders.firstOrNull { it.isBooker }
    val legs = remember(riders) { riders.map { FareSplit.Leg(it.riderId, it.boardKm, it.alightKm) } }
    val shares = remember(riders) {
        if (!pooled) emptyMap() else runCatching { FareSplit.splitLegs(first.fareRupees ?: 100, legs) }.getOrDefault(emptyMap())
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedContent(Triple(pooled, booked, 0), label = "poolTitle") { (isPooled, isBooked, _) ->
            BasicText(
                when { isBooked -> "ride booked"; isPooled -> "you're pooled"; else -> "finding your pool" },
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
                        "looking for riders heading your way" + (left?.let { " · $it min left" } ?: "") +
                            ". we'll notify you when you're pooled.",
                        style = TextStyle(CarpoolrColors.CreamMuted, 14.sp)
                    )
                }
                if (booked) BasicText(
                    "₹${first.fareRupees} on ${first.provider ?: "the ride app"}, booked by ${if (booker?.isMe == true) "you" else booker?.displayName ?: "the booker"}",
                    style = TextStyle(CarpoolrColors.Cream, 15.sp, fontWeight = FontWeight.Medium)
                )
                riders.forEach { r -> RiderRow(r, shares[r.riderId], booked) }
                if (pooled && !booked) BasicText(
                    "shares are by distance ridden. rupees appear once the cab is booked.",
                    style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
                )
                Spacer(Modifier.height(2.dp))

                when {
                    // the farthest rider books; their route covers everyone
                    pooled && !booked && me.isBooker -> {
                        BasicText("you ride farthest, so you book. pick the app:", style = TextStyle(CarpoolrColors.CreamMuted, 13.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Provider.entries.forEach { p ->
                                Box(
                                    Modifier.weight(1f).clip(Capsule()).background(CarpoolrColors.Raspberry)
                                        .clickable(enabled = !busy) { onBook(p) }.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) { BasicText(p.label, style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)) }
                            }
                        }
                    }
                    pooled && !booked -> BasicText(
                        "${booker?.displayName ?: "the farthest rider"} is booking the cab. hang tight.",
                        style = TextStyle(CarpoolrColors.Cream, 14.sp)
                    )
                    booked && !me.isBooker && !me.paid -> {
                        val owe = shares[me.riderId] ?: 0
                        val upi = booker?.upiId
                        PrimaryPill(if (upi != null) "pay ₹$owe to ${booker?.displayName ?: "booker"}" else "${booker?.displayName ?: "booker"} hasn't added a upi id", enabled = upi != null && owe > 0) {
                            payUpi(context, upi!!, booker?.displayName ?: "carpoolr rider", owe)
                        }
                        PrimaryPill("i've paid", enabled = !busy, color = CarpoolrColors.Cream.copy(alpha = 0.14f), onClick = onPaid)
                    }
                }

                if (booked) PrimaryPill(if (busy) "ending…" else "end ride", enabled = !busy,
                    color = CarpoolrColors.Cream.copy(alpha = 0.14f), onClick = onFinish)
                else PrimaryPill(
                    if (busy) "leaving…" else if (pooled) "leave pool" else "stop searching",
                    enabled = !busy,
                    color = CarpoolrColors.Cream.copy(alpha = 0.14f),
                    onClick = onLeave
                )
            }
        }
    }
}

/** Opens the rider's UPI app with the amount filled in. */
private fun payUpi(context: android.content.Context, vpa: String, name: String, rupees: Int) {
    val uri = android.net.Uri.Builder().scheme("upi").authority("pay")
        .appendQueryParameter("pa", vpa).appendQueryParameter("pn", name)
        .appendQueryParameter("am", "$rupees.00").appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "carpoolr ride share").build()
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "pay with")) }
        .onFailure { android.widget.Toast.makeText(context, "no upi app found", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
private fun RiderRow(r: PoolRider, share: Int?, rupees: Boolean) {
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
                (if (r.isMe) "you" else (r.displayName ?: "rider")) + (if (r.isBooker && r.status == "matched") " · books" else ""),
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
            share?.let {
                BasicText(
                    if (rupees) "₹$it" + (if (r.paid && !r.isBooker) " · paid" else "") else "$it% of fare",
                    style = TextStyle(if (rupees) CarpoolrColors.Cream else CarpoolrColors.CreamMuted, if (rupees) 14.sp else 12.sp,
                        fontWeight = if (rupees) FontWeight.Bold else FontWeight.Normal)
                )
            }
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

private fun toast(context: android.content.Context, e: Throwable) {
    val msg = e.message?.lineSequence()?.firstOrNull()?.take(120)?.lowercase() ?: "something went wrong"
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}
