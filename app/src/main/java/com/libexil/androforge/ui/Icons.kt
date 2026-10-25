package com.libexil.androforge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.FolderStyle
import com.libexil.androforge.data.IconImage

/** Notification count per package, for icon dots. */
val LocalNotifCounts = androidx.compose.runtime.staticCompositionLocalOf { emptyMap<String, Int>() }

@Composable
fun rememberTintSpec(): com.libexil.androforge.data.IconTintSpec? {
    val settings = LocalSettings.current
    val pal = LocalPalette.current
    if (settings.iconTint == com.libexil.androforge.data.IconTint.OFF) return null
    return androidx.compose.runtime.remember(settings.iconTint, settings.iconTintStrength, pal) {
        val mono = settings.iconTint == com.libexil.androforge.data.IconTint.MONO
        com.libexil.androforge.data.IconTintSpec(
            mode = settings.iconTint,
            dark = (if (mono) pal.surface else pal.background).toArgbInt(),
            light = (if (mono) pal.accent else androidx.compose.ui.graphics.lerp(pal.accent, pal.foreground, 0.35f)).toArgbInt(),
            strength = settings.iconTintStrength,
        )
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()

@Composable
fun AppIcon(key: AppKey, size: Dp, modifier: Modifier = Modifier, dot: Boolean = false) {
    val repo = LauncherApp.instance.apps
    val settings = LocalSettings.current
    val custom = LocalIconOverrides.current[key.id]
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version by repo.iconVersion.collectAsState()
    val tint = rememberTintSpec()
    val cacheKey = repo.iconCacheKey(key, px, settings.iconShape, settings.iconPack, custom, tint)
    val image: IconImage? by produceState(repo.cachedIcon(cacheKey), cacheKey, version) {
        value = repo.loadIcon(key, px, settings.iconShape, settings.iconPack, custom, tint)
    }
    val shape = settings.iconShape.toShape()
    val img = image
    Box(modifier.size(size)) {
        if (img == null) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.12f)))
        } else {
            Image(
                bitmap = img.bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(if (img.clip) Modifier.clip(shape) else Modifier),
            )
        }
        if (dot) NotificationDot(LocalNotifCounts.current[key.pkg] ?: 0, size)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NotificationDot(count: Int, iconSize: Dp) {
    val style = LocalSettings.current.notificationDots
    if (count <= 0 || style == com.libexil.androforge.data.DotStyle.OFF) return
    val accent = LocalAccent.current
    val withCount = style == com.libexil.androforge.data.DotStyle.COUNT
    val d = if (withCount) iconSize * 0.36f else iconSize * 0.22f
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = d * 0.18f, y = -d * 0.18f)
            .size(d)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(accent)
            .border(1.5.dp, Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (withCount) {
            Text(
                if (count > 99) "99" else "$count",
                color = Color.White,
                fontSize = (d.value * 0.55f).sp,
                maxLines = 1,
                style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

@Composable
fun FolderIcon(folder: FolderItem, size: Dp, modifier: Modifier = Modifier, dot: Boolean = false) {
    val counts = LocalNotifCounts.current
    val total = if (dot) folder.apps.distinctBy { it.pkg }.sumOf { counts[it.pkg] ?: 0 } else 0
    Box(modifier.size(size)) {
        FolderIconBody(folder, size)
        if (total > 0) NotificationDot(total, size)
    }
}

@Composable
private fun FolderIconBody(folder: FolderItem, size: Dp, modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val shape = settings.iconShape.toShape()
    val bg = folder.color?.let { Color(it) } ?: accent
    when (settings.folderStyle) {
        FolderStyle.GRID -> {
            val mini = size * 0.34f
            Box(
                modifier
                    .size(size)
                    .clip(shape)
                    .background(bg.copy(alpha = 0.32f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                    folder.apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                            row.forEach { AppIcon(it, mini) }
                            if (row.size == 1) Spacer(Modifier.width(mini))
                        }
                    }
                }
            }
        }
        FolderStyle.STACK -> {
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                val apps = folder.apps.take(3)
                apps.reversed().forEachIndexed { i, k ->
                    val depth = apps.size - 1 - i
                    AppIcon(
                        k,
                        size * 0.78f,
                        Modifier
                            .offset(x = size * 0.08f * (depth - 1), y = -size * 0.04f * depth)
                            .rotate((depth - 1) * 8f),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(size * 0.3f)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${folder.apps.size}",
                        color = Color.White,
                        fontSize = (size.value * 0.16f).sp,
                    )
                }
            }
        }
    }
}

val labelShadow = Shadow(Color.Black.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, 1.5f), 6f)

@Composable
fun IconLabel(text: String, modifier: Modifier = Modifier, color: Color? = null, sizeSp: Int? = null) {
    val settings = LocalSettings.current
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 2.dp),
        style = TextStyle(
            color = color ?: Color(settings.labelColor),
            fontSize = (sizeSp ?: settings.labelSizeSp).sp,
            shadow = labelShadow,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Icon + optional label, centered in its cell. */
@Composable
fun IconWithLabel(
    label: String,
    showLabel: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { icon() }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            IconLabel(label)
        }
    }
}

@Composable
fun labelFor(key: AppKey, fallback: String?): String =
    LocalLabels.current[key.id] ?: fallback ?: key.pkg.substringAfterLast('.')
