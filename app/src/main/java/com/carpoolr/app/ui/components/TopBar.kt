package com.carpoolr.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.R
import com.carpoolr.app.ui.glass.LiquidButton
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop

private val GlassSurface = CarpoolrColors.GreenDeep.copy(alpha = 0.35f)

/** Menu (left) · carpoolr tag (centre) · profile (right), all liquid glass. */
@Composable
fun CarpoolrTopBar(
    backdrop: Backdrop,
    onMenu: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
    ) {
        LiquidButton(
            onClick = onMenu,
            backdrop = backdrop,
            surfaceColor = GlassSurface,
            height = 52.dp,
            horizontalPadding = 0.dp,
            modifier = Modifier.align(Alignment.CenterStart).width(52.dp)
        ) {
            GlassIcon(R.drawable.ic_menu, "Menu")
        }

        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            surfaceColor = GlassSurface,
            height = 44.dp,
            horizontalPadding = 22.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            BasicText(
                "carpoolr",
                style = TextStyle(
                    color = CarpoolrColors.Cream,
                    fontFamily = Anton,
                    fontSize = 24.sp,
                    letterSpacing = 0.5.sp
                )
            )
        }

        LiquidButton(
            onClick = onProfile,
            backdrop = backdrop,
            surfaceColor = GlassSurface,
            height = 52.dp,
            horizontalPadding = 0.dp,
            modifier = Modifier.align(Alignment.CenterEnd).width(52.dp)
        ) {
            GlassIcon(R.drawable.ic_person, "Profile")
        }
    }
}

@Composable
fun GlassIcon(res: Int, description: String?, modifier: Modifier = Modifier, size: Int = 24) {
    Image(
        painter = painterResource(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(CarpoolrColors.Cream),
        modifier = modifier.size(size.dp)
    )
}
