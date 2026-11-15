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
class HomeEnterState(val progress: State<Float>, val style: HomeEnter)

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
        val delay = when (st.style) {
            HomeEnter.WAVE -> dist
            HomeEnter.BLOOM -> 1f - dist
            HomeEnter.RISE -> 1f - fy
            HomeEnter.CASCADE -> (fx + fy) / 2f
            HomeEnter.ZOOM, HomeEnter.OFF -> 0f
        }
        val spread = if (st.style == HomeEnter.ZOOM) 0f else 0.42f
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
            HomeEnter.OFF -> Unit
        }
        // Items come into focus as they arrive (Android 12+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = (1f - e) * 14.dp.toPx()
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
        }
    }
}
