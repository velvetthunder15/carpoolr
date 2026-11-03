package com.carpoolr.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import com.carpoolr.app.ui.theme.Motion

/** Springy press-down micro-interaction. Runs only on the render thread (graphicsLayer), so it stays at 60fps+. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.bouncy, label = "pressScale")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
