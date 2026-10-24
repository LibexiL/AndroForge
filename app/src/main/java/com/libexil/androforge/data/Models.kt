package com.libexil.androforge.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Identifies one launchable activity for one user profile (work profiles get their own serial). */
@Serializable
data class AppKey(val pkg: String, val cls: String, val user: Long = 0L) {
    val id: String get() = "$pkg/$cls#$user"
}

/** Something placed on a home page or in the dock. */
@Serializable
sealed class HomeItem {
    abstract val id: String
    abstract val page: Int
    abstract val x: Int
    abstract val y: Int
    abstract val spanX: Int
    abstract val spanY: Int
}

@Serializable
@SerialName("app")
data class AppItem(
    override val id: String,
    override val page: Int = 0,
    override val x: Int = 0,
    override val y: Int = 0,
    val app: AppKey,
) : HomeItem() {
    override val spanX: Int get() = 1
    override val spanY: Int get() = 1
}

@Serializable
@SerialName("folder")
data class FolderItem(
    override val id: String,
    override val page: Int = 0,
    override val x: Int = 0,
    override val y: Int = 0,
    val name: String = "Folder",
    val apps: List<AppKey> = emptyList(),
    val color: Long? = null,
) : HomeItem() {
    override val spanX: Int get() = 1
    override val spanY: Int get() = 1
}

@Serializable
@SerialName("widget")
data class WidgetItem(
    override val id: String,
    override val page: Int = 0,
    override val x: Int = 0,
    override val y: Int = 0,
    override val spanX: Int = 2,
    override val spanY: Int = 2,
    val widgetId: Int,
    val provider: String,
) : HomeItem()

fun HomeItem.moved(page: Int, x: Int, y: Int): HomeItem = when (this) {
    is AppItem -> copy(page = page, x = x, y = y)
    is FolderItem -> copy(page = page, x = x, y = y)
    is WidgetItem -> copy(page = page, x = x, y = y)
    is ForgeWidgetItem -> copy(page = page, x = x, y = y)
}

fun HomeItem.covers(page: Int, cx: Int, cy: Int): Boolean =
    this.page == page && cx >= x && cx < x + spanX && cy >= y && cy < y + spanY

@Serializable
data class IconRef(val pack: String, val drawable: String)

@Serializable
enum class IconShape(val label: String) {
    SYSTEM("System"), CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded"),
    SQUARE("Square"), TEARDROP("Teardrop"), HEXAGON("Hexagon")
}

@Serializable
enum class DockStyle(val label: String) {
    FLOATING("Floating"), GLASS("Glass"), BAR("Bar"), MINIMAL("Minimal")
}

@Serializable
enum class GestureAction(val label: String) {
    NONE("Nothing"), APP_DRAWER("App drawer"), SEARCH("Search apps"),
    NOTIFICATIONS("Notifications"), QUICK_SETTINGS("Quick settings"),
    LOCK_SCREEN("Lock screen"), LAUNCHER_SETTINGS("Launcher settings")
}

@Serializable
enum class ThemeMode(val label: String) {
    PALETTE("Palette accent"), WALLPAPER("From wallpaper"), MATERIAL_YOU("Material You"), CUSTOM("Custom")
}

@Serializable
enum class BarMode(val label: String) {
    OFF("Off"), ISLAND("Island only"), ISLAND_BAR("Island + custom bar")
}

@Serializable
enum class IslandIdle(val label: String) { PILL("Pill"), DOT("Camera ring"), HIDDEN("Hidden") }

@Serializable
enum class IslandColor(val label: String) { BLACK("Black"), PALETTE("Palette"), GRADIENT("Gradient"), GLASS("Glass") }

/** How the custom status bar is drawn. */
@Serializable
enum class BarStyle(val label: String) {
    SOLID("Solid"), GLASS("Glass"), CLEAR("Clear"), NEON("Neon line"), FLOATING("Floating pill"), SPLIT("Split pills")
}

@Serializable
enum class ClockPosition(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

@Serializable
enum class ShadeMode(val label: String) { ANDROID("Android's"), FORGE("AndroForge") }

@Serializable
enum class ShadeStyle(val label: String) { GLASS("Glass"), SOLID("Solid"), GRADIENT("Gradient"), CLEAR("Clear") }

@Serializable
enum class TileShape(val label: String) { ROUNDED("Rounded"), CIRCLE("Circle"), SQUIRCLE("Squircle"), PILL("Pill") }

@Serializable
enum class ShadeTile(val label: String) {
    WIFI("Wi-Fi"), BLUETOOTH("Bluetooth"), DATA("Internet"), FLASHLIGHT("Flashlight"), RING("Sound"),
    DND("Do not disturb"), ROTATE("Auto-rotate"), LOCATION("Location"), HOTSPOT("Hotspot"),
    SCREENSHOT("Screenshot"), LOCK("Lock"), POWER("Power menu"), SETTINGS("Settings"), ANDROID("Android panel")
}

@Serializable
enum class IslandTap(val label: String) { GLANCE("Glance panel"), SHADE("Notification shade") }

@Serializable
enum class BarBackground(val label: String) { BLACK("Black"), PALETTE("Palette"), GRADIENT("Gradient") }

@Serializable
enum class BatteryStyle(val label: String) { ICON_PERCENT("Icon + %"), ICON("Icon"), PERCENT("%"), BAR("Line") }

@Serializable
enum class FolderStyle(val label: String) {
    GRID("Grid"), STACK("Stack")
}

@Serializable
data class LauncherSettings(
    // Home grid
    val columns: Int = 5,
    val rows: Int = 6,
    val iconSizeDp: Int = 54,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 12,
    val labelColor: Long = 0xFFFFFFFF,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconPack: String? = null,
    val pageIndicator: Boolean = true,
    val lockLayout: Boolean = false,
    // Motion
    val animations: Boolean = true,
    val animScale: Float = 0.8f,
    val animBounce: Float = 0.3f,
    val stretchScroll: Boolean = true,
    // Widgets under the dock (0 = off)
    val belowDockRows: Int = 0,
    val folderStyle: FolderStyle = FolderStyle.GRID,
    // Dock
    val dockEnabled: Boolean = true,
    val dockStyle: DockStyle = DockStyle.FLOATING,
    val dockMaxIcons: Int = 5,
    val dockIconSizeDp: Int = 52,
    val dockMagnify: Boolean = true,
    val dockMagnifyAmount: Float = 1.55f,
    val dockShowRecents: Boolean = true,
    val dockRecentCount: Int = 2,
    val dockOpacity: Float = 0.45f,
    val dockCornerDp: Int = 30,
    val dockAdaptiveColor: Boolean = true,
    val dockShowLabels: Boolean = false,
    val dockBounce: Boolean = true,
    val dockGlow: Boolean = true,
    // Drawer
    val drawerColumns: Int = 5,
    val drawerOpacity: Float = 0.88f,
    val drawerShowSuggestions: Boolean = true,
    val drawerAutoFocusSearch: Boolean = false,
    val drawerFastScroll: Boolean = true,
    // Theme
    val themeMode: ThemeMode = ThemeMode.PALETTE,
    // Palette (ThemeForge format)
    val paletteSource: PaletteSource = PaletteSource.WALLPAPER,
    val palettePreset: String = "Catppuccin Mocha",
    val customPalettes: List<ForgePalette> = emptyList(),
    val customPaletteName: String? = null,
    // Dynamic island & status bar
    val barMode: BarMode = BarMode.ISLAND,
    val islandIdle: IslandIdle = IslandIdle.PILL,
    val islandColor: IslandColor = IslandColor.BLACK,
    val islandWidthDp: Int = 110,
    val islandHeightDp: Int = 30,
    val islandOffsetX: Int = 0,
    val islandOffsetY: Int = 0,
    val islandFollowCamera: Boolean = true,
    val islandMusic: Boolean = true,
    val islandVisualizer: Boolean = true,
    val islandCharging: Boolean = true,
    val islandNotifications: Boolean = true,
    val islandHideLandscape: Boolean = true,
    val islandGlow: Boolean = true,
    val islandCalls: Boolean = true,
    val islandLiveActivities: Boolean = true,
    val islandDeviceAlerts: Boolean = true,
    val islandPeekSeconds: Int = 5,
    val islandPeekActions: Boolean = true,
    val islandTapAction: IslandTap = IslandTap.GLANCE,
    val islandExcludedApps: Set<String> = emptySet(),
    val islandBubble: Boolean = true,
    val barBackground: BarBackground = BarBackground.BLACK,
    val barText: ColorRole = ColorRole.FOREGROUND,
    val barSeconds: Boolean = false,
    val barTwelveHour: Boolean? = null,
    val barShowDate: Boolean = false,
    val barBattery: BatteryStyle = BatteryStyle.ICON_PERCENT,
    val barNotificationIcons: Boolean = true,
    val barNetwork: Boolean = true,
    val barHeightExtraDp: Int = 0,
    val barStyle: BarStyle = BarStyle.SOLID,
    val barOpacity: Float = 1f,
    val barFont: PanelFont = PanelFont.DEFAULT,
    val barTextSize: Int = 14,
    val barBold: Boolean = true,
    val barClock: ClockPosition = ClockPosition.LEFT,
    val barSidePadding: Int = 22,
    val barCorner: Int = 18,
    // Pull-down shade
    val shadeMode: ShadeMode = ShadeMode.ANDROID,
    val shadeStyle: ShadeStyle = ShadeStyle.GLASS,
    val shadeOpacity: Float = 0.9f,
    val shadeCorner: Int = 30,
    val shadeTileShape: TileShape = TileShape.ROUNDED,
    val shadeColumns: Int = 4,
    val shadeTiles: List<ShadeTile> = listOf(
        ShadeTile.WIFI, ShadeTile.BLUETOOTH, ShadeTile.FLASHLIGHT, ShadeTile.RING,
        ShadeTile.DND, ShadeTile.ROTATE, ShadeTile.SCREENSHOT, ShadeTile.ANDROID,
    ),
    val shadeMedia: Boolean = true,
    val shadeBrightness: Boolean = true,
    val shadeHighlight: ColorRole = ColorRole.ACCENT,
    // Override Android's own bar (needs WRITE_SECURE_SETTINGS via adb)
    val hideSystemPopups: Boolean = false,
    val hideSystemIcons: Boolean = false,
    val accentColor: Long = 0xFF8B6CFF,
    // Gestures
    val swipeUp: GestureAction = GestureAction.APP_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.LOCK_SCREEN,
    // Updates
    val autoCheckUpdates: Boolean = true,
)

@Serializable
data class LauncherState(
    val pageCount: Int = 2,
    val items: List<HomeItem> = emptyList(),
    val dock: List<HomeItem> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val labels: Map<String, String> = emptyMap(),
    val icons: Map<String, IconRef> = emptyMap(),
    val recents: List<AppKey> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
    val settings: LauncherSettings = LauncherSettings(),
    val initialized: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val skippedVersion: Int = 0,
)
