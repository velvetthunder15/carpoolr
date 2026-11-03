package com.carpoolr.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.carpoolr.app.ui.theme.CarpoolrColors

/** Deep-green field with soft light pools, so the liquid glass has something to bend. */
@Composable
fun CarpoolrBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(CarpoolrColors.GreenDeep, CarpoolrColors.Green, CarpoolrColors.GreenDeep)
            )
        )
        fun glow(center: Offset, radius: Float, color: Color) = drawCircle(
            Brush.radialGradient(listOf(color, Color.Transparent), center, radius),
            radius,
            center
        )
        glow(Offset(size.width * 0.15f, size.height * 0.18f), size.width * 0.7f, CarpoolrColors.GreenLight.copy(alpha = 0.75f))
        glow(Offset(size.width * 0.95f, size.height * 0.45f), size.width * 0.6f, CarpoolrColors.Cream.copy(alpha = 0.10f))
        glow(Offset(size.width * 0.3f, size.height * 0.92f), size.width * 0.8f, CarpoolrColors.GreenLight.copy(alpha = 0.6f))
    }
}
