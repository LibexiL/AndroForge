package com.libexil.androforge.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherHost
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.LockService
import com.libexil.androforge.SystemActions
import com.libexil.androforge.UpdateUi
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.DockStyle
import com.libexil.androforge.data.FolderStyle
import com.libexil.androforge.data.GestureAction
import com.libexil.androforge.data.IconPacks
import com.libexil.androforge.data.IconShape
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.ThemeMode
import com.libexil.androforge.data.BarBackground
import com.libexil.androforge.data.BarMode
import com.libexil.androforge.data.BatteryStyle
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.data.ForgePalette
import com.libexil.androforge.data.IslandColor
import com.libexil.androforge.data.IslandIdle
import com.libexil.androforge.data.IslandTap
import com.libexil.androforge.data.PaletteSource
import com.libexil.androforge.data.Palettes
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.PermissionBus
import com.libexil.androforge.ui.forge.EdRoleSwatches
import com.libexil.androforge.ui.forge.PaletteStrip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.collectAsState
import com.libexil.androforge.update.Updater
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val accentPresets = listOf(
    0xFF8B6CFFL, 0xFF4F8CFFL, 0xFF2BD4C5L, 0xFF46C46EL, 0xFFFFC23DL,
    0xFFFF8A3DL, 0xFFFF5A6EL, 0xFFE46BFFL, 0xFFB0B0B8L,
)

@Composable
fun SettingsScreen(vm: LauncherViewModel, host: LauncherHost, state: LauncherState, apps: List<AppInfo>) {
    val s = state.settings
    val context = LocalContext.current
    fun set(transform: (LauncherSettings) -> LauncherSettings) = vm.updateSettings(transform)
    val iconPacks = remember { IconPacks.installed(context) }
    val pal = LocalPalette.current
    val permissionTick by PermissionBus.version.collectAsState()
    val mediaAccess = remember(permissionTick) { MediaHub.accessGranted(context) }
    val audioOk = remember(permissionTick) { AudioViz.hasPermission() }
    val btOk = remember(permissionTick) {
        android.os.Build.VERSION.SDK_INT < 31 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var excludeOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (excludeOpen) {
        ExcludeAppsDialog(apps, s.islandExcludedApps, onToggle = { pkg ->
            set { it.copy(islandExcludedApps = if (pkg in it.islandExcludedApps) it.islandExcludedApps - pkg else it.islandExcludedApps + pkg) }
        }) { excludeOpen = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AndroForge", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
                    Text("v${Updater.currentVersionName}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = { vm.settingsOpen = false }) { Icon(Icons.Default.Close, "Close") }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
                if (!host.isDefaultLauncher()) {
                    item {
                        Banner(
                            "AndroForge isn't your home app yet",
                            "Set it as default so the home button opens it.",
                            "Set default",
                        ) { host.requestDefaultLauncher() }
                    }
                }

                item { Section("Home screen") }
                item { IntSlider("Columns", s.columns, 3..8) { v -> set { it.copy(columns = v) } } }
                item { IntSlider("Rows", s.rows, 3..10) { v -> set { it.copy(rows = v) } } }
                item { IntSlider("Icon size", s.iconSizeDp, 36..76, suffix = "dp") { v -> set { it.copy(iconSizeDp = v) } } }
                item { SwitchRow("Show labels", s.showLabels) { v -> set { it.copy(showLabels = v) } } }
                if (s.showLabels) {
                    item { IntSlider("Label size", s.labelSizeSp, 9..16, suffix = "sp") { v -> set { it.copy(labelSizeSp = v) } } }
                    item {
                        ColorRow(
                            "Label color",
                            s.labelColor,
                            listOf(0xFFFFFFFFL, 0xFFE8E8EEL, 0xFFB8B8C0L, 0xFF111111L) + accentPresets,
                        ) { c -> set { it.copy(labelColor = c) } }
                    }
                }
                item { SwitchRow("Page indicator", s.pageIndicator) { v -> set { it.copy(pageIndicator = v) } } }
                item {
                    SwitchRow("Lock layout", s.lockLayout, "Stops icons from being moved or removed by accident") { v ->
                        set { it.copy(lockLayout = v) }
                    }
                }

                item { Section("Motion") }
                item { SwitchRow("Animations", s.animations, "Turn off for instant, no-motion transitions") { v -> set { it.copy(animations = v) } } }
                if (s.animations) {
                    item {
                        val presets = listOf(
                            Triple("Snappy", 0.6f, 0.12f),
                            Triple("Smooth", 0.8f, 0.3f),
                            Triple("Relaxed", 1.25f, 0.25f),
                            Triple("Bouncy", 0.9f, 0.7f),
                        )
                        val current = presets.firstOrNull { kotlin.math.abs(it.second - s.animScale) < 0.01f && kotlin.math.abs(it.third - s.animBounce) < 0.01f }
                        Choice("Feel", presets.map { it.first } + "Custom", current?.first ?: "Custom", { it }) { name ->
                            presets.firstOrNull { it.first == name }?.let { p -> set { it.copy(animScale = p.second, animBounce = p.third) } }
                        }
                    }
                    item {
                        FloatSlider("Speed", s.animScale, 0.3f..2f, { v ->
                            when {
                                v < 0.55f -> "Very fast"
                                v < 0.75f -> "Fast"
                                v < 1.05f -> "Normal"
                                v < 1.5f -> "Slow"
                                else -> "Very slow"
                            } + " (${(v * 100).roundToInt()}%)"
                        }) { v -> set { it.copy(animScale = (v * 20).roundToInt() / 20f) } }
                    }
                    item {
                        FloatSlider("Bounce", s.animBounce, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v ->
                            set { it.copy(animBounce = (v * 20).roundToInt() / 20f) }
                        }
                    }
                }

                item { Section("Icons") }
                item {
                    Choice("Icon shape", IconShape.entries, s.iconShape, { it.label }) { v -> set { it.copy(iconShape = v) } }
                }
                item {
                    val options = listOf<String?>(null) + iconPacks.map { it.pkg }
                    Choice(
                        "Icon pack",
                        options,
                        s.iconPack,
                        { pkg -> if (pkg == null) "System" else iconPacks.firstOrNull { it.pkg == pkg }?.label ?: pkg },
                    ) { v -> set { it.copy(iconPack = v) } }
                }
                if (iconPacks.isEmpty()) {
                    item {
                        Hint("Install any ADW/Nova-compatible icon pack to theme every icon at once. You can also change single icons from an app's Edit menu.")
                    }
                }
                item {
                    Choice("Folder style", FolderStyle.entries, s.folderStyle, { it.label }) { v -> set { it.copy(folderStyle = v) } }
                }

                item { Section("Dock") }
                item { SwitchRow("Show dock", s.dockEnabled) { v -> set { it.copy(dockEnabled = v) } } }
                if (s.dockEnabled) {
                    item { Choice("Style", DockStyle.entries, s.dockStyle, { it.label }) { v -> set { it.copy(dockStyle = v) } } }
                    item { IntSlider("Pinned icons", s.dockMaxIcons, 3..8) { v -> set { it.copy(dockMaxIcons = v) } } }
                    item { IntSlider("Dock icon size", s.dockIconSizeDp, 36..72, suffix = "dp") { v -> set { it.copy(dockIconSizeDp = v) } } }
                    item {
                        SwitchRow("Magnify on touch", s.dockMagnify, "Slide your finger along the dock, let go to launch") { v ->
                            set { it.copy(dockMagnify = v) }
                        }
                    }
                    if (s.dockMagnify) {
                        item {
                            FloatSlider("Magnification", s.dockMagnifyAmount, 1.1f..2.2f, { "${(it * 100).roundToInt()}%" }) { v ->
                                set { it.copy(dockMagnifyAmount = v) }
                            }
                        }
                    }
                    item {
                        SwitchRow("Recent apps in dock", s.dockShowRecents, "Adds your latest apps after the pinned ones") { v ->
                            set { it.copy(dockShowRecents = v) }
                        }
                    }
                    if (s.dockShowRecents) {
                        item { IntSlider("Recent slots", s.dockRecentCount, 1..4) { v -> set { it.copy(dockRecentCount = v) } } }
                    }
                    item {
                        FloatSlider("Background opacity", s.dockOpacity, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v ->
                            set { it.copy(dockOpacity = v) }
                        }
                    }
                    item { IntSlider("Corner radius", s.dockCornerDp, 0..40, suffix = "dp") { v -> set { it.copy(dockCornerDp = v) } } }
                    item { SwitchRow("Tint from theme", s.dockAdaptiveColor) { v -> set { it.copy(dockAdaptiveColor = v) } } }
                    item { SwitchRow("Accent glow", s.dockGlow) { v -> set { it.copy(dockGlow = v) } } }
                    item { SwitchRow("Bounce on launch", s.dockBounce) { v -> set { it.copy(dockBounce = v) } } }
                    item { SwitchRow("Dock labels", s.dockShowLabels) { v -> set { it.copy(dockShowLabels = v) } } }
                    item { IntSlider("Widgets under the dock", s.belowDockRows, 0..3, suffix = " rows") { v -> set { it.copy(belowDockRows = v) } } }
                    if (s.belowDockRows > 0) {
                        item { Hint("Drag any widget down there, or long-press a widget \u2192 Move under dock.") }
                    }
                }

                item { Section("App drawer") }
                item { IntSlider("Columns", s.drawerColumns, 3..7) { v -> set { it.copy(drawerColumns = v) } } }
                item {
                    FloatSlider("Background opacity", s.drawerOpacity, 0.3f..1f, { "${(it * 100).roundToInt()}%" }) { v ->
                        set { it.copy(drawerOpacity = v) }
                    }
                }
                item { SwitchRow("Suggested apps", s.drawerShowSuggestions) { v -> set { it.copy(drawerShowSuggestions = v) } } }
                item { SwitchRow("A\u2013Z scroll bar", s.drawerFastScroll, "Slide along the letters to jump through your apps") { v -> set { it.copy(drawerFastScroll = v) } } }
                item { Hint("Long-press an app \u2192 Select apps to pick several and put them in a folder at once.") }
                item { SwitchRow("Open keyboard automatically", s.drawerAutoFocusSearch) { v -> set { it.copy(drawerAutoFocusSearch = v) } } }
                item {
                    ClickRow("Hidden apps", "${state.hidden.size} hidden") { vm.hiddenAppsOpen = true }
                }

                item { Section("Palette") }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(pal.source.name, fontSize = 16.sp)
                        Text("Used by AndroForge widgets, the dock and the island", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                        Spacer(Modifier.height(8.dp))
                        PaletteStrip(pal, Modifier.fillMaxWidth())
                    }
                }
                item { Choice("Source", PaletteSource.entries, s.paletteSource, { it.label }) { v -> set { it.copy(paletteSource = v) } } }
                if (s.paletteSource == PaletteSource.PRESET) {
                    Palettes.presets.forEach { p ->
                        item(key = "preset:" + p.name) {
                            PaletteRow(Pal(p), selected = s.palettePreset == p.name) { set { it.copy(palettePreset = p.name) } }
                        }
                    }
                }
                if (s.paletteSource == PaletteSource.CUSTOM) {
                    if (s.customPalettes.isEmpty()) {
                        item { Hint("No saved themes yet. Import one from ThemeForge or browse the community themes.") }
                    }
                    s.customPalettes.forEach { p ->
                        item(key = "custom:" + p.name) {
                            PaletteRow(
                                Pal(p),
                                selected = (s.customPaletteName ?: s.customPalettes.firstOrNull()?.name) == p.name,
                                onDelete = { vm.deleteCustomPalette(p.name) },
                            ) { set { it.copy(customPaletteName = p.name) } }
                        }
                    }
                }
                item {
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { vm.paletteBrowserOpen = true }) { Text("Browse ThemeForge themes") }
                        OutlinedButton(onClick = { host.importPalette() }) { Text("Import .json") }
                        OutlinedButton(onClick = { host.exportPalette(pal.source) }) { Text("Export") }
                    }
                }
                item { Hint("Theme files are the same format ThemeForge uses, so you can move a theme between your PC and phone.") }

                item { Section("Look & feel") }
                item { Choice("Accent color", ThemeMode.entries, s.themeMode, { it.label }) { v -> set { it.copy(themeMode = v) } } }
                if (s.themeMode == ThemeMode.CUSTOM ||
                    (s.themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                ) {
                    item { ColorRow("Custom accent", s.accentColor, accentPresets) { c -> set { it.copy(accentColor = c) } } }
                }
                item { ClickRow("Wallpaper", "Pick a new wallpaper") { host.pickWallpaper() } }

                item { Section("Dynamic island & status bar") }
                if (s.barMode != BarMode.OFF && !LockService.isRunning) {
                    item {
                        Banner(
                            "Turn on AndroForge system features",
                            "Android only lets an accessibility service draw over the status bar. It doesn't read your screen.",
                            "Open accessibility",
                        ) { host.openAccessibility() }
                    }
                }
                if (s.barMode != BarMode.OFF && !mediaAccess) {
                    item {
                        Banner(
                            "Allow notification access",
                            "Needed for music, notification peeks and the notification icons in your bar.",
                            "Allow",
                        ) { host.openNotificationAccess() }
                    }
                }
                if (s.barMode != BarMode.OFF && s.islandVisualizer && !audioOk) {
                    item {
                        Banner(
                            "Let the visualizer hear your music",
                            "Audio access lets the island and widgets react to what's playing. Nothing is recorded or saved.",
                            "Allow",
                        ) { host.requestPermission(android.Manifest.permission.RECORD_AUDIO) }
                    }
                }
                item { Choice("Mode", BarMode.entries, s.barMode, { it.label }) { v -> set { it.copy(barMode = v) } } }
                if (s.barMode == BarMode.ISLAND) {
                    item { Hint("Island only sits on top of Android's own status bar. Pick \u201cIsland + custom bar\u201d to cover it with AndroForge's bar.") }
                }
                if (s.barMode != BarMode.OFF) {
                    item { Choice("When idle", IslandIdle.entries, s.islandIdle, { it.label }) { v -> set { it.copy(islandIdle = v) } } }
                    item { Choice("Island color", IslandColor.entries, s.islandColor, { it.label }) { v -> set { it.copy(islandColor = v) } } }
                    item { IntSlider("Width", s.islandWidthDp, 50..240, suffix = "dp") { v -> set { it.copy(islandWidthDp = v) } } }
                    item { IntSlider("Height", s.islandHeightDp, 18..48, suffix = "dp") { v -> set { it.copy(islandHeightDp = v) } } }
                    item {
                        SwitchRow("Center on the camera", s.islandFollowCamera, "Lines the island up with your front camera hole") { v ->
                            set { it.copy(islandFollowCamera = v) }
                        }
                    }
                    item { IntSlider("Move sideways", s.islandOffsetX, -160..160, suffix = "dp") { v -> set { it.copy(islandOffsetX = v) } } }
                    item { IntSlider("Move down", s.islandOffsetY, -12..40, suffix = "dp") { v -> set { it.copy(islandOffsetY = v) } } }
                    item { SwitchRow("Music", s.islandMusic, "Album art + visualizer; tap to expand the player") { v -> set { it.copy(islandMusic = v) } } }
                    item { SwitchRow("Visualizer in island", s.islandVisualizer) { v -> set { it.copy(islandVisualizer = v) } } }
                    item { SwitchRow("Charging & low battery", s.islandCharging) { v -> set { it.copy(islandCharging = v) } } }
                    item { SwitchRow("Calls & alarms", s.islandCalls, "Answer or decline from the island; call timer while you talk") { v -> set { it.copy(islandCalls = v) } } }
                    item { SwitchRow("Live activities", s.islandLiveActivities, "Timers, stopwatch, recording, downloads, navigation") { v -> set { it.copy(islandLiveActivities = v) } } }
                    item { SwitchRow("Device alerts", s.islandDeviceAlerts, "Headphones connected, silent / vibrate / ring") { v -> set { it.copy(islandDeviceAlerts = v) } } }
                    if (s.islandDeviceAlerts && android.os.Build.VERSION.SDK_INT >= 31 && !btOk) {
                        item {
                            Banner(
                                "Show earbud battery",
                                "Allow \u201cNearby devices\u201d so the island can show your Bluetooth headphones' battery.",
                                "Allow",
                            ) { host.requestPermission(android.Manifest.permission.BLUETOOTH_CONNECT) }
                        }
                    }
                    item { SwitchRow("Second bubble", s.islandBubble, "A small extra bubble when two things run at once") { v -> set { it.copy(islandBubble = v) } } }
                    item { Choice("Tap the idle island", IslandTap.entries, s.islandTapAction, { it.label }) { v -> set { it.copy(islandTapAction = v) } } }
                    item { Section("Smart alerts") }
                    item { SwitchRow("Notification pop-ups", s.islandNotifications, "Tap to open, swipe up or \u2715 to dismiss") { v -> set { it.copy(islandNotifications = v) } } }
                    if (s.islandNotifications) {
                        item { SwitchRow("Action buttons", s.islandPeekActions, "Reply, mark as read, mute\u2026 right from the pop-up") { v -> set { it.copy(islandPeekActions = v) } } }
                        item { IntSlider("Show for", s.islandPeekSeconds, 2..12, suffix = " s") { v -> set { it.copy(islandPeekSeconds = v) } } }
                        item { ClickRow("Apps that don't pop up", if (s.islandExcludedApps.isEmpty()) "None" else "${s.islandExcludedApps.size} apps") { excludeOpen = true } }
                        item { Hint("Getting two pop-ups? Turn off \u201cPop on screen\u201d (or floating notifications) for those apps in Android's notification settings.") }
                    }
                    item { SwitchRow("Palette glow", s.islandGlow) { v -> set { it.copy(islandGlow = v) } } }
                    item { SwitchRow("Hide in landscape", s.islandHideLandscape, "Keeps games and videos clear") { v -> set { it.copy(islandHideLandscape = v) } } }
                }
                if (s.barMode == BarMode.ISLAND_BAR) {
                    item { Section("Custom status bar") }
                    item { Hint("Covers the system status bar with your own. Swiping down still opens notifications.") }
                    item { Choice("Background", BarBackground.entries, s.barBackground, { it.label }) { v -> set { it.copy(barBackground = v) } } }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            EdRoleSwatches("Text color", pal, s.barText, listOf(ColorRole.FOREGROUND, ColorRole.ACCENT, ColorRole.ACCENT2, ColorRole.RED, ColorRole.GREEN, ColorRole.YELLOW, ColorRole.BLUE, ColorRole.MAGENTA, ColorRole.CYAN)) { v ->
                                set { it.copy(barText = v) }
                            }
                        }
                    }
                    item { SwitchRow("Seconds", s.barSeconds) { v -> set { it.copy(barSeconds = v) } } }
                    item {
                        Choice("Clock", listOf<Boolean?>(null, true, false), s.barTwelveHour, { if (it == null) "System" else if (it) "12-hour" else "24-hour" }) { v ->
                            set { it.copy(barTwelveHour = v) }
                        }
                    }
                    item { SwitchRow("Date", s.barShowDate) { v -> set { it.copy(barShowDate = v) } } }
                    item { Choice("Battery", BatteryStyle.entries, s.barBattery, { it.label }) { v -> set { it.copy(barBattery = v) } } }
                    item { SwitchRow("Notification icons", s.barNotificationIcons) { v -> set { it.copy(barNotificationIcons = v) } } }
                    item { SwitchRow("Wi-Fi / mobile icon", s.barNetwork) { v -> set { it.copy(barNetwork = v) } } }
                }

                item { Section("Gestures") }
                item { Choice("Swipe up", GestureAction.entries, s.swipeUp, { it.label }) { v -> set { it.copy(swipeUp = v) } } }
                item { Choice("Swipe down", GestureAction.entries, s.swipeDown, { it.label }) { v -> set { it.copy(swipeDown = v) } } }
                item { Choice("Double tap", GestureAction.entries, s.doubleTap, { it.label }) { v -> set { it.copy(doubleTap = v) } } }
                val usesLock = listOf(s.swipeUp, s.swipeDown, s.doubleTap).contains(GestureAction.LOCK_SCREEN)
                if (usesLock && !LockService.isRunning) {
                    item {
                        Banner(
                            "Screen lock needs one permission",
                            "Turn on “AndroForge screen lock” in Accessibility. It only locks the screen.",
                            "Open settings",
                        ) { SystemActions.openAccessibilitySettings(context) }
                    }
                }

                item { Section("Updates") }
                item {
                    SwitchRow("Check for updates automatically", s.autoCheckUpdates, "Looks for new releases on GitHub every few hours") { v ->
                        set { it.copy(autoCheckUpdates = v) }
                    }
                }
                item {
                    val ui = vm.update
                    val status = when (ui) {
                        UpdateUi.Idle -> if (state.lastUpdateCheck > 0) "Last checked " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.lastUpdateCheck)) else "Not checked yet"
                        UpdateUi.Checking -> "Checking…"
                        UpdateUi.UpToDate -> "You're on the latest version"
                        is UpdateUi.Available -> "Version ${ui.info.versionName} is available"
                        is UpdateUi.Downloading -> "Downloading ${ui.info.versionName}…"
                        is UpdateUi.Installing -> "Installing…"
                        is UpdateUi.Error -> "Couldn't check: ${ui.message}"
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Installed: ${Updater.currentVersionName}", fontSize = 14.sp)
                        Text(status, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                val current = vm.update
                                if (current is UpdateUi.Available) vm.showUpdateDialog = true else vm.checkForUpdates(manual = true)
                            }) { Text(if (ui is UpdateUi.Available) "Update now" else "Check now") }
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASES_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text("Release notes") }
                        }
                    }
                }

                item { Section("Backup") }
                item { ClickRow("Export layout", "Save your home screen, dock and settings to a file") { host.exportLayout() } }
                item { ClickRow("Import layout", "Restore from a file (widgets need re-adding)") { host.importLayout() } }

                item { Section("About") }
                item {
                    Hint(
                        "AndroForge • ${apps.size} apps • ${state.items.size} home items\n" +
                            "Long-press the home screen for widgets, pages and wallpaper. Drop an app onto another to make a folder.",
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- building blocks

@Composable
private fun PaletteRow(pal: Pal, selected: Boolean, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pal.source.name, fontSize = 15.sp, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
                if (selected) Text("  \u2713", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            PaletteStrip(pal, Modifier.fillMaxWidth())
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "Delete theme") }
        }
    }
}

@Composable
private fun ExcludeAppsDialog(apps: List<AppInfo>, excluded: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    val byPkg = remember(apps) { apps.distinctBy { it.key.pkg } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("Don't pop up for", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(byPkg.size) { i ->
                        val app = byPkg[i]
                        val on = app.key.pkg in excluded
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.key.pkg) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { onToggle(app.key.pkg) })
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Done") }
            }
        }
    }
}

/** ThemeForge's community themes, fetched straight from its GitHub repo. */
@Composable
fun PaletteBrowser(vm: LauncherViewModel) {
    val themes by androidx.compose.runtime.produceState<List<Pair<ForgePalette, String>>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val c = java.net.URL(Palettes.COMMUNITY_INDEX).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 12_000
                c.readTimeout = 15_000
                try {
                    Palettes.parseIndex(c.inputStream.bufferedReader().readText())
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.paletteBrowserOpen = false }) {
        androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("ThemeForge themes", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(
                    "Community themes from the ThemeForge repo. Tap one to add it.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                val list = themes
                when {
                    list == null -> Text("Loading\u2026", modifier = Modifier.padding(20.dp))
                    list.isEmpty() -> Text("Couldn't load the theme list. Check your connection.", modifier = Modifier.padding(20.dp))
                    else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(list.size) { i ->
                            val (p, desc) = list[i]
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.addCustomPalette(p)
                                        vm.paletteBrowserOpen = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(p.name, fontSize = 15.sp)
                                if (desc.isNotBlank()) Text(desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                                Spacer(Modifier.height(6.dp))
                                PaletteStrip(Pal(p), Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                TextButton(onClick = { vm.paletteBrowserOpen = false }, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun Banner(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
    ) {
        Text(title, fontSize = 15.sp)
        Text(body, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, fontSize = 15.sp)
        Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun IntSlider(title: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}$suffix", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun FloatSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(format(local), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun ColorRow(title: String, selected: Long, colors: List<Long>, onSelect: (Long) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colors.distinct().forEach { c ->
                ColorDot(Color(c), selected = Color(c).toArgb() == Color(selected).toArgb()) { onSelect(c) }
            }
        }
    }
}
