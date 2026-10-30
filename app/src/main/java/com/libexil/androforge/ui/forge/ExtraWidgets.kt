package com.libexil.androforge.ui.forge

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.HabitItem
import com.libexil.androforge.live.NotifHub
import com.libexil.androforge.live.PermissionBus
import com.libexil.androforge.ui.Pal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos

private fun today(): Long = Calendar.getInstance().let { c ->
    (c.timeInMillis + c.timeZone.getOffset(c.timeInMillis)) / 86_400_000L
}

private fun readable(on: Color): Color =
    if (androidx.core.graphics.ColorUtils.calculateLuminance(on.toArgb()) > 0.5) Color(0xFF1B1B1F) else Color.White

// ═══════════════════════════════════════════════════════════════════ sticky note

@Composable
fun NoteWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val bg = lerp(pal.background, pal.role(item.options.noteColor), 0.55f)
    val ink = readable(bg)
    Box(
        Modifier
            .fillMaxSize()
            .clickable { actions.customize() },
    ) {
        Text(
            item.options.noteText,
            color = ink,
            fontSize = ts(15f),
            lineHeight = ts(20f),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════ photo

@Composable
fun PhotoWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val context = LocalContext.current
    val file = item.options.photoFile
    val image: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(File(File(context.filesDir, "widget_photos"), file).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val look = item.look
    val shape = RoundedCornerShape(look.radius.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(5.dp)
            .then(
                if (look.shadow) Modifier.background(Color.Black.copy(alpha = 0.18f), shape).padding(bottom = 2.dp) else Modifier,
            )
            .clip(shape)
            .background(pal.surface)
            .then(if (look.border) Modifier.border(2.dp, Brush.linearGradient(pal.stops(look.highlight)), shape) else Modifier)
            .clickable { if (image == null) actions.pickPhoto() else actions.customize() },
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(
                img, null,
                contentScale = if (item.options.photoFill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🖼", fontSize = 28.sp)
                Text("Tap to pick a photo", color = pal.muted, fontSize = ts(12f), textAlign = TextAlign.Center)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ world clocks

val worldZoneChoices = listOf(
    "America/Los_Angeles", "America/Denver", "America/Chicago", "America/New_York", "America/Sao_Paulo",
    "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Moscow", "Africa/Cairo", "Asia/Dubai",
    "Asia/Kolkata", "Asia/Singapore", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Pacific/Auckland", "Pacific/Honolulu",
)

fun zoneName(id: String): String = id.substringAfterLast('/').replace('_', ' ')

@Composable
fun WorldClockWidget(item: ForgeWidgetItem, pal: Pal) {
    val now = rememberNow(false)
    val context = LocalContext.current
    val twelve = !android.text.format.DateFormat.is24HourFormat(context)
    val zones = item.options.worldZones.take(4).ifEmpty { listOf(TimeZone.getDefault().id) }
    val local = Calendar.getInstance()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        zones.forEach { id ->
            val tz = TimeZone.getTimeZone(id)
            val cal = Calendar.getInstance(tz).apply { timeInMillis = now }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val day = h in 7..18
            val fmt = SimpleDateFormat(if (twelve) "h:mm" else "HH:mm", Locale.getDefault()).apply { timeZone = tz }
            val dayDiff = cal.get(Calendar.DAY_OF_YEAR) - local.get(Calendar.DAY_OF_YEAR)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(22.dp)) {
                    if (day) {
                        drawCircle(pal.ansi.getOrElse(3) { pal.accent }, size.minDimension * 0.3f)
                    } else {
                        drawCircle(pal.accent2, size.minDimension * 0.34f)
                        drawCircle(pal.background, size.minDimension * 0.3f, Offset(size.width * 0.66f, size.height * 0.4f))
                    }
                }
                Text(fmt.format(Date(now)), color = pal.foreground, fontSize = ts(20f), fontWeight = FontWeight.SemiBold)
                if (twelve) Text(if (h < 12) "AM" else "PM", color = pal.muted, fontSize = ts(9.5f))
                Text(zoneName(id), color = pal.role(item.look.highlight), fontSize = ts(11f), maxLines = 1)
                if (dayDiff != 0) Text(if (dayDiff > 0 || dayDiff < -300) "Tomorrow" else "Yesterday", color = pal.muted, fontSize = ts(9.5f))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ stopwatch & timer

private fun fmtElapsed(ms: Long): String {
    val t = ms / 10
    val cs = t % 100
    val s = t / 100 % 60
    val m = t / 6000 % 60
    val h = t / 360000
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%02d".format(m, s, cs)
}

@Composable
fun StopwatchWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val context = LocalContext.current
    val o = item.options
    val running = o.swStartedAt > 0
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(running) {
        while (running) {
            androidx.compose.runtime.withFrameMillis { tick++ }
        }
    }
    tick
    val elapsed = o.swAccumulated + if (running) System.currentTimeMillis() - o.swStartedAt else 0L
    val hl = pal.role(item.look.highlight)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtElapsed(elapsed), color = pal.foreground, fontSize = ts(30f), fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Pill(if (running) "Pause" else if (elapsed > 0) "Resume" else "Start", hl, Color.White) {
                actions.update {
                    if (running) it.copy(swStartedAt = 0L, swAccumulated = elapsed) else it.copy(swStartedAt = System.currentTimeMillis())
                }
            }
            if (elapsed > 0) {
                Spacer(Modifier.width(6.dp))
                Pill("Reset", pal.foreground.copy(alpha = 0.12f), pal.foreground) { actions.update { it.copy(swStartedAt = 0L, swAccumulated = 0L) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Timer", color = pal.muted, fontSize = ts(11f))
            o.timerPresets.take(4).forEach { min ->
                Pill("${min}m", pal.foreground.copy(alpha = 0.1f), pal.foreground) {
                    // The clock app runs the timer (so it rings), and the island shows it counting down.
                    runCatching {
                        context.startActivity(
                            Intent(AlarmClock.ACTION_SET_TIMER)
                                .putExtra(AlarmClock.EXTRA_LENGTH, min * 60)
                                .putExtra(AlarmClock.EXTRA_MESSAGE, "$min minute timer")
                                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(
        text, color = fg, fontSize = ts(12.5f), fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ═══════════════════════════════════════════════════════════════════ quote

private val quotes = listOf(
    "The best way out is always through." to "Robert Frost",
    "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
    "Well done is better than well said." to "Benjamin Franklin",
    "It always seems impossible until it's done." to "Nelson Mandela",
    "What we think, we become." to "Buddha",
    "The secret of getting ahead is getting started." to "Mark Twain",
    "Stay hungry, stay foolish." to "Stewart Brand",
    "Make it work, make it right, make it fast." to "Kent Beck",
    "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
    "Act as if what you do makes a difference. It does." to "William James",
    "Little by little, one travels far." to "J.R.R. Tolkien",
    "Fortune favors the bold." to "Virgil",
    "The journey of a thousand miles begins with one step." to "Lao Tzu",
    "Whatever you are, be a good one." to "Abraham Lincoln",
    "Imagination is more important than knowledge." to "Albert Einstein",
    "Not all those who wander are lost." to "J.R.R. Tolkien",
    "Dream big and dare to fail." to "Norman Vaughan",
    "Turn your wounds into wisdom." to "Oprah Winfrey",
    "Everything you can imagine is real." to "Pablo Picasso",
    "Quality is not an act, it is a habit." to "Aristotle",
    "Be yourself; everyone else is already taken." to "Oscar Wilde",
    "If you're going through hell, keep going." to "Winston Churchill",
    "The only way to do great work is to love what you do." to "Steve Jobs",
    "Talk is cheap. Show me the code." to "Linus Torvalds",
    "First, solve the problem. Then, write the code." to "John Johnson",
    "Done is better than perfect." to "Sheryl Sandberg",
    "Happiness depends upon ourselves." to "Aristotle",
    "In the middle of difficulty lies opportunity." to "Albert Einstein",
    "Keep your face always toward the sunshine." to "Walt Whitman",
    "Nothing will work unless you do." to "Maya Angelou",
)

@Composable
fun QuoteWidget(item: ForgeWidgetItem, pal: Pal) {
    var extra by remember { mutableIntStateOf(0) }
    val (q, who) = quotes[((today() + extra) % quotes.size).toInt()]
    Column(
        Modifier.fillMaxSize().clickable { extra++ },
        verticalArrangement = Arrangement.Center,
    ) {
        Text("“", color = pal.role(item.look.highlight), fontSize = ts(34f), fontWeight = FontWeight.Bold, modifier = Modifier.height(26.dp))
        Text(q, color = pal.foreground, fontSize = ts(16f), fontStyle = FontStyle.Italic, lineHeight = ts(21f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text("— $who", color = pal.muted, fontSize = ts(12f), modifier = Modifier.padding(top = 6.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════ moon

private const val SYNODIC = 29.530588853

/** 0 = new moon, 0.5 = full. */
fun moonPhase(timeMs: Long = System.currentTimeMillis()): Double {
    val knownNew = 947182440000L // 2000-01-06 18:14 UTC
    val days = (timeMs - knownNew) / 86_400_000.0
    return ((days / SYNODIC) % 1.0 + 1.0) % 1.0
}

private fun phaseName(p: Double): String = when {
    p < 0.03 || p > 0.97 -> "New moon"
    p < 0.22 -> "Waxing crescent"
    p < 0.28 -> "First quarter"
    p < 0.47 -> "Waxing gibbous"
    p < 0.53 -> "Full moon"
    p < 0.72 -> "Waning gibbous"
    p < 0.78 -> "Last quarter"
    else -> "Waning crescent"
}

@Composable
fun MoonWidget(item: ForgeWidgetItem, pal: Pal) {
    val now = rememberNow(false)
    val p = moonPhase(now)
    val lit = (1 - cos(2 * PI * p)) / 2
    val untilFull = ((0.5 - p + 1.0) % 1.0) * SYNODIC
    val nextFull = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now + (untilFull * 86_400_000).toLong()))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.3f
        val moon: @Composable () -> Unit = {
            Canvas(Modifier.size(minOf(maxWidth, maxHeight) * if (wide) 0.9f else 0.55f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF2A2A33), r, c)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6D8), Color(0xFFD9CFA8)), c, r), r, c, alpha = 0.18f)
                // Lit part: a half disc plus an ellipse that grows/shrinks with the phase.
                val waxing = p < 0.5
                val k = cos(2 * PI * p).toFloat() // 1 new .. -1 full
                val path = Path().apply {
                    val steps = 48
                    for (i in 0..steps) {
                        val t = PI.toFloat() * i / steps - PI.toFloat() / 2
                        val x = c.x + (if (waxing) 1 else -1) * r * cos(t)
                        val y = c.y + r * kotlin.math.sin(t)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    for (i in steps downTo 0) {
                        val t = PI.toFloat() * i / steps - PI.toFloat() / 2
                        val x = c.x + (if (waxing) 1 else -1) * r * k * cos(t)
                        val y = c.y + r * kotlin.math.sin(t)
                        lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, Brush.radialGradient(listOf(Color(0xFFFFF6D8), Color(0xFFD9CFA8)), c, r))
                drawCircle(pal.accent.copy(alpha = 0.25f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            }
        }
        val text: @Composable () -> Unit = {
            Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
                Text(phaseName(p), color = pal.foreground, fontSize = ts(13.5f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${(lit * 100).toInt()}% lit", color = pal.muted, fontSize = ts(11.5f))
                Text("Full: $nextFull", color = pal.role(item.look.highlight), fontSize = ts(11f), maxLines = 1)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                moon()
                Spacer(Modifier.width(12.dp))
                text()
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                moon()
                text()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ habits

@Composable
fun HabitWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val day = today()
    val hl = pal.role(item.look.highlight)
    val days = (6 downTo 0).map { day - it }
    val labels = days.map { d -> SimpleDateFormat("EEEEE", Locale.getDefault()).format(Date(d * 86_400_000L + 43_200_000L)) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            labels.forEachIndexed { i, l ->
                Text(l, color = if (i == 6) hl else pal.muted, fontSize = ts(10f), textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
            }
        }
        item.options.habits.take(5).forEachIndexed { hi, h ->
            val streak = streak(h, day)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.name, color = pal.foreground, fontSize = ts(13f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (streak > 1) Text("🔥 $streak days", color = pal.muted, fontSize = ts(9.5f))
                }
                days.forEach { d ->
                    val done = d in h.done
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (done) hl else pal.foreground.copy(alpha = 0.1f))
                                .then(if (d == day && !done) Modifier.border(1.5.dp, hl.copy(alpha = 0.7f), CircleShape) else Modifier)
                                .clickable {
                                    actions.update { o ->
                                        o.copy(habits = o.habits.mapIndexed { i, x ->
                                            if (i != hi) x else x.copy(done = if (d in x.done) x.done - d else (x.done + d).filter { it > day - 60 })
                                        })
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (done) Text("✓", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

private fun streak(h: HabitItem, today: Long): Int {
    var d = if (today in h.done) today else today - 1
    var n = 0
    while (d in h.done) {
        n++
        d--
    }
    return n
}

// ═══════════════════════════════════════════════════════════════════ notifications

@Composable
fun NotifsWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val context = LocalContext.current
    val entries by NotifHub.entries.collectAsState()
    val perm by PermissionBus.version.collectAsState()
    val access = remember(perm) { com.libexil.androforge.live.MediaHub.accessGranted(context) }
    if (!access) {
        Box(Modifier.fillMaxSize().clickable { actions.openNotificationAccess() }, contentAlignment = Alignment.Center) {
            Text("Tap to allow notification access", color = pal.muted, fontSize = ts(12.5f))
        }
        return
    }
    val list = entries.filter { it.clearable }.take(item.options.notifMax.coerceIn(1, 10))
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("All caught up ✨", color = pal.muted, fontSize = ts(13f)) }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { NotifHub.open(e, context) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (e.color != 0) Color(e.color) else pal.accent), contentAlignment = Alignment.Center) {
                    when {
                        e.largeIcon != null -> Image(e.largeIcon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        e.smallIcon != null -> Image(e.smallIcon, null, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = pal.foreground, fontSize = ts(12.5f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.text.isNotBlank()) Text(e.text, color = pal.muted, fontSize = ts(11.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(e.postTime)),
                    color = pal.muted, fontSize = ts(10f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ agenda

private class Event(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

@Composable
fun AgendaWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val context = LocalContext.current
    val perm by PermissionBus.version.collectAsState()
    val granted = remember(perm) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    if (!granted) {
        Box(Modifier.fillMaxSize().clickable { actions.requestPermission(Manifest.permission.READ_CALENDAR) }, contentAlignment = Alignment.Center) {
            Text("Tap to show your calendar", color = pal.muted, fontSize = ts(12.5f))
        }
        return
    }
    val now = rememberNow(false)
    val minute = now / 60_000
    val days = item.options.agendaDays.coerceIn(1, 14)
    val events by produceState(emptyList<Event>(), minute / 5, days) {
        value = withContext(Dispatchers.IO) { loadEvents(context, days) }
    }
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing coming up", color = pal.muted, fontSize = ts(13f)) }
        return
    }
    val dayFmt = SimpleDateFormat("EEE d", Locale.getDefault())
    val timeFmt = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        var lastDay = ""
        events.take(8).forEach { e ->
            val d = dayFmt.format(Date(e.begin))
            if (d != lastDay) {
                lastDay = d
                Text(if (android.text.format.DateUtils.isToday(e.begin)) "Today" else d, color = pal.role(item.look.highlight), fontSize = ts(10.5f), fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(if (e.color != 0) Color(e.color or 0xFF000000.toInt()) else pal.accent))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = pal.foreground, fontSize = ts(12.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (e.allDay) "All day" else timeFmt.format(Date(e.begin)) + " – " + timeFmt.format(Date(e.end)), color = pal.muted, fontSize = ts(10.5f))
                }
            }
        }
    }
}

private fun loadEvents(context: android.content.Context, days: Int): List<Event> = try {
    val start = System.currentTimeMillis()
    val end = start + days * 86_400_000L
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, start)
        ContentUris.appendId(it, end)
    }.build()
    val out = ArrayList<Event>()
    context.contentResolver.query(
        uri,
        arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        ),
        null, null, CalendarContract.Instances.BEGIN + " ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            out += Event(c.getLong(0), c.getString(1) ?: "(No title)", c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getInt(5))
        }
    }
    out.filter { it.end > start }
} catch (e: Exception) {
    emptyList()
}

// ═══════════════════════════════════════════════════════════════════ picker glyphs

@Composable
fun ExtraKindGlyph(kind: ForgeKind, pal: Pal) {
    when (kind) {
        ForgeKind.NOTE -> Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(lerp(pal.background, pal.role(com.libexil.androforge.data.ColorRole.YELLOW), 0.6f)), contentAlignment = Alignment.Center) {
            Text("Aa", color = Color(0xFF1B1B1F), fontSize = 14.sp)
        }
        ForgeKind.PHOTO -> Box(Modifier.size(width = 44.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(pal.gradient)))
        ForgeKind.WORLD_CLOCK -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("9:41", "14:41").forEach { Text(it, color = pal.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        ForgeKind.STOPWATCH -> Text("00:42.7", color = pal.foreground, fontSize = 15.sp, fontWeight = FontWeight.Light)
        ForgeKind.QUOTE -> Text("“”", color = pal.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        ForgeKind.MOON -> Canvas(Modifier.size(34.dp)) {
            drawCircle(Color(0xFF2A2A33))
            drawCircle(Color(0xFFFFF6D8), size.minDimension / 2.2f, Offset(size.width * 0.62f, size.height / 2))
        }
        ForgeKind.HABIT -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (i < 3) pal.accent else pal.muted.copy(alpha = 0.4f))) }
        }
        ForgeKind.NOTIFS -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(width = 44.dp, height = 7.dp).clip(RoundedCornerShape(3.dp)).background(pal.foreground.copy(alpha = 0.5f - it * 0.12f))) }
        }
        ForgeKind.AGENDA -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 9.dp).background(pal.gradientAt(i / 2f)))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(pal.foreground.copy(alpha = 0.5f)))
                }
            }
        }
        else -> Unit
    }
}

@Suppress("unused")
private val unusedFill = Modifier.fillMaxHeight()
