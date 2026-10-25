package com.libexil.androforge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.BarBackground
import com.libexil.androforge.data.BarMode
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.IslandColor
import com.libexil.androforge.data.IslandIdle
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.PanelBackground
import com.libexil.androforge.data.PanelLook
import com.libexil.androforge.ui.forge.ForgeActions
import com.libexil.androforge.ui.forge.ForgeWidget
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

enum class PreviewKind(val label: String) { HOME("Home screen"), DOCK("Dock"), DRAWER("App drawer"), PALETTE("Palette"), MOTION("Motion"), ISLAND("Island & status bar"), SHADE("Pull-down shade") }

fun previewFor(section: String?): PreviewKind? = when (section) {
    "Home screen", "Icons" -> PreviewKind.HOME
    "Motion" -> PreviewKind.MOTION
    "Dock" -> PreviewKind.DOCK
    "App drawer" -> PreviewKind.DRAWER
    "Palette", "Look & feel", "Night theme", "Live background" -> PreviewKind.PALETTE
    "Dynamic island & status bar", "Smart alerts", "Custom status bar", "Override Android's bar" -> PreviewKind.ISLAND
    "Pull-down shade" -> PreviewKind.SHADE
    else -> null
}

/** Live preview of whatever settings section you're looking at. Uses your real settings and apps. */
@Composable
fun SettingsPreview(kind: PreviewKind, vm: LauncherViewModel, state: LauncherState, apps: List<AppInfo>, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val motion = LocalMotion.current
    // A stand-in for your wallpaper, tinted with the palette.
    val backdrop = Brush.linearGradient(
        listOf(lerp(pal.background, pal.accent, 0.35f), pal.background, lerp(pal.background, pal.accent2, 0.3f)),
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backdrop)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { fadeIn(motion.tween(220, delay = 60)) togetherWith fadeOut(motion.tween(120)) },
            label = "preview",
        ) { k ->
            Box(Modifier.fillMaxSize()) {
                when (k) {
                    PreviewKind.HOME -> HomePreview(state, apps)
                    PreviewKind.DOCK -> DockPreview(vm, state, apps)
                    PreviewKind.DRAWER -> DrawerPreview(state, apps)
                    PreviewKind.PALETTE -> PalettePreview(pal)
                    PreviewKind.MOTION -> MotionPreview(pal)
                    PreviewKind.ISLAND -> IslandPreview(state, pal)
                    PreviewKind.SHADE -> Box(Modifier.fillMaxSize().padding(top = 26.dp, start = 10.dp, end = 10.dp)) {
                        com.libexil.androforge.island.ShadeView(
                            state.settings, pal, com.libexil.androforge.island.PreviewShadeActions,
                            Modifier.graphicsLayer {
                                scaleX = 0.8f
                                scaleY = 0.8f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            },
                            preview = true,
                        )
                    }
                }
            }
        }
        Text(
            "PREVIEW · ${kind.label.uppercase()}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.5.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun sampleApps(state: LauncherState, apps: List<AppInfo>, n: Int): List<AppInfo> {
    val visible = apps.filter { it.key.id !in state.hidden }
    val ranked = visible.sortedByDescending { state.launchCounts[it.key.id] ?: 0 }
    return ranked.take(n)
}

@Composable
private fun HomePreview(state: LauncherState, apps: List<AppInfo>) {
    val s = state.settings
    val cols = s.columns
    val sample = sampleApps(state, apps, cols * 2)
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 30.dp, bottom = 6.dp, start = 6.dp, end = 6.dp)) {
        val cellW = maxWidth / cols
        val rowH = maxHeight / 2
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            listOf(0, 1).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until cols) {
                        val i = row * cols + c
                        Box(Modifier.width(cellW).height(rowH), contentAlignment = Alignment.Center) {
                            if (row == 1 && c == cols - 1 && sample.size >= 4) {
                                val folder = com.libexil.androforge.data.FolderItem("preview", name = "Folder", apps = sample.take(4).map { it.key })
                                IconWithLabel("Folder", s.showLabels, s.iconSizeDp.dp) { FolderIcon(folder, s.iconSizeDp.dp) }
                            } else {
                                sample.getOrNull(i)?.let { a ->
                                    IconWithLabel(labelFor(a.key, a.label), s.showLabels, s.iconSizeDp.dp) { AppIcon(a.key, s.iconSizeDp.dp) }
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
private fun DockPreview(vm: LauncherViewModel, state: LauncherState, apps: List<AppInfo>) {
    val appsById = remember(apps) { apps.associateBy { it.key.id } }
    val fakeDrag = remember { DragState() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Text(
            if (state.settings.dockMagnify) "Slide your finger along the dock" else "",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
        )
        if (state.settings.dockEnabled) {
            Dock(vm = vm, state = state, appsById = appsById, drag = fakeDrag, onDragEnd = { fakeDrag.clear() })
        } else {
            Text("Dock is off", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
        }
    }
}

@Composable
private fun DrawerPreview(state: LauncherState, apps: List<AppInfo>) {
    val s = state.settings
    val cols = s.drawerColumns
    val sample = remember(apps, state.labels) {
        apps.filter { it.key.id !in state.hidden }.sortedBy { (state.labels[it.key.id] ?: it.label).lowercase() }.take(cols * 2)
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 26.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF0C0C10).copy(alpha = s.drawerOpacity)),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = if (s.drawerFastScroll) 22.dp else 10.dp, top = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.CenterStart,
            ) { Text("  Search ${apps.size} apps", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellW = maxWidth / cols
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    sample.chunked(cols).forEach { row ->
                        Row {
                            row.forEach { a ->
                                Box(Modifier.width(cellW), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppIcon(a.key, (s.iconSizeDp * 0.8f).dp)
                                        IconLabel(state.labels[a.key.id] ?: a.label, color = Color.White, sizeSp = 10)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (s.drawerFastScroll) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf('#', 'A', 'D', 'G', 'K', 'N', 'R', 'U', 'Z').forEach {
                    Text(it.toString(), color = if (it == 'A') LocalAccent.current else Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                }
            }
        }
    }
}

private object NoActions : ForgeActions {
    override fun requestAudio() = Unit
    override fun requestLocation() = Unit
    override fun openNotificationAccess() = Unit
    override fun customize() = Unit
}

@Composable
private fun PalettePreview(pal: Pal) {
    val clock = remember { ForgeWidgetItem("preview-clock", kind = ForgeKind.CLOCK, look = PanelLook(background = PanelBackground.SOLID)) }
    Column(Modifier.fillMaxSize().padding(top = 30.dp, start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1.4f).fillMaxHeight()) { ForgeWidget(clock, pal, NoActions) }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(pal.source.name, color = pal.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = true, onCheckedChange = null, modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f })
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(LocalAccent.current).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { Text("Accent", color = Color.White, fontSize = 11.sp) }
                }
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val n = 14
                    val slot = size.width / n
                    for (i in 0 until n) {
                        val v = 0.3f + 0.7f * abs(sin(i * 0.8f + 0.4f))
                        drawRoundRect(
                            Brush.verticalGradient(pal.gradient.reversed(), 0f, size.height),
                            Offset(i * slot + slot * 0.15f, size.height * (1 - v)),
                            Size(slot * 0.7f, size.height * v),
                            CornerRadius(slot * 0.3f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        com.libexil.androforge.ui.forge.PaletteStrip(pal, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MotionPreview(pal: Pal) {
    val motion = LocalMotion.current
    val open = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(motion.enabled, motion.scale, motion.bounce) {
        while (true) {
            open.animateTo(1f, motion.spring(stiffness = 650f))
            slide.animateTo(1f, motion.spring(stiffness = 500f))
            delay(700)
            open.animateTo(0f, motion.spring(stiffness = 650f))
            slide.animateTo(0f, motion.spring(stiffness = 500f))
            delay(600)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 30.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        val w = maxWidth
        // A folder popping open.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(110.dp)
                .graphicsLayer {
                    val k = 0.45f + 0.55f * open.value
                    scaleX = k
                    scaleY = k
                    alpha = (0.4f + 0.6f * open.value).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(pal.surface)
                .border(1.dp, pal.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) { c -> Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(pal.gradientAt((r * 2 + c) / 3f))) }
                    }
                }
            }
        }
        // A dot springing across (shows the bounce).
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = 130.dp + (w - 170.dp) * slide.value)
                .size(34.dp)
                .clip(CircleShape)
                .background(LocalAccent.current),
        )
        Text(
            if (motion.enabled) "Speed ${(motion.scale * 100).toInt()}% · Bounce ${(motion.bounce * 100).toInt()}%" else "Animations off",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun IslandPreview(state: LauncherState, pal: Pal) {
    val s = state.settings
    val motion = LocalMotion.current
    val stages = buildList {
        add(0)
        if (s.islandMusic) add(1)
        if (s.islandNotifications) add(2)
        if (s.islandCharging) add(3)
    }
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(stages) {
        var i = 0
        while (true) {
            stage = stages[i % stages.size]
            delay(2200)
            i++
        }
    }
    val bg: Brush = when (s.islandColor) {
        IslandColor.BLACK -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        IslandColor.PALETTE -> Brush.linearGradient(listOf(pal.background, pal.background))
        IslandColor.GRADIENT -> Brush.linearGradient(listOf(lerp(pal.background, pal.accent, 0.28f), pal.background, lerp(pal.background, pal.accent2, 0.22f)))
        IslandColor.GLASS -> Brush.verticalGradient(listOf(pal.surface.copy(alpha = 0.75f), pal.background.copy(alpha = 0.6f)))
    }
    val fg = if (s.islandColor == IslandColor.BLACK) Color.White else pal.foreground
    val barH = 30.dp
    Box(Modifier.fillMaxSize().padding(top = 26.dp)) {
        // Status bar
        val custom = s.barMode == BarMode.ISLAND_BAR
        val barBg: Brush = when {
            !custom -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            s.barBackground == BarBackground.BLACK -> Brush.linearGradient(listOf(Color.Black, Color.Black))
            s.barBackground == BarBackground.PALETTE -> Brush.linearGradient(listOf(pal.background, pal.background))
            else -> Brush.horizontalGradient(listOf(pal.background, lerp(pal.background, pal.accent, 0.35f), pal.background))
        }
        val barText = if (!custom) Color.White.copy(alpha = 0.55f) else if (s.barText == com.libexil.androforge.data.ColorRole.FOREGROUND) {
            if (s.barBackground == BarBackground.BLACK) Color.White else pal.foreground
        } else {
            pal.role(s.barText)
        }
        if (custom) {
            com.libexil.androforge.island.StatusBarView(s, pal, Modifier.fillMaxWidth().height(barH))
        } else Row(
            Modifier.fillMaxWidth().height(barH).background(barBg).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val time = SimpleDateFormat(if (s.barSeconds) "h:mm:ss" else "h:mm", Locale.getDefault()).format(Date())
            Text(time, color = barText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (custom && s.barShowDate) Text("  " + SimpleDateFormat("EEE d", Locale.getDefault()).format(Date()), color = barText.copy(alpha = 0.7f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (custom) "${if (s.barNotificationIcons) "● ● " else ""}${if (s.barNetwork) "▲ " else ""}80%" else "▲ ▮ 80%", color = barText, fontSize = 11.sp)
        }
        if (s.barMode == BarMode.OFF) {
            Text("Island is off", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val h = s.islandHeightDp.dp.coerceAtMost(barH + 6.dp)
        val big = stage == 2
        val shape = RoundedCornerShape(if (big) 22.dp else h / 2)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = s.islandOffsetX.dp * 0.5f, y = ((barH - h) / 2).coerceAtLeast(1.dp) + s.islandOffsetY.dp * 0.5f)
                .animateContentSize(motion.spring(stiffness = 520f))
                .clip(shape)
                .background(bg)
                .then(if (s.islandGlow && stage != 0) Modifier.border(1.dp, Brush.linearGradient(pal.gradient), shape) else Modifier),
        ) {
            when (stage) {
                0 -> if (s.islandIdle == IslandIdle.HIDDEN) Box(Modifier.size(1.dp)) else Box(Modifier.size(if (s.islandIdle == IslandIdle.DOT) h else s.islandWidthDp.dp, h))
                1 -> Row(Modifier.height(h).width((s.islandWidthDp + 90).dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(h - 8.dp).clip(CircleShape).background(Brush.linearGradient(pal.gradient)))
                    Spacer(Modifier.weight(1f))
                    PreviewBars(pal, Modifier.width(36.dp).fillMaxHeight().padding(vertical = 6.dp))
                    Spacer(Modifier.width(4.dp))
                }
                2 -> Column(Modifier.width(280.dp).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(pal.accent), contentAlignment = Alignment.Center) { Text("A", color = Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MESSAGES · now", color = fg.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.sp)
                            Text("Alex", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("See you at 8?", color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
                        }
                    }
                    if (s.islandPeekActions) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Reply", "Mark as read").forEachIndexed { i, t ->
                                Text(
                                    t, color = fg, fontSize = 11.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50))
                                        .background(if (i == 0) pal.accent.copy(alpha = 0.3f) else fg.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                else -> Row(Modifier.height(h).width(190.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val green = pal.ansi.getOrElse(2) { Color(0xFF30A46C) }
                    Text("⚡ Charging", color = fg, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("80%", color = green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PreviewBars(pal: Pal, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "bars").animateFloat(
        0f, 6.28f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        val n = 5
        val slot = size.width / n
        for (i in 0 until n) {
            val v = 0.3f + 0.7f * abs(sin(t + i * 1.1f))
            val bh = size.height * v
            drawRoundRect(pal.gradientAt(i / (n - 1f)), Offset(i * slot + slot * 0.2f, (size.height - bh) / 2), Size(slot * 0.6f, bh), CornerRadius(slot * 0.3f))
        }
    }
}
