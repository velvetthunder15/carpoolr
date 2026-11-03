package com.carpoolr.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpoolr.app.ui.theme.Anton
import com.carpoolr.app.ui.theme.CarpoolrColors

/** Empty state: the Carpoolr route draws itself in, then the stop and endpoints pop. */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, contentPadding: PaddingValues) {
    val draw = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(width = 180.dp, height = 110.dp)) {
            val s = size.width / 2.2f
            val c = Offset(size.width / 2f, size.height / 2f)
            fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
            val a = p(-0.8f, 0.42f); val b = p(0.8f, -0.42f); val m = p(0f, 0f)
            val path = Path().apply {
                moveTo(a.x, a.y)
                val c1 = p(-0.2f, 0.55f); val c2 = p(-0.35f, 0f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, m.x, m.y)
                val c3 = p(0.35f, 0f); val c4 = p(0.2f, -0.55f)
                cubicTo(c3.x, c3.y, c4.x, c4.y, b.x, b.y)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * draw.value, partial, true)
            val lw = s * 0.133f
            drawPath(partial, CarpoolrColors.Cream, style = Stroke(width = lw, cap = StrokeCap.Round))
            val k = pop.value
            drawCircle(CarpoolrColors.Cream, s * 0.188f * k, a)
            drawCircle(CarpoolrColors.Cream, s * 0.188f * k, b)
            drawCircle(CarpoolrColors.Cream, s * 0.212f * k, m)
            drawCircle(CarpoolrColors.Green, s * 0.109f * k, m)
        }
        BasicText(
            title,
            style = TextStyle(CarpoolrColors.Cream, 34.sp, fontFamily = Anton, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 28.dp)
        )
        BasicText(
            subtitle,
            style = TextStyle(CarpoolrColors.CreamMuted, 15.sp, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
