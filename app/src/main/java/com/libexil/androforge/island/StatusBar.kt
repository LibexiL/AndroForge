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

@Composable
fun StatusBarContent(geo: IslandGeometry) {
    val store = LauncherApp.instance.store
    val state by store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
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
    val text = if (s.barText == ColorRole.FOREGROUND) {
        if (s.barBackground == BarBackground.BLACK) Color.White else pal.foreground
    } else {
        pal.role(s.barText)
    }
    val bg: Brush = when (s.barBackground) {
        BarBackground.BLACK -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        BarBackground.PALETTE -> Brush.linearGradient(listOf(pal.background, pal.background))
        BarBackground.GRADIENT -> Brush.horizontalGradient(
            listOf(pal.background, androidx.compose.ui.graphics.lerp(pal.background, pal.accent, 0.35f), pal.background),
        )
    }
    if (!geo.visible.value) return
    Row(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (s.barShowDate) {
            Spacer(Modifier.width(8.dp))
            Text(date, color = text.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (s.barNotificationIcons) {
            icons.take(4).forEach { ic ->
                Image(ic.smallIcon!!, null, colorFilter = ColorFilter.tint(text.copy(alpha = 0.9f)), modifier = Modifier.padding(end = 6.dp).size(14.dp))
            }
            if (icons.isNotEmpty()) Spacer(Modifier.width(4.dp))
        }
        if (s.barNetwork) {
            NetworkGlyph(net, text, Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        when (s.barBattery) {
            BatteryStyle.ICON_PERCENT -> {
                Text("${battery.level}%", color = text, fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                BatteryGlyph(battery.level, battery.charging, batteryColor(battery, pal, text), Modifier.size(width = 22.dp, height = 11.dp))
            }
            BatteryStyle.ICON -> BatteryGlyph(battery.level, battery.charging, batteryColor(battery, pal, text), Modifier.size(width = 22.dp, height = 11.dp))
            BatteryStyle.PERCENT -> Text("${battery.level}%" + if (battery.charging) " ⚡" else "", color = text, fontSize = 13.sp)
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
private fun NetworkGlyph(net: NetKind, color: Color, modifier: Modifier) {
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
