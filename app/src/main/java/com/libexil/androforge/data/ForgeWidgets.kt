package com.libexil.androforge.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** AndroForge's own widgets, drawn in the palette (like ThemeForge's panels). */
@Serializable
enum class ForgeKind(val label: String, val title: String, val w: Int, val h: Int, val blurb: String) {
    CLOCK("Clock", "", 4, 2, "Digital with a sweeping seconds ring, plain digital, stacked or analog"),
    MUSIC("Now playing", "NOW PLAYING", 4, 2, "Album art, progress, controls; pulses with the bass"),
    VISUALIZER("Music visualizer", "", 4, 1, "cava-style bars, LED blocks, waves or a radial ring"),
    WEATHER("Weather", "WEATHER", 4, 2, "Animated icons and a forecast"),
    SYSTEM("System gauges", "SYSTEM", 4, 2, "Battery, memory and storage gauges"),
    CALENDAR("Calendar", "", 4, 3, "Month view you can page through; today glows"),
    COUNTDOWN("Countdown", "COUNTDOWN", 4, 2, "Days, hours, minutes and seconds to any date"),
    TODO("To-do", "TO-DO", 4, 2, "A checklist on your home screen - tap to tick"),
    SHELF("App shelf", "", 4, 1, "Apps you pick, like your games, that lift and glow"),
    NETWORK("Network speed", "NETWORK", 4, 2, "Live download and upload graph"),
    GREETING("Greeting", "", 4, 1, "Good morning / afternoon / evening with the date"),
    SEARCH("Search bar", "", 4, 1, "Tap to search your apps or the web"),
    BATTERY("Battery", "BATTERY", 2, 2, "Big charge ring with time-to-full while charging"),
}

@Serializable
data class TodoItem(val text: String, val done: Boolean = false)

@Serializable
enum class PanelBackground(val label: String) {
    SOLID("Solid"), GRADIENT("Gradient"), FROSTED("Frosted"), OUTLINE("Outline"), NONE("None")
}

@Serializable
enum class PanelFont(val label: String) { DEFAULT("Default"), MONO("Mono"), SERIF("Serif") }

@Serializable
enum class ClockStyle(val label: String) { RING("Ring"), DIGITAL("Digital"), STACKED("Stacked"), ANALOG("Analog") }

@Serializable
enum class ArtStyle(val label: String) { SQUARE("Square"), RECORD("Spinning record"), NONE("No art") }

@Serializable
enum class VizStyle(val label: String) {
    BARS("Bars"), MIRROR("Mirrored bars"), BLOCKS("LED blocks"), WAVE("Wave"),
    MIRRORWAVE("Mirrored wave"), RADIAL("Radial ring"), DOTS("Dot columns")
}

@Serializable
enum class GaugeStyle(val label: String) { ARCS("Arc gauges"), BARS("Bars") }

@Serializable
enum class TempUnits(val label: String) { US("°F, mph"), METRIC("°C, km/h") }

@Serializable
data class WeatherPlace(val name: String, val lat: Double, val lon: Double)

/** Look options every AndroForge widget shares (same set as ThemeForge panels). */
@Serializable
data class PanelLook(
    val background: PanelBackground = PanelBackground.SOLID,
    val opacity: Float = 0.75f,
    val radius: Int = 22,
    val border: Boolean = true,
    val glow: Boolean = false,
    val pulse: Boolean = true,
    val highlight: ColorRole = ColorRole.ACCENT,
    val showTitle: Boolean = true,
    val title: String = "",
    val font: PanelFont = PanelFont.DEFAULT,
    val textScale: Float = 1f,
)

@Serializable
data class ForgeOptions(
    // clock
    val clockStyle: ClockStyle = ClockStyle.RING,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val twelveHour: Boolean? = null, // null = follow the system
    // music
    val artStyle: ArtStyle = ArtStyle.SQUARE,
    val showControls: Boolean = true,
    val showProgress: Boolean = true,
    val musicVisualizer: Boolean = true,
    // visualizer
    val vizStyle: VizStyle = VizStyle.BARS,
    val bars: Int = 40,
    val gap: Float = 0.25f,
    val rounded: Boolean = true,
    val peaks: Boolean = true,
    val vizColor: ColorRole = ColorRole.GRADIENT,
    // weather
    val place: WeatherPlace? = null, // null = device location
    val units: TempUnits? = null, // null = follow locale
    val forecastDays: Int = 3,
    // system
    val gaugeStyle: GaugeStyle = GaugeStyle.ARCS,
    // calendar
    val mondayFirst: Boolean = false,
    // countdown (0 = next New Year)
    val countdownTitle: String = "New Year",
    val countdownTarget: Long = 0L,
    // to-do
    val todos: List<TodoItem> = listOf(TodoItem("Long-press \u2192 Customize to edit this list")),
    val hideDone: Boolean = false,
    // app shelf
    val shelfApps: List<AppKey> = emptyList(),
    val shelfLabels: Boolean = true,
    // greeting
    val greetingName: String = "",
    // search
    val searchHint: String = "Search apps & the web",
)

@Serializable
@SerialName("forge")
data class ForgeWidgetItem(
    override val id: String,
    override val page: Int = 0,
    override val x: Int = 0,
    override val y: Int = 0,
    override val spanX: Int = 4,
    override val spanY: Int = 2,
    val kind: ForgeKind,
    val look: PanelLook = PanelLook(),
    val options: ForgeOptions = ForgeOptions(),
) : HomeItem()

fun defaultLook(kind: ForgeKind): PanelLook = when (kind) {
    ForgeKind.CLOCK -> PanelLook(background = PanelBackground.NONE, border = false, showTitle = false)
    ForgeKind.VISUALIZER -> PanelLook(background = PanelBackground.NONE, border = false, showTitle = false)
    ForgeKind.GREETING -> PanelLook(background = PanelBackground.NONE, border = false, showTitle = false)
    ForgeKind.SHELF -> PanelLook(background = PanelBackground.FROSTED, showTitle = false, radius = 26)
    ForgeKind.SEARCH -> PanelLook(background = PanelBackground.FROSTED, showTitle = false, radius = 40)
    ForgeKind.CALENDAR -> PanelLook(showTitle = false)
    else -> PanelLook()
}

fun HomeItem.resized(spanX: Int, spanY: Int): HomeItem = when (this) {
    is WidgetItem -> copy(spanX = spanX, spanY = spanY)
    is ForgeWidgetItem -> copy(spanX = spanX, spanY = spanY)
    else -> this
}

val HomeItem.isResizable: Boolean get() = this is WidgetItem || this is ForgeWidgetItem
