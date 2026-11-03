package com.carpoolr.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.carpoolr.app.R

object CarpoolrColors {
    val Green = Color(0xFF004720)
    val GreenDeep = Color(0xFF00301A)
    val GreenLight = Color(0xFF0B6B36)
    val Cream = Color(0xFFEFEEE0)
    val CreamMuted = Color(0xB3EFEEE0)
    val CreamFaint = Color(0x66EFEEE0)
}

val Anton = FontFamily(Font(R.font.anton))

/** Shared spring tokens so every motion in the app feels like one system. */
object Motion {
    val snappy = spring<Float>(dampingRatio = 0.6f, stiffness = 500f)
    val gentle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = 380f)
}
