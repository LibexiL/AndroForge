@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.libexil.androforge.ui.forge

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.data.ArtStyle
import com.libexil.androforge.data.ClockStyle
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.GaugeStyle
import com.libexil.androforge.data.TempUnits
import com.libexil.androforge.data.VizStyle
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.PermissionBus
import com.libexil.androforge.live.Sky
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.live.WeatherRepo
import com.libexil.androforge.live.WeatherState
import com.libexil.androforge.ui.Pal
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What a widget can ask the launcher to do. */
interface ForgeActions {
    fun requestAudio()
    fun requestLocation()
    fun openNotificationAccess()
    fun customize()
    fun update(transform: (com.libexil.androforge.data.ForgeOptions) -> com.libexil.androforge.data.ForgeOptions) = Unit
    fun launch(app: com.libexil.androforge.data.AppKey) = Unit
    fun openSearch() = Unit
    fun pickPhoto() = Unit
    fun requestPermission(permission: String) = Unit
}

/** The palette as this widget's look wants it (custom text color). */
fun Pal.forLook(look: com.libexil.androforge.data.PanelLook): Pal {
    val textRole = look.textRole ?: return this
    val c = role(textRole)
    return Pal(source.copy(foreground = com.libexil.androforge.data.Palettes.hex(c.toArgb())))
}

@Composable
fun ForgeWidget(item: ForgeWidgetItem, basePal: Pal, actions: ForgeActions) {
    val pal = androidx.compose.runtime.remember(basePal, item.look.textRole) { basePal.forLook(item.look) }
    when (item.kind) {
        ForgeKind.CLOCK -> ForgePanel(item.look, item.kind, pal) { ClockWidget(item, pal) }
        ForgeKind.MUSIC -> ForgePanel(item.look, item.kind, pal, reactsToMusic = true) { MusicWidget(item, pal, actions) }
        ForgeKind.VISUALIZER -> ForgePanel(item.look, item.kind, pal, reactsToMusic = true, padding = 8.dp) { VisualizerWidget(item, pal, actions) }
        ForgeKind.WEATHER -> ForgePanel(item.look, item.kind, pal) { WeatherWidget(item, pal, actions) }
        ForgeKind.SYSTEM -> ForgePanel(item.look, item.kind, pal) { SystemWidget(item, pal) }
        ForgeKind.CALENDAR -> ForgePanel(item.look, item.kind, pal) { CalendarWidget(item, pal) }
        ForgeKind.COUNTDOWN -> ForgePanel(item.look, item.kind, pal) { CountdownWidget(item, pal) }
        ForgeKind.TODO -> ForgePanel(item.look, item.kind, pal) { TodoWidget(item, pal, actions) }
        ForgeKind.SHELF -> ForgePanel(item.look, item.kind, pal, padding = 8.dp) { ShelfWidget(item, pal, actions) }
        ForgeKind.NETWORK -> ForgePanel(item.look, item.kind, pal) { NetworkWidget(item, pal) }
        ForgeKind.GREETING -> ForgePanel(item.look, item.kind, pal) { GreetingWidget(item, pal) }
        ForgeKind.SEARCH -> ForgePanel(item.look, item.kind, pal, padding = 4.dp) { SearchWidget(item, pal, actions) }
        ForgeKind.BATTERY -> ForgePanel(item.look, item.kind, pal) { BatteryWidget(item, pal) }
        ForgeKind.STACK -> StackWidget(item, pal, actions)
        ForgeKind.NOTE -> ForgePanel(item.look.copy(bgRole = item.options.noteColor), item.kind, pal) { NoteWidget(item, pal, actions) }
        ForgeKind.PHOTO -> PhotoWidget(item, pal, actions)
        ForgeKind.WORLD_CLOCK -> ForgePanel(item.look, item.kind, pal) { WorldClockWidget(item, pal) }
        ForgeKind.STOPWATCH -> ForgePanel(item.look, item.kind, pal) { StopwatchWidget(item, pal, actions) }
        ForgeKind.QUOTE -> ForgePanel(item.look, item.kind, pal) { QuoteWidget(item, pal) }
        ForgeKind.MOON -> ForgePanel(item.look, item.kind, pal) { MoonWidget(item, pal) }
        ForgeKind.HABIT -> ForgePanel(item.look, item.kind, pal) { HabitWidget(item, pal, actions) }
        ForgeKind.NOTIFS -> ForgePanel(item.look, item.kind, pal) { NotifsWidget(item, pal, actions) }
        ForgeKind.AGENDA -> ForgePanel(item.look, item.kind, pal) { AgendaWidget(item, pal, actions) }
    }
}

@Composable
private fun Muted(text: String, pal: Pal, size: Float = 12f, modifier: Modifier = Modifier) =
    Text(text, color = pal.muted, fontSize = ts(size), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

@Composable
private fun Hint(text: String, pal: Pal, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = pal.muted, fontSize = ts(12.5f), maxLines = 3)
    }
}

// ═══════════════════════════════════════════════════════════════════ clock

@Composable
fun rememberNow(everySecond: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everySecond) {
        while (true) {
            now = System.currentTimeMillis()
            val step = if (everySecond) 1000L else 60_000L
            delay(step - now % step + 5)
        }
    }
    return now
}

@Composable
private fun ClockWidget(item: ForgeWidgetItem, pal: Pal) {
    val o = item.options
    val context = LocalContext.current
    val twelve = o.twelveHour ?: !DateFormat.is24HourFormat(context)
    val needsSeconds = o.showSeconds || o.clockStyle == ClockStyle.RING || o.clockStyle == ClockStyle.ANALOG
    val now = rememberNow(needsSeconds)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hh = SimpleDateFormat(if (twelve) "h" else "HH", Locale.getDefault()).format(Date(now))
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(Date(now))
    val ss = SimpleDateFormat("ss", Locale.getDefault()).format(Date(now))
    val ampm = if (twelve) SimpleDateFormat("a", Locale.getDefault()).format(Date(now)) else ""
    val weekday = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(now))
    val hl = pal.role(item.look.highlight)
    val stops = pal.stops(item.look.highlight)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        when (o.clockStyle) {
            ClockStyle.RING -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val d = minOf(h, w * 0.55f)
                Box(Modifier.size(d), contentAlignment = Alignment.Center) {
                    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.minDimension * 0.06f
                        val inset = sw / 2
                        val sz = Size(size.width - sw, size.height - sw)
                        drawArc(pal.muted.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                        drawArc(
                            Brush.sweepGradient(stops + stops.first()),
                            -90f, 360f * (sec / 60f), false, Offset(inset, inset), sz,
                            style = Stroke(sw, cap = StrokeCap.Round),
                        )
                        val a = (sec / 60f) * 2 * PI.toFloat() - PI.toFloat() / 2
                        val r = (size.minDimension - sw) / 2
                        drawCircle(hl, sw * 0.9f, Offset(center.x + cos(a) * r, center.y + sin(a) * r))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$hh:$mm", fontSize = (d.value * 0.25f).sp * LocalTextScale.current, fontWeight = FontWeight.Light, maxLines = 1)
                        if (o.showSeconds) Text(ss, color = hl, fontSize = (d.value * 0.11f).sp * LocalTextScale.current)
                    }
                }
                if (o.showDate) {
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(weekday, color = hl, fontSize = ts(18f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(date, fontSize = ts(14f), maxLines = 1)
                        if (ampm.isNotEmpty()) Muted(ampm, pal)
                    }
                }
            }
            ClockStyle.DIGITAL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$hh:$mm", fontSize = (h.value * 0.42f).coerceAtMost(w.value * 0.22f).sp * LocalTextScale.current, fontWeight = FontWeight.Light, maxLines = 1)
                    Column(Modifier.padding(start = 6.dp, bottom = (h.value * 0.06f).dp)) {
                        if (ampm.isNotEmpty()) Text(ampm, color = hl, fontSize = ts(14f))
                        if (o.showSeconds) Text(ss, color = hl, fontSize = ts(18f))
                    }
                }
                if (o.showDate) Text("$weekday, $date", color = pal.foreground.copy(alpha = 0.85f), fontSize = ts(15f), maxLines = 1)
            }
            ClockStyle.STACKED -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val big = (h.value * 0.4f).sp * LocalTextScale.current
                Column {
                    Text(hh.padStart(2, '0'), color = hl, fontSize = big, fontWeight = FontWeight.Black, lineHeight = big, maxLines = 1)
                    Text(mm, fontSize = big, fontWeight = FontWeight.Black, lineHeight = big, maxLines = 1)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    if (o.showDate) {
                        Text(weekday.uppercase(), color = hl, fontSize = ts(13f), letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(date, fontSize = ts(16f))
                    }
                    if (o.showSeconds) Muted(":$ss $ampm", pal, 14f)
                }
            }
            ClockStyle.ANALOG -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val d = minOf(h, w * 0.55f)
                Canvas(Modifier.size(d)) {
                    val r = size.minDimension / 2
                    val c = center
                    drawCircle(pal.surface.copy(alpha = 0.45f), r, c)
                    drawCircle(Brush.sweepGradient(stops + stops.first(), c), r, c, style = Stroke(r * 0.04f))
                    for (i in 0 until 60) {
                        val a = i / 60f * 2 * PI.toFloat()
                        val major = i % 5 == 0
                        val r1 = r * (if (major) 0.8f else 0.87f)
                        drawLine(
                            if (major) pal.foreground else pal.muted.copy(alpha = 0.6f),
                            Offset(c.x + sin(a) * r1, c.y - cos(a) * r1),
                            Offset(c.x + sin(a) * r * 0.92f, c.y - cos(a) * r * 0.92f),
                            strokeWidth = if (major) r * 0.03f else r * 0.012f,
                            cap = StrokeCap.Round,
                        )
                    }
                    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
                    val min = cal.get(Calendar.MINUTE) + sec / 60f
                    val hour = (cal.get(Calendar.HOUR) + min / 60f)
                    fun hand(frac: Float, len: Float, width: Float, color: Color) {
                        val a = frac * 2 * PI.toFloat()
                        drawLine(color, c, Offset(c.x + sin(a) * r * len, c.y - cos(a) * r * len), strokeWidth = width, cap = StrokeCap.Round)
                    }
                    hand(hour / 12f, 0.5f, r * 0.07f, pal.foreground)
                    hand(min / 60f, 0.72f, r * 0.045f, pal.foreground)
                    if (o.showSeconds) hand(sec / 60f, 0.8f, r * 0.02f, hl)
                    drawCircle(hl, r * 0.05f, c)
                }
                if (o.showDate) {
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(weekday, color = hl, fontSize = ts(18f), fontWeight = FontWeight.SemiBold)
                        Text(date, fontSize = ts(14f))
                        Muted("$hh:$mm $ampm", pal)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ music

@Composable
private fun MusicWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val o = item.options
    val hasAccess by MediaHub.hasAccess.collectAsState()
    val media by MediaHub.state.collectAsState()
    val context = LocalContext.current
    val hl = pal.role(item.look.highlight)
    if (!hasAccess) {
        Hint("Tap to allow notification access so AndroForge can show what's playing", pal) { actions.openNotificationAccess() }
        return
    }
    val m = media
    if (m == null) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            TransportIcon(Transport.PLAY, pal.muted, 28.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Nothing playing", fontSize = ts(15f))
                Muted("Start music in any app", pal)
            }
        }
        return
    }
    val vizStatus by AudioViz.status.collectAsState()
    val showViz = o.musicVisualizer && m.playing && vizStatus != AudioViz.Status.NO_PERMISSION && vizStatus != AudioViz.Status.UNSUPPORTED
    val frame = rememberViz(48, active = showViz)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        if (showViz) {
            VisualizerCanvas(
                frame, VizStyle.BARS, pal.stops(ColorRole.GRADIENT).map { it.copy(alpha = 0.35f) },
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(h * 0.45f),
                gap = 0.35f, peaks = false, minLevel = 0f,
            )
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (o.artStyle != ArtStyle.NONE) {
                AlbumArt(m.art, o.artStyle, m.playing, pal, minOf(h, w * 0.4f), Modifier.clickable { MediaHub.openPlayer(context) })
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    m.title, fontSize = ts(16f), fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Muted(listOf(m.artist, m.album).filter { it.isNotBlank() }.joinToString(" · "), pal, 12.5f)
                if (o.showProgress && m.durationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    SeekBar(m, pal, hl)
                }
                if (o.showControls) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pulse = item.look.pulse && showViz
                        ControlButton(Transport.PREV, pal.foreground, 26.dp) { MediaHub.previous() }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    frame.tick
                                    drawRect(hl.copy(alpha = (0.22f + if (pulse) AudioViz.bass * 0.55f else 0f).coerceAtMost(0.9f)))
                                }
                                .clickable { MediaHub.playPause() },
                            contentAlignment = Alignment.Center,
                        ) { TransportIcon(if (m.playing) Transport.PAUSE else Transport.PLAY, pal.foreground, 22.dp) }
                        Spacer(Modifier.width(10.dp))
                        ControlButton(Transport.NEXT, pal.foreground, 26.dp) { MediaHub.next() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(kind: Transport, color: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 10.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TransportIcon(kind, color, size) }
}

@Composable
fun AlbumArt(art: androidx.compose.ui.graphics.ImageBitmap?, style: ArtStyle, playing: Boolean, pal: Pal, size: Dp, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "record")
    val angle = if (style == ArtStyle.RECORD && playing) {
        spin.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin").value
    } else {
        0f
    }
    val shape = if (style == ArtStyle.RECORD) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(size)
            .rotate(angle)
            .clip(shape)
            .background(Brush.linearGradient(pal.gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            TransportIcon(Transport.PLAY, Color.White.copy(alpha = 0.8f), size * 0.35f)
        }
        if (style == ArtStyle.RECORD) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                for (k in 1..3) drawCircle(Color.Black.copy(alpha = 0.18f), r * (0.45f + k * 0.15f), style = Stroke(1.2f))
                drawCircle(pal.background, r * 0.16f)
                drawCircle(pal.muted, r * 0.05f)
            }
        }
    }
}

@Composable
private fun SeekBar(m: com.libexil.androforge.live.MediaInfo, pal: Pal, hl: Color) {
    rememberNow(true) // recompose every second
    val pos = m.position()
    val frac = (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(m.durationMs) {
                    detectTapGestures { off -> MediaHub.seekTo((off.x / size.width * m.durationMs).toLong()) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawRoundRect(pal.muted.copy(alpha = 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                drawRoundRect(
                    Brush.horizontalGradient(pal.gradient),
                    size = Size(size.width * frac, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
                drawCircle(hl, size.height * 1.4f, Offset(size.width * frac, size.height / 2))
            }
        }
        Row {
            Muted(fmt(pos), pal, 10.5f)
            Spacer(Modifier.weight(1f))
            Muted(fmt(m.durationMs), pal, 10.5f)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ═══════════════════════════════════════════════════════════════════ visualizer

@Composable
private fun VisualizerWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val o = item.options
    val status by AudioViz.status.collectAsState()
    if (status == AudioViz.Status.NO_PERMISSION) {
        val frame = rememberViz(o.bars, active = false)
        Box(Modifier.fillMaxSize()) {
            VisualizerCanvas(frame, o.vizStyle, pal.stops(o.vizColor), Modifier.fillMaxSize(), o.gap, o.rounded, false, 0.06f)
            Hint("Tap to let the visualizer hear your music (audio access)", pal) { actions.requestAudio() }
        }
        return
    }
    if (status == AudioViz.Status.UNSUPPORTED) {
        Hint("This phone doesn't allow apps to read the audio output", pal)
        return
    }
    val frame = rememberViz(o.bars, active = true)
    VisualizerCanvas(
        frame, o.vizStyle, pal.stops(o.vizColor), Modifier.fillMaxSize(),
        gap = o.gap, rounded = o.rounded, peaks = o.peaks,
        peakColor = pal.foreground,
    )
}

// ═══════════════════════════════════════════════════════════════════ weather

@Composable
private fun WeatherWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val o = item.options
    val context = LocalContext.current
    val units = o.units ?: WeatherRepo.defaultUnits()
    val key = WeatherRepo.key(o.place, units)
    val state by remember(key) { WeatherRepo.flow(key) }.collectAsState()
    var tick by remember { mutableStateOf(0) }
    val permissions by PermissionBus.version.collectAsState()
    LaunchedEffect(key, tick, permissions) {
        while (true) {
            WeatherRepo.refresh(context, o.place, units)
            delay(15 * 60 * 1000L)
        }
    }
    val hl = pal.role(item.look.highlight)
    when (val s = state) {
        WeatherState.Loading -> Hint("Loading weather…", pal)
        WeatherState.NeedsLocation -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Where are you?", fontSize = ts(15f))
            Spacer(Modifier.height(6.dp))
            Row {
                Chip("Use my location", hl) {
                    actions.requestLocation()
                    tick++
                }
                Spacer(Modifier.width(8.dp))
                Chip("Pick a city", hl) { actions.customize() }
            }
        }
        is WeatherState.Failed -> Hint("Couldn't load weather (${s.message}). Tap to retry.", pal) { tick++ }
        is WeatherState.Ready -> {
            val w = s.weather
            val deg = "°"
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val boxH = maxHeight
                val tall = boxH > 120.dp
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(w.sky, w.isDay, pal, Modifier.size(minOf(boxH * (if (tall && o.forecastDays > 0) 0.5f else 0.8f), 76.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${w.temp}$deg", fontSize = ts(30f), fontWeight = FontWeight.Light, maxLines = 1)
                            Text(w.condition, fontSize = ts(13f), maxLines = 1)
                            Muted(w.place, pal, 11.5f)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("↑ ${w.high}$deg  ↓ ${w.low}$deg", fontSize = ts(12f), color = hl, maxLines = 1)
                            Muted("Feels ${w.feels}$deg", pal, 11.5f)
                            Muted("${w.humidity}%  ·  ${w.wind} ${if (w.units == TempUnits.US) "mph" else "km/h"}", pal, 11.5f)
                        }
                    }
                    if (tall && o.forecastDays > 0) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            w.days.drop(1).take(o.forecastDays).forEach { d ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Muted(d.label, pal, 11f)
                                    WeatherIcon(d.sky, true, pal, Modifier.size(26.dp), animate = false)
                                    Text("${d.max}$deg", fontSize = ts(12f))
                                    Muted("${d.min}$deg", pal, 11f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = ts(12.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Small animated weather glyphs drawn in the palette. */
@Composable
fun WeatherIcon(sky: Sky, isDay: Boolean, pal: Pal, modifier: Modifier, animate: Boolean = true) {
    val t = if (animate) {
        rememberInfiniteTransition(label = "wx").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t").value
    } else {
        0.3f
    }
    val sun = pal.ansi.getOrElse(3) { pal.accent }
    val cloud = pal.foreground.copy(alpha = 0.92f)
    val rain = pal.ansi.getOrElse(4) { pal.accent2 }
    val bolt = pal.ansi.getOrElse(3) { pal.accent }
    Canvas(modifier) {
        val s = size.minDimension
        fun sunAt(c: Offset, r: Float) {
            if (isDay) {
                rotate(t * 90f, c) {
                    for (i in 0 until 8) {
                        val a = i / 8f * 2 * PI.toFloat()
                        drawLine(sun, Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f), Offset(c.x + cos(a) * r * 1.75f, c.y + sin(a) * r * 1.75f), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
                    }
                }
                drawCircle(sun, r, c)
            } else {
                drawCircle(pal.foreground.copy(alpha = 0.9f), r, c)
                drawCircle(pal.background, r * 0.85f, Offset(c.x + r * 0.45f, c.y - r * 0.35f))
            }
        }
        fun cloudAt(c: Offset, k: Float, color: Color = cloud) {
            drawCircle(color, s * 0.16f * k, Offset(c.x - s * 0.14f * k, c.y + s * 0.03f * k))
            drawCircle(color, s * 0.21f * k, Offset(c.x + s * 0.03f * k, c.y - s * 0.05f * k))
            drawCircle(color, s * 0.15f * k, Offset(c.x + s * 0.19f * k, c.y + s * 0.04f * k))
            drawRect(color, Offset(c.x - s * 0.14f * k, c.y + s * 0.02f * k), Size(s * 0.33f * k, s * 0.17f * k))
        }
        val drift = sin(t * 2 * PI.toFloat()) * s * 0.03f
        val mid = Offset(s / 2 + drift, s * 0.45f)
        when (sky) {
            Sky.CLEAR -> sunAt(Offset(s / 2, s / 2), s * 0.2f)
            Sky.PARTLY -> {
                sunAt(Offset(s * 0.36f, s * 0.36f), s * 0.15f)
                cloudAt(Offset(s * 0.56f + drift, s * 0.55f), 0.85f)
            }
            Sky.CLOUDY -> {
                cloudAt(Offset(s * 0.62f - drift, s * 0.38f), 0.6f, pal.muted)
                cloudAt(mid, 1f)
            }
            Sky.FOG -> for (i in 0 until 4) {
                val y = s * (0.3f + i * 0.14f)
                val off = sin((t + i * 0.25f) * 2 * PI.toFloat()) * s * 0.06f
                drawLine(cloud.copy(alpha = 0.8f - i * 0.12f), Offset(s * 0.15f + off, y), Offset(s * 0.85f + off, y), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
            Sky.DRIZZLE, Sky.RAIN, Sky.STORM -> {
                cloudAt(Offset(s / 2, s * 0.36f), 1f)
                val drops = if (sky == Sky.DRIZZLE) 3 else 5
                for (i in 0 until drops) {
                    val x = s * (0.25f + i * 0.5f / (drops - 1).coerceAtLeast(1))
                    val ph = (t * 2f + i * 0.37f) % 1f
                    val y = s * (0.6f + ph * 0.32f)
                    drawLine(rain.copy(alpha = 1f - ph), Offset(x, y), Offset(x - s * 0.03f, y + s * 0.08f), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
                }
                if (sky == Sky.STORM && (t * 4f) % 1f < 0.18f) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.52f, s * 0.5f); lineTo(s * 0.4f, s * 0.72f); lineTo(s * 0.5f, s * 0.72f); lineTo(s * 0.42f, s * 0.94f)
                        lineTo(s * 0.62f, s * 0.66f); lineTo(s * 0.52f, s * 0.66f); close()
                    }
                    drawPath(p, bolt)
                }
            }
            Sky.SNOW -> {
                cloudAt(Offset(s / 2, s * 0.36f), 1f)
                for (i in 0 until 4) {
                    val ph = (t + i * 0.27f) % 1f
                    val x = s * (0.28f + i * 0.15f) + sin((ph + i) * 6f) * s * 0.03f
                    drawCircle(Color.White.copy(alpha = 1f - ph * 0.6f), s * 0.035f, Offset(x, s * (0.62f + ph * 0.3f)))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ system

@Composable
private fun SystemWidget(item: ForgeWidgetItem, pal: Pal) {
    val context = LocalContext.current
    val battery by SystemMonitor.battery.collectAsState()
    val snap by produceState(SystemMonitor.snapshot(context)) {
        while (true) {
            delay(4000)
            value = SystemMonitor.snapshot(context)
        }
    }
    val ram = if (snap.ramTotal > 0) snap.ramUsed.toFloat() / snap.ramTotal else 0f
    val disk = if (snap.storageTotal > 0) snap.storageUsed.toFloat() / snap.storageTotal else 0f
    val gauges = listOf(
        Triple("BATTERY", battery.level / 100f, "${battery.level}%" + if (battery.charging) " ⚡" else ""),
        Triple("MEMORY", ram, "${(ram * 100).toInt()}%"),
        Triple("STORAGE", disk, "${(disk * 100).toInt()}%"),
    )
    val upH = snap.uptimeMs / 3_600_000
    val upM = snap.uptimeMs / 60_000 % 60
    val footer = "up ${if (upH > 0) "${upH}h " else ""}${upM}m  ·  ${"%.1f".format(battery.tempC)}°C  ·  " +
        "${gb(snap.ramUsed)}/${gb(snap.ramTotal)} GB RAM"
    val hl = pal.role(item.look.highlight)
    when (item.options.gaugeStyle) {
        GaugeStyle.ARCS -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                gauges.forEachIndexed { i, (label, v, text) ->
                    val c = pal.gradientAt(i / 2f)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val d = min(maxWidth.value, maxHeight.value).dp
                        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                                val sw = size.minDimension * 0.1f
                                val inset = Offset(sw / 2, sw / 2)
                                val sz = Size(size.width - sw, size.height - sw)
                                drawArc(pal.muted.copy(alpha = 0.25f), 135f, 270f, false, inset, sz, style = Stroke(sw, cap = StrokeCap.Round))
                                drawArc(c, 135f, 270f * v.coerceIn(0f, 1f), false, inset, sz, style = Stroke(sw, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text, fontSize = (d.value * 0.17f).sp * LocalTextScale.current, maxLines = 1)
                                Text(label, color = pal.muted, fontSize = (d.value * 0.09f).sp * LocalTextScale.current, letterSpacing = 1.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Muted(footer, pal, 10.5f, Modifier.align(Alignment.CenterHorizontally))
        }
        GaugeStyle.BARS -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            gauges.forEachIndexed { i, (label, v, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label.take(4), color = hl, fontSize = ts(11.5f), fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    Canvas(Modifier.weight(1f).height(8.dp)) {
                        val cr = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                        drawRoundRect(pal.muted.copy(alpha = 0.25f), cornerRadius = cr)
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(pal.gradientAt(i / 3f), pal.gradientAt((i + 1) / 3f))),
                            size = Size(size.width * v.coerceIn(0f, 1f), size.height),
                            cornerRadius = cr,
                        )
                    }
                    Text(text, fontSize = ts(12f), modifier = Modifier.width(62.dp).padding(start = 8.dp), maxLines = 1)
                }
            }
            Muted(footer, pal, 10.5f)
        }
    }
}

private fun gb(bytes: Long) = "%.1f".format(bytes / 1_073_741_824.0)

/** Static thumbnail for the widget picker. */
@Composable
fun ForgeKindGlyph(kind: ForgeKind, pal: Pal) {
    when (kind) {
        ForgeKind.CLOCK -> Text(
            SimpleDateFormat("H:mm", Locale.getDefault()).format(Date()),
            color = pal.foreground, fontSize = 22.sp, fontWeight = FontWeight.Light,
        )
        ForgeKind.WEATHER -> Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(Sky.PARTLY, true, pal, Modifier.size(34.dp), animate = false)
            Text("21°", color = pal.foreground, fontSize = 18.sp)
        }
        ForgeKind.CALENDAR, ForgeKind.COUNTDOWN, ForgeKind.TODO, ForgeKind.SHELF,
        ForgeKind.NETWORK, ForgeKind.GREETING, ForgeKind.SEARCH, ForgeKind.BATTERY, ForgeKind.STACK -> NewKindGlyph(kind, pal)
        ForgeKind.NOTE, ForgeKind.PHOTO, ForgeKind.WORLD_CLOCK, ForgeKind.STOPWATCH, ForgeKind.QUOTE,
        ForgeKind.MOON, ForgeKind.HABIT, ForgeKind.NOTIFS, ForgeKind.AGENDA -> ExtraKindGlyph(kind, pal)
        ForgeKind.MUSIC, ForgeKind.VISUALIZER, ForgeKind.SYSTEM -> Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            when (kind) {
                ForgeKind.SYSTEM -> for (i in 0 until 3) {
                    val d = h
                    val x = i * (w / 3f) + (w / 3f - d) / 2f
                    drawArc(pal.muted.copy(alpha = 0.3f), 135f, 270f, false, Offset(x, 0f), Size(d, d), style = Stroke(d * 0.12f, cap = StrokeCap.Round))
                    drawArc(pal.gradientAt(i / 2f), 135f, 270f * (0.35f + i * 0.2f), false, Offset(x, 0f), Size(d, d), style = Stroke(d * 0.12f, cap = StrokeCap.Round))
                }
                else -> {
                    val n = 16
                    val start = if (kind == ForgeKind.MUSIC) h + 6f else 0f
                    if (kind == ForgeKind.MUSIC) drawRoundRect(Brush.linearGradient(pal.gradient), Offset.Zero, Size(h, h), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                    val slot = (w - start) / n
                    for (i in 0 until n) {
                        val v = 0.25f + 0.7f * kotlin.math.abs(sin(i * 0.9f + 0.6f))
                        drawRoundRect(
                            Brush.verticalGradient(pal.gradient.reversed(), 0f, h),
                            Offset(start + i * slot + slot * 0.15f, h - v * h),
                            Size(slot * 0.7f, v * h),
                            androidx.compose.ui.geometry.CornerRadius(slot * 0.35f, slot * 0.35f),
                        )
                    }
                }
            }
        }
    }
}
