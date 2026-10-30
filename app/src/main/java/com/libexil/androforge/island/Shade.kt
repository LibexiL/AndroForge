package com.libexil.androforge.island

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.ShadeStyle
import com.libexil.androforge.data.ShadeTile
import com.libexil.androforge.data.TileShape
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.NetKind
import com.libexil.androforge.live.NotifEntry
import com.libexil.androforge.live.NotifHub
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.ui.Motion
import com.libexil.androforge.ui.Pal
import com.libexil.androforge.ui.SuperellipseShape
import com.libexil.androforge.ui.forge.AlbumArt
import com.libexil.androforge.ui.forge.Transport
import com.libexil.androforge.ui.forge.TransportIcon
import com.libexil.androforge.ui.forge.rememberNow
import com.libexil.androforge.ui.rememberPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the shade can ask the system to do (no-ops in the settings preview). */
interface ShadeActions {
    fun global(action: Int) = Unit
    fun start(intent: Intent) = Unit
    fun close() = Unit
    /** Let the shade take the keyboard (for typing a Wi-Fi password). */
    fun keyboard(on: Boolean) = Unit
}

object PreviewShadeActions : ShadeActions

/** AndroForge's pull-down shade window: slides down from the top, swipe up or tap outside to close. */
@Composable
fun ShadeContent(service: AccessibilityService, geo: IslandGeometry, onRemove: () -> Unit) {
    val state by LauncherApp.instance.store.state.collectAsState()
    val s = state.settings
    val pal = rememberPalette(s)
    val motion = remember(s.animations, s.animScale, s.animBounce) { Motion.of(s) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { progress.animateTo(1f, motion.spring(stiffness = 480f)) }
    LaunchedEffect(geo.shadeOpen.value) {
        if (!geo.shadeOpen.value) {
            progress.animateTo(0f, motion.tween(170))
            onRemove()
        }
    }
    fun closeShade() {
        geo.shadeOpen.value = false
    }
    val actions = remember(service) {
        object : ShadeActions {
            override fun global(action: Int) {
                closeShade()
                scope.launch {
                    delay(320)
                    service.performGlobalAction(action)
                }
            }

            override fun start(intent: Intent) {
                runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                closeShade()
            }

            override fun close() = closeShade()

            override fun keyboard(on: Boolean) {
                geo.shadeFocus.value = on
            }
        }
    }
    val sb = with(LocalDensity.current) { geo.statusBarPx.intValue.toDp() }
    val scrim = if (s.shadeStyle == ShadeStyle.CLEAR) 0.62f else 0.38f
    var dragged by remember { mutableFloatStateOf(0f) }
    // Swiping up anywhere on the panel closes it: once the list can't scroll any further up,
    // the rest of the swipe pulls the whole panel up, and letting go far enough (or fast) closes it.
    val closeOnSwipe = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                // Already pulled up and now moving back down: lower the panel before scrolling the list.
                if (dragged < 0f && available.y > 0f) {
                    val d = minOf(available.y, -dragged)
                    dragged += d
                    return Offset(0f, d)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y < 0f) {
                    dragged += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (dragged == 0f) return androidx.compose.ui.unit.Velocity.Zero
                if (dragged < -70f || available.y < -900f) {
                    closeShade()
                } else {
                    androidx.compose.animation.core.animate(dragged, 0f, animationSpec = motion.spring(stiffness = 700f)) { v, _ -> dragged = v }
                }
                return available
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = scrim))
            .pointerInput(Unit) { detectTapGestures { closeShade() } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, d -> dragged = (dragged + d).coerceAtMost(0f) },
                    onDragEnd = {
                        if (dragged < -60f) closeShade() else dragged = 0f
                    },
                )
            },
    ) {
        ShadeView(
            s, pal, actions,
            Modifier
                .padding(top = sb + 4.dp, start = 8.dp, end = 8.dp)
                .nestedScroll(closeOnSwipe)
                .graphicsLayer {
                    val p = progress.value
                    translationY = -(1f - p) * 260.dp.toPx() + dragged
                    alpha = (1f + dragged / 600f).coerceIn(0.4f, 1f)
                    scaleY = 0.92f + 0.08f * p
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
        )
    }
}

private fun tileShape(t: TileShape): Shape = when (t) {
    TileShape.ROUNDED -> RoundedCornerShape(20.dp)
    TileShape.CIRCLE -> CircleShape
    TileShape.SQUIRCLE -> SuperellipseShape()
    TileShape.PILL -> RoundedCornerShape(50)
}

/** The shade's panel (tiles, brightness, media, notifications). Also drawn in the settings preview. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShadeView(s: LauncherSettings, pal: Pal, actions: ShadeActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val context = LocalContext.current
    val a = s.shadeOpacity.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(s.shadeCorner.dp)
    val panelBg: Modifier = when (s.shadeStyle) {
        ShadeStyle.SOLID -> Modifier.background(pal.background.copy(alpha = a), shape)
        ShadeStyle.GRADIENT -> Modifier.background(
            Brush.linearGradient(listOf(lerp(pal.background, pal.accent, 0.32f).copy(alpha = a), pal.background.copy(alpha = a), lerp(pal.background, pal.accent2, 0.25f).copy(alpha = a))),
            shape,
        )
        ShadeStyle.GLASS -> Modifier
            .background(pal.surface.copy(alpha = a * 0.55f), shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f))), shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        ShadeStyle.CLEAR -> Modifier
    }
    val fg = pal.foreground
    val muted = pal.muted
    val hl = pal.role(s.shadeHighlight)
    val hlBrush = Brush.linearGradient(pal.stops(s.shadeHighlight))
    val now = rememberNow(false)

    val row = s.shadeLayout == com.libexil.androforge.data.ShadeLayout.ROW
    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(panelBg)
            .then(if (preview) Modifier else Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState()))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Header: one compact line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)), color = fg, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)), color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            val b by SystemMonitor.battery.collectAsState()
            Text("${b.level}%" + if (b.charging) " \u26a1" else "", color = fg, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(fg.copy(alpha = 0.1f)).clickable { if (!preview) actions.start(Intent(Settings.ACTION_SETTINGS)) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Settings, "Settings", tint = fg, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(10.dp))

        var sub by remember { androidx.compose.runtime.mutableStateOf<ShadeTile?>(null) }
        when (sub) {
            ShadeTile.WIFI -> {
                WifiPanel(pal, hl, hlBrush, actions) { sub = null }
                return@Column
            }
            ShadeTile.BLUETOOTH -> {
                BluetoothPanel(pal, hl, hlBrush, actions) { sub = null }
                return@Column
            }
            else -> Unit
        }

        if (row) {
            // Wi-Fi and Bluetooth get big buttons that show what you're connected to.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionCard(ShadeTile.WIFI, pal, hlBrush, preview, Modifier.weight(1f)) { sub = ShadeTile.WIFI }
                ConnectionCard(ShadeTile.BLUETOOTH, pal, hlBrush, preview, Modifier.weight(1f)) { sub = ShadeTile.BLUETOOTH }
            }
            val rest = s.shadeTiles.filter { it != ShadeTile.WIFI && it != ShadeTile.BLUETOOTH }
            if (rest.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rest.forEach { t -> Box(Modifier.width(54.dp)) { ShadeTileView(t, s, pal, hl, hlBrush, actions, pill = false, preview = preview, small = true) } }
                }
            }
        } else {
            val pill = s.shadeTileShape == TileShape.PILL
            val cols = if (pill) (s.shadeColumns / 2).coerceAtLeast(2) else s.shadeColumns.coerceIn(3, 6)
            s.shadeTiles.chunked(cols).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.forEach { t ->
                        Box(Modifier.weight(1f)) { ShadeTileView(t, s, pal, hl, hlBrush, actions, pill, preview, onSub = { sub = it }) }
                    }
                    repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Brightness & volume
        if (s.shadeBrightness) {
            Spacer(Modifier.height(10.dp))
            BrightnessRow(pal, hl, actions, preview)
        }
        if (s.shadeVolume) {
            Spacer(Modifier.height(6.dp))
            VolumeRow(pal, hl, actions, preview)
        }

        // Media
        if (s.shadeMedia) {
            val m by MediaHub.state.collectAsState()
            m?.let { media ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(fg.copy(alpha = 0.07f))
                        .clickable { MediaHub.openPlayer(context); actions.close() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArt(media.art, com.libexil.androforge.data.ArtStyle.SQUARE, media.playing, pal, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(media.title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(media.artist, color = muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    listOf(Transport.PREV to { MediaHub.previous() }, (if (media.playing) Transport.PAUSE else Transport.PLAY) to { MediaHub.playPause() }, Transport.NEXT to { MediaHub.next() })
                        .forEach { (k, f) ->
                            Box(Modifier.size(34.dp).clip(CircleShape).clickable { f() }, contentAlignment = Alignment.Center) { TransportIcon(k, fg, 18.dp) }
                        }
                }
            }
        }

        // Notifications (the whole panel scrolls, so the list can be as long as it needs)
        val entries by NotifHub.entries.collectAsState()
        val list = entries.take(if (preview) 2 else 40)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NOTIFICATIONS", color = hl, fontSize = 10.5.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (list.any { it.clearable }) {
                Text(
                    "Clear all", color = muted, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { NotifHub.clearAll() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (list.isEmpty()) {
            Text("No notifications", color = muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            // Same app = one stack; tap it to fan the notifications out.
            val groups = remember(list) { list.groupBy { it.pkg }.values.toList() }
            var expanded by remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
            groups.forEach { g ->
                androidx.compose.runtime.key(g.first().pkg) {
                    if (g.size == 1) {
                        ShadeNotification(g.first(), pal, actions)
                    } else {
                        NotificationStack(
                            g, pal, hl,
                            open = g.first().pkg in expanded,
                            onToggle = { pkg -> expanded = if (pkg in expanded) expanded - pkg else expanded + pkg },
                            actions = actions,
                        )
                    }
                }
            }
        }
        // Handle: drag up or tap to close.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
                .size(width = 80.dp, height = 18.dp)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { actions.close() }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { _, d -> total += d }, onDragEnd = { if (total < -30f) actions.close() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.3f)))
        }
    }
}

/** Wi-Fi / Bluetooth button: icon, name of what you're connected to, signal or battery. */
@Composable
private fun ConnectionCard(t: ShadeTile, pal: Pal, hlBrush: Brush, preview: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val net by SystemMonitor.net.collectAsState()
    val now = rememberNow(false)
    val info = remember(t, net, now) { if (t == ShadeTile.WIFI) wifiInfo(context, net) else bluetoothInfo(context) }
    val fg = pal.foreground
    val on = info.on
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) hlBrush else Brush.linearGradient(listOf(fg.copy(alpha = 0.09f), fg.copy(alpha = 0.09f))))
            .clickable { if (!preview) onOpen() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (on) Color.White else fg
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.22f) else fg.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (t == ShadeTile.WIFI) WifiBars(info.level, tint, Modifier.size(18.dp)) else TileGlyph(ShadeTile.BLUETOOTH, tint, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, color = tint, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(info.subtitle, color = tint.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("\u203a", color = tint.copy(alpha = 0.7f), fontSize = 20.sp)
    }
}

private class ConnInfo(val on: Boolean, val title: String, val subtitle: String, val level: Int = 0)

@Suppress("DEPRECATION")
private fun wifiInfo(context: Context, net: NetKind): ConnInfo {
    val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
    val enabled = runCatching { wm?.isWifiEnabled == true }.getOrDefault(false)
    if (!enabled) return ConnInfo(false, "Wi-Fi", "Off")
    if (net != NetKind.WIFI) return ConnInfo(true, "Wi-Fi", "On \u00b7 not connected", 0)
    val ci = runCatching { wm?.connectionInfo }.getOrNull()
    val ssid = ci?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    val level = ci?.rssi?.let { rssi ->
        if (Build.VERSION.SDK_INT >= 30) (wm?.calculateSignalLevel(rssi) ?: 0) * 4 / ((wm?.maxSignalLevel ?: 4).coerceAtLeast(1))
        else android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)
    } ?: 4
    val strength = when (level) {
        4 -> "Excellent"
        3 -> "Good"
        2 -> "Fair"
        else -> "Weak"
    }
    return ConnInfo(true, ssid ?: "Connected", strength + " signal", level)
}

private fun bluetoothAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun bluetoothInfo(context: Context): ConnInfo {
    if (!bluetoothAvailable(context)) return ConnInfo(false, "Bluetooth", "Allow Nearby devices in AndroForge")
    val on = bluetoothOn(context)
    if (!on) return ConnInfo(false, "Bluetooth", "Off")
    val audio = context.getSystemService(android.media.AudioManager::class.java)
    val types = mutableSetOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    if (Build.VERSION.SDK_INT >= 31) types += android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
    val dev = runCatching { audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types } }.getOrNull()
    if (dev == null) return ConnInfo(true, "Bluetooth", "On \u00b7 no device")
    val name = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Connected"
    val battery = SystemMonitor.bluetoothBattery(dev)
    return ConnInfo(true, name, if (battery != null) "Connected \u00b7 $battery%" else "Connected")
}

@Composable
private fun WifiBars(level: Int, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.12f
        for (k in 1..3) {
            val r = w * 0.17f * k + sw
            val a = if (level >= k + 1 || level >= 4) 1f else 0.3f
            drawArc(tint.copy(alpha = a), 225f, 90f, false, Offset(w / 2 - r, h * 0.85f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
        drawCircle(tint, sw * 0.8f, Offset(w / 2, h * 0.85f))
    }
}

@Composable
private fun VolumeRow(pal: Pal, hl: Color, actions: ShadeActions, preview: Boolean) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(android.media.AudioManager::class.java) }
    val max = remember { runCatching { audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(runCatching { audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max }.getOrDefault(0.5f)) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(pal.foreground.copy(alpha = 0.07f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).clickable {
                if (!preview) actions.start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS))
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height
                val c = pal.foreground
                val body = Path().apply {
                    moveTo(w * 0.1f, h * 0.38f); lineTo(w * 0.3f, h * 0.38f); lineTo(w * 0.55f, h * 0.15f)
                    lineTo(w * 0.55f, h * 0.85f); lineTo(w * 0.3f, h * 0.62f); lineTo(w * 0.1f, h * 0.62f); close()
                }
                drawPath(body, c)
                if (level > 0.01f) drawArc(c, -45f, 90f, false, Offset(w * 0.45f, h * 0.3f), Size(w * 0.3f, h * 0.4f), style = Stroke(w * 0.09f, cap = StrokeCap.Round))
                if (level > 0.5f) drawArc(c, -45f, 90f, false, Offset(w * 0.4f, h * 0.15f), Size(w * 0.55f, h * 0.7f), style = Stroke(w * 0.09f, cap = StrokeCap.Round))
                if (level <= 0.01f) drawLine(c, Offset(w * 0.68f, h * 0.36f), Offset(w * 0.95f, h * 0.64f), w * 0.09f, StrokeCap.Round)
            }
        }
        Slider(
            value = level,
            onValueChange = { v ->
                level = v
                if (!preview) runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (v * max).roundToIntSafe(), 0) }
            },
            colors = SliderDefaults.colors(thumbColor = hl, activeTrackColor = hl, inactiveTrackColor = pal.foreground.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShadeTileView(t: ShadeTile, s: LauncherSettings, pal: Pal, hl: Color, hlBrush: Brush, actions: ShadeActions, pill: Boolean, preview: Boolean, small: Boolean = false, onSub: (ShadeTile) -> Unit = {}) {
    val context = LocalContext.current
    val net by SystemMonitor.net.collectAsState()
    val torch by SystemMonitor.torch.collectAsState()
    val ringer by SystemMonitor.ringer.collectAsState()
    val dnd by NotifHub.dnd.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val active: Boolean = remember(t, net, torch, ringer, dnd, refresh) {
        when (t) {
            ShadeTile.WIFI -> net == NetKind.WIFI
            ShadeTile.DATA -> net != NetKind.NONE
            ShadeTile.BLUETOOTH -> bluetoothOn(context)
            ShadeTile.FLASHLIGHT -> torch
            ShadeTile.RING -> ringer == android.media.AudioManager.RINGER_MODE_NORMAL
            ShadeTile.DND -> dnd
            ShadeTile.ROTATE -> runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)
            ShadeTile.LOCATION -> if (Build.VERSION.SDK_INT >= 28) runCatching { context.getSystemService(LocationManager::class.java).isLocationEnabled }.getOrDefault(false) else false
            else -> false
        }
    }
    val label = when (t) {
        ShadeTile.RING -> when (ringer) {
            android.media.AudioManager.RINGER_MODE_SILENT -> "Silent"
            android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Sound"
        }
        else -> t.label
    }
    val onClick: () -> Unit = {
        if (!preview) when (t) {
            ShadeTile.WIFI -> onSub(ShadeTile.WIFI)
            ShadeTile.DATA -> actions.start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
            ShadeTile.BLUETOOTH -> onSub(ShadeTile.BLUETOOTH)
            ShadeTile.FLASHLIGHT -> SystemMonitor.toggleTorch()
            ShadeTile.RING -> SystemMonitor.cycleRinger()
            ShadeTile.DND -> NotifHub.toggleDnd()
            ShadeTile.ROTATE -> if (Settings.System.canWrite(context)) {
                runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (active) 0 else 1) }
                refresh++
            } else {
                actions.start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
            }
            ShadeTile.LOCATION -> actions.start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            ShadeTile.HOTSPOT -> actions.start(Intent("android.settings.TETHER_SETTINGS").takeIf { it.resolveActivity(context.packageManager) != null } ?: Intent(Settings.ACTION_WIRELESS_SETTINGS))
            ShadeTile.SCREENSHOT -> if (Build.VERSION.SDK_INT >= 28) actions.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            ShadeTile.LOCK -> if (Build.VERSION.SDK_INT >= 28) actions.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            ShadeTile.POWER -> actions.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            ShadeTile.SETTINGS -> actions.start(Intent(Settings.ACTION_SETTINGS))
            ShadeTile.ANDROID -> {
                com.libexil.androforge.SystemActions.allowSystemShade()
                actions.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            }
        }
    }
    val onLong: () -> Unit = {
        if (!preview) when (t) {
            ShadeTile.WIFI -> actions.start(Intent(Settings.ACTION_WIFI_SETTINGS))
            ShadeTile.BLUETOOTH -> actions.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            ShadeTile.DND -> actions.start(Intent("android.settings.ZEN_MODE_SETTINGS"))
            ShadeTile.RING -> actions.start(Intent(Settings.ACTION_SOUND_SETTINGS))
            ShadeTile.ROTATE -> actions.start(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            else -> onClick()
        }
    }
    val fg = pal.foreground
    val shape = tileShape(s.shadeTileShape)
    val bg = if (active) hlBrush else Brush.linearGradient(listOf(fg.copy(alpha = 0.09f), fg.copy(alpha = 0.09f)))
    val iconTint = if (active) Color.White else fg
    if (pill) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(bg)
                .combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileGlyph(t, iconTint, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = iconTint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val d = minOf(maxWidth, if (small) 48.dp else 62.dp)
                Box(
                    Modifier
                        .size(d)
                        .clip(shape)
                        .background(bg)
                        .combinedClickable(onClick = onClick, onLongClick = onLong),
                    contentAlignment = Alignment.Center,
                ) { TileGlyph(t, iconTint, Modifier.size(d * 0.4f)) }
            }
            Text(label, color = pal.muted, fontSize = if (small) 10.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun bluetoothOn(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        false
    } else {
        @Suppress("MissingPermission")
        context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true
    }
} catch (e: Exception) {
    false
}

@Composable
private fun TileGlyph(t: ShadeTile, tint: Color, modifier: Modifier) {
    when (t) {
        ShadeTile.WIFI -> NetworkGlyph(NetKind.WIFI, tint, modifier)
        ShadeTile.RING -> Icon(Icons.Default.Notifications, null, tint = tint, modifier = modifier)
        ShadeTile.ROTATE -> Icon(Icons.Default.Refresh, null, tint = tint, modifier = modifier)
        ShadeTile.LOCATION -> Icon(Icons.Default.LocationOn, null, tint = tint, modifier = modifier)
        ShadeTile.LOCK -> Icon(Icons.Default.Lock, null, tint = tint, modifier = modifier)
        ShadeTile.SETTINGS -> Icon(Icons.Default.Settings, null, tint = tint, modifier = modifier)
        ShadeTile.ANDROID -> Icon(Icons.Default.Menu, null, tint = tint, modifier = modifier)
        else -> Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = w * 0.1f
            when (t) {
                ShadeTile.BLUETOOTH -> {
                    val p = Path().apply {
                        moveTo(w * 0.28f, h * 0.3f); lineTo(w * 0.72f, h * 0.7f); lineTo(w * 0.5f, h * 0.9f); lineTo(w * 0.5f, h * 0.1f)
                        lineTo(w * 0.72f, h * 0.3f); lineTo(w * 0.28f, h * 0.7f)
                    }
                    drawPath(p, tint, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                ShadeTile.DATA -> {
                    drawLine(tint, Offset(w * 0.35f, h * 0.85f), Offset(w * 0.35f, h * 0.15f), sw, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.35f, h * 0.15f), Offset(w * 0.18f, h * 0.35f), sw, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.65f, h * 0.15f), Offset(w * 0.65f, h * 0.85f), sw, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.65f, h * 0.85f), Offset(w * 0.82f, h * 0.65f), sw, StrokeCap.Round)
                }
                ShadeTile.FLASHLIGHT -> {
                    val p = Path().apply {
                        moveTo(w * 0.25f, h * 0.1f); lineTo(w * 0.75f, h * 0.1f); lineTo(w * 0.75f, h * 0.28f)
                        lineTo(w * 0.62f, h * 0.45f); lineTo(w * 0.62f, h * 0.92f); lineTo(w * 0.38f, h * 0.92f)
                        lineTo(w * 0.38f, h * 0.45f); lineTo(w * 0.25f, h * 0.28f); close()
                    }
                    drawPath(p, tint, style = Stroke(sw * 0.9f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawCircle(tint, sw * 0.7f, Offset(w * 0.5f, h * 0.6f))
                }
                ShadeTile.DND -> {
                    drawCircle(tint, w * 0.42f, style = Stroke(sw))
                    drawLine(tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), sw, StrokeCap.Round)
                }
                ShadeTile.HOTSPOT -> {
                    drawCircle(tint, sw * 0.9f, Offset(w / 2, h * 0.55f))
                    for (k in 1..2) {
                        val r = w * 0.2f * k
                        drawArc(tint, 200f, 140f, false, Offset(w / 2 - r, h * 0.55f - r), Size(r * 2, r * 2), style = Stroke(sw * 0.85f, cap = StrokeCap.Round))
                    }
                }
                ShadeTile.SCREENSHOT -> {
                    val l = w * 0.28f
                    listOf(Offset(w * 0.1f, h * 0.1f) to Offset(1f, 1f), Offset(w * 0.9f, h * 0.1f) to Offset(-1f, 1f), Offset(w * 0.1f, h * 0.9f) to Offset(1f, -1f), Offset(w * 0.9f, h * 0.9f) to Offset(-1f, -1f))
                        .forEach { (c, d) ->
                            drawLine(tint, c, Offset(c.x + d.x * l, c.y), sw, StrokeCap.Round)
                            drawLine(tint, c, Offset(c.x, c.y + d.y * l), sw, StrokeCap.Round)
                        }
                }
                ShadeTile.POWER -> {
                    drawArc(tint, -60f, 300f, false, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = Stroke(sw, cap = StrokeCap.Round))
                    drawLine(tint, Offset(w / 2, h * 0.08f), Offset(w / 2, h * 0.45f), sw, StrokeCap.Round)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun BrightnessRow(pal: Pal, hl: Color, actions: ShadeActions, preview: Boolean) {
    val context = LocalContext.current
    val canWrite = remember { Settings.System.canWrite(context) }
    var level by remember {
        mutableFloatStateOf(runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f))
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(pal.foreground.copy(alpha = 0.07f)).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(pal.foreground, size.minDimension * 0.22f)
            for (i in 0 until 8) {
                val ang = i / 8f * 2 * Math.PI.toFloat()
                val c = center
                drawLine(
                    pal.foreground,
                    Offset(c.x + kotlin.math.cos(ang) * size.minDimension * 0.34f, c.y + kotlin.math.sin(ang) * size.minDimension * 0.34f),
                    Offset(c.x + kotlin.math.cos(ang) * size.minDimension * 0.48f, c.y + kotlin.math.sin(ang) * size.minDimension * 0.48f),
                    strokeWidth = 2f, cap = StrokeCap.Round,
                )
            }
        }
        if (canWrite || preview) {
            Slider(
                value = level,
                onValueChange = { v ->
                    level = v
                    if (!preview) runCatching {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
                    }
                },
                colors = SliderDefaults.colors(thumbColor = hl, activeTrackColor = hl, inactiveTrackColor = pal.foreground.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        } else {
            Text(
                "Tap to allow brightness control",
                color = pal.muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { actions.start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Several notifications from one app: a stacked card that opens into the separate notifications. */
@Composable
private fun NotificationStack(group: List<NotifEntry>, pal: Pal, hl: Color, open: Boolean, onToggle: (String) -> Unit, actions: ShadeActions) {
    val fg = pal.foreground
    val top = group.first()
    val pkg = top.pkg
    androidx.compose.animation.AnimatedContent(
        targetState = open,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top))
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top))
        },
        label = "stack",
    ) { isOpen ->
        if (!isOpen) {
            // Collapsed: the newest one on top, the others showing as thin edges underneath
            // (inside the stack's own space, so nothing overlaps the next notification).
            val behind = (group.size - 1).coerceAtMost(2)
            val cardColor = androidx.compose.ui.graphics.lerp(pal.background, fg, 0.09f)
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardColor)
                        .clickable { onToggle(pkg) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotifAvatar(top, pal, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(top.appName, color = pal.muted, fontSize = 10.5.sp, maxLines = 1)
                            Text(top.title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (top.text.isNotBlank()) Text(top.text, color = fg.copy(alpha = 0.75f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${group.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(hl).padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        "+${group.size - 1} more \u2022 tap to see all", color = pal.muted, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 44.dp, top = 4.dp),
                    )
                }
                for (k in 1..behind) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = (k * 12).dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(androidx.compose.ui.graphics.lerp(pal.background, fg, 0.09f - k * 0.025f)),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(top.appName, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (group.any { it.clearable }) {
                        Text(
                            "Clear", color = pal.muted, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { group.filter { it.clearable }.forEach { NotifHub.dismiss(it.key) } }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        "Show less \u25B4", color = hl, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onToggle(pkg) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                group.forEach { e -> ShadeNotification(e, pal, actions) }
            }
        }
    }
}

@Composable
private fun NotifAvatar(e: NotifEntry, pal: Pal, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (e.color != 0) Color(e.color) else pal.accent),
        contentAlignment = Alignment.Center,
    ) {
        when {
            e.largeIcon != null -> Image(e.largeIcon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            e.smallIcon != null -> Image(e.smallIcon, null, modifier = Modifier.size(size * 0.53f))
        }
    }
}

@Composable
private fun ShadeNotification(e: NotifEntry, pal: Pal, actions: ShadeActions) {
    val context = LocalContext.current
    val fg = pal.foreground
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fg.copy(alpha = 0.07f))
            .clickable {
                NotifHub.open(e, context)
                actions.close()
            }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (e.color != 0) Color(e.color) else pal.accent),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    e.largeIcon != null -> Image(e.largeIcon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    e.smallIcon != null -> Image(e.smallIcon, null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.appName, color = pal.muted, fontSize = 10.5.sp, maxLines = 1)
                Text(e.title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.text.isNotBlank()) Text(e.text, color = fg.copy(alpha = 0.75f), fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.clearable) {
                Box(Modifier.size(28.dp).clip(CircleShape).clickable { NotifHub.dismiss(e.key) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "Dismiss", tint = pal.muted, modifier = Modifier.size(15.dp))
                }
            }
        }
        val buttons = e.actions.filter { !it.isReply }.take(3)
        if (buttons.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp, start = 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                buttons.forEach { act ->
                    Text(
                        act.title, color = fg, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.12f)).clickable { NotifHub.runAction(act, context) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
