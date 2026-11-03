package com.carpoolr.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Staggered spring entrance (fade + rise + settle), one item after another. */
@Composable
fun Modifier.staggeredEntrance(index: Int, stepMillis: Long = 55L): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMillis)
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 28.dp.toPx()
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}
