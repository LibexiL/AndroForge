@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.libexil.androforge.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.ArtStyle
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.IslandColor
import com.libexil.androforge.data.IslandIdle
import com.libexil.androforge.data.IslandTap
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.VizStyle
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.live.BatteryInfo
import com.libexil.androforge.live.CallState
import com.libexil.androforge.live.LiveKind
import com.libexil.androforge.live.EventKind
import com.libexil.androforge.live.IslandEvent
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.MediaInfo
import com.libexil.androforge.live.NotifAction
import com.libexil.androforge.live.NotifEntry
import com.libexil.androforge.live.NotifHub
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.live.WeatherRepo
import com.libexil.androforge.live.WeatherState
import com.libexil.androforge.ui.Pal
import com.libexil.androforge.ui.Motion
import com.libexil.androforge.ui.forge.AlbumArt
import com.libexil.androforge.ui.forge.Transport
import com.libexil.androforge.ui.forge.TransportIcon
import com.libexil.androforge.ui.forge.VisualizerCanvas
import com.libexil.androforge.ui.forge.WeatherIcon
import com.libexil.androforge.ui.forge.rememberNow
import com.libexil.androforge.ui.forge.rememberViz
import com.libexil.androforge.ui.rememberPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Mode { HIDDEN, IDLE, MUSIC, LIVE, CALL, EVENT, PEEK, INCOMING, PLAYER, GLANCE, LIVECARD }
private enum class Panel { NONE, PLAYER, GLANCE, LIVE }

/** The color the island uses for an app: the one you picked, or (optionally) the app's own. */
private fun appTint(pkg: String?, color: Int, s: LauncherSettings): Color? {
    if (pkg == null) return null
    s.islandAppColors[pkg]?.let { return Color(it) }
    if (!s.islandUseAppColor || color == 0) return null
    // Keep app colors readable on a dark island.
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
    hsl[2] = hsl[2].coerceIn(0.5f, 0.72f)
    hsl[1] = hsl[1].coerceAtLeast(0.35f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

private val Green = Color(0xFF30A46C)
private val Red = Color(0xFFE5484D)

/** Colors the island draws with. */
private class IslandColors(val bg: Brush, val solid: Color, val fg: Color, val muted: Color)

@Composable
fun IslandContent(service: AccessibilityService, geo: IslandGeometry) {
    val state by LauncherApp.instance.store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
    val motion = remember(s.animations, s.animScale, s.animBounce) { Motion.of(s) }
    val media by MediaHub.state.collectAsState()
    val peekPair by NotifHub.peek.collectAsState()
    val entries by NotifHub.entries.collectAsState()
    val event by SystemMonitor.event.collectAsState()
    val vizStatus by AudioViz.status.collectAsState()
    var panel by remember { mutableStateOf(Panel.NONE) }
    var replyKey by remember { mutableStateOf<String?>(null) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    fun touched() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    SideEffect { geo.wantsFocus.value = replyKey != null }

    // Tapping outside closes panels (but not while typing a reply - the keyboard counts as "outside").
    LaunchedEffect(geo.collapseTick.intValue) {
        if (replyKey == null) panel = Panel.NONE
    }
    LaunchedEffect(panel, lastTouch) {
        if (panel != Panel.NONE) {
            delay(if (panel == Panel.GLANCE) 12_000 else 8_000)
            panel = Panel.NONE
        }
    }
    val peek = peekPair?.first
    LaunchedEffect(peek?.key, peekPair?.second, lastTouch, replyKey) {
        val p = peekPair ?: return@LaunchedEffect
        if (replyKey != null) return@LaunchedEffect
        val shownFor = SystemClock.elapsedRealtime() - maxOf(p.second, lastTouch)
        delay((s.islandPeekSeconds * 1000L - shownFor).coerceAtLeast(0))
        NotifHub.clearPeek(p.first.key)
    }
    LaunchedEffect(peek?.key) { if (peek == null || peek.key != replyKey) replyKey = null }
    LaunchedEffect(event?.at) {
        val e = event ?: return@LaunchedEffect
        delay(if (e.kind == EventKind.HEADPHONES) 4000 else if (e.kind == EventKind.CHARGING && s.islandChargeAnimation) 3400 else 2800)
        if (SystemMonitor.event.value?.at == e.at) SystemMonitor.clearEvent()
    }

    val m = media
    val musicOn = s.islandMusic && m != null && m.playing
    val incoming = if (s.islandCalls) entries.firstOrNull { it.call == CallState.INCOMING || it.urgent } else null
    val ongoingCall = if (s.islandCalls) entries.firstOrNull { it.call == CallState.ONGOING } else null
    val live = if (s.islandLiveActivities) entries.firstOrNull { it.isLiveActivity } else null
    val ev = event?.takeIf {
        when (it.kind) {
            EventKind.CHARGING, EventKind.UNPLUGGED, EventKind.BATTERY_LOW -> s.islandCharging
            else -> s.islandDeviceAlerts
        }
    }
    val mode = when {
        !geo.visible.value -> Mode.HIDDEN
        incoming != null -> Mode.INCOMING
        panel == Panel.PLAYER && m != null -> Mode.PLAYER
        panel == Panel.GLANCE -> Mode.GLANCE
        panel == Panel.LIVE && live != null -> Mode.LIVECARD
        peek != null && s.islandNotifications -> Mode.PEEK
        ev != null -> Mode.EVENT
        ongoingCall != null -> Mode.CALL
        live != null -> Mode.LIVE
        musicOn -> Mode.MUSIC
        s.islandIdle == IslandIdle.HIDDEN -> Mode.HIDDEN
        else -> Mode.IDLE
    }
    LaunchedEffect(m == null) { if (m == null && panel == Panel.PLAYER) panel = Panel.NONE }
    LaunchedEffect(live == null) { if (live == null && panel == Panel.LIVE) panel = Panel.NONE }

    if (mode == Mode.HIDDEN) {
        Box(Modifier.size(1.dp))
        return
    }

    val density = LocalDensity.current
    val sbDp = with(density) { geo.statusBarPx.intValue.toDp() }
    val h = s.islandHeightDp.dp
    val topPad = ((sbDp - h) / 2).coerceAtLeast(2.dp)
    val maxW = with(density) { (geo.screenWidthPx.intValue.toDp() - 20.dp).coerceAtLeast(220.dp) }.coerceAtMost(520.dp)
    val vizOk = s.islandVisualizer && vizStatus != AudioViz.Status.NO_PERMISSION && vizStatus != AudioViz.Status.UNSUPPORTED
    val colors = when (s.islandColor) {
        IslandColor.BLACK -> IslandColors(SolidColor(Color.Black), Color.Black, Color.White, Color.White.copy(alpha = 0.6f))
        IslandColor.PALETTE -> IslandColors(SolidColor(pal.background), pal.background, pal.foreground, pal.muted)
        IslandColor.GLASS -> IslandColors(
            Brush.verticalGradient(listOf(lerp(pal.surface, Color.White, 0.08f).copy(alpha = 0.82f), pal.background.copy(alpha = 0.72f))),
            pal.background, pal.foreground, pal.muted,
        )
        IslandColor.GRADIENT -> IslandColors(
            Brush.linearGradient(listOf(lerp(pal.background, pal.accent, 0.28f), pal.background, lerp(pal.background, pal.accent2, 0.22f))),
            pal.background, pal.foreground, pal.muted,
        )
    }
    val charging = mode == Mode.EVENT && ev?.kind == EventKind.CHARGING && s.islandChargeAnimation
    val big = mode in setOf(Mode.PEEK, Mode.INCOMING, Mode.PLAYER, Mode.GLANCE, Mode.LIVECARD) || charging
    val glow = s.islandGlow && mode != Mode.IDLE
    val focusApp = when (mode) {
        Mode.PEEK -> peek?.let { appTint(it.pkg, it.color, s) }
        Mode.LIVE, Mode.LIVECARD -> live?.let { appTint(it.pkg, it.color, s) }
        Mode.INCOMING -> incoming?.let { appTint(it.pkg, it.color, s) }
        Mode.MUSIC, Mode.PLAYER -> m?.pkg?.let { pkg -> s.islandAppColors[pkg]?.let { Color(it) } }
        else -> null
    }

    // Second bubble beside the pill (like iOS when two things are running at once).
    val bubbleMusic = s.islandBubble && musicOn && (mode == Mode.CALL || mode == Mode.LIVE)
    val bubbleLive = s.islandBubble && mode == Mode.MUSIC && (live ?: ongoingCall) != null
    val bubbleEntry = if (bubbleLive) (ongoingCall ?: live) else null

    Row(Modifier.padding(top = topPad, start = 10.dp, end = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        IslandMorph(
            corner = if (big) 30.dp else h / 2,
            bg = colors.bg,
            border = if (!glow) null else if (focusApp != null) SolidColor(focusApp.copy(alpha = 0.85f)) else Brush.linearGradient(pal.gradient.map { it.copy(alpha = 0.75f) }),
            motion = motion,
            modifier = Modifier
                .pointerInput(mode, m?.pkg, peek?.key, live?.key, ongoingCall?.key) {
                    detectTapGestures(
                        onTap = {
                            touched()
                            when (mode) {
                                Mode.IDLE -> {
                                    if (s.islandTapAction == IslandTap.GLANCE) {
                                        panel = Panel.GLANCE
                                    } else {
                                        geo.openShade()
                                    }
                                }
                                Mode.MUSIC -> panel = Panel.PLAYER
                                Mode.CALL -> ongoingCall?.let { NotifHub.open(it, context) }
                                Mode.LIVE -> panel = Panel.LIVE
                                Mode.LIVECARD -> live?.let { NotifHub.open(it, context) }
                                Mode.PEEK -> peek?.let { NotifHub.open(it, context) }
                                Mode.EVENT -> SystemMonitor.clearEvent()
                                Mode.INCOMING -> incoming?.let { NotifHub.send(it.contentIntent, context) }
                                else -> Unit
                            }
                        },
                        onLongPress = {
                            when {
                                mode == Mode.MUSIC || mode == Mode.PLAYER -> MediaHub.openPlayer(context)
                                mode == Mode.IDLE -> geo.openShade()
                            }
                        },
                    )
                }
                .pointerInput(mode) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, d -> total += d },
                        onDragEnd = {
                            touched()
                            if (total > 40f) {
                                when (mode) {
                                    Mode.MUSIC -> panel = Panel.PLAYER
                                    Mode.LIVE -> panel = Panel.LIVE
                                    Mode.IDLE, Mode.CALL, Mode.EVENT -> panel = Panel.GLANCE
                                    else -> geo.openShade()
                                }
                            } else if (total < -40f) {
                                panel = Panel.NONE
                                replyKey = null
                                peek?.let { NotifHub.clearPeek(it.key) }
                                SystemMonitor.clearEvent()
                            }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = mode,
                modifier = Modifier.widthIn(max = maxW),
                transitionSpec = {
                    (fadeIn(motion.tween(200, delay = 90)) togetherWith fadeOut(motion.tween(90)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                contentAlignment = Alignment.TopCenter,
                label = "island",
            ) { md ->
                when (md) {
                    Mode.IDLE -> IdlePill(s, h)
                    Mode.MUSIC -> m?.let { MusicPill(it, s, h, pal, colors, vizOk) } ?: IdlePill(s, h)
                    Mode.CALL -> ongoingCall?.let { CallPill(it, s, h, colors) } ?: IdlePill(s, h)
                    Mode.LIVE -> live?.let { LivePill(it, s, h, pal, colors, appTint(it.pkg, it.color, s)) } ?: IdlePill(s, h)
                    Mode.LIVECARD -> live?.let { LiveCard(it, pal, colors, maxW, appTint(it.pkg, it.color, s), ::touched) { panel = Panel.NONE } } ?: IdlePill(s, h)
                    Mode.EVENT -> ev?.let { if (charging) ChargingCard(it, h, pal, colors) else EventPill(it, h, pal, colors) } ?: IdlePill(s, h)
                    Mode.PEEK -> peek?.let { p ->
                        PeekCard(
                            p, s, pal, colors, maxW,
                            replying = replyKey == p.key,
                            onReply = { replyKey = if (replyKey == p.key) null else p.key; touched() },
                            onDone = {
                                replyKey = null
                                NotifHub.clearPeek(p.key)
                            },
                            touched = ::touched,
                        )
                    } ?: IdlePill(s, h)
                    Mode.INCOMING -> incoming?.let { IncomingCard(it, pal, colors, maxW) } ?: IdlePill(s, h)
                    Mode.PLAYER -> m?.let { PlayerCard(it, pal, colors, maxW, vizOk, ::touched) } ?: IdlePill(s, h)
                    Mode.GLANCE -> GlancePanel(state.items, pal, colors, maxW, entries, ::touched) { panel = Panel.NONE }
                    Mode.HIDDEN -> Box(Modifier.size(1.dp))
                }
            }
        }
        AnimatedVisibility(bubbleMusic || bubbleEntry != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(h)
                    .clip(CircleShape)
                    .background(colors.bg)
                    .clickable {
                        touched()
                        if (bubbleMusic) panel = Panel.PLAYER else bubbleEntry?.let { NotifHub.open(it, context) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bubbleMusic && m != null) {
                    AlbumArt(m.art, ArtStyle.RECORD, m.playing, pal, h - 6.dp)
                } else {
                    bubbleEntry?.let { e ->
                        if (e.call == CallState.ONGOING) {
                            Icon(Icons.Default.Call, null, tint = Green, modifier = Modifier.size(h * 0.55f))
                        } else {
                            e.smallIcon?.let { Image(it, null, colorFilter = ColorFilter.tint(colors.fg), modifier = Modifier.size(h * 0.5f)) }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ morph

/**
 * The island's shape. The overlay window resizes only twice per transition (grow at the start,
 * shrink at the end); in between, the rounded background and its clip animate in the draw phase,
 * so nothing is re-laid-out per frame.
 */
@Composable
private fun IslandMorph(corner: Dp, bg: Brush, border: Brush?, motion: Motion, modifier: Modifier, content: @Composable () -> Unit) {
    var target by remember { mutableStateOf(IntSize.Zero) }
    var hold by remember { mutableStateOf(IntSize.Zero) }
    val w = remember { Animatable(0f) }
    val hh = remember { Animatable(0f) }
    val radius = animateDpAsState(corner, motion.spring(stiffness = 600f), label = "radius")
    LaunchedEffect(target) {
        if (target == IntSize.Zero) return@LaunchedEffect
        if (w.value == 0f) {
            w.snapTo(target.width.toFloat())
            hh.snapTo(target.height.toFloat())
            hold = target
            return@LaunchedEffect
        }
        hold = IntSize(maxOf(hold.width, target.width), maxOf(hold.height, target.height))
        coroutineScope {
            launch { w.animateTo(target.width.toFloat(), motion.spring(stiffness = 520f)) }
            launch { hh.animateTo(target.height.toFloat(), motion.spring(stiffness = 520f)) }
        }
        hold = target
    }
    Layout(
        content = { Box(Modifier.onSizeChanged { target = it }) { content() } },
        modifier = modifier.drawWithContent {
            val rw = w.value.coerceAtLeast(1f).coerceAtMost(size.width)
            val rh = hh.value.coerceAtLeast(1f).coerceAtMost(size.height)
            val left = (size.width - rw) / 2f
            val r = radius.value.toPx().coerceAtMost(rh / 2f)
            val path = Path().apply {
                addRoundRect(RoundRect(left, 0f, left + rw, rh, CornerRadius(r, r)))
            }
            drawPath(path, bg)
            clipPath(path) { this@drawWithContent.drawContent() }
            if (border != null) drawPath(path, border, style = Stroke(1.dp.toPx()))
        },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val lw = maxOf(hold.width, p.width)
        val lh = maxOf(hold.height, p.height)
        layout(lw, lh) { p.place((lw - p.width) / 2, 0) }
    }
}

// ═══════════════════════════════════════════════════════════════════ pills

@Composable
private fun IdlePill(s: LauncherSettings, h: Dp) {
    when (s.islandIdle) {
        IslandIdle.DOT -> Box(Modifier.size(h))
        else -> Box(Modifier.size(width = s.islandWidthDp.dp, height = h))
    }
}

@Composable
private fun MusicPill(m: MediaInfo, s: LauncherSettings, h: Dp, pal: Pal, c: IslandColors, vizOk: Boolean) {
    val frame = rememberViz(s.islandVizBars.coerceIn(4, 32), active = vizOk)
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
                frame, s.islandVizStyle, pal.stops(ColorRole.GRADIENT),
                Modifier
                    .width(if (s.islandVizStyle == VizStyle.ORB || s.islandVizStyle == VizStyle.RADIAL) 30.dp else (30 + s.islandVizBars * 2).coerceAtMost(80).dp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
                gap = 0.4f, peaks = false, minLevel = 0.12f,
            )
        } else {
            EqualizerBars(pal, Modifier.width(26.dp).fillMaxHeight().padding(vertical = 8.dp))
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** Fallback "now playing" bars when the real visualizer isn't allowed. */
@Composable
private fun EqualizerBars(pal: Pal, modifier: Modifier) {
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

private fun clock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun chrono(e: NotifEntry): String? {
    val base = e.chronoBase ?: return null
    val now = rememberNow(true)
    return clock(if (e.countDown) base - now else now - base)
}

@Composable
private fun CallPill(e: NotifEntry, s: LauncherSettings, h: Dp, c: IslandColors) {
    Row(
        Modifier
            .height(h)
            .width((s.islandWidthDp + 110).dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(h - 10.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size((h - 10.dp) * 0.6f))
        }
        Spacer(Modifier.width(8.dp))
        Text(e.title, color = c.fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(chrono(e) ?: "Call", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LivePill(e: NotifEntry, s: LauncherSettings, h: Dp, pal: Pal, c: IslandColors, tint: Color?) {
    val accent = tint ?: if (e.color != 0) Color(e.color) else pal.accent
    val time = chrono(e)
    val kind = e.liveKind
    Row(
        Modifier
            .height(h)
            .width((s.islandWidthDp + 130).dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(h - 8.dp), contentAlignment = Alignment.Center) {
            val p = e.progress
            when {
                kind == LiveKind.NAVIGATION && e.largeIcon != null ->
                    Image(e.largeIcon, null, colorFilter = ColorFilter.tint(accent), modifier = Modifier.size(h - 8.dp))
                p != null && p >= 0f -> {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.minDimension * 0.12f
                        drawArc(c.muted.copy(alpha = 0.3f), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        drawArc(accent, -90f, 360f * p, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    e.smallIcon?.let { Image(it, null, colorFilter = ColorFilter.tint(accent), modifier = Modifier.size((h - 8.dp) * 0.5f)) }
                }
                kind == LiveKind.TIMER || kind == LiveKind.STOPWATCH -> {
                    // A ring that ticks once a second, like a clock face.
                    val now = rememberNow(true)
                    val sec = ((now / 1000) % 60).toInt()
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.minDimension * 0.12f
                        drawArc(c.muted.copy(alpha = 0.25f), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        val sweep = if (kind == LiveKind.TIMER) 360f * (60 - sec) / 60f else 360f * sec / 60f
                        drawArc(accent, -90f, sweep, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    e.smallIcon?.let { Image(it, null, colorFilter = ColorFilter.tint(accent), modifier = Modifier.size((h - 8.dp) * 0.48f)) }
                }
                kind == LiveKind.RECORDING -> {
                    val blink = rememberNow(true) / 1000 % 2 == 0L
                    Box(Modifier.size((h - 8.dp) * 0.45f).clip(CircleShape).background(Red.copy(alpha = if (blink) 1f else 0.4f)))
                }
                else -> e.smallIcon?.let { Image(it, null, colorFilter = ColorFilter.tint(accent), modifier = Modifier.size((h - 8.dp) * 0.6f)) }
            }
        }
        Spacer(Modifier.width(8.dp))
        val middle = when (kind) {
            LiveKind.NAVIGATION -> e.text.ifBlank { e.title }
            LiveKind.DELIVERY -> e.title + if (e.text.isNotBlank()) " \u00b7 " + e.text else ""
            else -> e.title
        }
        Text(
            middle, color = c.fg, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
        )
        Spacer(Modifier.width(6.dp))
        val right = when (kind) {
            LiveKind.NAVIGATION -> e.title.takeIf { e.text.isNotBlank() }?.take(12) ?: ""
            else -> time ?: e.progress?.let { if (it < 0f) "\u2026" else "${(it * 100).toInt()}%" } ?: ""
        }
        Text(right, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A live activity opened up: big readout, details and its buttons (pause, lap, stop\u2026). */
@Composable
private fun LiveCard(e: NotifEntry, pal: Pal, c: IslandColors, maxW: Dp, tint: Color?, touched: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val accent = tint ?: if (e.color != 0) Color(e.color) else pal.accent
    val time = chrono(e)
    val kind = e.liveKind
    Column(Modifier.width(maxW).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                val icon = if (kind == LiveKind.NAVIGATION) e.largeIcon ?: e.smallIcon else e.smallIcon
                icon?.let { Image(it, null, colorFilter = ColorFilter.tint(accent), modifier = Modifier.size(if (kind == LiveKind.NAVIGATION) 40.dp else 26.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.appName.uppercase(), color = c.muted, fontSize = 10.5.sp, letterSpacing = 1.sp, maxLines = 1)
                Text(e.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.text.isNotBlank()) Text(e.text, color = c.fg.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.subText?.takeIf { it != e.text }?.let { Text(it, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (time != null) {
                Spacer(Modifier.width(8.dp))
                Text(time, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        val p = e.progress
        if (p != null) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.muted.copy(alpha = 0.25f))) {
                if (p >= 0f) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(accent))
                } else {
                    val now = rememberNow(true)
                    val x = (now / 1000 % 3) / 3f
                    Box(Modifier.fillMaxWidth((x + 0.3f).coerceAtMost(1f)).fillMaxHeight().background(accent.copy(alpha = 0.7f)))
                }
            }
        }
        val actions = e.actions.filter { !it.isReply }.take(3)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { a ->
                    ActionChip(a.title, accent.copy(alpha = 0.25f), c.fg) {
                        touched()
                        NotifHub.runAction(a, context)
                    }
                }
                ActionChip("Open", c.fg.copy(alpha = 0.12f), c.fg) {
                    NotifHub.open(e, context)
                    onClose()
                }
            }
        }
    }
}

/** Plugging in: a battery that fills up with a moving liquid edge. */
@Composable
private fun ChargingCard(e: IslandEvent, h: Dp, pal: Pal, c: IslandColors) {
    val green = pal.ansi.getOrElse(2) { Green }
    val level = (e.value ?: 0).coerceIn(0, 100)
    val fill = remember { Animatable(0f) }
    LaunchedEffect(e.at) {
        fill.snapTo(0f)
        fill.animateTo(level / 100f, tween(1400))
    }
    val now = rememberNow(false)
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { phase = (it / 1_000_000_000.0 * 3.0).toFloat() }
        }
    }
    Row(
        Modifier
            .height(h * 2.1f)
            .widthIn(min = 250.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 58.dp, height = 30.dp)) {
            val bw = size.width * 0.9f
            val r = CornerRadius(size.height * 0.25f)
            drawRoundRect(c.fg.copy(alpha = 0.6f), Offset.Zero, Size(bw, size.height), r, style = Stroke(2.5f))
            drawRoundRect(c.fg.copy(alpha = 0.6f), Offset(bw + 2f, size.height * 0.32f), Size(size.width - bw - 2f, size.height * 0.36f), CornerRadius(3f))
            val inset = 4f
            val fw = (bw - inset * 2) * fill.value
            val path = Path()
            val top = inset
            val bottom = size.height - inset
            path.moveTo(inset, top)
            // Wavy front edge.
            val steps = 12
            for (i in 0..steps) {
                val y = top + (bottom - top) * i / steps
                val x = inset + fw + kotlin.math.sin(phase + i * 0.9f) * 3f
                path.lineTo(x.coerceAtLeast(inset), y)
            }
            path.lineTo(inset, bottom)
            path.close()
            clipPath(Path().apply { addRoundRect(RoundRect(inset, inset, bw - inset, size.height - inset, CornerRadius(size.height * 0.18f))) }) {
                drawPath(path, Brush.horizontalGradient(listOf(green.copy(alpha = 0.7f), green)))
            }
            // Bolt
            val bx = bw / 2
            val by = size.height / 2
            val bolt = Path().apply {
                moveTo(bx + 2f, by - 11f)
                lineTo(bx - 6f, by + 2f)
                lineTo(bx - 0.5f, by + 2f)
                lineTo(bx - 2f, by + 11f)
                lineTo(bx + 6f, by - 2f)
                lineTo(bx + 0.5f, by - 2f)
                close()
            }
            drawPath(bolt, Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(e.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)), color = c.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text("${(fill.value * 100).toInt()}%", color = green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventPill(e: IslandEvent, h: Dp, pal: Pal, c: IslandColors) {
    val green = pal.ansi.getOrElse(2) { Green }
    val red = pal.ansi.getOrElse(1) { Red }
    val tint = when (e.kind) {
        EventKind.CHARGING -> green
        EventKind.BATTERY_LOW -> red
        EventKind.SILENT -> red
        EventKind.HEADPHONES -> pal.accent
        else -> c.fg
    }
    Row(
        Modifier
            .height(h)
            .widthIn(min = 200.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (e.kind) {
            EventKind.CHARGING, EventKind.UNPLUGGED, EventKind.BATTERY_LOW ->
                BatteryGlyph(e.value ?: 0, e.kind == EventKind.CHARGING, tint, Modifier.size(width = 24.dp, height = 12.dp))
            EventKind.HEADPHONES, EventKind.HEADPHONES_OFF -> HeadphonesGlyph(tint, Modifier.size(18.dp))
            EventKind.SILENT, EventKind.VIBRATE, EventKind.RING -> Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Notifications, null, tint = tint, modifier = Modifier.size(18.dp))
                if (e.kind == EventKind.SILENT) {
                    Canvas(Modifier.size(18.dp)) { drawLine(tint, Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), strokeWidth = 3f, cap = StrokeCap.Round) }
                }
            }
            EventKind.TORCH_ON, EventKind.TORCH_OFF -> Text("🔦", fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(e.title, color = c.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        val right = when {
            e.value != null && e.kind == EventKind.HEADPHONES -> "${e.value}%"
            e.value != null -> "${e.value}%"
            else -> e.detail
        }
        Text(right, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun HeadphonesGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val hh = size.height
        val sw = w * 0.13f
        drawArc(color, 180f, 180f, false, Offset(sw, sw), Size(w - sw * 2, hh * 1.1f - sw * 2), style = Stroke(sw, cap = StrokeCap.Round))
        drawRoundRect(color, Offset(0f, hh * 0.52f), Size(w * 0.3f, hh * 0.48f), CornerRadius(w * 0.1f))
        drawRoundRect(color, Offset(w * 0.7f, hh * 0.52f), Size(w * 0.3f, hh * 0.48f), CornerRadius(w * 0.1f))
    }
}

// ═══════════════════════════════════════════════════════════════════ cards

@Composable
private fun AppAvatar(e: NotifEntry, pal: Pal, size: Dp) {
    val bg = if (e.color != 0) Color(e.color) else pal.accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            when {
                e.largeIcon != null -> Image(e.largeIcon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                e.smallIcon != null -> Image(e.smallIcon, null, modifier = Modifier.size(size * 0.5f))
                else -> Text(e.title.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.4f).sp)
            }
        }
        if (e.largeIcon != null && e.smallIcon != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.38f)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) { Image(e.smallIcon, null, modifier = Modifier.size(size * 0.24f)) }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, fg: Color, onClick: () -> Unit) {
    Text(
        label,
        color = fg,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PeekCard(
    p: NotifEntry,
    s: LauncherSettings,
    pal: Pal,
    c: IslandColors,
    maxW: Dp,
    replying: Boolean,
    onReply: () -> Unit,
    onDone: () -> Unit,
    touched: () -> Unit,
) {
    val context = LocalContext.current
    val time = remember(p.postTime) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(p.postTime)) }
    Column(Modifier.width(maxW).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(p, pal, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.appName.uppercase(), color = c.muted, fontSize = 10.5.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Text("  ·  $time", color = c.muted, fontSize = 10.5.sp, maxLines = 1)
                }
                Text(p.title, color = c.fg, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.text.isNotBlank()) {
                    Text(p.text, color = c.fg.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (p.clearable) NotifHub.dismiss(p.key) else NotifHub.clearPeek(p.key)
                        onDone()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Dismiss", tint = c.muted, modifier = Modifier.size(16.dp)) }
        }
        if (s.islandPeekActions && p.actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.actions.take(3).forEach { a ->
                    val chipBg = if (a.isReply) pal.accent.copy(alpha = 0.3f) else c.fg.copy(alpha = 0.12f)
                    ActionChip(a.title, chipBg, c.fg) {
                        touched()
                        if (a.isReply) {
                            onReply()
                        } else {
                            NotifHub.runAction(a, context)
                            onDone()
                        }
                    }
                }
            }
        }
        if (replying) {
            p.actions.firstOrNull { it.isReply }?.let { a -> ReplyField(a, pal, c, touched, onDone) }
        }
    }
}

@Composable
private fun ReplyField(a: NotifAction, pal: Pal, c: IslandColors, touched: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250) // the window becomes focusable first
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    fun send() {
        if (text.isBlank()) return
        NotifHub.runAction(a, context, text.trim())
        keyboard?.hide()
        onDone()
    }
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.fg.copy(alpha = 0.1f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text("Reply…", color = c.muted, fontSize = 14.sp)
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    touched()
                },
                textStyle = TextStyle(color = c.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (text.isBlank()) Color.Transparent else pal.accent)
                .clickable { send() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Send, "Send", tint = if (text.isBlank()) c.muted else Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun IncomingCard(e: NotifEntry, pal: Pal, c: IslandColors, maxW: Dp) {
    val context = LocalContext.current
    val answer = e.actions.firstOrNull { it.isAnswer }
    val decline = e.actions.firstOrNull { it.isDecline }
    val isCall = e.call == CallState.INCOMING
    Row(Modifier.width(maxW).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        AppAvatar(e, pal, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            Text(
                e.text.ifBlank { if (isCall) "Incoming call" else e.appName },
                color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCall && (answer != null || decline != null)) {
            decline?.let {
                CallButton(Red, 135f) { NotifHub.runAction(it, context) }
                Spacer(Modifier.width(10.dp))
            }
            answer?.let { CallButton(Green, 0f) { NotifHub.runAction(it, context) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                e.actions.take(2).forEachIndexed { i, a ->
                    ActionChip(a.title, if (i == e.actions.size - 1) pal.accent else c.fg.copy(alpha = 0.14f), c.fg) { NotifHub.runAction(a, context) }
                }
                if (e.actions.isEmpty()) ActionChip("Open", pal.accent, Color.White) { NotifHub.send(e.contentIntent, context) }
            }
        }
    }
}

@Composable
private fun CallButton(color: Color, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(22.dp).rotate(rotation)) }
}

@Composable
private fun PlayerCard(m: MediaInfo, pal: Pal, c: IslandColors, maxW: Dp, vizOk: Boolean, touched: () -> Unit) {
    val frame = rememberViz(40, active = vizOk && m.playing)
    val context = LocalContext.current
    rememberNow(true)
    val pos = m.position()
    Column(Modifier.width(maxW).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(m.art, ArtStyle.SQUARE, m.playing, pal, 60.dp, Modifier.clickable { MediaHub.openPlayer(context) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                Text(m.artist, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                drawLine(c.fg.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Brush.horizontalGradient(pal.gradient), Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 5f, cap = StrokeCap.Round)
                drawCircle(c.fg, 7f, Offset(size.width * frac, y))
            }
            Row {
                Text(clock(pos), color = c.muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(clock(m.durationMs), color = c.muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IslandButton(Transport.PREV, c.fg, 26) { touched(); MediaHub.previous() }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(pal.gradient))
                    .clickable { touched(); MediaHub.playPause() },
                contentAlignment = Alignment.Center,
            ) { TransportIcon(if (m.playing) Transport.PAUSE else Transport.PLAY, Color.White, 26.dp) }
            IslandButton(Transport.NEXT, c.fg, 26) { touched(); MediaHub.next() }
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

// ═══════════════════════════════════════════════════════════════════ glance

@Composable
private fun GlancePanel(
    items: List<com.libexil.androforge.data.HomeItem>,
    pal: Pal,
    c: IslandColors,
    maxW: Dp,
    entries: List<NotifEntry>,
    touched: () -> Unit,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow(false)
    val battery by SystemMonitor.battery.collectAsState()
    val torch by SystemMonitor.torch.collectAsState()
    val ringer by SystemMonitor.ringer.collectAsState()
    val weatherItem = items.firstOrNull { it is ForgeWidgetItem && it.kind == ForgeKind.WEATHER } as? ForgeWidgetItem
    val place = weatherItem?.options?.place
    val units = weatherItem?.options?.units ?: WeatherRepo.defaultUnits()
    val key = WeatherRepo.key(place, units)
    val weather by remember(key) { WeatherRepo.flow(key) }.collectAsState()
    LaunchedEffect(key) { WeatherRepo.refresh(context, place, units) }

    fun launch(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        close()
    }

    Column(Modifier.width(maxW).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)), color = c.fg, fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)), color = c.muted, fontSize = 13.sp)
            }
            (weather as? WeatherState.Ready)?.weather?.let { w ->
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(w.sky, w.isDay, pal, Modifier.size(36.dp))
                        Text("${w.temp}°", color = c.fg, fontSize = 24.sp)
                    }
                    Text("${w.condition} · ${w.high}°/${w.low}°", color = c.muted, fontSize = 11.5.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlanceTile(Modifier.weight(1f), if (battery.charging) pal.ansi.getOrElse(2) { Green }.copy(alpha = 0.25f) else c.fg.copy(alpha = 0.08f), {}) {
                BatteryGlyph(battery.level, battery.charging, c.fg, Modifier.size(width = 22.dp, height = 11.dp))
                Spacer(Modifier.width(6.dp))
                Text("${battery.level}%", color = c.fg, fontSize = 13.sp)
            }
            if (SystemMonitor.hasTorch()) {
                GlanceTile(Modifier.weight(1f), if (torch) pal.accent.copy(alpha = 0.5f) else c.fg.copy(alpha = 0.08f), { touched(); SystemMonitor.toggleTorch() }) {
                    Text(if (torch) "Light on" else "Light", color = c.fg, fontSize = 13.sp)
                }
            }
            GlanceTile(Modifier.weight(1f), c.fg.copy(alpha = 0.08f), { touched(); SystemMonitor.cycleRinger() }) {
                Icon(Icons.Default.Notifications, null, tint = c.fg, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when (ringer) {
                        android.media.AudioManager.RINGER_MODE_SILENT -> "Silent"
                        android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        else -> "Ring"
                    },
                    color = c.fg, fontSize = 13.sp,
                )
            }
            GlanceTile(Modifier, c.fg.copy(alpha = 0.08f), { launch(Intent(Settings.ACTION_SETTINGS)) }) {
                Icon(Icons.Default.Settings, "Settings", tint = c.fg, modifier = Modifier.size(16.dp))
            }
        }
        val list = entries.filter { !it.ongoing }.take(8)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NOTIFICATIONS", color = pal.accent, fontSize = 10.5.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (list.any { it.clearable }) {
                Text(
                    "Clear all", color = c.muted, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { touched(); NotifHub.clearAll() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (list.isEmpty()) {
            Text("You're all caught up", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                list.forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.fg.copy(alpha = 0.06f))
                            .clickable {
                                NotifHub.open(e, context)
                                close()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppAvatar(e, pal, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = c.fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.text.isNotBlank()) Text(e.text, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (e.clearable) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).clickable { touched(); NotifHub.dismiss(e.key) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Close, "Dismiss", tint = c.muted, modifier = Modifier.size(15.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceTile(modifier: Modifier, bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content() }
}
