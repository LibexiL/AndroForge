package com.libexil.androforge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.libexil.androforge.data.LiveBackground
import com.libexil.androforge.live.AudioViz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated backgrounds drawn in the palette behind the home screen (over the wallpaper).
 * Frames only run while the launcher is in front.
 */
@Composable
fun LiveBackgroundLayer(kind: LiveBackground, opacity: Float, speed: Float, pal: Pal, modifier: Modifier = Modifier) {
    if (kind == LiveBackground.OFF) return
    var t by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var resumed by remember { androidx.compose.runtime.mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    if (kind == LiveBackground.PULSE) {
        LifecycleResumeEffect(Unit) {
            AudioViz.acquire()
            onPauseOrDispose { AudioViz.release() }
        }
    }
    LaunchedEffect(resumed, speed) {
        if (!resumed) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                t += dt * speed
                if (kind == LiveBackground.PULSE) bass += (AudioViz.bass - bass) * (dt * 12f).coerceAtMost(1f)
            }
        }
    }
    val stars = remember { List(90) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    val bubbles = remember { List(18) { Triple(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat() * 0.6f) } }
    val colors = remember(pal) { (pal.gradient + pal.accent + pal.accent2).distinct() }
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.coerceIn(0f, 1f) },
    ) {
        val w = size.width
        val h = size.height
        when (kind) {
            LiveBackground.AURORA -> {
                // Slow, soft blobs drifting across the screen.
                colors.take(4).forEachIndexed { i, c ->
                    val ph = t * (0.07f + i * 0.02f) + i * 1.7f
                    val cx = w * (0.5f + 0.42f * sin(ph))
                    val cy = h * (0.35f + 0.3f * cos(ph * 0.8f + i))
                    val r = w * (0.55f + 0.12f * sin(ph * 1.3f))
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                }
            }
            LiveBackground.FLOW -> {
                val a = t * 0.12f
                val start = Offset(w * (0.5f + 0.5f * cos(a)), h * (0.5f + 0.5f * sin(a)))
                val end = Offset(w - start.x, h - start.y)
                drawRect(Brush.linearGradient(colors.map { it.copy(alpha = 0.6f) } + colors.first().copy(alpha = 0.6f), start, end))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, pal.background.copy(alpha = 0.55f))))
            }
            LiveBackground.PULSE -> {
                val b = bass.coerceIn(0f, 1f)
                val cx = w / 2f
                val cy = h * 0.58f
                for (i in 0 until 4) {
                    val r = w * (0.25f + i * 0.18f + b * 0.22f) + (t * 40f + i * 60f) % 60f
                    val c = pal.gradientAt(i / 3f)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (0.32f + b * 0.4f) / (i + 1)), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                }
                drawRect(Brush.verticalGradient(listOf(lerp(pal.accent, Color.Transparent, 0.6f - b * 0.3f).copy(alpha = 0.25f + b * 0.3f), Color.Transparent), endY = h * 0.5f))
            }
            LiveBackground.BUBBLES -> {
                bubbles.forEachIndexed { i, (x0, y0, s) ->
                    val rise = (y0 + t * 0.03f * s) % 1.2f
                    val y = h * (1.1f - rise)
                    val x = w * x0 + sin(t * 0.6f + i) * w * 0.03f
                    val r = w * 0.05f * s + w * 0.04f
                    val c = colors[i % colors.size]
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f), c.copy(alpha = 0.05f)), Offset(x, y), r), r, Offset(x, y))
                }
            }
            LiveBackground.STARS -> {
                drawRect(Brush.verticalGradient(listOf(pal.background.copy(alpha = 0.6f), lerp(pal.background, pal.accent, 0.25f).copy(alpha = 0.5f))))
                stars.forEachIndexed { i, (x0, y0, s) ->
                    val tw = 0.35f + 0.65f * ((sin(t * (0.8f + s * 2f) + i) + 1f) / 2f)
                    val x = (x0 * w + t * 6f * s) % w
                    val c = if (i % 7 == 0) pal.accent else pal.foreground
                    drawCircle(c.copy(alpha = tw * 0.9f), 1.2f + s * 2.6f, Offset(x, y0 * h))
                }
                // An occasional shooting star.
                val cycle = (t * 0.15f) % 1f
                if (cycle < 0.12f) {
                    val p = cycle / 0.12f
                    val sx = w * (0.8f - p * 0.6f)
                    val sy = h * (0.1f + p * 0.25f)
                    drawLine(Brush.linearGradient(listOf(Color.Transparent, pal.foreground), Offset(sx + w * 0.12f, sy - h * 0.05f), Offset(sx, sy)), Offset(sx + w * 0.12f, sy - h * 0.05f), Offset(sx, sy), strokeWidth = 3f)
                }
            }
            LiveBackground.OFF -> Unit
        }
    }
}

@Suppress("unused")
private val TWO_PI = (2 * PI).toFloat()
