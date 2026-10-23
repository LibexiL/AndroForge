package com.libexil.androforge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.MenuRequest
import com.libexil.androforge.MenuTarget
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppItem
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.DockStyle
import com.libexil.androforge.data.DragSource
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.HomeItem
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.WidgetItem
import com.libexil.androforge.data.newId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

private sealed interface DockEntry {
    val key: String

    data class Pinned(val item: HomeItem) : DockEntry {
        override val key get() = "p:" + item.id
    }

    data class Recent(val app: AppKey) : DockEntry {
        override val key get() = "r:" + app.id
    }
}

/**
 * The dynamic dock: magnifies under your finger (slide along it and let go to launch),
 * shows recently used apps, tints itself from the wallpaper, and bounces icons on launch.
 */
@Composable
fun Dock(
    vm: LauncherViewModel,
    state: LauncherState,
    appsById: Map<String, AppInfo>,
    drag: DragState,
    onDragEnd: () -> Unit,
) {
    val s = state.settings
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val pinned = state.dock
    val pinnedApps = remember(pinned) {
        pinned.flatMap {
            when (it) {
                is AppItem -> listOf(it.app)
                is FolderItem -> it.apps
                is WidgetItem -> emptyList()
            }
        }.toSet()
    }
    val recents = if (s.dockShowRecents) {
        state.recents.filter { it.id in appsById && it.id !in state.hidden && it !in pinnedApps }.take(s.dockRecentCount)
    } else {
        emptyList()
    }
    val entries: List<DockEntry> = pinned.map { DockEntry.Pinned(it) } + recents.map { DockEntry.Recent(it) }
    val currentEntries by rememberUpdatedState(entries)

    val iconSize = s.dockIconSizeDp.dp
    val slotWidth = iconSize + 14.dp
    val slotPx = with(density) { slotWidth.toPx() }
    val rects = remember(entries.size) { arrayOfNulls<Rect>(entries.size) }
    val rowPos = remember { PosHolder() }
    var touchX by remember { mutableStateOf<Float?>(null) }
    var bounceKey by remember { mutableStateOf("") }
    var bounceTick by remember { mutableIntStateOf(0) }

    // ---- magnification
    val magnifying = s.dockMagnify && touchX != null && !drag.active
    val scales = entries.mapIndexed { i, e ->
        key(e.key) {
            val target = run {
                val tx = touchX
                val r = rects.getOrNull(i)
                if (!magnifying || tx == null || r == null) 1f
                else {
                    val d = abs(r.center.x - tx) / (slotPx * 2.3f)
                    if (d >= 1f) 1f else 1f + (s.dockMagnifyAmount - 1f) * ((cos(d * PI) + 1.0) / 2.0).toFloat()
                }
            }
            animateFloatAsState(
                target,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "dock-scale",
            ).value
        }
    }
    val extras = scales.map { slotPx * (it - 1f) }
    val totalExtra = extras.sum()
    val shifts = extras.indices.map { i -> extras.take(i).sum() + extras[i] / 2f - totalExtra / 2f }

    // ---- colors
    val baseColor = if (s.dockAdaptiveColor) {
        Color(ColorUtils.blendARGB(accent.toArgb(), 0xFF0B0B10.toInt(), 0.72f))
    } else {
        Color(0xFF101014)
    }
    val bg = baseColor.copy(alpha = s.dockOpacity)
    val corner = with(density) { s.dockCornerDp.dp.toPx() }
    val glowAlpha by animateFloatAsState(if (magnifying) 0.9f else 0.45f, label = "glow")

    fun slotAt(rootX: Float): Int {
        val gap = with(density) { 6.dp.toPx() }
        rects.forEachIndexed { i, r ->
            if (r != null && rootX >= r.left - gap && rootX <= r.right + gap) return i
        }
        return -1
    }

    fun activate(index: Int) {
        val entry = currentEntries.getOrNull(index) ?: return
        val r = rects.getOrNull(index)
        val bounds = r?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        when (entry) {
            is DockEntry.Pinned -> when (val item = entry.item) {
                is AppItem -> vm.launch(item.app, bounds)
                is FolderItem -> vm.openFolderId = item.id
                is WidgetItem -> Unit
            }
            is DockEntry.Recent -> vm.launch(entry.app, bounds)
        }
        if (s.dockBounce) {
            bounceKey = entry.key
            bounceTick++
        }
    }

    fun beginDrag(index: Int, at: Offset) {
        val entry = currentEntries.getOrNull(index) ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (entry) {
            is DockEntry.Pinned -> {
                vm.menu = MenuRequest(at, MenuTarget.Home(entry.item, inDock = true))
                if (!s.lockLayout) drag.start(DragSource.Dock(entry.item.id), entry.item, at)
            }
            is DockEntry.Recent -> {
                vm.menu = MenuRequest(at, MenuTarget.DrawerApp(entry.app))
                drag.start(DragSource.Drawer(entry.app), AppItem(newId(), app = entry.app), at)
            }
        }
    }

    val gestureModifier = Modifier.pointerInput(rects, s, pinned) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            val origin = rowPos.pos
            touchX = origin.x + down.position.x
            var last = down.position
            var slid = false
            // 1 = released, 2 = vertical swipe (let the drawer gesture have it), null = long press
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var res = 0
                while (res == 0) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    if (ch == null) {
                        res = 2
                    } else if (!ch.pressed) {
                        res = 1
                    } else {
                        last = ch.position
                        touchX = origin.x + last.x
                        val d = last - down.position
                        if (abs(d.x) > slop) slid = true
                        if (!slid && abs(d.y) > slop * 2 && abs(d.y) > abs(d.x)) res = 2
                    }
                }
                res
            }
            try {
                when (outcome) {
                    1 -> activate(slotAt(origin.x + last.x))
                    2 -> Unit
                    else -> if (!slid) {
                        val index = slotAt(origin.x + last.x)
                        if (index >= 0) {
                            touchX = null
                            beginDrag(index, origin + last)
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) {
                                        ch.consume()
                                        break
                                    }
                                    val delta = ch.position - last
                                    last = ch.position
                                    ch.consume()
                                    drag.moveBy(delta)
                                    if (drag.moved) vm.menu = null
                                }
                            } finally {
                                onDragEnd()
                            }
                        }
                    } else {
                        // Keep sliding the magnifier; launch whatever is under the finger on release.
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            last = ch.position
                            if (!ch.pressed) {
                                val rowHeight = rowPos.size.height
                                if (last.y > -rowHeight * 0.6f) activate(slotAt(origin.x + last.x))
                                break
                            }
                            touchX = origin.x + last.x
                        }
                    }
                }
            } finally {
                touchX = null
            }
        }
    }

    val floating = s.dockStyle != DockStyle.BAR
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (floating) 14.dp else 0.dp)
            .padding(top = 6.dp, bottom = if (floating) 10.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(if (floating) Modifier else Modifier.fillMaxWidth())
                .defaultMinSize(minWidth = slotWidth * 2, minHeight = iconSize + 20.dp)
                .onGloballyPositioned {
                    rowPos.pos = it.boundsInRoot().topLeft
                    rowPos.size = it.size
                    drag.dockRect = it.boundsInRoot()
                    drag.dockSlots = rects.take(pinned.size).filterNotNull()
                }
                .drawBehind {
                    val grow = if (floating) totalExtra else 0f
                    val topLeft = Offset(-grow / 2f, 0f)
                    val sz = Size(size.width + grow, size.height)
                    val radius = if (floating) CornerRadius(corner, corner) else CornerRadius.Zero
                    when (s.dockStyle) {
                        DockStyle.FLOATING, DockStyle.BAR -> drawRoundRect(bg, topLeft, sz, radius)
                        DockStyle.GLASS -> {
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), bg)),
                                topLeft, sz, radius,
                            )
                            drawRoundRect(
                                Color.White.copy(alpha = 0.22f), topLeft, sz, radius,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        DockStyle.MINIMAL -> Unit
                    }
                    if (s.dockGlow && s.dockStyle != DockStyle.MINIMAL) {
                        val w = sz.width * 0.55f
                        drawRoundRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, accent.copy(alpha = glowAlpha), Color.Transparent),
                                startX = topLeft.x + (sz.width - w) / 2f,
                                endX = topLeft.x + (sz.width + w) / 2f,
                            ),
                            Offset(topLeft.x + (sz.width - w) / 2f, 0f),
                            Size(w, 2.dp.toPx()),
                            CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }
                }
                .then(gestureModifier)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = if (floating) Arrangement.Center else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            entries.forEachIndexed { i, entry ->
                key(entry.key) {
                    val hidden = drag.active && entry is DockEntry.Pinned &&
                        drag.source is DragSource.Dock && drag.item?.id == entry.item.id
                    DockSlot(
                        entry = entry,
                        appsById = appsById,
                        iconSize = iconSize,
                        slotWidth = slotWidth,
                        scale = scales.getOrElse(i) { 1f },
                        shift = shifts.getOrElse(i) { 0f },
                        hidden = hidden,
                        showLabel = s.dockShowLabels,
                        bounce = if (bounceKey == entry.key) bounceTick else 0,
                        onPlaced = { r -> if (i < rects.size) rects[i] = r },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockSlot(
    entry: DockEntry,
    appsById: Map<String, AppInfo>,
    iconSize: androidx.compose.ui.unit.Dp,
    slotWidth: androidx.compose.ui.unit.Dp,
    scale: Float,
    shift: Float,
    hidden: Boolean,
    showLabel: Boolean,
    bounce: Int,
    onPlaced: (Rect) -> Unit,
) {
    val accent = LocalAccent.current
    val bounceAnim = remember { Animatable(0f) }
    val bouncePx = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(bounce) {
        if (bounce > 0) {
            repeat(2) {
                bounceAnim.animateTo(-1f, tween(140))
                bounceAnim.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 900f))
            }
        }
    }
    // Outer box = stable layout bounds (used for hit testing); inner = visual transform.
    Box(
        Modifier
            .width(slotWidth)
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shift
                translationY = bounceAnim.value * bouncePx
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = if (hidden) 0f else 1f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (entry) {
                is DockEntry.Pinned -> when (val item = entry.item) {
                    is AppItem -> AppIcon(item.app, iconSize)
                    is FolderItem -> FolderIcon(item, iconSize)
                    is WidgetItem -> Unit
                }
                is DockEntry.Recent -> AppIcon(entry.app, iconSize)
            }
            if (showLabel) {
                Spacer(Modifier.height(2.dp))
                val label = when (entry) {
                    is DockEntry.Pinned -> when (val item = entry.item) {
                        is AppItem -> labelFor(item.app, appsById[item.app.id]?.label)
                        is FolderItem -> item.name
                        is WidgetItem -> ""
                    }
                    is DockEntry.Recent -> labelFor(entry.app, appsById[entry.app.id]?.label)
                }
                IconLabel(label, sizeSp = 10)
            }
            // Recents get a small accent dot so they read as "dynamic" slots.
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (entry is DockEntry.Recent) accent else Color.Transparent),
            )
        }
    }
}
