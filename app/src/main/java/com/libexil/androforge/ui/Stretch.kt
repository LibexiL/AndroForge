package com.libexil.androforge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Rubber-band stretch when a list hits its end: the content stretches toward your finger and
 * springs back, and a fling into the end gives a quick stretch-and-settle.
 * Positive [amount] = stretched at the bottom, negative = at the top.
 */
@Stable
class Stretch(private val scope: CoroutineScope, private val top: Boolean, private val bottom: Boolean) {
    val amount = Animatable(0f)
    var heightPx = 1f
    var motion: Motion = Motion.Default

    private val max get() = heightPx * 0.22f

    private fun release(velocity: Float = 0f) {
        scope.launch { amount.animateTo(0f, motion.spring(stiffness = 420f), initialVelocity = velocity) }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val a = amount.value
            // Undo the stretch first when the finger reverses.
            if (a > 0f && available.y > 0f || a < 0f && available.y < 0f) {
                val next = if (a > 0f) (a - available.y).coerceAtLeast(0f) else (a - available.y).coerceAtMost(0f)
                scope.launch { amount.snapTo(next) }
                return Offset(0f, a - next)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            val dragUp = available.y < 0f
            if (dragUp && !bottom || !dragUp && !top) return Offset.Zero
            val a = amount.value
            val resistance = (1f - abs(a) / max).coerceIn(0.05f, 1f) * 0.5f
            val next = (a - available.y * resistance).coerceIn(if (top) -max else 0f, if (bottom) max else 0f)
            scope.launch { amount.snapTo(next) }
            return available
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (amount.value != 0f) {
                release()
                return available
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val v = available.y
            if (v < 0f && bottom || v > 0f && top) {
                // Hit the end at speed: bump out and settle back.
                release(initialVelocity(v))
            }
            return Velocity.Zero
        }
    }

    private fun initialVelocity(v: Float): Float = (-v * 0.12f).coerceIn(-max * 8f, max * 8f)
}

@Composable
fun rememberStretch(top: Boolean = false, bottom: Boolean = true): Stretch {
    val scope = rememberCoroutineScope()
    val stretch = remember(top, bottom) { Stretch(scope, top, bottom) }
    stretch.motion = LocalMotion.current
    return stretch
}

/** Applies the stretch: attach to the scrolling list (inside any other nested-scroll parents). */
fun Modifier.stretchy(stretch: Stretch?): Modifier = if (stretch == null) this else this
    .onSizeChanged { stretch.heightPx = it.height.toFloat().coerceAtLeast(1f) }
    .graphicsLayer {
        val a = stretch.amount.value
        val k = 1f + abs(a) / stretch.heightPx * 0.75f
        scaleY = k
        transformOrigin = TransformOrigin(0.5f, if (a >= 0f) 1f else 0f)
    }
    .nestedScroll(stretch.connection)

/** Turns off the platform glow/stretch so ours is the only one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoPlatformOverscroll(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) CompositionLocalProvider(LocalOverscrollConfiguration provides null, content = content) else content()
}
