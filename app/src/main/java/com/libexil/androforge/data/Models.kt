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
    WALLPAPER("From wallpaper"), MATERIAL_YOU("Material You"), CUSTOM("Custom")
}

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
    // Theme
    val themeMode: ThemeMode = ThemeMode.WALLPAPER,
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
