package com.carpoolr.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carpoolr.app.ui.theme.CarpoolrColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** Liquid-glass surface: blur + lens refraction at the edges + specular highlight. */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(corner) },
                effects = {
                    vibrancy()
                    blur(12f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = { drawRect(CarpoolrColors.GreenDeep.copy(alpha = 0.30f)) }
            )
            .padding(contentPadding),
        content = content
    )
}
