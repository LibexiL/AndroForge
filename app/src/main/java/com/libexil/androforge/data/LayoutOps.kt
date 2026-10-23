package com.libexil.androforge.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

/** Where a drag started. */
sealed interface DragSource {
    data class Home(val itemId: String) : DragSource
    data class Dock(val itemId: String) : DragSource
    data class Drawer(val app: AppKey) : DragSource
    data class Folder(val folderId: String, val app: AppKey) : DragSource
}

/** Where a drag ended. */
sealed interface DropTarget {
    data class HomeCell(val page: Int, val x: Int, val y: Int) : DropTarget
    /** [onto] = dropped on top of the slot (make/extend a folder) instead of between slots. */
    data class Dock(val index: Int, val onto: Boolean) : DropTarget
    data object Remove : DropTarget
    data object Info : DropTarget
    data object Uninstall : DropTarget
    data object None : DropTarget
}

data class DropResult(val state: LauncherState, val message: String? = null)

/** Pure layout transformations - no Android APIs, easy to reason about. */
object LayoutOps {

    fun findFolder(s: LauncherState, id: String): FolderItem? =
        (s.items + s.dock).firstOrNull { it.id == id } as? FolderItem

    fun payload(s: LauncherState, src: DragSource): HomeItem? = when (src) {
        is DragSource.Home -> s.items.firstOrNull { it.id == src.itemId }
        is DragSource.Dock -> s.dock.firstOrNull { it.id == src.itemId }
        is DragSource.Drawer -> AppItem(newId(), app = src.app)
        is DragSource.Folder -> AppItem(newId(), app = src.app)
    }

    fun occupied(s: LauncherState, page: Int, cols: Int, rows: Int, ignoreId: String? = null): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        s.items.filter { it.page == page && it.id != ignoreId }.forEach { item ->
            for (yy in item.y until (item.y + item.spanY)) for (xx in item.x until (item.x + item.spanX)) {
                if (yy in 0 until rows && xx in 0 until cols) grid[yy][xx] = true
            }
        }
        return grid
    }

    fun fits(s: LauncherState, page: Int, x: Int, y: Int, spanX: Int, spanY: Int, cols: Int, rows: Int, ignoreId: String? = null): Boolean {
        if (x < 0 || y < 0 || x + spanX > cols || y + spanY > rows) return false
        val grid = occupied(s, page, cols, rows, ignoreId)
        for (yy in y until y + spanY) for (xx in x until x + spanX) if (grid[yy][xx]) return false
        return true
    }

    /** First free spot scanning from [startPage]; may return pageCount (= needs a new page). */
    fun findSpot(s: LauncherState, startPage: Int, spanX: Int, spanY: Int, cols: Int, rows: Int): Triple<Int, Int, Int> {
        val pages = (startPage until s.pageCount) + (0 until startPage)
        for (page in pages) {
            for (y in 0..(rows - spanY)) for (x in 0..(cols - spanX)) {
                if (fits(s, page, x, y, spanX, spanY, cols, rows)) return Triple(page, x, y)
            }
        }
        return Triple(s.pageCount, 0, 0)
    }

    fun addToHome(s: LauncherState, item: HomeItem, startPage: Int): LauncherState {
        val cols = s.settings.columns
        val rows = s.settings.rows
        val (page, x, y) = findSpot(s, startPage, item.spanX.coerceAtMost(cols), item.spanY.coerceAtMost(rows), cols, rows)
        val pageCount = maxOf(s.pageCount, page + 1)
        return s.copy(items = s.items + item.moved(page, x, y), pageCount = pageCount)
    }

    private fun reindexDock(dock: List<HomeItem>) = dock.mapIndexed { i, it -> it.moved(-1, i, 0) }

    private fun withoutApp(folder: FolderItem, app: AppKey): HomeItem? {
        val left = folder.apps - app
        return when {
            left.isEmpty() -> null
            left.size == 1 -> AppItem(folder.id, folder.page, folder.x, folder.y, left[0])
            else -> folder.copy(apps = left)
        }
    }

    fun removeSource(s: LauncherState, src: DragSource): LauncherState = when (src) {
        is DragSource.Home -> s.copy(items = s.items.filterNot { it.id == src.itemId })
        is DragSource.Dock -> s.copy(dock = reindexDock(s.dock.filterNot { it.id == src.itemId }))
        is DragSource.Drawer -> s
        is DragSource.Folder -> s.copy(
            items = s.items.mapNotNull { if (it.id == src.folderId && it is FolderItem) withoutApp(it, src.app) else it },
            dock = s.dock.mapNotNull { if (it.id == src.folderId && it is FolderItem) withoutApp(it, src.app) else it },
        )
    }

    fun drop(s: LauncherState, src: DragSource, target: DropTarget): DropResult {
        val item = payload(s, src) ?: return DropResult(s)
        if (target == DropTarget.None || target == DropTarget.Info || target == DropTarget.Uninstall) return DropResult(s)
        if (target == DropTarget.Remove) return DropResult(removeSource(s, src))

        val oldDockIndex = (src as? DragSource.Dock)?.let { d -> s.dock.indexOfFirst { it.id == d.itemId } } ?: -1

        when (target) {
            is DropTarget.HomeCell -> {
                val cols = s.settings.columns
                val rows = s.settings.rows
                if (target.x !in 0 until cols || target.y !in 0 until rows) return DropResult(s)
                // Dropping onto its own folder = nothing to do.
                if (src is DragSource.Folder) {
                    val occupant = s.items.firstOrNull { it.covers(target.page, target.x, target.y) }
                    if (occupant?.id == src.folderId) return DropResult(s)
                }
                val base = removeSource(s, src)
                val occupant = base.items.firstOrNull { it.id != item.id && it.covers(target.page, target.x, target.y) }
                return when {
                    occupant == null -> DropResult(
                        base.copy(items = base.items + item.moved(target.page, target.x, target.y)),
                    )
                    occupant is AppItem && item is AppItem -> {
                        if (occupant.app == item.app) return DropResult(s)
                        val folder = FolderItem(
                            id = newId(), page = occupant.page, x = occupant.x, y = occupant.y,
                            name = "Folder", apps = listOf(occupant.app, item.app),
                        )
                        DropResult(base.copy(items = base.items.map { if (it.id == occupant.id) folder else it }))
                    }
                    occupant is FolderItem && item is AppItem -> {
                        if (item.app in occupant.apps) return DropResult(s)
                        val folder = occupant.copy(apps = occupant.apps + item.app)
                        DropResult(base.copy(items = base.items.map { if (it.id == occupant.id) folder else it }))
                    }
                    else -> DropResult(s, "No room there")
                }
            }
            is DropTarget.Dock -> {
                if (item is WidgetItem || item is ForgeWidgetItem) return DropResult(s)
                val slot = s.dock.getOrNull(target.index)
                if (target.onto && slot != null && slot.id != item.id && item is AppItem) {
                    if (src is DragSource.Folder && slot.id == src.folderId) return DropResult(s)
                    val base = removeSource(s, src)
                    val replaced: HomeItem = when (slot) {
                        is FolderItem -> if (item.app in slot.apps) return DropResult(s) else slot.copy(apps = slot.apps + item.app)
                        is AppItem -> if (slot.app == item.app) return DropResult(s) else
                            FolderItem(newId(), -1, slot.x, 0, "Folder", listOf(slot.app, item.app))
                        else -> return DropResult(s)
                    }
                    return DropResult(base.copy(dock = reindexDock(base.dock.map { if (it.id == slot.id) replaced else it })))
                }
                if (src !is DragSource.Dock && s.dock.size >= s.settings.dockMaxIcons) {
                    return DropResult(s, "The dock is full")
                }
                val base = removeSource(s, src)
                var index = target.index
                if (oldDockIndex in 0 until index) index -= 1
                index = index.coerceIn(0, base.dock.size)
                val newDock = base.dock.toMutableList().apply { add(index, item) }
                return DropResult(base.copy(dock = reindexDock(newDock)))
            }
            else -> return DropResult(s)
        }
    }

    fun removePage(s: LauncherState, page: Int): LauncherState {
        if (s.pageCount <= 1) return s
        val items = s.items.filter { it.page != page }.map { if (it.page > page) it.moved(it.page - 1, it.x, it.y) else it }
        return s.copy(items = items, pageCount = s.pageCount - 1)
    }

    /** Keep items inside the grid when the user shrinks columns/rows. */
    fun reflow(s: LauncherState): LauncherState {
        val cols = s.settings.columns
        val rows = s.settings.rows
        var result = s.copy(items = emptyList())
        val misplaced = ArrayList<HomeItem>()
        for (item in s.items.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))) {
            val sx = item.spanX.coerceAtMost(cols)
            val sy = item.spanY.coerceAtMost(rows)
            val sized = item.resized(sx, sy)
            if (fits(result, item.page, item.x, item.y, sx, sy, cols, rows)) {
                result = result.copy(items = result.items + sized)
            } else {
                misplaced += sized
            }
        }
        for (item in misplaced) result = addToHome(result, item, item.page)
        return result
    }
}
