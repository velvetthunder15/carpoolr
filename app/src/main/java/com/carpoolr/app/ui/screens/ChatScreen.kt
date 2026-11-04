package com.carpoolr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.ChatMessage
import com.carpoolr.app.data.PoolRider
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Group chat for your live pool. Polls every 3 seconds. */
@Composable
fun ChatScreen(contentPadding: PaddingValues) {
    var pool by remember { mutableStateOf<List<PoolRider>?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Backend.myPool() }.onSuccess { pool = it }
            val inPool = pool?.firstOrNull()?.poolId != null && pool?.firstOrNull()?.status == "matched"
            if (inPool) runCatching { Backend.messages(messages.lastOrNull()?.id ?: 0) }.onSuccess { new ->
                if (new.isNotEmpty()) { messages += new; list.animateScrollToItem(maxOf(0, messages.size - 1)) }
            } else messages.clear()
            delay(3000)
        }
    }

    val riders = pool
    if (riders == null) return
    if (riders.firstOrNull()?.poolId == null) {
        PlaceholderScreen("quiet in here", "chat with your pool once you're matched.", contentPadding)
        return
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()).imePadding()) {
        BasicText(
            riders.filter { !it.isMe }.joinToString(", ") { it.displayName ?: "rider" }.ifBlank { "your pool" },
            style = TextStyle(CarpoolrColors.Cream, 34.sp, fontFamily = Anton),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) item {
                BasicText("say hi and agree where to meet.", style = TextStyle(CarpoolrColors.CreamMuted, 14.sp))
            }
            items(messages, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.isMe) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(RoundedRectangle(18.dp))
                            .background(if (m.isMe) CarpoolrColors.Raspberry else CarpoolrColors.Cream.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        if (!m.isMe) BasicText(m.displayName, style = TextStyle(CarpoolrColors.CreamMuted, 12.sp))
                        BasicText(m.body, style = TextStyle(Color.White, 15.sp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).clip(Capsule()).background(CarpoolrColors.Cream.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (draft.isEmpty()) BasicText("message", style = TextStyle(CarpoolrColors.CreamFaint, 15.sp))
                BasicTextField(
                    draft, { draft = it.take(1000) },
                    textStyle = TextStyle(CarpoolrColors.Cream, 15.sp),
                    cursorBrush = SolidColor(CarpoolrColors.Raspberry),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(Capsule()).background(CarpoolrColors.Raspberry)
                    .clickable(enabled = draft.isNotBlank()) {
                        val text = draft.trim(); draft = ""
                        scope.launch {
                            runCatching { Backend.sendChat(text) }
                            runCatching { Backend.messages(messages.lastOrNull()?.id ?: 0) }.onSuccess { new ->
                                messages += new; if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) { BasicText("send", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold)) }
        }
    }
}
