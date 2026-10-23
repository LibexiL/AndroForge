package com.libexil.androforge.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import com.libexil.androforge.data.DragSource
import com.libexil.androforge.data.DropTarget
import com.libexil.androforge.data.HomeItem
import kotlin.math.abs

/**
 * Shared drag & drop state. Everything is in root (window) coordinates and drop targets
 * are resolved geometrically, so a drag can start in the drawer or a folder and end anywhere.
 */
@Stable
class DragState {
    var source by mutableStateOf<DragSource?>(null)
        private set
    var item by mutableStateOf<HomeItem?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var moved by mutableStateOf(false)
        private set
    private var travelled = 0f
    var moveThresholdPx = 30f

    val active: Boolean get() = source != null && moved

    // Geometry published by the UI.
    var gridRect: Rect = Rect.Zero
    var page: Int = 0
    var cols: Int = 5
    var rows: Int = 6
    var dockRect: Rect = Rect.Zero
    var dockSlots: List<Rect> = emptyList()
    var removeRect: Rect = Rect.Zero
    var infoRect: Rect = Rect.Zero
    var uninstallRect: Rect = Rect.Zero

    fun start(source: DragSource, item: HomeItem, at: Offset) {
        this.source = source
        this.item = item
        position = at
        moved = false
        travelled = 0f
    }

    fun moveBy(delta: Offset) {
        if (source == null) return
        position += delta
        travelled += abs(delta.x) + abs(delta.y)
        if (!moved && travelled > moveThresholdPx) moved = true
    }

    fun clear() {
        source = null
        item = null
        moved = false
        travelled = 0f
    }

    fun target(): DropTarget {
        val p = position
        if (removeRect.contains(p)) return DropTarget.Remove
        if (infoRect.contains(p)) return DropTarget.Info
        if (uninstallRect.contains(p)) return DropTarget.Uninstall
        if (dockRect != Rect.Zero && dockRect.inflate(12f).contains(p)) {
            dockSlots.forEachIndexed { i, r ->
                if (p.x >= r.left && p.x <= r.right && abs(p.x - r.center.x) < r.width * 0.28f) {
                    return DropTarget.Dock(i, onto = true)
                }
            }
            val index = dockSlots.count { it.center.x < p.x }
            return DropTarget.Dock(index, onto = false)
        }
        if (gridRect != Rect.Zero && gridRect.contains(p) && cols > 0 && rows > 0) {
            val cw = gridRect.width / cols
            val ch = gridRect.height / rows
            val x = ((p.x - gridRect.left) / cw).toInt().coerceIn(0, cols - 1)
            val y = ((p.y - gridRect.top) / ch).toInt().coerceIn(0, rows - 1)
            return DropTarget.HomeCell(page, x, y)
        }
        return DropTarget.None
    }
}

/**
 * Tap / long-press / drag-after-long-press in one handler. Moving before the long-press
 * timeout releases the gesture so pagers and lists can scroll.
 */
fun Modifier.launcherGestures(
    key: Any?,
    enabled: Boolean = true,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
): Modifier = pointerInput(key, enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val slop = viewConfiguration.touchSlop
        var last = down.position
        // 1 = tap, 2 = cancelled, null = long press
        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var res = 0
            while (res == 0) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed) {
                    res = 2
                } else if (!change.pressed) {
                    change.consume()
                    res = 1
                } else {
                    last = change.position
                    if ((change.position - down.position).getDistance() > slop) res = 2
                }
            }
            res
        }
        when (outcome) {
            1 -> onTap()
            2 -> Unit
            else -> {
                onLongPress(last)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.position - last
                        last = change.position
                        change.consume()
                        if (delta != Offset.Zero) onDrag(delta)
                    }
                } finally {
                    onDragEnd()
                }
            }
        }
    }
}
