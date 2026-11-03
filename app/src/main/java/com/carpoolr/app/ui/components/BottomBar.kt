package com.carpoolr.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.ui.glass.LiquidBottomTab
import com.carpoolr.app.ui.glass.LiquidBottomTabs
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop

/** Every tab icon is a 24dp Lucide line icon, so all tabs read at the same size. */
enum class Tab(val label: String, val icon: Int) {
    Home("home", R.drawable.ic_home),
    Rides("rides", R.drawable.ic_car),
    Chats("chats", R.drawable.ic_chat)
}

/** The floating liquid-glass icon bar, bottom-centre. Drag the pill or tap a tab. */
@Composable
fun CarpoolrBottomBar(
    backdrop: Backdrop,
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = Tab.entries
    LiquidBottomTabs(
        selectedTabIndex = selected,
        onTabSelected = onSelect,
        backdrop = backdrop,
        tabsCount = tabs.size,
        accentColor = CarpoolrColors.Cream,
        containerColor = CarpoolrColors.GreenDeep.copy(alpha = 0.40f),
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 56.dp, end = 56.dp, bottom = 12.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            LiquidBottomTab({ onSelect(index) }) {
                Image(
                    painter = painterResource(tab.icon),
                    contentDescription = tab.label,
                    colorFilter = ColorFilter.tint(CarpoolrColors.CreamMuted),
                    modifier = Modifier.size(24.dp)
                )
                BasicText(
                    tab.label,
                    style = TextStyle(
                        color = CarpoolrColors.CreamMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
