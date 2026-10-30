package com.libexil.androforge.ui.forge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.PanelBackground
import com.libexil.androforge.data.PanelFont
import com.libexil.androforge.data.PanelLook
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.ui.Pal

/** Text scale chosen in the panel's look. */
val LocalTextScale = staticCompositionLocalOf { 1f }

fun PanelFont.family(): FontFamily = when (this) {
    PanelFont.DEFAULT -> FontFamily.Default
    PanelFont.MONO -> FontFamily.Monospace
    PanelFont.SERIF -> FontFamily.Serif
}

@Composable
fun ts(base: Float): TextUnit = (base * LocalTextScale.current).sp

/**
 * The frame every AndroForge widget sits in - background, border, breathing glow and
 * bass pulse, title - drawn like a ThemeForge panel.
 */
@Composable
fun ForgePanel(
    look: PanelLook,
    kind: ForgeKind,
    pal: Pal,
    modifier: Modifier = Modifier,
    reactsToMusic: Boolean = false,
    padding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val hl = pal.role(look.highlight)
    val hlStops = pal.stops(look.highlight)
    val transition = rememberInfiniteTransition(label = "panel")
    val breath: State<Float>? = if (look.glow) {
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        null
    }
    val pulseTick: State<Float>? = if (look.pulse && reactsToMusic) {
        // Re-reads AudioViz.bass every frame while visible.
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "pulse")
    } else {
        null
    }
    val overWallpaper = look.background == PanelBackground.NONE || look.background == PanelBackground.OUTLINE
    val bgBase = look.bgRole?.let { androidx.compose.ui.graphics.lerp(pal.background, pal.role(it), 0.55f) } ?: pal.background
    val surfBase = look.bgRole?.let { androidx.compose.ui.graphics.lerp(pal.surface, pal.role(it), 0.45f) } ?: pal.surface
    val textStyle = TextStyle(
        fontFamily = look.font.family(),
        color = pal.foreground,
        shadow = if (overWallpaper) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 6f) else null,
    )
    Box(
        modifier
            .fillMaxSize()
            .padding(5.dp)
            .drawBehind {
                val r = look.radius.dp.toPx()
                val cr = CornerRadius(r, r)
                val glow = (breath?.value?.let { 0.15f + 0.3f * it } ?: 0f) +
                    (pulseTick?.let { it.value; AudioViz.bass * 0.9f } ?: 0f)
                if (glow > 0.02f && look.background != PanelBackground.NONE) {
                    for (k in 1..4) {
                        val spread = k * 3.dp.toPx()
                        drawRoundRect(
                            hl.copy(alpha = (glow * 0.16f / k).coerceIn(0f, 0.4f)),
                            Offset(-spread, -spread),
                            Size(size.width + spread * 2, size.height + spread * 2),
                            CornerRadius(r + spread, r + spread),
                        )
                    }
                }
                val a = look.opacity.coerceIn(0f, 1f)
                if (look.shadow && look.background != PanelBackground.NONE) {
                    for (k in 1..4) {
                        val o = k * 1.5.dp.toPx()
                        drawRoundRect(
                            Color.Black.copy(alpha = 0.09f),
                            Offset(-o / 2f, o),
                            Size(size.width + o, size.height + o / 2f),
                            CornerRadius(r + o, r + o),
                        )
                    }
                }
                when (look.background) {
                    PanelBackground.SOLID -> drawRoundRect(bgBase.copy(alpha = a), cornerRadius = cr)
                    PanelBackground.GRADIENT -> drawRoundRect(
                        Brush.linearGradient(
                            listOf(
                                androidx.compose.ui.graphics.lerp(surfBase, hl, 0.22f).copy(alpha = a),
                                bgBase.copy(alpha = a),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        cornerRadius = cr,
                    )
                    PanelBackground.FROSTED -> {
                        drawRoundRect(surfBase.copy(alpha = a * 0.55f), cornerRadius = cr)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))),
                            cornerRadius = cr,
                        )
                        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = cr, style = Stroke(1.dp.toPx()))
                    }
                    PanelBackground.OUTLINE -> drawRoundRect(
                        Brush.linearGradient(hlStops),
                        cornerRadius = cr,
                        style = Stroke(1.6.dp.toPx()),
                    )
                    PanelBackground.NONE -> Unit
                }
                if (look.border && look.background != PanelBackground.NONE && look.background != PanelBackground.OUTLINE) {
                    drawRoundRect(
                        Brush.linearGradient(hlStops.map { it.copy(alpha = 0.5f) }),
                        cornerRadius = cr,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .padding(padding * look.paddingScale.coerceIn(0.3f, 2f)),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalContentColor provides pal.foreground,
            LocalTextScale provides look.textScale,
        ) {
            val title = look.title.ifBlank { kind.title }
            if (look.showTitle && title.isNotBlank()) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        title.uppercase(),
                        color = hl,
                        fontSize = ts(10.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                    )
                    Box(Modifier.fillMaxSize().padding(top = 6.dp), content = content)
                }
            } else {
                content()
            }
        }
    }
}

enum class Transport { PREV, PLAY, PAUSE, NEXT }

/** Playback icons drawn as shapes (the core icon set has no pause/skip). */
@Composable
fun TransportIcon(kind: Transport, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun tri(x0: Float, x1: Float) {
            val p = Path().apply {
                moveTo(x0, h * 0.18f)
                lineTo(x1, h / 2f)
                lineTo(x0, h * 0.82f)
                close()
            }
            drawPath(p, color)
        }
        fun triBack(x0: Float, x1: Float) {
            val p = Path().apply {
                moveTo(x1, h * 0.18f)
                lineTo(x0, h / 2f)
                lineTo(x1, h * 0.82f)
                close()
            }
            drawPath(p, color)
        }
        when (kind) {
            Transport.PLAY -> tri(w * 0.24f, w * 0.84f)
            Transport.PAUSE -> {
                val bw = w * 0.2f
                drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(bw, h * 0.64f), CornerRadius(bw / 4))
                drawRoundRect(color, Offset(w * 0.56f, h * 0.18f), Size(bw, h * 0.64f), CornerRadius(bw / 4))
            }
            Transport.NEXT -> {
                tri(w * 0.12f, w * 0.52f)
                tri(w * 0.46f, w * 0.86f)
            }
            Transport.PREV -> {
                triBack(w * 0.48f, w * 0.88f)
                triBack(w * 0.14f, w * 0.54f)
            }
        }
    }
}

@Composable
fun Centered(content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
