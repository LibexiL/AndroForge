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
    var belowRect: Rect = Rect.Zero
    var belowRows: Int = 0
    /** Finger offset from the dragged item's top-left corner (used for multi-cell items). */
    var grab by mutableStateOf(Offset.Zero)
        private set
    var dockRect: Rect = Rect.Zero
    var dockSlots: List<Rect> = emptyList()
    var removeRect: Rect = Rect.Zero
    var infoRect: Rect = Rect.Zero
    var uninstallRect: Rect = Rect.Zero

    fun start(source: DragSource, item: HomeItem, at: Offset, grab: Offset = Offset.Zero) {
        this.source = source
        this.item = item
        this.grab = grab
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
        val big = item?.let { it.spanX > 1 || it.spanY > 1 } == true
        fun cell(r: Rect, rowsHere: Int, page: Int): DropTarget {
            val cw = r.width / cols
            val ch = r.height / rowsHere
            val it = item
            return if (big && it != null) {
                // Snap the item's top-left corner, keeping the spot you grabbed it by under your finger.
                val x = ((p.x - grab.x - r.left) / cw + 0.5f).toInt().coerceIn(0, (cols - it.spanX).coerceAtLeast(0))
                val y = ((p.y - grab.y - r.top) / ch + 0.5f).toInt().coerceIn(0, (rowsHere - it.spanY).coerceAtLeast(0))
                DropTarget.HomeCell(page, x, y)
            } else {
                val x = ((p.x - r.left) / cw).toInt().coerceIn(0, cols - 1)
                val y = ((p.y - r.top) / ch).toInt().coerceIn(0, rowsHere - 1)
                DropTarget.HomeCell(page, x, y)
            }
        }
        val isWidget = item is com.libexil.androforge.data.WidgetItem || item is com.libexil.androforge.data.ForgeWidgetItem
        // The layer under the dock only takes widgets; apps keep going to the dock / home grid.
        if (isWidget && belowRows > 0 && belowRect != Rect.Zero && belowRect.contains(p)) {
            return cell(belowRect, belowRows, com.libexil.androforge.data.BELOW_DOCK)
        }
        if (!isWidget && dockRect != Rect.Zero && dockRect.inflate(12f).contains(p)) {
            dockSlots.forEachIndexed { i, r ->
                if (p.x >= r.left && p.x <= r.right && abs(p.x - r.center.x) < r.width * 0.28f) {
                    return DropTarget.Dock(i, onto = true)
                }
            }
            val index = dockSlots.count { it.center.x < p.x }
            return DropTarget.Dock(index, onto = false)
        }
        if (gridRect != Rect.Zero && gridRect.contains(p) && cols > 0 && rows > 0) {
            return cell(gridRect, rows, page)
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
    onPress: (Boolean) -> Unit = {},
): Modifier = pointerInput(key, enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        onPress(true)
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
        onPress(false)
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

/**
 * Long-press (then drag) detection that sees touches *before* the widget's own buttons do.
 * Once the long press fires it takes over the gesture, so the button underneath never clicks.
 */
fun Modifier.longPressFirst(
    key: Any?,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var last = down.position
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!ch.pressed) return@withTimeoutOrNull true
                last = ch.position
                if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        if (ended != null) return@awaitEachGesture // tap or scroll: leave it to the widget / pager
        onLongPress(last)
        try {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                val delta = ch.position - last
                last = ch.position
                ch.consume()
                if (!ch.pressed) break
                if (delta != Offset.Zero) onDrag(delta)
            }
        } finally {
            onDragEnd()
        }
    }
}
