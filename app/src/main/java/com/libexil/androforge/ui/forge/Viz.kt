package com.libexil.androforge.ui.forge

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import com.libexil.androforge.data.VizStyle
import com.libexil.androforge.live.AudioViz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Smoothed bar heights for one renderer, advanced every frame (cava-style gravity + peaks). */
class VizFrame(val bars: Int) {
    val values = FloatArray(bars)
    val peaks = FloatArray(bars)
    private val peakHold = FloatArray(bars)
    var tick by mutableLongStateOf(0L)
    var spin = 0f
        private set
    private var last = 0L

    fun resetClock() {
        last = 0L
    }

    fun advance(nanos: Long, source: FloatArray): Boolean {
        val dt = if (last == 0L) 0.016f else ((nanos - last) / 1e9f).coerceIn(0.001f, 0.1f)
        last = nanos
        spin = (spin + dt * 8f) % 360f
        var active = false
        val n = source.size
        for (i in 0 until bars) {
            // Each bar averages its slice of the bands.
            val a = (i * n / bars).coerceIn(0, n - 1)
            val b = ((i + 1) * n / bars).coerceIn(a + 1, n)
            var sum = 0f
            for (k in a until b) sum += source[k]
            val target = sum / (b - a)
            val v = values[i]
            values[i] = if (target > v) v + (target - v) * min(1f, dt * 28f) else max(target, v - dt * 1.6f)
            if (values[i] >= peaks[i]) {
                peaks[i] = values[i]
                peakHold[i] = 0.35f
            } else if (peakHold[i] > 0f) {
                peakHold[i] -= dt
            } else {
                peaks[i] = max(values[i], peaks[i] - dt * 0.7f)
            }
            if (values[i] > 0.002f || peaks[i] > 0.002f) active = true
        }
        return active
    }
}

/** Subscribes to the audio tap while on screen and keeps a [VizFrame] moving. */
@Composable
fun rememberViz(bars: Int, active: Boolean = true): VizFrame {
    val frame = remember(bars) { VizFrame(bars.coerceIn(4, AudioViz.BANDS)) }
    LifecycleStartEffect(active) {
        if (active) AudioViz.acquire()
        onStopOrDispose { if (active) AudioViz.release() }
    }
    LaunchedEffect(frame, active) {
        val silent = FloatArray(AudioViz.BANDS)
        var wasActive = true
        while (true) {
            val moving = withFrameNanos { now ->
                val m = frame.advance(now, if (active) AudioViz.levels else silent)
                if (m || wasActive) frame.tick = now
                m
            }
            wasActive = moving
            // Nothing playing: stop asking for frames and just check back a few times a second.
            if (!moving) {
                frame.resetClock()
                delay(180)
            }
        }
    }
    return frame
}

/** Draws a [VizFrame] in any ThemeForge visualizer style. */
@Composable
fun VisualizerCanvas(
    frame: VizFrame,
    style: VizStyle,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    gap: Float = 0.25f,
    rounded: Boolean = true,
    peaks: Boolean = true,
    minLevel: Float = 0.02f,
    peakColor: Color? = null,
) {
    Canvas(modifier) {
        frame.tick // redraw on every advanced frame
        drawViz(frame, style, colors, gap, rounded, peaks, minLevel, peakColor ?: colors.last())
    }
}

fun DrawScope.drawViz(
    frame: VizFrame,
    style: VizStyle,
    colors: List<Color>,
    gap: Float,
    rounded: Boolean,
    peaks: Boolean,
    minLevel: Float,
    peakColor: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val n = frame.bars
    val stops = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
    val vertical = Brush.verticalGradient(stops.reversed(), startY = 0f, endY = h)
    val slot = w / n
    val bw = (slot * (1f - gap.coerceIn(0f, 0.85f))).coerceAtLeast(1f)
    val radius = if (rounded) CornerRadius(bw / 2f, bw / 2f) else CornerRadius.Zero
    fun level(i: Int) = frame.values[i].coerceAtLeast(minLevel)

    when (style) {
        VizStyle.BARS -> for (i in 0 until n) {
            val bh = level(i) * h
            val x = i * slot + (slot - bw) / 2f
            drawRoundRect(vertical, Offset(x, h - bh), Size(bw, bh), radius)
            if (peaks && frame.peaks[i] > minLevel * 2) {
                val py = h - frame.peaks[i] * h
                drawRoundRect(peakColor, Offset(x, (py - 3f).coerceAtLeast(0f)), Size(bw, 2.5f), CornerRadius(1.5f, 1.5f))
            }
        }
        VizStyle.MIRROR -> {
            val mid = h / 2f
            val brush = Brush.verticalGradient(stops.reversed() + stops, startY = 0f, endY = h)
            for (i in 0 until n) {
                val bh = level(i) * h
                val x = i * slot + (slot - bw) / 2f
                drawRoundRect(brush, Offset(x, mid - bh / 2f), Size(bw, bh), radius)
            }
        }
        VizStyle.BLOCKS -> {
            val cell = bw * 0.55f + 2f
            val rows = (h / cell).toInt().coerceAtLeast(3)
            val bh = h / rows
            for (i in 0 until n) {
                val lit = (level(i) * rows).toInt()
                val x = i * slot + (slot - bw) / 2f
                for (r in 0 until rows) {
                    val y = h - (r + 1) * bh
                    val c = gradientAt(stops, r / (rows - 1f).coerceAtLeast(1f))
                    val on = r < lit || (peaks && r == (frame.peaks[i] * rows).toInt() && r > 0)
                    drawRect(
                        if (on) c else c.copy(alpha = 0.1f),
                        Offset(x, y + 1f),
                        Size(bw, bh - 2f),
                    )
                }
            }
        }
        VizStyle.WAVE, VizStyle.MIRRORWAVE -> {
            val mirror = style == VizStyle.MIRRORWAVE
            val base = if (mirror) h / 2f else h
            val amp = if (mirror) h / 2f else h
            val pts = (0 until n).map { i -> Offset(i * slot + slot / 2f, base - level(i) * amp * 0.95f) }
            fun smooth(points: List<Offset>, closeTo: Float): Path = Path().apply {
                moveTo(0f, closeTo)
                lineTo(0f, points.first().y)
                for (k in 0 until points.size - 1) {
                    val p0 = points[k]
                    val p1 = points[k + 1]
                    val mx = (p0.x + p1.x) / 2f
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                }
                lineTo(w, points.last().y)
                lineTo(w, closeTo)
                close()
            }
            val top = smooth(pts, base)
            drawPath(top, Brush.verticalGradient(stops.reversed().map { it.copy(alpha = 0.55f) }, 0f, base))
            drawPath(top, Brush.horizontalGradient(stops), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            if (mirror) {
                val bottom = smooth(pts.map { Offset(it.x, base + (base - it.y)) }, base)
                drawPath(bottom, Brush.verticalGradient(stops.map { it.copy(alpha = 0.55f) }, base, h))
                drawPath(bottom, Brush.horizontalGradient(stops), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }
        }
        VizStyle.RADIAL -> {
            val c = Offset(w / 2f, h / 2f)
            val r0 = min(w, h) * 0.26f
            val maxLen = min(w, h) / 2f - r0 - 2f
            val stroke = (2f * PI.toFloat() * r0 / n * 0.6f).coerceIn(1.5f, 10f)
            rotate(frame.spin, c) {
                for (i in 0 until n) {
                    val a = (i / n.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val len = level(i) * maxLen
                    val dir = Offset(cos(a), sin(a))
                    drawLine(
                        gradientAt(stops, i / (n - 1f).coerceAtLeast(1f)),
                        c + dir * r0,
                        c + dir * (r0 + len.coerceAtLeast(1f)),
                        strokeWidth = stroke,
                        cap = if (rounded) StrokeCap.Round else StrokeCap.Butt,
                    )
                }
            }
            drawCircle(Brush.sweepGradient(stops + stops.first(), c), r0 - 3f, c, style = Stroke(1.5f))
        }
        VizStyle.DOTS -> {
            val d = bw.coerceAtMost(h / 6f)
            val rows = (h / (d * 1.35f)).toInt().coerceAtLeast(3)
            for (i in 0 until n) {
                val lit = (level(i) * rows).toInt().coerceAtLeast(1)
                val cx = i * slot + slot / 2f
                for (r in 0 until lit) {
                    val cy = h - (r + 0.5f) * (h / rows)
                    drawCircle(gradientAt(stops, r / (rows - 1f).coerceAtLeast(1f)), d / 2f, Offset(cx, cy))
                }
                if (peaks && frame.peaks[i] > minLevel * 2) {
                    val pr = (frame.peaks[i] * rows).toInt().coerceIn(0, rows - 1)
                    drawCircle(peakColor, d / 2f, Offset(cx, h - (pr + 0.5f) * (h / rows)), alpha = 0.8f)
                }
            }
        }
    }
}

fun gradientAt(stops: List<Color>, t: Float): Color {
    if (stops.size == 1) return stops[0]
    val x = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[i], stops[i + 1], x - i)
}
