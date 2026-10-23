package com.libexil.androforge

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.libexil.androforge.data.AppItem
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.DragSource
import com.libexil.androforge.data.DropTarget
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.HomeItem
import com.libexil.androforge.data.IconRef
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.LayoutOps
import com.libexil.androforge.data.WidgetItem
import com.libexil.androforge.data.moved
import com.libexil.androforge.data.newId
import com.libexil.androforge.update.CheckResult
import com.libexil.androforge.update.UpdateInfo
import com.libexil.androforge.update.UpdateNotifier
import com.libexil.androforge.update.UpdateWorker
import com.libexil.androforge.update.Updater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface MenuTarget {
    data class Home(val item: HomeItem, val inDock: Boolean) : MenuTarget
    data class DrawerApp(val app: AppKey) : MenuTarget
    data class FolderApp(val folderId: String, val app: AppKey) : MenuTarget
    data class Background(val page: Int) : MenuTarget
}

data class MenuRequest(val anchor: Offset, val target: MenuTarget)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Installing(val info: UpdateInfo) : UpdateUi
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateUi
}

sealed interface UiEvent {
    data object OpenDrawer : UiEvent
    data object CloseDrawer : UiEvent
    data class ScrollToPage(val page: Int) : UiEvent
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LauncherApp
    val store = app.store
    val repo = app.apps
    val state = store.state
    val apps = repo.apps

    // ---- overlay / dialog state
    var menu by mutableStateOf<MenuRequest?>(null)
    var openFolderId by mutableStateOf<String?>(null)
    var settingsOpen by mutableStateOf(false)
    var widgetPickerPage by mutableStateOf<Int?>(null)
    var editApp by mutableStateOf<AppKey?>(null)
    var iconPickerFor by mutableStateOf<AppKey?>(null)
    var renameFolderId by mutableStateOf<String?>(null)
    var resizeWidgetId by mutableStateOf<String?>(null)
    var movingWidgetId by mutableStateOf<String?>(null)
    var hiddenAppsOpen by mutableStateOf(false)
    var searchFocusTick by mutableIntStateOf(0)
    var currentPage by mutableIntStateOf(0)

    /** Cell size in px, published by the home grid (needed to size new widgets). */
    var cellWidthPx = 1
    var cellHeightPx = 1

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    fun emit(e: UiEvent) {
        _events.tryEmit(e)
    }

    fun toast(msg: String) = Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()

    init {
        viewModelScope.launch {
            repo.loaded.first { it }
            if (!state.value.initialized) seedDefaults()
        }
    }

    /** First run: fill the dock with the default phone/messages/browser/camera apps. */
    private fun seedDefaults() {
        val pm = app.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        )
        val dockApps = intents.mapNotNull { intent ->
            @Suppress("DEPRECATION")
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            pkg?.takeIf { it != "android" }?.let { repo.findByPackage(it)?.key }
        }.distinct().take(5)
        val homeApps = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        ).mapNotNull { intent ->
            @Suppress("DEPRECATION")
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            pkg?.takeIf { it != "android" }?.let { repo.findByPackage(it)?.key }
        }.distinct().filter { it !in dockApps }
        store.update { s ->
            var next = s.copy(
                dock = dockApps.mapIndexed { i, k -> AppItem(newId(), -1, i, 0, k) },
                initialized = true,
            )
            val rows = next.settings.rows
            homeApps.forEachIndexed { i, k ->
                next = next.copy(items = next.items + AppItem(newId(), 0, i % next.settings.columns, rows - 1, k))
            }
            next
        }
    }

    // ---------------------------------------------------------------- apps

    fun launch(key: AppKey, bounds: android.graphics.Rect? = null) {
        repo.launch(key, bounds)
    }

    fun addToHome(key: AppKey) {
        store.update { LayoutOps.addToHome(it, AppItem(newId(), app = key), currentPage) }
        toast("Added to home screen")
    }

    fun addToDock(key: AppKey) {
        val s = state.value
        if (s.dock.size >= s.settings.dockMaxIcons) {
            toast("The dock is full")
            return
        }
        store.update { it.copy(dock = it.dock + AppItem(newId(), -1, it.dock.size, 0, key)) }
    }

    fun setHidden(key: AppKey, hidden: Boolean) =
        store.update { it.copy(hidden = if (hidden) it.hidden + key.id else it.hidden - key.id) }

    fun setLabel(key: AppKey, label: String?) = store.update {
        it.copy(labels = if (label.isNullOrBlank()) it.labels - key.id else it.labels + (key.id to label.trim()))
    }

    fun setIcon(key: AppKey, ref: IconRef?) = store.update {
        it.copy(icons = if (ref == null) it.icons - key.id else it.icons + (key.id to ref))
    }

    // ---------------------------------------------------------------- items

    fun removeItem(id: String, host: LauncherHost?) {
        val s = state.value
        (s.items.firstOrNull { it.id == id } as? WidgetItem)?.let { host?.deleteWidget(it.widgetId) }
        store.update { st -> st.copy(items = st.items.filterNot { it.id == id }, dock = st.dock.filterNot { it.id == id }.mapIndexed { i, it -> it.moved(-1, i, 0) }) }
    }

    fun renameFolder(id: String, name: String) = store.update { s ->
        fun f(list: List<HomeItem>) = list.map { if (it.id == id && it is FolderItem) it.copy(name = name.ifBlank { "Folder" }) else it }
        s.copy(items = f(s.items), dock = f(s.dock))
    }

    fun setFolderColor(id: String, color: Long?) = store.update { s ->
        fun f(list: List<HomeItem>) = list.map { if (it.id == id && it is FolderItem) it.copy(color = color) else it }
        s.copy(items = f(s.items), dock = f(s.dock))
    }

    fun removeFromFolder(folderId: String, key: AppKey) =
        store.update { LayoutOps.removeSource(it, DragSource.Folder(folderId, key)) }

    fun addPage() = store.update { it.copy(pageCount = (it.pageCount + 1).coerceAtMost(12)) }

    fun removePage(page: Int, host: LauncherHost?) {
        state.value.items.filter { it.page == page && it is WidgetItem }
            .forEach { host?.deleteWidget((it as WidgetItem).widgetId) }
        store.update { LayoutOps.removePage(it, page) }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val before = state.value.settings
        store.update { s ->
            val next = s.copy(settings = transform(s.settings))
            if (next.settings.columns != s.settings.columns || next.settings.rows != s.settings.rows) {
                LayoutOps.reflow(next)
            } else {
                next
            }
        }
        val after = state.value.settings
        if (before.iconPack != after.iconPack || before.iconShape != after.iconShape) repo.invalidateIcons()
        if (before.autoCheckUpdates != after.autoCheckUpdates) UpdateWorker.schedule(app, after.autoCheckUpdates)
    }

    // ---------------------------------------------------------------- drag & drop

    fun finishDrag(drag: com.libexil.androforge.ui.DragState, host: LauncherHost?) {
        val src = drag.source
        if (src == null || !drag.moved) {
            drag.clear()
            return
        }
        val target = drag.target()
        val payload = drag.item
        menu = null
        val appKey = (payload as? AppItem)?.app
        when (target) {
            DropTarget.Info -> appKey?.let(repo::openAppInfo)
            DropTarget.Uninstall -> appKey?.let(repo::uninstall)
            DropTarget.Remove -> {
                if (payload is FolderItem || src is DragSource.Folder || src is DragSource.Home || src is DragSource.Dock) {
                    if (src is DragSource.Home || src is DragSource.Dock) {
                        (state.value.items.firstOrNull { it.id == payload?.id } as? WidgetItem)?.let { host?.deleteWidget(it.widgetId) }
                    }
                    store.update { LayoutOps.removeSource(it, src) }
                }
            }
            else -> {
                if (target is DropTarget.HomeCell && target.page >= state.value.pageCount) addPage()
                var message: String? = null
                store.update { s ->
                    val r = LayoutOps.drop(s, src, target)
                    message = r.message
                    r.state
                }
                message?.let(::toast)
            }
        }
        if (src is DragSource.Drawer) emit(UiEvent.CloseDrawer)
        if (src is DragSource.Folder) {
            // Close the folder if it dissolved, otherwise keep it hidden after dragging out.
            openFolderId = null
        }
        drag.clear()
    }

    // ---------------------------------------------------------------- widgets

    fun placeWidget(widgetId: Int, info: AppWidgetProviderInfo, page: Int) {
        val s = state.value
        val cols = s.settings.columns
        val rows = s.settings.rows
        val density = app.resources.displayMetrics.density
        // minWidth/minHeight are px; add a little padding allowance per cell.
        val cw = (cellWidthPx - 8 * density).coerceAtLeast(1f)
        val ch = (cellHeightPx - 8 * density).coerceAtLeast(1f)
        val spanX = Math.ceil(info.minWidth / cw.toDouble()).toInt().coerceIn(1, cols)
        val spanY = Math.ceil(info.minHeight / ch.toDouble()).toInt().coerceIn(1, rows)
        val item = WidgetItem(
            id = newId(), spanX = spanX, spanY = spanY, widgetId = widgetId,
            provider = info.provider.flattenToString(),
        )
        store.update { LayoutOps.addToHome(it, item, page) }
    }

    fun resizeWidget(id: String, spanX: Int, spanY: Int): Boolean {
        val s = state.value
        val w = s.items.firstOrNull { it.id == id } as? WidgetItem ?: return false
        if (!LayoutOps.fits(s, w.page, w.x, w.y, spanX, spanY, s.settings.columns, s.settings.rows, ignoreId = id)) {
            toast("Not enough room - move things out of the way first")
            return false
        }
        store.update { st -> st.copy(items = st.items.map { if (it.id == id && it is WidgetItem) it.copy(spanX = spanX, spanY = spanY) else it }) }
        return true
    }

    fun moveWidgetTo(page: Int, x: Int, y: Int) {
        val id = movingWidgetId ?: return
        val s = state.value
        val w = s.items.firstOrNull { it.id == id } as? WidgetItem ?: return
        if (!LayoutOps.fits(s, page, x, y, w.spanX, w.spanY, s.settings.columns, s.settings.rows, ignoreId = id)) {
            toast("It doesn't fit there")
            return
        }
        store.update { st -> st.copy(items = st.items.map { if (it.id == id) it.moved(page, x, y) else it }) }
        movingWidgetId = null
    }

    // ---------------------------------------------------------------- back / home

    /** Returns true if something was closed. */
    fun closeTopOverlay(): Boolean {
        when {
            menu != null -> menu = null
            iconPickerFor != null -> iconPickerFor = null
            editApp != null -> editApp = null
            renameFolderId != null -> renameFolderId = null
            resizeWidgetId != null -> resizeWidgetId = null
            movingWidgetId != null -> movingWidgetId = null
            hiddenAppsOpen -> hiddenAppsOpen = false
            widgetPickerPage != null -> widgetPickerPage = null
            settingsOpen -> settingsOpen = false
            openFolderId != null -> openFolderId = null
            else -> return false
        }
        return true
    }

    fun closeEverything() {
        while (closeTopOverlay()) Unit
        emit(UiEvent.CloseDrawer)
    }

    // ---------------------------------------------------------------- updates

    var update by mutableStateOf<UpdateUi>(UpdateUi.Idle)
        private set
    var showUpdateDialog by mutableStateOf(false)
    private var updateJob: Job? = null

    fun checkForUpdates(manual: Boolean) {
        if (update is UpdateUi.Checking || update is UpdateUi.Downloading) return
        updateJob = viewModelScope.launch {
            update = UpdateUi.Checking
            val result = Updater.check()
            store.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            update = when (result) {
                is CheckResult.Available -> {
                    if (manual || result.info.versionCode != state.value.skippedVersion) showUpdateDialog = true
                    UpdateUi.Available(result.info)
                }
                CheckResult.UpToDate -> UpdateUi.UpToDate
                is CheckResult.Failed -> {
                    if (manual) toast("Update check failed: ${result.message}")
                    UpdateUi.Error(result.message)
                }
            }
        }
    }

    fun maybeAutoCheck() {
        val s = state.value
        if (!s.settings.autoCheckUpdates) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < 3 * 60 * 60 * 1000L) return
        checkForUpdates(manual = false)
    }

    fun skipUpdate(info: UpdateInfo) {
        store.update { it.copy(skippedVersion = info.versionCode) }
        showUpdateDialog = false
    }

    fun installUpdate(info: UpdateInfo) {
        if (!Updater.canInstall(app)) {
            toast("Allow AndroForge to install updates, then tap Update again")
            Updater.openInstallPermissionSettings(app)
            return
        }
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            try {
                update = UpdateUi.Downloading(info, 0f)
                val apk = Updater.download(app, info) { p -> update = UpdateUi.Downloading(info, p) }
                update = UpdateUi.Installing(info)
                UpdateNotifier.cancel(app)
                store.flush()
                Updater.install(app, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateUi.Error(e.message ?: "Update failed", info)
            }
        }
    }
}

/** Things only the Activity can do (widget binding, activity results). */
interface LauncherHost {
    fun widgetView(item: WidgetItem): android.view.View?
    fun pickWidget(info: AppWidgetProviderInfo, page: Int)
    fun deleteWidget(widgetId: Int)
    fun pickWallpaper()
    fun requestDefaultLauncher()
    fun isDefaultLauncher(): Boolean
    fun exportLayout()
    fun importLayout()
}
