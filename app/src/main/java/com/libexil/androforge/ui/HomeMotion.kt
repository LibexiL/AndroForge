package com.libexil.androforge.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.libexil.androforge.data.HomeEnter
import kotlin.math.pow
import kotlin.math.sqrt

/** Drives the "home rebuilding" animation: [progress] runs 0 → 1 each time you come home. */
class HomeEnterState(val progress: State<Float>, val style: HomeEnter) {
    /** Screen size in px, so items can fly in from real places (the center, the dock...). */
    var screenW = 1080f
    var screenH = 2400f
}

val LocalHomeEnter = staticCompositionLocalOf<HomeEnterState?> { null }

/** Gentle overshoot (settles like a spring, without the wobble). */
fun backOut(t: Float, overshoot: Float = 1.25f): Float {
    val x = t - 1f
    return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x
}

fun cubicOut(t: Float): Float = 1f - (1f - t).pow(3)

/**
 * Animates one home item back in. [fx]/[fy] are where the item sits on screen (0..1), so
 * the wave can travel across the grid: from the center, from the bottom, corner to corner...
 * Everything happens in the draw phase, so it costs nothing in recomposition.
 */
@Composable
fun Modifier.homeEnter(fx: Float, fy: Float): Modifier {
    val st = LocalHomeEnter.current ?: return this
    if (st.style == HomeEnter.OFF) return this
    return this.graphicsLayer {
        val p = st.progress.value
        if (p >= 1f) {
            renderEffect = null
            return@graphicsLayer
        }
        val dx = fx - 0.5f
        val dy = fy - 0.55f
        val dist = (sqrt(dx * dx + dy * dy) / 0.72f).coerceIn(0f, 1f)
        // A stable "random" number per item, so each one has its own flight.
        val seed = kotlin.math.abs(kotlin.math.sin(fx * 127.1f + fy * 311.7f) * 43758.547f) % 1f
        val delay = when (st.style) {
            HomeEnter.WAVE, HomeEnter.VORTEX -> dist
            HomeEnter.BLOOM -> 1f - dist
            HomeEnter.RISE -> 1f - fy
            HomeEnter.CASCADE -> (fx + fy) / 2f
            HomeEnter.ASSEMBLE -> seed
            HomeEnter.MAGNET -> (1f - fy) * 0.7f + kotlin.math.abs(fx - 0.5f) * 0.6f
            HomeEnter.DOMINO -> (fy * 0.75f + fx * 0.25f)
            HomeEnter.ZOOM, HomeEnter.OFF -> 0f
        }
        val spread = when (st.style) {
            HomeEnter.ZOOM -> 0f
            HomeEnter.ASSEMBLE -> 0.35f
            HomeEnter.DOMINO -> 0.55f
            else -> 0.42f
        }
        // Where the item really is, relative to the screen center (for flights through space).
        val px = fx * st.screenW
        val py = fy / 0.82f * st.screenH * 0.8f
        val cx = st.screenW / 2f
        val cy = st.screenH * 0.45f
        val t = ((p - delay * spread) / (1f - spread)).coerceIn(0f, 1f)
        val e = cubicOut(t)
        alpha = e
        when (st.style) {
            HomeEnter.WAVE -> {
                val s = 0.7f + 0.3f * backOut(t)
                scaleX = s
                scaleY = s
                translationY = (1f - e) * 14.dp.toPx()
            }
            HomeEnter.BLOOM -> {
                val s = 0.55f + 0.45f * backOut(t, 1.5f)
                scaleX = s
                scaleY = s
                rotationZ = (1f - e) * (if (fx < 0.5f) -10f else 10f)
            }
            HomeEnter.RISE -> {
                translationY = (1f - backOut(t, 0.9f)) * 90.dp.toPx()
                val s = 0.9f + 0.1f * e
                scaleX = s
                scaleY = s
            }
            HomeEnter.CASCADE -> {
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationX = (1f - e) * -55f
                translationY = (1f - e) * 18.dp.toPx()
            }
            HomeEnter.ZOOM -> {
                val s = 1.16f - 0.16f * e
                scaleX = s
                scaleY = s
            }
            HomeEnter.VORTEX -> {
                // Spirals out from the middle of the screen into its spot.
                val angle = Math.toRadians(((1f - e) * 210f).toDouble())
                val r = 0.25f + 0.75f * e
                val vx = (px - cx) * r
                val vy = (py - cy) * r
                val rx = (vx * kotlin.math.cos(angle) - vy * kotlin.math.sin(angle)).toFloat()
                val ry = (vx * kotlin.math.sin(angle) + vy * kotlin.math.cos(angle)).toFloat()
                translationX = cx + rx - px
                translationY = cy + ry - py
                rotationZ = (1f - e) * 160f
                val s = 0.35f + 0.65f * e
                scaleX = s
                scaleY = s
            }
            HomeEnter.ASSEMBLE -> {
                // Pieces scattered off-screen fly back together and lock into place.
                val a = seed * 6.2832f
                val far = (0.45f + seed * 0.5f) * st.screenW
                val k = 1f - backOut(t, 1.1f)
                translationX = kotlin.math.cos(a) * far * k
                translationY = kotlin.math.sin(a) * far * k
                rotationZ = (seed - 0.5f) * 300f * k
                rotationY = (0.5f - seed) * 120f * k
                cameraDistance = 14f * density
                val s = 0.4f + 0.6f * e
                scaleX = s
                scaleY = s
            }
            HomeEnter.MAGNET -> {
                // Pulled out of the dock like iron filings, snapping into place.
                val sx = cx
                val sy = st.screenH * 0.97f
                val k = 1f - backOut(t, 1.6f)
                translationX = (sx - px) * k
                translationY = (sy - py) * k
                val stretch = 1f + kotlin.math.abs(k) * 0.35f
                scaleY = (0.3f + 0.7f * e) * stretch
                scaleX = (0.3f + 0.7f * e) / stretch
            }
            HomeEnter.DOMINO -> {
                cameraDistance = 10f * density
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = (1f - backOut(t, 0.8f)) * 100f
            }
            HomeEnter.OFF -> Unit
        }
        // Items come into focus as they arrive (Android 12+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = (1f - e) * 14.dp.toPx()
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
        }
    }
}


/** Drawer icons arrive row by row as the drawer opens, each tipping up into place. */
@Composable
fun Modifier.drawerCascade(index: Int, columns: Int, progress: () -> Float, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.graphicsLayer {
        val p = progress()
        if (p >= 0.999f) return@graphicsLayer
        val row = (index / columns.coerceAtLeast(1)).coerceAtMost(8)
        val col = index % columns.coerceAtLeast(1)
        val start = 0.12f + row * 0.06f + col * 0.012f
        val t = ((p - start) / (1f - start).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
        val e = cubicOut(t)
        alpha = e
        translationY = (1f - e) * 40.dp.toPx()
        cameraDistance = 12f * density
        rotationX = (1f - e) * 35f
        val s = 0.82f + 0.18f * backOut(t, 1.4f)
        scaleX = s
        scaleY = s
    }
}

/** Apps burst out of the middle of an opening folder, one after another. */
fun Modifier.folderBurst(index: Int, col: Int, row: Int, cols: Int, rows: Int, progress: () -> Float): Modifier = this.graphicsLayer {
    val p = progress()
    if (p >= 0.999f) return@graphicsLayer
    val start = index.coerceAtMost(12) * 0.045f
    val t = ((p - start) / (1f - start).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
    val e = backOut(t, 1.3f)
    val dx = col - (cols - 1) / 2f
    val dy = row - (rows - 1) / 2f
    translationX = -dx * 46.dp.toPx() * (1f - e)
    translationY = -dy * 46.dp.toPx() * (1f - e)
    rotationZ = dx * 25f * (1f - e)
    val s = 0.25f + 0.75f * e
    scaleX = s
    scaleY = s
    alpha = cubicOut(t)
}

/**
 * Effects drawn over the home screen while it rebuilds: a shockwave ring from the middle, and
 * a "landing" on the icon of the app you just left (rings, a glow and a burst of sparks).
 */
@Composable
fun HomeFx(
    rebuild: () -> Float,
    shockwave: Boolean,
    landing: () -> Float,
    landingRect: android.graphics.Rect?,
    pal: Pal,
    modifier: Modifier = Modifier,
) {
    val sparks = androidx.compose.runtime.remember {
        List(14) { i ->
            val a = i / 14f * 6.2832f + (i % 3) * 0.21f
            Triple(a, 0.7f + (i * 37 % 10) / 20f, 1.6f + (i * 53 % 7) / 3f)
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val p = rebuild()
        if (shockwave && p in 0.001f..0.999f) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.45f)
            val maxR = kotlin.math.hypot(size.width, size.height) * 0.75f
            val e = cubicOut(p)
            val r = maxR * e
            val a = (1f - p) * 0.55f
            val w = 18.dp.toPx() * (1f - p * 0.6f)
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    ((r - w) / r).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Transparent,
                    ((r - w / 3f) / r).coerceIn(0f, 1f) to pal.accent.copy(alpha = a),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                    center = c, radius = r.coerceAtLeast(1f),
                ),
                radius = r.coerceAtLeast(1f), center = c,
            )
            // A softer second ring trailing behind.
            val r2 = maxR * cubicOut((p - 0.12f).coerceAtLeast(0f) / 0.88f)
            if (r2 > 1f) {
                drawCircle(pal.accent2.copy(alpha = a * 0.5f), r2, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        val l = landing()
        val rect = landingRect
        if (rect != null && l in 0.001f..0.999f) {
            val c = androidx.compose.ui.geometry.Offset(rect.exactCenterX(), rect.exactCenterY())
            val base = maxOf(rect.width(), rect.height()) / 2f
            // Glow that blooms then fades.
            val glowA = kotlin.math.sin(l * Math.PI.toFloat()) * 0.55f
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(pal.accent.copy(alpha = glowA), androidx.compose.ui.graphics.Color.Transparent), c, base * 2.2f),
                base * 2.2f, c,
            )
            // Two rings, the second a beat behind.
            for (k in 0..1) {
                val t = ((l - k * 0.15f) / 0.85f).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val r = base * (1f + 1.4f * cubicOut(t))
                drawCircle(pal.gradientAt(k.toFloat()).copy(alpha = (1f - t) * 0.8f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke((3.5f - k) * density * (1f - t * 0.5f)))
            }
            // Sparks shoot out, slow down and fade.
            sparks.forEachIndexed { i, (a, speed, sz) ->
                val t = (l / 0.9f).coerceIn(0f, 1f)
                val d = base * (0.9f + 2.2f * speed * cubicOut(t))
                val pos = c + androidx.compose.ui.geometry.Offset(kotlin.math.cos(a) * d, kotlin.math.sin(a) * d)
                drawCircle(pal.gradientAt(i / 13f).copy(alpha = (1f - t)), sz * density * (1f - t * 0.6f), pos)
            }
        }
    }
}
