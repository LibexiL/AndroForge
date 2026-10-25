package com.libexil.androforge.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Decides when the night theme is on, and what it looks like. */
object NightTheme {
    private var cachedPlace: Pair<Double, Double>? = null
    private var placeCheckedAt = 0L

    /** Last known coarse location (only if weather already has location access). */
    private fun place(context: Context): Pair<Double, Double>? {
        val now = System.currentTimeMillis()
        if (now - placeCheckedAt < 30 * 60_000L) return cachedPlace
        placeCheckedAt = now
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return cachedPlace
        }
        return try {
            val lm = context.getSystemService(LocationManager::class.java)
            val loc = lm?.getProviders(true)?.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }
            loc?.let { (it.latitude to it.longitude) }?.also { cachedPlace = it } ?: cachedPlace
        } catch (e: SecurityException) {
            cachedPlace
        }
    }

    /** Sunrise and sunset today, in minutes after local midnight (NOAA approximation). */
    fun sunTimes(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Pair<Int, Int>? {
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val tzHours = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        fun calc(rising: Boolean): Double? {
            val lngHour = lon / 15.0
            val t = day + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
            val m = 0.9856 * t - 3.289
            var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
            l = (l + 360.0) % 360.0
            var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
            ra = (ra + 360.0) % 360.0
            ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
            ra /= 15.0
            val sinDec = 0.39782 * sin(Math.toRadians(l))
            val cosDec = cos(asin(sinDec))
            val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(Math.toRadians(lat))) / (cosDec * cos(Math.toRadians(lat)))
            if (cosH > 1 || cosH < -1) return null
            var h = if (rising) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
            h /= 15.0
            val tt = h + ra - 0.06571 * t - 6.622
            val ut = ((tt - lngHour) % 24.0 + 24.0) % 24.0
            return ((ut + tzHours) % 24.0 + 24.0) % 24.0
        }
        val rise = calc(true) ?: return null
        val set = calc(false) ?: return null
        return (rise * 60).toInt() to (set * 60).toInt()
    }

    fun isNight(context: Context, s: LauncherSettings, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!s.nightTheme) return false
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val (start, end) = when (s.nightSchedule) {
            NightSchedule.FIXED -> s.nightStartMin to s.nightEndMin
            NightSchedule.SUN -> place(context)?.let { (lat, lon) -> sunTimes(lat, lon, cal)?.let { (rise, set) -> set to rise } }
                ?: (19 * 60 + 30 to 6 * 60 + 30)
        }
        return if (start <= end) now in start until end else now >= start || now < end
    }

    /** A softer, darker take on [day] for night time. */
    fun dimmed(day: ForgePalette): ForgePalette {
        fun dim(hex: String, light: Float, sat: Float): String {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(Palettes.parse(hex), hsl)
            hsl[1] = (hsl[1] * sat).coerceIn(0f, 1f)
            hsl[2] = (hsl[2] * light).coerceIn(0f, 1f)
            return Palettes.hex(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        }
        return day.copy(
            name = day.name + " (night)",
            background = dim(day.background, 0.6f, 0.8f),
            surface = dim(day.surface, 0.65f, 0.8f),
            foreground = dim(day.foreground, 0.85f, 0.7f),
            muted = dim(day.muted, 0.8f, 0.7f),
            accent = dim(day.accent, 0.78f, 0.75f),
            accent2 = dim(day.accent2, 0.78f, 0.75f),
            ansi = day.ansi.map { dim(it, 0.8f, 0.75f) },
            gradient = day.gradient.map { dim(it, 0.75f, 0.75f) },
        )
    }

    /** The palette to use right now, given the day one. */
    fun apply(context: Context, s: LauncherSettings, day: ForgePalette, night: Boolean): ForgePalette {
        if (!night) return day
        val name = s.nightPalette ?: return dimmed(day)
        return Palettes.presets.firstOrNull { it.name == name } ?: s.customPalettes.firstOrNull { it.name == name } ?: dimmed(day)
    }
}
