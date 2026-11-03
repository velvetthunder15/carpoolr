package com.carpoolr.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
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

/** Route-mark + carpoolr headline (centre, no glass) · profile (right, glass). */
@Composable
fun CarpoolrTopBar(
    backdrop: Backdrop,
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
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_route_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CarpoolrColors.Raspberry),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                "carpoolr",
                style = TextStyle(
                    color = CarpoolrColors.Cream,
                    fontFamily = Anton,
                    fontSize = 26.sp,
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
            GlassIcon(R.drawable.ic_person, "profile")
        }
    }
}

@Composable
fun GlassIcon(
    res: Int,
    description: String?,
    modifier: Modifier = Modifier,
    size: Int = 24,
    tint: Color = CarpoolrColors.Cream
) {
    Image(
        painter = painterResource(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size.dp)
    )
}
