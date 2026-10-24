package com.libexil.androforge.ui.forge

import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.ui.AppIcon
import com.libexil.androforge.ui.IconLabel
import com.libexil.androforge.ui.LocalMotion
import com.libexil.androforge.ui.Pal
import com.libexil.androforge.ui.labelFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
private fun Faint(text: String, pal: Pal, size: Float = 12f, modifier: Modifier = Modifier) =
    Text(text, color = pal.muted, fontSize = ts(size), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

// ═══════════════════════════════════════════════════════════════════ calendar

@Composable
fun CalendarWidget(item: ForgeWidgetItem, pal: Pal) {
    val hl = pal.role(item.look.highlight)
    val today = remember(rememberNow(false) / 86_400_000L) { LocalDate.now() }
    var offset by remember { mutableIntStateOf(0) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val first = if (item.options.mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val days = (0 until 7).map { first.plus(it.toLong()) }
    val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val glow = rememberInfiniteTransition(label = "today").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "g")
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                fontSize = ts(15f), fontWeight = FontWeight.SemiBold, color = hl, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowLeft, "Previous month", tint = pal.foreground, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { offset-- })
            if (offset != 0) {
                Text("Today", fontSize = ts(11f), color = pal.muted, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { offset = 0 }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, "Next month", tint = pal.foreground, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { offset++ })
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = pal.muted, fontSize = ts(10.5f), modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val rows = (cells + 6) / 7
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val isToday = offset == 0 && day == today.dayOfMonth
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .then(
                                            if (isToday) {
                                                Modifier.drawBehind {
                                                    drawCircle(hl.copy(alpha = 0.25f * glow.value), size.minDimension * 0.75f)
                                                    drawCircle(hl, size.minDimension / 2)
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = ts(11.5f),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) Color.White else pal.foreground,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ countdown

fun nextNewYear(): Long {
    val c = Calendar.getInstance()
    c.set(c.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1, 0, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

@Composable
fun CountdownWidget(item: ForgeWidgetItem, pal: Pal) {
    val o = item.options
    val target = if (o.countdownTarget > 0) o.countdownTarget else remember { nextNewYear() }
    val now = rememberNow(true)
    val left = (target - now).coerceAtLeast(0) / 1000
    val parts = listOf(left / 86400 to "DAYS", left / 3600 % 24 to "HRS", left / 60 % 60 to "MIN", left % 60 to "SEC")
    val hl = pal.role(item.look.highlight)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            o.countdownTitle.ifBlank { "Countdown" } + if (left == 0L) " — it's here!" else "",
            fontSize = ts(14f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Faint(SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(target)), pal, 11.5f)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { (v, label) -> FlipDigit(v, label, pal, hl, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FlipDigit(value: Long, label: String, pal: Pal, hl: Color, modifier: Modifier) {
    val motion = LocalMotion.current
    val flip = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != shown) {
            flip.snapTo(1f)
            shown = value
            flip.animateTo(0f, motion.tween(260))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .graphicsLayer {
                    rotationX = flip.value * 70f
                    cameraDistance = 12 * density
                }
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(pal.surface, pal.background)))
                .border(1.dp, hl.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (label == "DAYS") "$shown" else "%02d".format(shown), fontSize = ts(22f), fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(Color.Black.copy(alpha = 0.35f)) }
        }
        Text(label, color = pal.muted, fontSize = ts(9.5f), letterSpacing = 1.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════ to-do

@Composable
fun TodoWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val o = item.options
    val hl = pal.role(item.look.highlight)
    val shown = o.todos.withIndex().filter { !(o.hideDone && it.value.done) }
    Column(Modifier.fillMaxSize()) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { actions.customize() }, contentAlignment = Alignment.Center) {
                Faint("All done! Tap to add more", pal, 13f)
            }
            return
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            shown.forEach { (i, t) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            actions.update { op -> op.copy(todos = op.todos.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }) }
                        }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (t.done) hl else Color.Transparent)
                            .border(1.5.dp, if (t.done) hl else pal.muted, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (t.done) Text("✓", color = Color.White, fontSize = 11.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.text,
                        fontSize = ts(13.5f),
                        color = if (t.done) pal.muted else pal.foreground,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val done = o.todos.count { it.done }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Faint("$done of ${o.todos.size} done", pal, 11f, Modifier.weight(1f))
            Text("+ Add", color = hl, fontSize = ts(12f), modifier = Modifier.clip(RoundedCornerShape(50)).clickable { actions.customize() }.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ app shelf

@Composable
fun ShelfWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val o = item.options
    val apps by LauncherApp.instance.apps.apps.collectAsState()
    val byId = remember(apps) { apps.associateBy { it.key.id } }
    val list = o.shelfApps.filter { it.id in byId }
    val hl = pal.role(item.look.highlight)
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().clickable { actions.customize() }, contentAlignment = Alignment.Center) {
            Faint("Tap to pick apps for this shelf", pal, 13f)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val perRow = (maxWidth / 64.dp).toInt().coerceAtLeast(1)
        val rows = (list.size + perRow - 1) / perRow
        val iconSize = minOf(maxWidth / perRow - 12.dp, maxHeight / rows - (if (o.shelfLabels) 20.dp else 8.dp)).coerceIn(28.dp, 64.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            list.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key -> ShelfIcon(key, byId[key.id]?.label, iconSize, o.shelfLabels, hl, actions) }
                    repeat(perRow - row.size) { Spacer(Modifier.width(iconSize)) }
                }
            }
        }
    }
}

@Composable
private fun ShelfIcon(key: com.libexil.androforge.data.AppKey, label: String?, size: androidx.compose.ui.unit.Dp, showLabel: Boolean, hl: Color, actions: ForgeActions) {
    val motion = LocalMotion.current
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                scope.launch {
                    lift.animateTo(1f, motion.spring(stiffness = 1200f))
                    lift.animateTo(0f, motion.spring(stiffness = 600f))
                }
                actions.launch(key)
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationY = -lift.value * 8.dp.toPx()
                    scaleX = 1f + lift.value * 0.1f
                    scaleY = 1f + lift.value * 0.1f
                }
                .drawBehind {
                    if (lift.value > 0.01f) drawCircle(hl.copy(alpha = 0.35f * lift.value), size.minDimension * 0.7f)
                },
        ) { AppIcon(key, size) }
        if (showLabel) IconLabel(labelFor(key, label), sizeSp = 10)
    }
}

// ═══════════════════════════════════════════════════════════════════ network

private data class NetSample(val down: Float, val up: Float)

@Composable
fun NetworkWidget(item: ForgeWidgetItem, pal: Pal) {
    val history = remember { ArrayDeque<NetSample>() }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = System.nanoTime()
        while (true) {
            delay(1000)
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val t = System.nanoTime()
            val dt = ((t - lastT) / 1e9f).coerceAtLeast(0.1f)
            if (rx >= 0 && tx >= 0) {
                history.addLast(NetSample(((rx - lastRx).coerceAtLeast(0) / dt), ((tx - lastTx).coerceAtLeast(0) / dt)))
                while (history.size > 60) history.removeFirst()
            }
            lastRx = rx
            lastTx = tx
            lastT = t
            tick++
        }
    }
    val down = pal.ansi.getOrElse(6) { pal.accent }
    val up = pal.ansi.getOrElse(5) { pal.accent2 }
    val latest = history.lastOrNull()
    Column(Modifier.fillMaxSize()) {
        Row {
            Text("↓ " + speed(latest?.down ?: 0f), color = down, fontSize = ts(14f), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("↑ " + speed(latest?.up ?: 0f), color = up, fontSize = ts(14f), fontWeight = FontWeight.SemiBold)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            tick
            val samples = history.toList()
            if (samples.size < 2) return@Canvas
            val max = samples.maxOf { maxOf(it.down, it.up) }.coerceAtLeast(16_000f)
            val step = size.width / 59f
            val x0 = size.width - (samples.size - 1) * step
            fun line(sel: (NetSample) -> Float, c: Color) {
                val p = Path()
                samples.forEachIndexed { i, s ->
                    val x = x0 + i * step
                    val y = size.height - sel(s) / max * size.height * 0.95f
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                val fill = Path().apply {
                    addPath(p)
                    lineTo(size.width, size.height)
                    lineTo(x0, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent)))
                drawPath(p, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            for (k in 1..3) drawLine(pal.muted.copy(alpha = 0.15f), Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4))
            line({ it.up }, up)
            line({ it.down }, down)
        }
    }
}

private fun speed(bps: Float): String = when {
    bps >= 1_000_000f -> "%.1f MB/s".format(bps / 1_000_000f)
    bps >= 1_000f -> "%.0f KB/s".format(bps / 1_000f)
    else -> "%.0f B/s".format(bps)
}

// ═══════════════════════════════════════════════════════════════════ greeting

@Composable
fun GreetingWidget(item: ForgeWidgetItem, pal: Pal) {
    val now = rememberNow(false)
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    val name = item.options.greetingName.trim()
    val hl = pal.role(item.look.highlight)
    val stops = pal.stops(item.look.highlight)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            buildString {
                append(part)
                if (name.isNotEmpty()) append(", ").append(name)
            },
            fontSize = ts(24f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(stops)),
        )
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)), color = hl.copy(alpha = 0.9f), fontSize = ts(13f))
    }
}

// ═══════════════════════════════════════════════════════════════════ search

@Composable
fun SearchWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val hl = pal.role(item.look.highlight)
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(item.look.radius.dp))
            .clickable { actions.openSearch() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = hl, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(item.options.searchHint.ifBlank { "Search" }, color = pal.muted, fontSize = ts(15f), maxLines = 1, modifier = Modifier.weight(1f))
    }
}

// ═══════════════════════════════════════════════════════════════════ battery

@Composable
fun BatteryWidget(item: ForgeWidgetItem, pal: Pal) {
    val context = LocalContext.current
    val b by SystemMonitor.battery.collectAsState()
    val green = pal.ansi.getOrElse(2) { Color(0xFF46A758) }
    val red = pal.ansi.getOrElse(1) { Color(0xFFE5484D) }
    val color = when {
        b.charging -> green
        b.level <= 15 -> red
        else -> pal.role(item.look.highlight)
    }
    val remaining by produceState(-1L, b.charging, b.level) {
        value = if (b.charging && Build.VERSION.SDK_INT >= 28) {
            runCatching { context.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining() }.getOrDefault(-1L)
        } else {
            -1L
        }
    }
    val pulse = rememberInfiniteTransition(label = "charge").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight)
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val sw = size.minDimension * 0.1f
                val inset = Offset(sw / 2, sw / 2)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(pal.muted.copy(alpha = 0.22f), -90f, 360f, false, inset, sz, style = Stroke(sw))
                drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))), -90f, 360f * b.level / 100f, false, inset, sz, style = Stroke(sw, cap = StrokeCap.Round))
                if (b.charging) {
                    val r = size.minDimension / 2 * (0.5f + 0.4f * pulse.value)
                    drawCircle(color.copy(alpha = 0.25f * (1f - pulse.value)), r)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${b.level}%", fontSize = (d.value * 0.2f).sp * LocalTextScale.current, fontWeight = FontWeight.SemiBold)
                val sub = when {
                    b.charging && remaining > 0 -> "full in " + duration(remaining)
                    b.charging -> "charging ⚡"
                    else -> "%.0f°C".format(b.tempC)
                }
                Text(sub, color = pal.muted, fontSize = (d.value * 0.08f).sp * LocalTextScale.current, maxLines = 1)
            }
        }
    }
}

private fun duration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

// ═══════════════════════════════════════════════════════════════════ picker thumbnails

@Composable
fun NewKindGlyph(kind: ForgeKind, pal: Pal) {
    when (kind) {
        ForgeKind.CALENDAR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("MMM", Locale.getDefault()).format(Date()).uppercase(), color = pal.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("d", Locale.getDefault()).format(Date()), color = pal.foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        ForgeKind.COUNTDOWN -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("68", "12", "05").forEach {
                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(pal.surface).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(it, color = pal.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        ForgeKind.TODO -> Column {
            listOf(true, false).forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (d) pal.accent else pal.muted.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(pal.foreground.copy(alpha = if (d) 0.4f else 0.8f)))
                }
                Spacer(Modifier.height(5.dp))
            }
        }
        ForgeKind.SHELF -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(pal.gradientAt(i / 3f))) }
        }
        ForgeKind.NETWORK -> Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val p = Path()
            for (i in 0..20) {
                val x = size.width * i / 20f
                val y = size.height * (0.5f + 0.35f * kotlin.math.sin(i * 0.7f))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, pal.ansi.getOrElse(6) { pal.accent }, style = Stroke(2.5f))
        }
        ForgeKind.GREETING -> Text("Hey!", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(pal.gradient)))
        ForgeKind.SEARCH -> Row(
            Modifier.clip(RoundedCornerShape(50)).background(pal.surface).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = pal.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(pal.muted))
        }
        ForgeKind.BATTERY -> Canvas(Modifier.size(38.dp)) {
            drawArc(pal.muted.copy(alpha = 0.3f), -90f, 360f, false, style = Stroke(5f))
            drawArc(pal.ansi.getOrElse(2) { pal.accent }, -90f, 260f, false, style = Stroke(5f, cap = StrokeCap.Round))
        }
        else -> Unit
    }
}
