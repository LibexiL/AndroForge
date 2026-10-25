package com.libexil.androforge.data

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
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

    /**
     * Builds a dark palette that keeps the source's mood: muted images give muted accents,
     * vivid ones stay vivid. [dark]/[light] are the image's own shadow / highlight tones when known.
     */
    private fun fromTones(primary: Int, secondary: Int?, tertiary: Int?, dark: Int? = null, light: Int? = null, name: String = "From wallpaper"): ForgePalette {
        val p = hsl(primary)
        val hue = p[0]
        val sat = p[1]
        fun accentSat(s: Float) = (s * 1.5f + 0.12f).coerceIn(0.22f, 0.82f)
        val accent = fromHsl(hue, accentSat(sat), 0.64f)
        val s2 = secondary?.let(::hsl)?.takeIf { val d = kotlin.math.abs(it[0] - hue); minOf(d, 360f - d) > 18f }
        val accent2 = if (s2 != null) fromHsl(s2[0], accentSat(s2[1]), 0.7f) else fromHsl(hue + 28f, accentSat(sat) * 0.85f, 0.72f)
        val s3 = tertiary?.let(::hsl)
        val third = s3?.let { fromHsl(it[0], accentSat(it[1]), 0.6f) }
        val d = dark?.let(::hsl)
        val bgHue = d?.get(0) ?: hue
        val bgSat = (d?.get(1) ?: sat * 0.5f).coerceIn(0.04f, 0.32f)
        val bg = fromHsl(bgHue, bgSat, 0.085f)
        val surface = fromHsl(bgHue, bgSat, 0.15f)
        val l = light?.let(::hsl)
        val fg = fromHsl(l?.get(0) ?: hue, (l?.get(1) ?: 0.12f).coerceAtMost(0.18f), 0.88f)
        val muted = fromHsl(l?.get(0) ?: hue, 0.1f, 0.55f)
        // Terminal colors keep their meaning but lean toward the image and match its saturation.
        val ansiSat = (sat * 1.3f + 0.25f).coerceIn(0.3f, 0.72f)
        fun ansiHue(base: Float) = base + ((hue - base + 540f) % 360f - 180f) * 0.15f
        val red = fromHsl(ansiHue(355f), ansiSat, 0.66f)
        val green = fromHsl(ansiHue(120f), ansiSat * 0.8f, 0.62f)
        val yellow = fromHsl(ansiHue(42f), ansiSat, 0.68f)
        val blue = fromHsl(ansiHue(215f), ansiSat, 0.68f)
        val magenta = fromHsl(ansiHue(295f), ansiSat * 0.85f, 0.72f)
        val cyan = fromHsl(ansiHue(180f), ansiSat * 0.85f, 0.64f)
        val gradient = listOfNotNull(accent2, accent, third ?: fromHsl(hue - 24f, accentSat(sat), 0.56f)).distinct()
        return build(name, bg, surface, fg, muted, accent, accent2, red, green, yellow, blue, magenta, cyan, gradient)
    }

    fun fromColors(primary: Int, secondary: Int?, tertiary: Int?): ForgePalette = fromTones(primary, secondary, tertiary)

    /** The actual wallpaper image, when the phone lets us read it. */
    fun wallpaperBitmap(context: Context): android.graphics.Bitmap? = try {
        val wm = WallpaperManager.getInstance(context)
        @Suppress("MissingPermission")
        val d = wm.drawable ?: wm.peekDrawable()
        d?.let { it.toBitmap(128, (128f * it.intrinsicHeight.coerceAtLeast(1) / it.intrinsicWidth.coerceAtLeast(1)).toInt().coerceIn(32, 512)) }
    } catch (e: Throwable) {
        null
    }

    private var cachedId = -2

    fun clearWallpaperCache() = synchronized(this) { cachedId = -2; cached = null }

    /** Bumped when the wallpaper changes, so everything drawing the palette recomputes. */
    val wallpaperTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var seenWallpaperId = Int.MIN_VALUE

    /** Call on wallpaper broadcasts and when the launcher resumes. Returns true if it really changed. */
    fun checkWallpaperChanged(context: Context): Boolean {
        val id = try {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } catch (e: Throwable) {
            -1
        }
        val first = seenWallpaperId == Int.MIN_VALUE
        if (!first && id == seenWallpaperId && id >= 0) return false
        seenWallpaperId = id
        if (first) return false
        clearWallpaperCache()
        wallpaperTick.value = wallpaperTick.value + 1
        return true
    }
    private var cached: ForgePalette? = null

    /**
     * Colors from the wallpaper, best source first: the wallpaper image itself, then the colors
     * Android shares, then (last resort) the system's palette.
     */
    fun fromWallpaper(context: Context, allowSystemPalette: Boolean = true): ForgePalette? {
        val wm = WallpaperManager.getInstance(context)
        val id = try { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) } catch (e: Throwable) { -1 }
        synchronized(this) { if (id >= 0 && id == cachedId) cached?.let { return it } }
        val fromImage = wallpaperBitmap(context)?.let { fromBitmap(it, "From wallpaper") }
        if (fromImage != null) {
            synchronized(this) {
                cachedId = id
                cached = fromImage
            }
            return fromImage
        }
        val result = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    val c = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                    c?.let { fromColors(it.primaryColor.toArgb(), it.secondaryColor?.toArgb(), it.tertiaryColor?.toArgb()) }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            })
            ?: (if (allowSystemPalette && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    fromColors(
                        context.getColor(android.R.color.system_accent1_400),
                        context.getColor(android.R.color.system_accent2_400),
                        context.getColor(android.R.color.system_accent3_400),
                    )
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            })
        return result
    }

    /** True when we can read the real wallpaper image (best results). */
    fun canReadWallpaper(context: Context): Boolean = wallpaperBitmap(context) != null

    /** Palette from any image: its strongest distinct hues, plus its own shadow and highlight tones. */
    fun fromBitmap(bitmap: android.graphics.Bitmap, name: String = "From image"): ForgePalette {
        val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val buckets = 36
        val weight = FloatArray(buckets)
        val sumR = FloatArray(buckets)
        val sumG = FloatArray(buckets)
        val sumB = FloatArray(buckets)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val hsl = FloatArray(3)
        val lum = FloatArray(px.size)
        var satSum = 0f
        for ((k, c) in px.withIndex()) {
            val r = android.graphics.Color.red(c)
            val g = android.graphics.Color.green(c)
            val b = android.graphics.Color.blue(c)
            ColorUtils.RGBToHSL(r, g, b, hsl)
            lum[k] = hsl[2]
            satSum += hsl[1]
            if (hsl[1] < 0.06f || hsl[2] < 0.06f || hsl[2] > 0.95f) continue
            val i = ((hsl[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            // Favour colourful mid-tones, but let large muted areas still count.
            val w = (hsl[1] * hsl[1] + 0.02f) * (1f - kotlin.math.abs(hsl[2] - 0.5f) * 1.4f).coerceAtLeast(0.05f)
            weight[i] += w
            sumR[i] += r * w; sumG[i] += g * w; sumB[i] += b * w
        }
        fun avgOf(sel: (Float) -> Boolean): Int? {
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for ((k, c) in px.withIndex()) if (sel(lum[k])) {
                r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
            }
            return if (n == 0) null else android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }
        val sorted = lum.sortedArray()
        val darkCut = sorted[(sorted.size * 0.3f).toInt()]
        val lightCut = sorted[(sorted.size * 0.85f).toInt().coerceAtMost(sorted.size - 1)]
        val dark = avgOf { it <= darkCut }
        val light = avgOf { it >= lightCut }
        fun colorOf(i: Int) = android.graphics.Color.rgb((sumR[i] / weight[i]).toInt(), (sumG[i] / weight[i]).toInt(), (sumB[i] / weight[i]).toInt())
        val order = (0 until buckets).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
        val picks = ArrayList<Int>()
        for (i in order) {
            if (picks.none { p -> val d = kotlin.math.abs(p - i); minOf(d, buckets - d) < 3 }) picks += i
            if (picks.size == 3) break
        }
        if (small !== bitmap) small.recycle()
        val primary = picks.getOrNull(0)?.let(::colorOf) ?: (avgOf { true } ?: 0xFF607080.toInt())
        return fromTones(primary, picks.getOrNull(1)?.let(::colorOf), picks.getOrNull(2)?.let(::colorOf), dark, light, name)
    }

    /** The palette the whole launcher (and the island) draws with right now. */
    fun resolve(context: Context, s: LauncherSettings): ForgePalette = when (s.paletteSource) {
        PaletteSource.WALLPAPER -> fromWallpaper(context, allowSystemPalette = false) ?: default
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
