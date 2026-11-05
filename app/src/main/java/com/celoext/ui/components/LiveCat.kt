package com.celoext.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.PI
import kotlin.math.sin

private val FUR = Color(0xFF16171C)
private val RIM = Color(0xFF454A58)
private val WHITE = Color(0xFFF4F1EA)
private val PINK = Color(0xFFE9A0A8)
private val EYE = Color(0xFFC8E05C)

/**
 * A 2D black-and-white (tuxedo) cat that sits on the home screen: it breathes, blinks, swings its tail,
 * twitches an ear, glances around, looks down while you type and purrs a heart when you tap it.
 */
@Composable
fun LiveCat(modifier: Modifier = Modifier, width: Dp = 210.dp, typing: Boolean = false) {
    var t by remember { mutableFloatStateOf(0f) }
    var happyAt by remember { mutableFloatStateOf(-10f) }
    LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { t = (it - start) / 1e9f }
    }
    Canvas(
        modifier.width(width).aspectRatio(200f / 196f).pointerInput(Unit) { detectTapGestures { happyAt = t } },
    ) {
        val u = size.width / 200f
        val time = t
        val happy = (time - happyAt).let { it in 0f..1.8f }

        // idle glances: every ~5 s the eyes drift to one side and back; typing pulls them down to the input
        val cycle = (time % 5.2f)
        val glance = if (cycle in 3.2f..4.6f) sin((cycle - 3.2f) / 1.4f * PI.toFloat()) * (if (((time / 5.2f).toInt()) % 2 == 0) 1f else -1f) else 0f
        val lookX = if (typing) 0f else glance * 3.2f
        val lookY = if (typing) 3.4f else 0f

        val bp = time % 4.3f
        val blink = if (bp < 0.16f) sin(bp / 0.16f * PI.toFloat()) else 0f
        val breath = 1f + 0.014f * sin(time * 2.1f)
        val earTwitch = ((time + 1.3f) % 6.7f).let { if (it < 0.3f) sin(it / 0.3f * PI.toFloat()) else 0f }
        val headBob = 1.2f * sin(time * 2.1f - 0.6f)

        // soft floor shadow
        oval(Color.Black.copy(alpha = .28f), 100f, 190f, 62f, 6f, u)

        // tail: swings behind the body
        val sway = sin(time * 1.6f) * 14f + (if (happy) sin(time * 9f) * 6f else 0f)
        val tail = Path().apply {
            moveTo(138f * u, 178f * u)
            cubicTo(178f * u, 176f * u, (186f + sway * .4f) * u, 130f * u, (170f + sway) * u, (96f - Math.abs(sway) * .4f) * u)
        }
        drawPath(tail, RIM, style = Stroke(width = 19f * u, cap = StrokeCap.Round))
        drawPath(tail, FUR, style = Stroke(width = 16f * u, cap = StrokeCap.Round))
        drawCircle(WHITE, 8f * u, Offset((170f + sway) * u, (96f - Math.abs(sway) * .4f) * u))

        // body (breathes) and white chest
        withScale(100f, 190f, 1f, breath, u) {
            oval(RIM, 100f, 146f, 49f, 47f, u)
            oval(FUR, 100f, 146f, 47f, 45f, u)
            oval(WHITE, 100f, 155f, 22f, 31f, u)
        }
        // front paws
        for (x in listOf(80f, 120f)) {
            oval(RIM, x, 180f, 15f, 10.5f, u)
            oval(WHITE, x, 180f, 13.5f, 9f, u)
        }

        // head
        withTranslate(0f, headBob * u) {
            // ears
            for (side in listOf(-1f, 1f)) {
                val tw = if (side > 0) earTwitch * 4f else 0f
                val ear = tri(100f + side * 44f, 62f, 100f + side * 48f + tw, 12f, 100f + side * 12f, 42f, u)
                drawPath(ear, RIM, style = Stroke(3f * u, join = StrokeJoin.Round))
                drawPath(ear, FUR)
                drawPath(tri(100f + side * 41f, 55f, 100f + side * 44f + tw, 24f, 100f + side * 20f, 44f, u), PINK.copy(alpha = .85f))
            }
            oval(RIM, 100f, 84f, 55f, 44f, u)
            oval(FUR, 100f, 84f, 53f, 42f, u)
            // white blaze and muzzle
            drawPath(tri(100f, 50f, 87f, 96f, 113f, 96f, u), WHITE)
            oval(WHITE, 100f, 101f, 25f, 17f, u)
            oval(WHITE, 100f, 98f, 14f, 14f, u)

            // eyes
            for (side in listOf(-1f, 1f)) {
                val cx = 100f + side * 27f
                val cy = 78f
                if (happy) {
                    drawArc(
                        EYE, 205f, 130f, false, Offset((cx - 9f) * u, (cy - 6f) * u), Size(18f * u, 16f * u),
                        style = Stroke(3.4f * u, cap = StrokeCap.Round),
                    )
                } else {
                    val ry = 10.5f * (1f - blink * .92f)
                    oval(EYE, cx, cy, 10.5f, ry, u)
                    oval(Color(0xFF0B0B0E), cx + lookX, cy + lookY, 4.2f, (9f * (1f - blink * .92f)).coerceAtLeast(.6f), u)
                    if (blink < .3f) drawCircle(Color.White, 2.1f * u, Offset((cx + lookX - 2.2f) * u, (cy + lookY - 3.5f) * u))
                }
            }
            // nose and mouth
            drawPath(tri(100f, 98f, 94.5f, 91.5f, 105.5f, 91.5f, u), PINK)
            val mouth = Path().apply {
                moveTo(100f * u, 98f * u); lineTo(100f * u, 102f * u)
                quadraticBezierTo(96f * u, 107f * u, 91f * u, 104f * u)
                moveTo(100f * u, 102f * u)
                quadraticBezierTo(104f * u, 107f * u, 109f * u, 104f * u)
            }
            drawPath(mouth, Color(0xFF6B5A5A), style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // whiskers
            val wig = sin(time * 3f) * 1.2f
            for (side in listOf(-1f, 1f)) for (i in 0..2) {
                val y = 98f + i * 5f
                drawLine(
                    Color(0xFFD9D6CF).copy(alpha = .85f),
                    Offset((100f + side * 22f) * u, y * u), Offset((100f + side * (50f + i * 2f)) * u, (y - 5f + i * 4.5f + wig) * u),
                    strokeWidth = 1.3f * u, cap = StrokeCap.Round,
                )
            }
        }

        // a heart floats up when you tap the cat
        if (happy) {
            val k = (time - happyAt) / 1.8f
            heart(130f, 40f - k * 34f, 1f - k, u)
            heart(78f, 46f - k * 26f, (1f - k) * .8f, u, .7f)
        }
    }
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, u: Float) =
    drawOval(color, Offset((cx - rx) * u, (cy - ry) * u), Size(2 * rx * u, 2 * ry * u))

private fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, u: Float) = Path().apply {
    moveTo(x1 * u, y1 * u); lineTo(x2 * u, y2 * u); lineTo(x3 * u, y3 * u); close()
}

private inline fun DrawScope.withScale(px: Float, py: Float, sx: Float, sy: Float, u: Float, block: DrawScope.() -> Unit) =
    androidx.compose.ui.graphics.drawscope.withTransform({ scale(sx, sy, Offset(px * u, py * u)) }) { block() }

private inline fun DrawScope.withTranslate(dx: Float, dy: Float, block: DrawScope.() -> Unit) =
    androidx.compose.ui.graphics.drawscope.withTransform({ translate(dx, dy) }) { block() }

private fun DrawScope.heart(cx: Float, cy: Float, alpha: Float, u: Float, scale: Float = 1f) {
    val a = alpha.coerceIn(0f, 1f)
    val c = Color(0xFFFF7A90).copy(alpha = a)
    val r = 6f * scale
    drawCircle(c, r * u, Offset((cx - r * .9f) * u, cy * u))
    drawCircle(c, r * u, Offset((cx + r * .9f) * u, cy * u))
    drawPath(tri(cx - r * 1.8f, cy + r * .35f, cx + r * 1.8f, cy + r * .35f, cx, cy + r * 2.6f, u), c)
}
