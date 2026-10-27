package com.libexil.androforge.island

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.live.BluetoothControl
import com.libexil.androforge.live.BtDevice
import com.libexil.androforge.live.BtKind
import com.libexil.androforge.live.ShellAccess
import com.libexil.androforge.live.WifiControl
import com.libexil.androforge.live.WifiNet
import com.libexil.androforge.ui.Pal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Opens AndroForge so it can ask for a permission (the shade window can't show the prompt). */
fun askFromLauncher(context: Context, vararg perms: String) {
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        it.putExtra(EXTRA_REQUEST_PERMISSIONS, perms)
        runCatching { context.startActivity(it) }
    }
}

const val EXTRA_REQUEST_PERMISSIONS = "androforge.request_permissions"

@Composable
private fun PanelHeader(title: String, on: Boolean, pal: Pal, hl: Color, onBack: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(pal.foreground.copy(alpha = 0.1f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = pal.foreground, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Text(title, color = pal.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = hl, checkedThumbColor = Color.White, uncheckedTrackColor = pal.foreground.copy(alpha = 0.15f)),
        )
    }
}

@Composable
private fun Note(text: String, pal: Pal, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pal.foreground.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        Text(text, color = pal.muted, fontSize = 12.sp)
        if (action != null) {
            Text(
                action, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(vertical = 4.dp),
            )
        }
    }
}

/** Shizuku nudge: makes the switches and joining networks instant. */
@Composable
private fun ShizukuNote(pal: Pal, what: String) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val installed = remember(tick) { ShellAccess.installed(context) }
    val running = remember(tick) { ShellAccess.running() }
    if (ShellAccess.granted()) return
    when {
        !installed -> Note("Android only lets apps $what through its own screens. Install the free Shizuku app to do it right here.", pal, "Get Shizuku") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + ShellAccess.SHIZUKU_PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        !running -> Note("Shizuku is installed but not started. Open it and tap Start (wireless debugging).", pal, "Open Shizuku") {
            context.packageManager.getLaunchIntentForPackage(ShellAccess.SHIZUKU_PKG)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        else -> Note("Let AndroForge use Shizuku to $what instantly.", pal, "Allow") {
            ShellAccess.request()
            tick++
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ Wi-Fi

@Composable
fun WifiPanel(pal: Pal, hl: Color, hlBrush: Brush, actions: ShadeActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nets by WifiControl.networks.collectAsState()
    val busy by WifiControl.busy.collectAsState()
    var on by remember { mutableStateOf(WifiControl.enabled()) }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        WifiControl.startWatching()
        onDispose {
            WifiControl.stopWatching()
            actions.keyboard(false)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            on = WifiControl.enabled()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            message = null
        }
    }
    Column {
        PanelHeader("Wi-Fi", on, pal, hl, onBack) { v ->
            scope.launch {
                if (WifiControl.setEnabled(v)) {
                    on = v
                    delay(1200)
                    WifiControl.startWatching()
                } else {
                    actions.start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
        }
        message?.let { Text(it, color = hl, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        ShizukuNote(pal, "switch Wi-Fi and join networks")
        when {
            !on -> Note("Wi-Fi is off.", pal)
            !WifiControl.canScan() -> Note("To list nearby networks Android needs precise location for AndroForge.", pal, "Allow") {
                askFromLauncher(context, Manifest.permission.ACCESS_FINE_LOCATION)
                actions.close()
            }
            !WifiControl.locationOn() -> Note("Turn on Location to see nearby networks (Android's rule for Wi-Fi scans).", pal, "Location settings") {
                actions.start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            nets.isEmpty() -> Note("Looking for networks…", pal)
        }
        Spacer(Modifier.height(6.dp))
        nets.take(25).forEach { n ->
            WifiRow(
                n, pal, hl, hlBrush,
                expanded = open == n.ssid,
                connecting = busy == n.ssid,
                onTap = {
                    if (n.connected) {
                        open = if (open == n.ssid) null else n.ssid
                    } else if (!n.secure || WifiControl.savedPassword(n.ssid) != null) {
                        scope.launch { message = WifiControl.connect(context, n, WifiControl.savedPassword(n.ssid)) }
                    } else {
                        open = if (open == n.ssid) null else n.ssid
                        actions.keyboard(open != null)
                    }
                },
                onConnect = { pw ->
                    actions.keyboard(false)
                    open = null
                    scope.launch { message = WifiControl.connect(context, n, pw) }
                },
                onDisconnect = {
                    scope.launch {
                        message = if (WifiControl.disconnect()) "Disconnected" else "Use Android's Wi-Fi panel to disconnect"
                        open = null
                    }
                },
                onForget = {
                    WifiControl.forget(n.ssid)
                    message = "Forgot the saved password"
                    open = null
                },
            )
        }
        Text(
            "More Wi-Fi settings", color = pal.muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).clickable { actions.start(Intent(Settings.ACTION_WIFI_SETTINGS)) }.padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WifiRow(
    n: WifiNet, pal: Pal, hl: Color, hlBrush: Brush,
    expanded: Boolean, connecting: Boolean,
    onTap: () -> Unit, onConnect: (String) -> Unit, onDisconnect: () -> Unit, onForget: () -> Unit,
) {
    val fg = pal.foreground
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (n.connected) hlBrush else SolidColor(fg.copy(alpha = 0.06f)))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val tint = if (n.connected) Color.White else fg
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalArcs(n.level, tint, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.ssid, color = tint, fontSize = 14.sp, fontWeight = if (n.connected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    connecting -> "Connecting…"
                    n.connected -> "Connected · ${n.band}"
                    n.saved -> "Saved · ${n.band}"
                    else -> (if (n.secure) "Secured" else "Open") + " · ${n.band}"
                }
                Text(sub, color = tint.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            if (n.secure) Text("🔒", fontSize = 12.sp, color = tint)
        }
        if (expanded && n.connected) {
            Spacer(Modifier.height(8.dp))
            WifiControl.details().forEach { (k, v) ->
                Row { Text(k, color = tint.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(v, color = tint, fontSize = 12.sp) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Disconnect", tint, onDisconnect)
                if (WifiControl.savedPassword(n.ssid) != null) Chip("Forget password", tint, onForget)
            }
        } else if (expanded) {
            var pw by remember { mutableStateOf("") }
            var show by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(250)
                runCatching { focus.requestFocus() }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 14.sp),
                    cursorBrush = SolidColor(hl),
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (pw.length >= 8) onConnect(pw) }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fg.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (pw.isEmpty()) Text("Password", color = pal.muted, fontSize = 14.sp)
                        inner()
                    },
                )
                Spacer(Modifier.width(6.dp))
                Chip(if (show) "Hide" else "Show", fg) { show = !show }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Connect", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (pw.length >= 8) hl else hl.copy(alpha = 0.35f))
                        .clickable(enabled = pw.length >= 8) { onConnect(pw) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SignalArcs(level: Int, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.11f
        for (k in 1..3) {
            val r = w * 0.15f * k + sw
            drawArc(tint.copy(alpha = if (level >= k + 1) 1f else 0.25f), 225f, 90f, false, Offset(w / 2 - r, h * 0.85f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
        drawCircle(tint, sw * 0.8f, Offset(w / 2, h * 0.85f))
    }
}

// ═══════════════════════════════════════════════════════════════════ Bluetooth

@Composable
fun BluetoothPanel(pal: Pal, hl: Color, hlBrush: Brush, actions: ShadeActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by BluetoothControl.devices.collectAsState()
    val found by BluetoothControl.found.collectAsState()
    val scanning by BluetoothControl.scanning.collectAsState()
    var on by remember { mutableStateOf(BluetoothControl.enabled()) }
    var message by remember { mutableStateOf<String?>(null) }
    var openAddr by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        BluetoothControl.startWatching()
        onDispose { BluetoothControl.stopWatching() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            on = BluetoothControl.enabled()
            BluetoothControl.publish()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            message = null
        }
    }
    Column {
        PanelHeader("Bluetooth", on, pal, hl, onBack) { v ->
            scope.launch {
                message = BluetoothControl.setEnabled(context, v)
                if (message == null && ShellAccess.granted()) on = v
                if (!ShellAccess.granted()) actions.close() // Android's confirm dialog shows over the shade otherwise
            }
        }
        message?.let { Text(it, color = hl, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        ShizukuNote(pal, "switch Bluetooth on and off")
        if (!BluetoothControl.allowed()) {
            Note("AndroForge needs “Nearby devices” to show your Bluetooth devices.", pal, "Allow") {
                askFromLauncher(
                    context,
                    *if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                )
                actions.close()
            }
            return@Column
        }
        if (!on) {
            Note("Bluetooth is off.", pal)
            return@Column
        }
        Text("MY DEVICES", color = hl, fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        if (devices.isEmpty()) Note("No paired devices yet.", pal)
        devices.forEach { d ->
            BtRow(d, pal, hlBrush, expanded = openAddr == d.address, onTap = { openAddr = if (openAddr == d.address) null else d.address }) { action ->
                when (action) {
                    "connect", "disconnect" -> {
                        val why = BluetoothControl.setConnected(d, action == "connect")
                        message = if (why == null) (if (action == "connect") "Connecting to ${d.name}…" else "Disconnected ${d.name}") else "$why — opening Android's Bluetooth settings"
                        if (why != null) actions.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    "forget" -> message = if (BluetoothControl.forget(d)) "Forgot ${d.name}" else "Couldn't forget it here"
                }
                openAddr = null
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("NEW DEVICES", color = hl, fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Chip(if (scanning) "Searching…" else "Search", pal.foreground) {
                if (!BluetoothControl.canScan()) {
                    askFromLauncher(context, *if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    actions.close()
                } else {
                    BluetoothControl.scan()
                }
            }
        }
        found.forEach { d ->
            BtRow(d, pal, hlBrush, expanded = false, onTap = {
                message = BluetoothControl.pair(d) ?: "Pairing with ${d.name}… accept the code on both devices"
            }) { }
        }
        if (found.isEmpty() && scanning) Note("Put your device in pairing mode.", pal)
    }
}

@Composable
private fun BtRow(d: BtDevice, pal: Pal, hlBrush: Brush, expanded: Boolean, onTap: () -> Unit, onAction: (String) -> Unit) {
    val fg = pal.foreground
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (d.connected) hlBrush else SolidColor(fg.copy(alpha = 0.06f)))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val tint = if (d.connected) Color.White else fg
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(
                    when (d.kind) {
                        BtKind.HEADPHONES -> "🎧"
                        BtKind.SPEAKER -> "🔊"
                        BtKind.CAR -> "🚗"
                        BtKind.WATCH -> "⌚"
                        BtKind.PHONE -> "📱"
                        BtKind.COMPUTER -> "💻"
                        BtKind.INPUT -> "⌨"
                        BtKind.OTHER -> "•"
                    },
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = tint, fontSize = 14.sp, fontWeight = if (d.connected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    !d.bonded -> "Tap to pair"
                    d.connected -> "Connected" + (d.battery?.let { " · $it%" } ?: "")
                    else -> "Paired"
                }
                Text(sub, color = tint.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            d.battery?.let { b ->
                Box(Modifier.size(width = 26.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(tint.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(b / 100f).height(12.dp).background(tint))
                }
            }
        }
        if (expanded && d.bonded) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (d.connected) "Disconnect" else "Connect", tint) { onAction(if (d.connected) "disconnect" else "connect") }
                Chip("Forget", tint) { onAction("forget") }
            }
        }
    }
}
