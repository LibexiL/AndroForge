package com.libexil.androforge.data

import android.provider.Settings
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/** Quick answers for the drawer's search box: math, unit conversions and settings shortcuts. */
object SmartSearch {

    // ───────────────────────────────────────────── calculator

    /** "15% of 80", "(3+4)*2", "2^10", "12 x 3", "sqrt 81". Null when it isn't math. */
    fun calculate(input: String): String? {
        var q = input.trim().lowercase()
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace(",", "")
            .removePrefix("=").trim()
        if (q.isEmpty() || q.none { it.isDigit() }) return null
        Regex("""^([\d.]+)\s*%\s*of\s*([\d.]+)$""").find(q)?.let { m ->
            val v = m.groupValues[1].toDouble() / 100.0 * m.groupValues[2].toDouble()
            return format(v)
        }
        q = q.replace(Regex("""(?<=[\d)\s])x(?=[\s\d(])"""), "*")
        // Needs an operator (or a function), so a bare number isn't "math".
        if (!q.any { it in "+-*/^%" } && !Regex("""(sqrt|sin|cos|tan|log|ln)""").containsMatchIn(q)) return null
        if (!q.all { it.isDigit() || it in "+-*/^%(). " || it.isLetter() }) return null
        return try {
            val p = Parser(q)
            val v = p.parse()
            if (v.isNaN() || v.isInfinite()) null else format(v)
        } catch (e: Exception) {
            null
        }
    }

    fun format(v: Double): String {
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) return String.format(java.util.Locale.US, "%,d", v.toLong())
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return bd.toPlainString()
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun parse(): Double {
            val v = expr()
            skip()
            if (i < s.length) error("junk")
            return v
        }
        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }
        private fun eat(c: Char): Boolean {
            skip()
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }
        private fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }
        private fun term(): Double {
            var v = factor()
            while (true) {
                v = when {
                    eat('*') -> v * factor()
                    eat('/') -> v / factor()
                    eat('%') -> v / 100.0 * (if (peekNumber()) factor() else 1.0)
                    else -> return v
                }
            }
        }
        private fun peekNumber(): Boolean {
            skip()
            return i < s.length && (s[i].isDigit() || s[i] == '(' || s[i] == '.')
        }
        private fun factor(): Double {
            val base = unary()
            return if (eat('^')) base.pow(factor()) else base
        }
        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> atom()
        }
        private fun atom(): Double {
            skip()
            if (eat('(')) {
                val v = expr()
                eat(')')
                return v
            }
            val start = i
            if (i < s.length && s[i].isLetter()) {
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i)
                if (name == "pi") return Math.PI
                if (name == "e") return Math.E
                val arg = unary()
                return when (name) {
                    "sqrt" -> kotlin.math.sqrt(arg)
                    "sin" -> kotlin.math.sin(Math.toRadians(arg))
                    "cos" -> kotlin.math.cos(Math.toRadians(arg))
                    "tan" -> kotlin.math.tan(Math.toRadians(arg))
                    "log" -> kotlin.math.log10(arg)
                    "ln" -> kotlin.math.ln(arg)
                    else -> error("fn")
                }
            }
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) error("number")
            return s.substring(start, i).toDouble()
        }
    }

    // ───────────────────────────────────────────── units

    private class UnitDef(val names: List<String>, val kind: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double, val label: String)

    private fun lin(kind: String, factor: Double, label: String, vararg names: String) =
        UnitDef(names.toList(), kind, { it * factor }, { it / factor }, label)

    private val units = listOf(
        lin("len", 0.001, "mm", "mm", "millimeter", "millimeters"),
        lin("len", 0.01, "cm", "cm", "centimeter", "centimeters"),
        lin("len", 1.0, "m", "m", "meter", "meters", "metre", "metres"),
        lin("len", 1000.0, "km", "km", "kilometer", "kilometers", "kilometre", "kilometres"),
        lin("len", 0.0254, "in", "in", "inch", "inches", "\""),
        lin("len", 0.3048, "ft", "ft", "foot", "feet", "'"),
        lin("len", 0.9144, "yd", "yd", "yard", "yards"),
        lin("len", 1609.344, "mi", "mi", "mile", "miles"),
        lin("mass", 0.001, "g", "g", "gram", "grams"),
        lin("mass", 1.0, "kg", "kg", "kilo", "kilos", "kilogram", "kilograms"),
        lin("mass", 0.0283495, "oz", "oz", "ounce", "ounces"),
        lin("mass", 0.453592, "lb", "lb", "lbs", "pound", "pounds"),
        lin("mass", 6.35029, "st", "st", "stone"),
        lin("vol", 0.001, "ml", "ml", "milliliter", "milliliters"),
        lin("vol", 1.0, "L", "l", "liter", "liters", "litre", "litres"),
        lin("vol", 0.236588, "cups", "cup", "cups"),
        lin("vol", 0.0295735, "fl oz", "floz", "fl oz"),
        lin("vol", 3.78541, "gal", "gal", "gallon", "gallons"),
        lin("vol", 0.00492892, "tsp", "tsp", "teaspoon", "teaspoons"),
        lin("vol", 0.0147868, "tbsp", "tbsp", "tablespoon", "tablespoons"),
        lin("speed", 0.44704, "mph", "mph"),
        lin("speed", 0.277778, "km/h", "kph", "kmh", "km/h"),
        lin("speed", 1.0, "m/s", "m/s", "mps"),
        lin("speed", 0.514444, "knots", "knot", "knots", "kn"),
        lin("data", 1.0, "B", "b", "byte", "bytes"),
        lin("data", 1024.0, "KB", "kb", "kilobyte", "kilobytes"),
        lin("data", 1024.0.pow(2), "MB", "mb", "megabyte", "megabytes"),
        lin("data", 1024.0.pow(3), "GB", "gb", "gigabyte", "gigabytes"),
        lin("data", 1024.0.pow(4), "TB", "tb", "terabyte", "terabytes"),
        UnitDef(listOf("c", "°c", "celsius"), "temp", { it }, { it }, "°C"),
        UnitDef(listOf("f", "°f", "fahrenheit"), "temp", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }, "°F"),
        UnitDef(listOf("k", "kelvin"), "temp", { it - 273.15 }, { it + 273.15 }, "K"),
    )

    private fun unitOf(name: String): UnitDef? = name.trim().lowercase().let { n -> units.firstOrNull { n in it.names } }

    /** "5 km to mi", "72 f in c", "3 cups to ml". Returns "5 km = 3.107 mi". */
    fun convert(input: String): String? {
        val m = Regex("""^\s*(-?[\d.,]+)\s*([a-z°'"/ ]+?)\s+(?:to|in|as|=)\s+([a-z°'"/ ]+?)\s*$""").find(input.lowercase()) ?: return null
        val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val from = unitOf(m.groupValues[2]) ?: return null
        val to = unitOf(m.groupValues[3]) ?: return null
        if (from.kind != to.kind) return null
        val out = to.fromBase(from.toBase(value))
        val rounded = BigDecimal(out).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
        return "${format(value)} ${from.label} = $rounded ${to.label}"
    }

    // ───────────────────────────────────────────── settings shortcuts

    class SettingLink(val label: String, val action: String, val words: List<String>)

    val settingLinks = listOf(
        SettingLink("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi-fi", "wireless", "internet")),
        SettingLink("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "bt", "pair", "earbuds", "headphones")),
        SettingLink("Mobile data", Settings.ACTION_DATA_ROAMING_SETTINGS, listOf("data", "mobile", "cellular", "sim", "roaming")),
        SettingLink("Hotspot & tethering", "android.settings.TETHER_SETTINGS", listOf("hotspot", "tether", "tethering")),
        SettingLink("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight", "aeroplane")),
        SettingLink("Display & brightness", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark mode", "refresh")),
        SettingLink("Sound & vibration", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration", "vibrate")),
        SettingLink("Notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("notification", "notifications", "alerts")),
        SettingLink("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("battery", "power", "saver", "charging")),
        SettingLink("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space", "memory", "files")),
        SettingLink("Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "default apps")),
        SettingLink("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
        SettingLink("Security & lock screen", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "lock", "password", "pin", "fingerprint", "face")),
        SettingLink("Privacy", Settings.ACTION_PRIVACY_SETTINGS, listOf("privacy", "permissions")),
        SettingLink("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility")),
        SettingLink("Date & time", Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock", "timezone")),
        SettingLink("Language & keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, listOf("keyboard", "language", "input")),
        SettingLink("NFC", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "tap to pay")),
        SettingLink("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging", "adb")),
        SettingLink("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone info", "android version", "model")),
        SettingLink("Wallpaper", Intent_SET_WALLPAPER, listOf("wallpaper", "background")),
        SettingLink("Do not disturb", "android.settings.ZEN_MODE_SETTINGS", listOf("dnd", "do not disturb", "quiet", "focus")),
        SettingLink("All settings", Settings.ACTION_SETTINGS, listOf("settings")),
    )

    fun matchSettings(q: String): List<SettingLink> {
        val t = q.trim().lowercase()
        if (t.length < 2) return emptyList()
        return settingLinks.filter { l -> l.label.lowercase().startsWith(t) || l.words.any { it.startsWith(t) || (t.length >= 4 && t in it) } }.take(3)
    }
}

private const val Intent_SET_WALLPAPER = "android.intent.action.SET_WALLPAPER"
