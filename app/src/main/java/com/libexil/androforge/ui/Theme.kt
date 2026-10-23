package com.libexil.androforge.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils
import com.libexil.androforge.data.IconRef
import com.libexil.androforge.data.IconShape
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.ThemeMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

val LocalSettings = staticCompositionLocalOf { LauncherSettings() }
val LocalIconOverrides = staticCompositionLocalOf { emptyMap<String, IconRef>() }
val LocalLabels = staticCompositionLocalOf { emptyMap<String, String>() }
val LocalAccent = staticCompositionLocalOf { Color(0xFF8B6CFF) }

/** Superellipse ("squircle") with exponent [n]. */
class SuperellipseShape(private val n: Float = 4.5f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val rx = size.width / 2f
        val ry = size.height / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = rx + rx * sign(c) * abs(c).pow(2.0 / n)
            val y = ry + ry * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        path.moveTo(w * 0.5f, 0f)
        path.lineTo(w, h * 0.25f)
        path.lineTo(w, h * 0.75f)
        path.lineTo(w * 0.5f, h)
        path.lineTo(0f, h * 0.75f)
        path.lineTo(0f, h * 0.25f)
        path.close()
        return Outline.Generic(path)
    }
}

private val squircle = SuperellipseShape()

fun IconShape.toShape(): Shape = when (this) {
    IconShape.SYSTEM -> RoundedCornerShape(24)
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> squircle
    IconShape.ROUNDED -> RoundedCornerShape(24)
    IconShape.SQUARE -> RoundedCornerShape(8)
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
    IconShape.HEXAGON -> HexagonShape
}

private fun wallpaperPrimary(context: Context): Int? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    } else {
        null
    }
} catch (e: Exception) {
    null
}

/** Keep accents readable on dark surfaces: push lightness/saturation into a pleasant band. */
fun vivid(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.9f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.72f)
    return ColorUtils.HSLToColor(hsl)
}

@Composable
fun rememberAccent(settings: LauncherSettings): Color {
    val context = LocalContext.current
    var wallColor by remember { mutableStateOf(wallpaperPrimary(context)) }
    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(context)
            val listener = WallpaperManager.OnColorsChangedListener { _, _ -> wallColor = wallpaperPrimary(context) }
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            onDispose { wm.removeOnColorsChangedListener(listener) }
        } else {
            onDispose { }
        }
    }
    return when (settings.themeMode) {
        ThemeMode.CUSTOM -> Color(settings.accentColor)
        ThemeMode.MATERIAL_YOU ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).primary
            else Color(settings.accentColor)
        ThemeMode.WALLPAPER -> wallColor?.let { Color(vivid(it)) } ?: Color(settings.accentColor)
    }
}

@Composable
fun AndroForgeTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        secondary = accent,
        tertiary = accent,
        primaryContainer = Color(ColorUtils.blendARGB(accent.toArgb(), 0xFF101015.toInt(), 0.7f)),
        onPrimaryContainer = Color.White,
        surface = Color(0xFF141418),
        surfaceContainer = Color(0xFF1B1B21),
        surfaceContainerHigh = Color(0xFF222229),
        surfaceContainerHighest = Color(0xFF2A2A32),
        background = Color(0xFF0F0F12),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
