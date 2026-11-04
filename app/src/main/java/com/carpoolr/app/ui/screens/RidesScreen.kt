package com.carpoolr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.PastRide
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.RoundedRectangle
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Finished pools, newest first. */
@Composable
fun RidesScreen(contentPadding: PaddingValues) {
    var rides by remember { mutableStateOf<List<PastRide>?>(null) }
    LaunchedEffect(Unit) { rides = runCatching { Backend.myRides() }.getOrDefault(emptyList()) }
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
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedRectangle(20.dp))
                    .background(CarpoolrColors.Cream.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicText("${r.pickupLabel} → ${r.dropoffLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(CarpoolrColors.Cream, 16.sp, fontWeight = FontWeight.Medium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val date = r.doneAt?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(fmt).lowercase() }.getOrNull() }
                    BasicText(
                        listOfNotNull(date, "${r.riders} riders", "${"%.1f".format(r.tripKm)} km",
                            r.fareRupees?.let { "₹$it total" + (r.provider?.let { p -> " on $p" } ?: "") }).joinToString(" · "),
                        style = TextStyle(CarpoolrColors.CreamMuted, 13.sp)
                    )
                }
            }
        }
    }
}
