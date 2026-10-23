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
import com.libexil.androforge.live.Peek
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

private enum class Mode { HIDDEN, IDLE, MUSIC, CHARGING, PEEK, EXPANDED }

@Composable
fun IslandContent(service: AccessibilityService, geo: IslandGeometry) {
    val store = LauncherApp.instance.store
    val state by store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
    val media by MediaHub.state.collectAsState()
    val peek by SystemMonitor.peek.collectAsState()
    val chargeAt by SystemMonitor.chargeEvent.collectAsState()
    val battery by SystemMonitor.battery.collectAsState()
    val vizStatus by AudioViz.status.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableStateOf(0L) }
    rememberNow(true) // re-evaluate timed states every second
    val elapsed = SystemClock.elapsedRealtime()

    // Outside tap collapses.
    LaunchedEffect(geo.collapseTick.intValue) { expanded = false }
    // Expanded card closes itself after a while.
    LaunchedEffect(expanded, lastTouch) {
        if (expanded) {
            delay(8000)
            expanded = false
        }
    }
    // Peeks disappear after 4.5 s.
    LaunchedEffect(peek?.key, peek?.at) {
        val p = peek ?: return@LaunchedEffect
        delay((4500 - (SystemClock.elapsedRealtime() - p.at)).coerceAtLeast(0))
        SystemMonitor.clearPeek(p.key)
    }

    val m = media
    val musicOn = s.islandMusic && m != null && m.playing
    val charging = s.islandCharging && chargeAt > 0 && elapsed - chargeAt < 3500
    val p = peek
    val mode = when {
        !geo.visible.value -> Mode.HIDDEN
        expanded && m != null -> Mode.EXPANDED
        p != null && s.islandNotifications -> Mode.PEEK
        charging -> Mode.CHARGING
        musicOn -> Mode.MUSIC
        s.islandIdle == IslandIdle.HIDDEN -> Mode.HIDDEN
        else -> Mode.IDLE
    }
    LaunchedEffect(m == null) { if (m == null) expanded = false }

    val density = LocalDensity.current
    val sbDp = with(density) { geo.statusBarPx.intValue.toDp() }
    val h = s.islandHeightDp.dp
    val topPad = ((sbDp - h) / 2).coerceAtLeast(2.dp)
    val bg = if (s.islandColor == IslandColor.BLACK) Color.Black else pal.background
    val fg = if (s.islandColor == IslandColor.BLACK) Color.White else pal.foreground
    val maxW = with(density) { (geo.screenWidthPx.intValue.toDp() - 20.dp).coerceAtLeast(200.dp) }
    val vizOk = s.islandVisualizer && vizStatus != AudioViz.Status.NO_PERMISSION && vizStatus != AudioViz.Status.UNSUPPORTED
    val context = LocalContext.current

    if (mode == Mode.HIDDEN) {
        Box(Modifier.size(1.dp))
        return
    }

    val shape = RoundedCornerShape(if (mode == Mode.EXPANDED || mode == Mode.PEEK) 28.dp else h / 2)
    val glow = s.islandGlow && mode != Mode.IDLE
    Box(Modifier.padding(top = topPad, start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        Box(
            Modifier
                .animateContentSize(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                .widthIn(max = maxW)
                .clip(shape)
                .background(bg)
                .then(if (glow) Modifier.border(1.dp, Brush.linearGradient(pal.gradient.map { it.copy(alpha = 0.7f) }), shape) else Modifier)
                .pointerInput(mode, m?.pkg) {
                    detectTapGestures(
                        onTap = {
                            lastTouch = SystemClock.elapsedRealtime()
                            when (mode) {
                                Mode.MUSIC -> expanded = true
                                Mode.PEEK -> p?.let {
                                    sendIntent(it.intent)
                                    SystemMonitor.clearPeek(it.key)
                                }
                                Mode.EXPANDED -> Unit
                                else -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                            }
                        },
                        onLongPress = {
                            if (m != null) MediaHub.openPlayer(context)
                        },
                    )
                }
                .pointerInput(mode) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, d -> total += d },
                        onDragEnd = {
                            if (total > 40f) {
                                if (mode == Mode.MUSIC && m != null) expanded = true
                                else service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                            } else if (total < -40f) {
                                expanded = false
                                p?.let { SystemMonitor.clearPeek(it.key) }
                            }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "island",
            ) { md ->
                when (md) {
                    Mode.IDLE -> IdlePill(s, h, pal)
                    Mode.MUSIC -> m?.let { MusicPill(it, s, h, pal, fg, vizOk) } ?: IdlePill(s, h, pal)
                    Mode.CHARGING -> ChargingPill(battery, h, pal, fg)
                    Mode.PEEK -> p?.let { PeekCard(it, pal, fg, maxW) } ?: IdlePill(s, h, pal)
                    Mode.EXPANDED -> m?.let { ExpandedCard(it, pal, fg, maxW, vizOk) { lastTouch = SystemClock.elapsedRealtime() } }
                        ?: IdlePill(s, h, pal)
                    Mode.HIDDEN -> Box(Modifier.size(1.dp))
                }
            }
        }
    }
}

private fun sendIntent(pi: PendingIntent?) {
    if (pi == null) return
    try {
        if (Build.VERSION.SDK_INT >= 34) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(null, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
    } catch (_: Exception) {
    }
}

@Composable
private fun IdlePill(s: LauncherSettings, h: androidx.compose.ui.unit.Dp, pal: Pal) {
    when (s.islandIdle) {
        IslandIdle.DOT -> Box(Modifier.size(h))
        else -> Box(Modifier.size(width = s.islandWidthDp.dp, height = h))
    }
}

@Composable
private fun MusicPill(m: MediaInfo, s: LauncherSettings, h: androidx.compose.ui.unit.Dp, pal: Pal, fg: Color, vizOk: Boolean) {
    val frame = rememberViz(10, active = vizOk)
    Row(
        Modifier
            .height(h)
            .width((s.islandWidthDp + 110).dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(m.art, ArtStyle.RECORD, m.playing, pal, h - 8.dp)
        Spacer(Modifier.weight(1f))
        if (vizOk) {
            VisualizerCanvas(
                frame, VizStyle.MIRROR, pal.stops(ColorRole.GRADIENT),
                Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
                gap = 0.4f, peaks = false, minLevel = 0.12f,
            )
        } else {
            EqualizerBars(pal, fg, Modifier.width(26.dp).fillMaxHeight().padding(vertical = 8.dp))
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** Fallback "now playing" bars when the real visualizer isn't allowed. */
@Composable
private fun EqualizerBars(pal: Pal, fg: Color, modifier: Modifier) {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            phase.value += 0.25f
            delay(80)
        }
    }
    Canvas(modifier) {
        val n = 4
        val slot = size.width / n
        for (i in 0 until n) {
            val v = 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(phase.value + i * 1.3f))
            val bh = size.height * v
            drawRoundRect(
                pal.gradientAt(i / (n - 1f)),
                Offset(i * slot + slot * 0.2f, (size.height - bh) / 2),
                Size(slot * 0.6f, bh),
                CornerRadius(slot * 0.3f, slot * 0.3f),
            )
        }
    }
}

@Composable
private fun ChargingPill(b: BatteryInfo, h: androidx.compose.ui.unit.Dp, pal: Pal, fg: Color) {
    val green = pal.ansi.getOrElse(2) { Color(0xFF6BD968) }
    Row(
        Modifier
            .height(h)
            .width(220.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚡", color = green, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text("Charging", color = fg, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("${b.level}%", color = green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        BatteryGlyph(b.level, true, green, Modifier.size(width = 26.dp, height = 13.dp))
    }
}

@Composable
private fun PeekCard(p: Peek, pal: Pal, fg: Color, maxW: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier
            .width(maxW)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (p.tint != 0) Color(p.tint) else pal.accent),
            contentAlignment = Alignment.Center,
        ) {
            p.icon?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.text.isNotBlank()) {
                Text(p.text, color = fg.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ExpandedCard(m: MediaInfo, pal: Pal, fg: Color, maxW: androidx.compose.ui.unit.Dp, vizOk: Boolean, touched: () -> Unit) {
    val frame = rememberViz(40, active = vizOk && m.playing)
    val context = LocalContext.current
    rememberNow(true)
    val pos = m.position()
    Column(Modifier.width(maxW).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(m.art, ArtStyle.SQUARE, m.playing, pal, 60.dp, Modifier.clickable { MediaHub.openPlayer(context) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                Text(m.artist, color = fg.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (vizOk) {
                VisualizerCanvas(frame, VizStyle.BARS, pal.gradient, Modifier.size(width = 54.dp, height = 30.dp), gap = 0.35f, peaks = false)
            }
        }
        if (m.durationMs > 0) {
            Spacer(Modifier.height(14.dp))
            val frac = (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(m.durationMs) {
                        detectTapGestures { off ->
                            touched()
                            MediaHub.seekTo((off.x / size.width * m.durationMs).toLong())
                        }
                    },
            ) {
                val y = size.height / 2
                drawLine(fg.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Brush.horizontalGradient(pal.gradient), Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 5f, cap = StrokeCap.Round)
                drawCircle(fg, 7f, Offset(size.width * frac, y))
            }
            Row {
                Text(fmt(pos), color = fg.copy(alpha = 0.55f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(fmt(m.durationMs), color = fg.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IslandButton(Transport.PREV, fg, 26) { touched(); MediaHub.previous() }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(pal.gradient))
                    .clickable { touched(); MediaHub.playPause() },
                contentAlignment = Alignment.Center,
            ) { TransportIcon(if (m.playing) Transport.PAUSE else Transport.PLAY, Color.White, 26.dp) }
            IslandButton(Transport.NEXT, fg, 26) { touched(); MediaHub.next() }
        }
    }
}

@Composable
private fun IslandButton(kind: Transport, color: Color, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size((size + 18).dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TransportIcon(kind, color, size.dp) }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ═══════════════════════════════════════════════════════════════════ status bar

@Composable
fun StatusBarContent(geo: IslandGeometry) {
    val store = LauncherApp.instance.store
    val state by store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
    val battery by SystemMonitor.battery.collectAsState()
    val net by SystemMonitor.net.collectAsState()
    val icons by SystemMonitor.icons.collectAsState()
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
                Image(ic.icon, null, colorFilter = ColorFilter.tint(text.copy(alpha = 0.9f)), modifier = Modifier.padding(end = 6.dp).size(14.dp))
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
