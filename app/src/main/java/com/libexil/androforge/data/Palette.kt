package com.libexil.androforge.data

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A palette in ThemeForge's exact format, so themes move freely between the desktop
 * app and the phone: six interface roles, 16 terminal colors and a 1-8 stop gradient.
 */
@Serializable
data class ForgePalette(
    val name: String = "Palette",
    val background: String,
    val surface: String,
    val foreground: String,
    val muted: String,
    val accent: String,
    val accent2: String,
    val ansi: List<String>,
    val gradient: List<String>,
)

@Serializable
enum class PaletteSource(val label: String) {
    WALLPAPER("From wallpaper"), PRESET("Theme"), CUSTOM("My themes")
}

/** Which palette color an element uses (same names as ThemeForge's highlight / visualizer color). */
@Serializable
enum class ColorRole(val label: String) {
    GRADIENT("Gradient"), ACCENT("Accent"), ACCENT2("Second accent"), FOREGROUND("Text"),
    RED("Red"), GREEN("Green"), YELLOW("Yellow"), BLUE("Blue"), MAGENTA("Magenta"), CYAN("Cyan")
}

object Palettes {
    // ── color math (ported from ThemeForge) ─────────────────────────
    fun parse(hex: String): Int = try {
        android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (e: Exception) {
        0xFF888888.toInt()
    }

    fun hex(color: Int): String = String.format("#%06x", color and 0xFFFFFF)

    private fun adjust(hexColor: String, dl: Float): String {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(parse(hexColor), hsl)
        hsl[2] = (hsl[2] + dl).coerceIn(0f, 1f)
        return hex(ColorUtils.HSLToColor(hsl))
    }

    private fun mix(a: String, b: String, t: Float): String = hex(ColorUtils.blendARGB(parse(a), parse(b), t))

    fun isDark(hexColor: String): Boolean = ColorUtils.calculateLuminance(parse(hexColor)) < 0.4

    fun build(
        name: String, bg: String, surface: String, fg: String, muted: String, accent: String, accent2: String,
        red: String, green: String, yellow: String, blue: String, magenta: String, cyan: String,
        gradient: List<String>? = null,
    ): ForgePalette {
        val step = if (isDark(bg)) 0.07f else -0.07f
        val normal = listOf(surface, red, green, yellow, blue, magenta, cyan, mix(fg, muted, 0.3f))
        val bright = listOf(muted) + listOf(red, green, yellow, blue, magenta, cyan).map { adjust(it, step) } + fg
        val ansi = normal + bright
        val grad = gradient ?: listOf(accent2, accent, ansi[5], ansi[1]).distinct()
        return ForgePalette(name, bg, surface, fg, muted, accent, accent2, ansi, grad)
    }

    //                         bg         surface    text       muted      accent     accent2    red        green      yellow     blue       magenta    cyan
    val presets: List<ForgePalette> = listOf(
        build("Catppuccin Mocha", "#1e1e2e", "#313244", "#cdd6f4", "#6c7086", "#cba6f7", "#89b4fa", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5"),
        build("Catppuccin Latte", "#eff1f5", "#ccd0da", "#4c4f69", "#9ca0b0", "#8839ef", "#1e66f5", "#d20f39", "#40a02b", "#df8e1d", "#1e66f5", "#ea76cb", "#179299"),
        build("Tokyo Night", "#1a1b26", "#24283b", "#c0caf5", "#565f89", "#7aa2f7", "#bb9af7", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff"),
        build("Nord", "#2e3440", "#3b4252", "#eceff4", "#4c566a", "#88c0d0", "#81a1c1", "#bf616a", "#a3be8c", "#ebcb8b", "#81a1c1", "#b48ead", "#8fbcbb"),
        build("Gruvbox Dark", "#282828", "#3c3836", "#ebdbb2", "#928374", "#fe8019", "#fabd2f", "#fb4934", "#b8bb26", "#fabd2f", "#83a598", "#d3869b", "#8ec07c"),
        build("Dracula", "#282a36", "#44475a", "#f8f8f2", "#6272a4", "#bd93f9", "#ff79c6", "#ff5555", "#50fa7b", "#f1fa8c", "#bd93f9", "#ff79c6", "#8be9fd"),
        build("Rosé Pine", "#191724", "#26233a", "#e0def4", "#6e6a86", "#c4a7e7", "#ebbcba", "#eb6f92", "#31748f", "#f6c177", "#9ccfd8", "#c4a7e7", "#ebbcba"),
        build("Everforest Dark", "#2d353b", "#3d484d", "#d3c6aa", "#859289", "#a7c080", "#83c092", "#e67e80", "#a7c080", "#dbbc7f", "#7fbbb3", "#d699b6", "#83c092"),
    )

    val default: ForgePalette get() = presets[0]

    // ── wallpaper → palette ─────────────────────────────────────────
    private fun hsl(color: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
    private fun fromHsl(h: Float, s: Float, l: Float): String =
        hex(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

    fun fromColors(primary: Int, secondary: Int?, tertiary: Int?): ForgePalette {
        val p = hsl(primary)
        val hue = p[0]
        val sat = p[1]
        val s2 = secondary?.let(::hsl)
        val s3 = tertiary?.let(::hsl)
        val accent = fromHsl(hue, maxOf(sat, 0.55f).coerceAtMost(0.9f), 0.68f)
        val accent2 = if (s2 != null && kotlin.math.abs(s2[0] - hue) > 20f) {
            fromHsl(s2[0], maxOf(s2[1], 0.5f).coerceAtMost(0.9f), 0.7f)
        } else {
            fromHsl(hue + 40f, maxOf(sat, 0.5f).coerceAtMost(0.85f), 0.72f)
        }
        val tint = (sat * 0.35f).coerceIn(0.06f, 0.28f)
        val bg = fromHsl(hue, tint, 0.085f)
        val surface = fromHsl(hue, tint, 0.15f)
        val fg = fromHsl(hue, 0.18f, 0.88f)
        val muted = fromHsl(hue, 0.12f, 0.52f)
        // Terminal colors keep their meaning but lean slightly toward the wallpaper hue.
        fun ansiHue(base: Float) = base + ((hue - base + 540f) % 360f - 180f) * 0.12f
        val red = fromHsl(ansiHue(355f), 0.72f, 0.68f)
        val green = fromHsl(ansiHue(120f), 0.45f, 0.64f)
        val yellow = fromHsl(ansiHue(42f), 0.78f, 0.7f)
        val blue = fromHsl(ansiHue(215f), 0.7f, 0.7f)
        val magenta = fromHsl(ansiHue(295f), 0.55f, 0.74f)
        val cyan = fromHsl(ansiHue(180f), 0.5f, 0.66f)
        val third = s3?.let { fromHsl(it[0], maxOf(it[1], 0.5f).coerceAtMost(0.9f), 0.66f) }
        val gradient = listOfNotNull(accent2, accent, third, magenta).distinct().take(4)
        return build("From wallpaper", bg, surface, fg, muted, accent, accent2, red, green, yellow, blue, magenta, cyan, gradient)
    }

    /**
     * Colors from the wallpaper. Some phones (Samsung with live/video/theme wallpapers) don't
     * share WallpaperColors, so fall back to the system's own wallpaper-derived palette (Android 12+).
     */
    fun fromWallpaper(context: Context): ForgePalette? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val c = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                if (c != null) return fromColors(c.primaryColor.toArgb(), c.secondaryColor?.toArgb(), c.tertiaryColor?.toArgb())
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val a1 = context.getColor(android.R.color.system_accent1_400)
                val a2 = context.getColor(android.R.color.system_accent2_400)
                val a3 = context.getColor(android.R.color.system_accent3_400)
                return fromColors(a1, a2, a3)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Palette from any image: finds the strongest distinct hues (like ThemeForge's wallpaper mode). */
    fun fromBitmap(bitmap: android.graphics.Bitmap, name: String = "From image"): ForgePalette {
        val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val buckets = 24
        val weight = FloatArray(buckets)
        val sumR = FloatArray(buckets)
        val sumG = FloatArray(buckets)
        val sumB = FloatArray(buckets)
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        val hsl = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val px = small.getPixel(x, y)
            val r = android.graphics.Color.red(px)
            val g = android.graphics.Color.green(px)
            val b = android.graphics.Color.blue(px)
            avgR += r; avgG += g; avgB += b
            ColorUtils.RGBToHSL(r, g, b, hsl)
            if (hsl[1] < 0.18f || hsl[2] < 0.12f || hsl[2] > 0.92f) continue
            val i = ((hsl[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            val w = hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f))
            weight[i] += w
            sumR[i] += r * w; sumG[i] += g * w; sumB[i] += b * w
        }
        val n = (small.width * small.height).toFloat()
        val avg = android.graphics.Color.rgb((avgR / n).toInt(), (avgG / n).toInt(), (avgB / n).toInt())
        fun colorOf(i: Int) = android.graphics.Color.rgb((sumR[i] / weight[i]).toInt(), (sumG[i] / weight[i]).toInt(), (sumB[i] / weight[i]).toInt())
        val order = (0 until buckets).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
        val picks = ArrayList<Int>()
        for (i in order) {
            if (picks.none { p -> val d = kotlin.math.abs(p - i); minOf(d, buckets - d) < 3 }) picks += i
            if (picks.size == 3) break
        }
        if (small !== bitmap) small.recycle()
        val primary = picks.getOrNull(0)?.let(::colorOf) ?: avg
        return fromColors(primary, picks.getOrNull(1)?.let(::colorOf), picks.getOrNull(2)?.let(::colorOf)).copy(name = name)
    }

    /** The palette the whole launcher (and the island) draws with right now. */
    fun resolve(context: Context, s: LauncherSettings): ForgePalette = when (s.paletteSource) {
        PaletteSource.WALLPAPER -> fromWallpaper(context) ?: default
        PaletteSource.PRESET -> presets.firstOrNull { it.name == s.palettePreset } ?: default
        PaletteSource.CUSTOM -> s.customPalettes.firstOrNull { it.name == s.customPaletteName }
            ?: s.customPalettes.firstOrNull() ?: default
    }

    // ── ThemeForge theme files ──────────────────────────────────────
    private val json = Json { ignoreUnknownKeys = true }
    private val roles = listOf("background", "surface", "foreground", "muted", "accent", "accent2")
    private val hexRe = Regex("^#?[0-9a-fA-F]{6}$")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { hexRe.matches(it) }?.let { if (it.startsWith("#")) it.lowercase() else "#${it.lowercase()}" }

    private fun JsonObject.hexList(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(hexRe::matches) }
            ?.map { if (it.startsWith("#")) it.lowercase() else "#${it.lowercase()}" }

    /** Parse a palette object as ThemeForge writes it (the theme file's "palette" or a bare palette). */
    fun fromJsonObject(obj: JsonObject, fallbackName: String): ForgePalette? {
        val pal = (obj["palette"] as? JsonObject) ?: obj
        val values = roles.map { pal.str(it) ?: return null }
        val name = (obj["name"] as? JsonPrimitive)?.content ?: (pal["name"] as? JsonPrimitive)?.content ?: fallbackName
        val ansi = pal.hexList("ansi")?.takeIf { it.size == 16 }
        val gradient = pal.hexList("gradient")?.takeIf { it.size in 1..8 }
        return if (ansi != null) {
            ForgePalette(name, values[0], values[1], values[2], values[3], values[4], values[5], ansi, gradient ?: listOf(values[5], values[4], ansi[5], ansi[1]).distinct())
        } else {
            build(name, values[0], values[1], values[2], values[3], values[4], values[5], "#e06c75", "#98c379", "#e5c07b", "#61afef", "#c678dd", "#56b6c2", gradient)
        }
    }

    fun parseThemeFile(text: String, fallbackName: String = "Imported"): ForgePalette? = try {
        fromJsonObject(json.parseToJsonElement(text).jsonObject, fallbackName)
    } catch (e: Exception) {
        null
    }

    /** ThemeForge's community index (themes/index.json): {"themes":[{"name":..,"palette":{..}}]} */
    fun parseIndex(text: String): List<Pair<ForgePalette, String>> = try {
        json.parseToJsonElement(text).jsonObject["themes"]!!.jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val desc = (o["description"] as? JsonPrimitive)?.content ?: ""
            fromJsonObject(o, name)?.copy(name = name)?.let { it to desc }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun toThemeFile(p: ForgePalette): String = buildString {
        append("{\n  \"name\": \"").append(p.name.replace("\"", "'")).append("\",\n  \"author\": \"AndroForge\",\n  \"palette\": {\n")
        roles.zip(listOf(p.background, p.surface, p.foreground, p.muted, p.accent, p.accent2)).forEach { (k, v) ->
            append("    \"").append(k).append("\": \"").append(v).append("\",\n")
        }
        append("    \"ansi\": [").append(p.ansi.joinToString(", ") { "\"$it\"" }).append("],\n")
        append("    \"gradient\": [").append(p.gradient.joinToString(", ") { "\"$it\"" }).append("]\n  }\n}\n")
    }

    const val COMMUNITY_INDEX = "https://raw.githubusercontent.com/LibexiL/ThemeForge/main/themes/index.json"
}
