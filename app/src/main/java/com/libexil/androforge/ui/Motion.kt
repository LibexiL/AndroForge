package com.libexil.androforge.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import com.libexil.androforge.data.LauncherSettings

/**
 * Every launcher animation goes through this, so the Motion settings (on/off, length, bounce)
 * apply everywhere. [scale] < 1 is faster; [bounce] 0 = no overshoot, 1 = very springy.
 */
@Immutable
class Motion(val enabled: Boolean, val scale: Float, val bounce: Float) {
    private val damping: Float get() = (1f - bounce.coerceIn(0f, 1f) * 0.55f).coerceIn(0.4f, 1f)

    /** A spring whose length follows [scale] (spring period grows with 1/sqrt(stiffness)). */
    fun <T> spring(stiffness: Float = 500f, visibilityThreshold: T? = null, bouncy: Boolean = true): FiniteAnimationSpec<T> =
        if (!enabled) snap() else spring(
            dampingRatio = if (bouncy) damping else 1f,
            stiffness = stiffness / (scale * scale).coerceAtLeast(0.05f),
            visibilityThreshold = visibilityThreshold,
        )

    fun <T> tween(ms: Int, easing: Easing = FastOutSlowInEasing, delay: Int = 0): FiniteAnimationSpec<T> =
        if (!enabled) snap() else tween((ms * scale).toInt().coerceAtLeast(1), (delay * scale).toInt(), easing)

    fun ms(ms: Int): Int = if (!enabled) 0 else (ms * scale).toInt()

    companion object {
        val Default = Motion(true, 0.8f, 0.3f)
        fun of(s: LauncherSettings) = Motion(s.animations, s.animScale, s.animBounce)
    }
}

val LocalMotion = staticCompositionLocalOf { Motion.Default }

/** Icons shrink slightly under your finger and spring back. */
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.pressScale(pressed: Boolean): androidx.compose.ui.Modifier {
    val m = LocalMotion.current
    val s = androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f,
        m.spring(stiffness = 1500f),
        label = "press",
    )
    return this.then(androidx.compose.ui.Modifier.graphicsLayerScale { s.value })
}

private fun androidx.compose.ui.Modifier.graphicsLayerScale(scale: () -> Float) =
    this.then(androidx.compose.ui.Modifier.graphicsLayer {
        val v = scale()
        scaleX = v
        scaleY = v
    })
