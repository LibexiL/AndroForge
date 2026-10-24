@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.libexil.androforge.island

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.ArtStyle
import com.libexil.androforge.data.BarBackground
import com.libexil.androforge.data.BatteryStyle
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.data.IslandColor
import com.libexil.androforge.data.IslandIdle
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.VizStyle
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.live.BatteryInfo
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.MediaInfo
import com.libexil.androforge.live.NetKind
import com.libexil.androforge.live.NotifHub
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.ui.Pal
import com.libexil.androforge.ui.forge.AlbumArt
import com.libexil.androforge.ui.forge.Transport
import com.libexil.androforge.ui.forge.TransportIcon
import com.libexil.androforge.ui.forge.VisualizerCanvas
import com.libexil.androforge.ui.forge.rememberNow
import com.libexil.androforge.ui.forge.rememberViz
import com.libexil.androforge.ui.rememberPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════ status bar

/** The custom bar's window: draws [StatusBarView] and, in AndroForge-shade mode, opens the shade on a pull. */
@Composable
fun StatusBarContent(geo: IslandGeometry) {
    val state by LauncherApp.instance.store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
    if (!geo.visible.value) return
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (s.shadeMode == com.libexil.androforge.data.ShadeMode.FORGE) {
                    Modifier.pointerInput(Unit) {
                        var total = 0f
                        androidx.compose.foundation.gestures.detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { ch, d ->
                                total += d
                                ch.consume()
                                if (total > 24f) {
                                    total = Float.NEGATIVE_INFINITY
                                    geo.openShade()
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) { StatusBarView(s, pal, Modifier.fillMaxSize()) }
}

/** The custom status bar itself (also used by the settings preview). */
@Composable
fun StatusBarView(s: com.libexil.androforge.data.LauncherSettings, pal: Pal, modifier: Modifier = Modifier) {
    val battery by SystemMonitor.battery.collectAsState()
    val net by SystemMonitor.net.collectAsState()
    val entries by NotifHub.entries.collectAsState()
    val icons = remember(entries) { entries.filter { it.smallIcon != null }.distinctBy { it.pkg } }
    val context = LocalContext.current
    val now = rememberNow(s.barSeconds)
    val twelve = s.barTwelveHour ?: !DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(
        (if (twelve) "h:mm" else "HH:mm") + (if (s.barSeconds) ":ss" else ""),
        Locale.getDefault(),
    ).format(Date(now))
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now))
    val style = s.barStyle
    val clear = style == com.libexil.androforge.data.BarStyle.CLEAR
    val text = when {
        s.barText != ColorRole.FOREGROUND -> pal.role(s.barText)
        clear -> Color.White
        s.barBackground == BarBackground.BLACK -> Color.White
        else -> pal.foreground
    }
    val a = s.barOpacity.coerceIn(0f, 1f)
    val base: List<Color> = when (s.barBackground) {
        BarBackground.BLACK -> listOf(Color.Black, Color.Black)
        BarBackground.PALETTE -> listOf(pal.background, pal.background)
        BarBackground.GRADIENT -> listOf(pal.background, androidx.compose.ui.graphics.lerp(pal.background, pal.accent, 0.35f), pal.background)
    }
    val bg = Brush.horizontalGradient(base.map { it.copy(alpha = it.alpha * a) })
    val glassBg = Brush.horizontalGradient(base.map { it.copy(alpha = 0.55f * a) })
    val textStyle = TextStyle(
        color = text,
        fontSize = s.barTextSize.sp,
        fontWeight = if (s.barBold) FontWeight.SemiBold else FontWeight.Normal,
        fontFamily = when (s.barFont) {
            com.libexil.androforge.data.PanelFont.MONO -> androidx.compose.ui.text.font.FontFamily.Monospace
            com.libexil.androforge.data.PanelFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
            else -> androidx.compose.ui.text.font.FontFamily.Default
        },
        shadow = if (clear) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 5f) else null,
    )
    val small = (s.barTextSize - 1).coerceAtLeast(9).sp
    val iconPx = (s.barTextSize + 1).dp

    val clock: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = textStyle, maxLines = 1)
            if (s.barShowDate) {
                Spacer(Modifier.width(8.dp))
                Text(date, style = textStyle.copy(color = text.copy(alpha = 0.75f), fontSize = small, fontWeight = FontWeight.Normal), maxLines = 1)
            }
        }
    }
    val status: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.barNotificationIcons) {
                icons.take(4).forEach { ic ->
                    Image(ic.smallIcon!!, null, colorFilter = ColorFilter.tint(text.copy(alpha = 0.9f)), modifier = Modifier.padding(end = 6.dp).size(iconPx))
                }
                if (icons.isNotEmpty()) Spacer(Modifier.width(4.dp))
            }
            if (s.barNetwork) {
                NetworkGlyph(net, text, Modifier.size(iconPx + 1.dp))
                Spacer(Modifier.width(8.dp))
            }
            val bw = (s.barTextSize * 1.6f).dp
            val bh = (s.barTextSize * 0.8f).dp
            when (s.barBattery) {
                BatteryStyle.ICON_PERCENT -> {
                    Text("${battery.level}%", style = textStyle.copy(fontSize = small))
                    Spacer(Modifier.width(5.dp))
                    BatteryGlyph(battery.level, battery.charging, batteryColor(battery, pal, text), Modifier.size(width = bw, height = bh))
                }
                BatteryStyle.ICON -> BatteryGlyph(battery.level, battery.charging, batteryColor(battery, pal, text), Modifier.size(width = bw, height = bh))
                BatteryStyle.PERCENT -> Text("${battery.level}%" + if (battery.charging) " \u26A1" else "", style = textStyle.copy(fontSize = small))
                BatteryStyle.BAR -> Box(Modifier.size(width = 34.dp, height = 4.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(text.copy(alpha = 0.25f), cornerRadius = CornerRadius(4f, 4f))
                        drawRoundRect(
                            Brush.horizontalGradient(pal.gradient),
                            size = Size(size.width * battery.level / 100f, size.height),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                }
            }
        }
    }
    val corner = RoundedCornerShape(s.barCorner.dp)
    fun Modifier.surface(shape: androidx.compose.ui.graphics.Shape?): Modifier = when (style) {
        com.libexil.androforge.data.BarStyle.CLEAR -> this
        com.libexil.androforge.data.BarStyle.GLASS -> (if (shape != null) clip(shape) else this)
            .background(glassBg)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f * a), Color.White.copy(alpha = 0.03f * a))))
            .then(if (shape != null) Modifier.border(1.dp, Color.White.copy(alpha = 0.16f * a), shape) else Modifier)
        else -> (if (shape != null) clip(shape) else this).background(bg)
    }
    val side = s.barSidePadding.dp
    when (style) {
        com.libexil.androforge.data.BarStyle.SPLIT -> Box(modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            val left = if (s.barClock == com.libexil.androforge.data.ClockPosition.RIGHT) status else clock
            val right = if (s.barClock == com.libexil.androforge.data.ClockPosition.RIGHT) clock else status
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().surface(corner).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { left() }
            if (s.barClock == com.libexil.androforge.data.ClockPosition.CENTER) {
                Box(Modifier.align(Alignment.Center).fillMaxHeight().surface(corner).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { clock() }
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().surface(corner).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { status() }
            } else {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().surface(corner).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { right() }
            }
        }
        else -> {
            val floating = style == com.libexil.androforge.data.BarStyle.FLOATING
            Box(
                modifier
                    .then(if (floating) Modifier.padding(horizontal = 8.dp, vertical = 2.dp) else Modifier)
                    .surface(if (floating) corner else null),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = if (floating) 14.dp else side),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.barClock == com.libexil.androforge.data.ClockPosition.LEFT) clock()
                    Spacer(Modifier.weight(1f))
                    status()
                    if (s.barClock == com.libexil.androforge.data.ClockPosition.RIGHT) {
                        Spacer(Modifier.width(10.dp))
                        clock()
                    }
                }
                if (s.barClock == com.libexil.androforge.data.ClockPosition.CENTER) {
                    Box(Modifier.align(Alignment.Center)) { clock() }
                }
                if (style == com.libexil.androforge.data.BarStyle.NEON) {
                    Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp)) {
                        val y = size.height - 1.dp.toPx()
                        for (k in 3 downTo 1) {
                            drawLine(
                                Brush.horizontalGradient(pal.gradient.map { it.copy(alpha = 0.25f / k) }),
                                Offset(0f, y), Offset(size.width, y), strokeWidth = (k * 2.5f).dp.toPx(),
                            )
                        }
                        drawLine(Brush.horizontalGradient(pal.gradient), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                    }
                }
            }
        }
    }
}

private fun batteryColor(b: BatteryInfo, pal: Pal, text: Color): Color = when {
    b.charging -> pal.ansi.getOrElse(2) { text }
    b.level <= 15 -> pal.ansi.getOrElse(1) { text }
    else -> text
}

@Composable
fun BatteryGlyph(level: Int, charging: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val nub = w * 0.09f
        val body = Size(w - nub - 1f, h)
        drawRoundRect(color.copy(alpha = 0.55f), Offset.Zero, body, CornerRadius(h * 0.28f), style = Stroke(1.6f))
        drawRoundRect(color.copy(alpha = 0.55f), Offset(w - nub, h * 0.3f), Size(nub, h * 0.4f), CornerRadius(2f))
        val inset = 2.6f
        drawRoundRect(
            color,
            Offset(inset, inset),
            Size((body.width - inset * 2) * (level.coerceIn(0, 100) / 100f), h - inset * 2),
            CornerRadius(h * 0.18f),
        )
        if (charging) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(body.width * 0.55f, h * 0.1f)
                lineTo(body.width * 0.35f, h * 0.55f)
                lineTo(body.width * 0.5f, h * 0.55f)
                lineTo(body.width * 0.42f, h * 0.92f)
                lineTo(body.width * 0.68f, h * 0.42f)
                lineTo(body.width * 0.52f, h * 0.42f)
                close()
            }
            drawPath(p, Color.Black.copy(alpha = 0.75f))
        }
    }
}

@Composable
internal fun NetworkGlyph(net: NetKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (net) {
            NetKind.WIFI -> {
                val c = Offset(w / 2, h * 0.92f)
                for (k in 1..3) {
                    val r = h * 0.28f * k
                    drawArc(color, 225f, 90f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.8f, cap = StrokeCap.Round))
                }
                drawCircle(color, 1.8f, c)
            }
            NetKind.CELL, NetKind.OTHER -> for (i in 0 until 4) {
                val bw = w / 5.5f
                val bh = h * (0.3f + i * 0.23f)
                drawRoundRect(color, Offset(i * (bw + bw * 0.45f), h - bh), Size(bw, bh), CornerRadius(1.5f))
            }
            NetKind.NONE -> for (i in 0 until 4) {
                val bw = w / 5.5f
                val bh = h * (0.3f + i * 0.23f)
                drawRoundRect(color.copy(alpha = 0.3f), Offset(i * (bw + bw * 0.45f), h - bh), Size(bw, bh), CornerRadius(1.5f))
            }
        }
    }
}
