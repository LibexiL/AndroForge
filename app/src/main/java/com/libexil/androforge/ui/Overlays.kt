package com.libexil.androforge.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.libexil.androforge.LauncherHost
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.MenuRequest
import com.libexil.androforge.MenuTarget
import com.libexil.androforge.UpdateUi
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppItem
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.ui.forge.ForgeKindGlyph
import com.libexil.androforge.data.HomeItem
import com.libexil.androforge.data.isResizable
import com.libexil.androforge.data.IconPacks
import com.libexil.androforge.data.IconRef
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.LayoutOps
import com.libexil.androforge.data.WidgetItem
import com.libexil.androforge.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class MenuEntry(val label: String, val icon: ImageVector, val action: () -> Unit)

@Composable
fun ContextMenu(req: MenuRequest, vm: LauncherViewModel, host: LauncherHost, state: LauncherState, appsById: Map<String, AppInfo>) {
    val target = req.target
    val repo = vm.repo
    fun close() {
        vm.menu = null
    }

    val appKey: AppKey? = when (target) {
        is MenuTarget.Home -> (target.item as? AppItem)?.app
        is MenuTarget.DrawerApp -> target.app
        is MenuTarget.FolderApp -> target.app
        is MenuTarget.Background -> null
    }
    val shortcuts: List<ShortcutInfo> by produceState(emptyList<ShortcutInfo>(), appKey) {
        value = if (appKey == null) emptyList() else withContext(Dispatchers.IO) { repo.shortcuts(appKey) }
    }

    val entries = buildList {
        when (target) {
            is MenuTarget.Home -> when (val item = target.item) {
                is AppItem -> {
                    add(MenuEntry("Edit", Icons.Default.Edit) { vm.editApp = item.app })
                    add(MenuEntry("App info", Icons.Default.Info) { repo.openAppInfo(item.app) })
                    add(MenuEntry("Uninstall", Icons.Default.Delete) { repo.uninstall(item.app) })
                    if (!state.settings.lockLayout) add(MenuEntry("Remove", Icons.Default.Close) { vm.removeItem(item.id, host) })
                }
                is FolderItem -> {
                    add(MenuEntry("Rename & color", Icons.Default.Edit) { vm.renameFolderId = item.id })
                    if (!state.settings.lockLayout) add(MenuEntry("Remove folder", Icons.Default.Close) { vm.removeItem(item.id, host) })
                }
                is ForgeWidgetItem -> {
                    add(MenuEntry("Customize", Icons.Default.Edit) { vm.editForgeId = item.id })
                    if (!state.settings.lockLayout) {
                        add(MenuEntry("Resize", Icons.Default.Menu) { vm.resizeWidgetId = item.id })
                        add(MenuEntry("Move", Icons.Default.KeyboardArrowDown) { vm.movingWidgetId = item.id })
                        add(MenuEntry("Remove", Icons.Default.Close) { vm.removeItem(item.id, host) })
                    }
                }
                is WidgetItem -> {
                    if (!state.settings.lockLayout) {
                        add(MenuEntry("Resize", Icons.Default.Menu) { vm.resizeWidgetId = item.id })
                        add(MenuEntry("Move", Icons.Default.KeyboardArrowDown) { vm.movingWidgetId = item.id })
                        add(MenuEntry("Remove", Icons.Default.Close) { vm.removeItem(item.id, host) })
                    }
                }
            }
            is MenuTarget.DrawerApp -> {
                add(MenuEntry("Add to home", Icons.Default.Home) { vm.addToHome(target.app) })
                add(MenuEntry("Add to dock", Icons.Default.Star) { vm.addToDock(target.app) })
                add(MenuEntry("Edit", Icons.Default.Edit) { vm.editApp = target.app })
                add(MenuEntry("Hide", Icons.Default.Lock) { vm.setHidden(target.app, true) })
                add(MenuEntry("App info", Icons.Default.Info) { repo.openAppInfo(target.app) })
                add(MenuEntry("Uninstall", Icons.Default.Delete) { repo.uninstall(target.app) })
            }
            is MenuTarget.FolderApp -> {
                add(MenuEntry("Remove from folder", Icons.Default.Close) { vm.removeFromFolder(target.folderId, target.app) })
                add(MenuEntry("Edit", Icons.Default.Edit) { vm.editApp = target.app })
                add(MenuEntry("App info", Icons.Default.Info) { repo.openAppInfo(target.app) })
            }
            is MenuTarget.Background -> {
                add(MenuEntry("Widgets", Icons.Default.Add) { vm.widgetPickerPage = target.page })
                add(MenuEntry("Wallpaper", Icons.Default.Face) { host.pickWallpaper() })
                add(MenuEntry("Launcher settings", Icons.Default.Settings) { vm.settingsOpen = true })
                add(MenuEntry("Add page", Icons.Default.Add) { vm.addPage() })
                if (state.pageCount > 1) add(MenuEntry("Remove this page", Icons.Default.Delete) { vm.removePage(target.page, host) })
                add(
                    MenuEntry(if (state.settings.lockLayout) "Unlock layout" else "Lock layout", Icons.Default.Lock) {
                        vm.updateSettings { it.copy(lockLayout = !it.lockLayout) }
                    },
                )
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val margin = with(density) { 12.dp.toPx() }
        val gap = with(density) { 36.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val x = (req.anchor.x - cardSize.width / 2f).coerceIn(margin, maxOf(margin, maxW - cardSize.width - margin))
        val above = req.anchor.y - cardSize.height - gap
        val yRaw = if (above > margin * 4) above else req.anchor.y + gap
        val y = yRaw.coerceIn(margin, maxOf(margin, maxH - cardSize.height - margin))
        Card(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(232.dp)
                .onSizeChanged { cardSize = it }
                .alpha(if (cardSize == IntSize.Zero) 0f else 1f)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                shortcuts.forEach { sc ->
                    MenuRow(sc.shortLabel?.toString() ?: "Shortcut", Icons.Default.Star) {
                        close()
                        repo.startShortcut(sc)
                    }
                }
                if (shortcuts.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                entries.forEach { e ->
                    MenuRow(e.label, e.icon) {
                        close()
                        e.action()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------- dialogs

val folderColors = listOf(
    0xFFE5484DL, 0xFFF76B15L, 0xFFFFC53DL, 0xFF46A758L,
    0xFF12A594L, 0xFF0090FFL, 0xFF6E56CFL, 0xFFD6409FL, 0xFF8D8D8DL,
)

@Composable
fun Dialogs(vm: LauncherViewModel, state: LauncherState, appsById: Map<String, AppInfo>, host: LauncherHost) {
    vm.editApp?.let { key -> EditAppDialog(vm, state, key, appsById[key.id]?.label) }
    vm.iconPickerFor?.let { key -> IconPickerDialog(vm, state, key) }
    vm.renameFolderId?.let { id -> LayoutOps.findFolder(state, id)?.let { FolderDialog(vm, it) } }
    vm.resizeWidgetId?.let { id -> state.items.firstOrNull { it.id == id && it.isResizable }?.let { ResizeDialog(vm, state, it) } }
    if (vm.hiddenAppsOpen) HiddenAppsDialog(vm, state)
}

@Composable
private fun EditAppDialog(vm: LauncherViewModel, state: LauncherState, key: AppKey, original: String?) {
    var label by remember(key) { mutableStateOf(state.labels[key.id] ?: original ?: "") }
    AlertDialog(
        onDismissRequest = { vm.editApp = null },
        title = { Text("Edit app") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(key, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            vm.editApp = null
                            vm.iconPickerFor = key
                        }) { Text("Change icon") }
                        if (state.icons[key.id] != null) {
                            TextButton(onClick = { vm.setIcon(key, null) }) { Text("Reset icon") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (original != null && label != original) {
                    TextButton(onClick = { label = original }) { Text("Restore “$original”") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setLabel(key, if (label == original) null else label)
                vm.editApp = null
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.editApp = null }) { Text("Cancel") } },
    )
}

@Composable
private fun IconPickerDialog(vm: LauncherViewModel, state: LauncherState, key: AppKey) {
    val context = LocalContext.current
    val packs = remember { IconPacks.installed(context) }
    Dialog(
        onDismissRequest = { vm.iconPickerFor = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose an icon", fontSize = 22.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.iconPickerFor = null }) { Icon(Icons.Default.Close, "Close") }
                }
                if (packs.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "No icon packs installed. Install any ADW/Nova-compatible icon pack " +
                            "(there are hundreds on the Play Store) and it will show up here.",
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    return@Column
                }
                var pack by remember { mutableStateOf(state.settings.iconPack?.takeIf { p -> packs.any { it.pkg == p } } ?: packs.first().pkg) }
                var query by remember { mutableStateOf("") }
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    packs.forEach { p ->
                        FilterChip(selected = p.pkg == pack, onClick = { pack = p.pkg }, label = { Text(p.label) })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search icons") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val names: List<String> by produceState(emptyList<String>(), pack) {
                    value = withContext(Dispatchers.Default) { IconPacks.get(context, pack).allDrawables }
                }
                val shown = remember(names, query) {
                    val q = query.trim().replace(' ', '_')
                    if (q.isEmpty()) names else names.filter { it.contains(q, ignoreCase = true) }
                }
                Spacer(Modifier.height(8.dp))
                Text("${shown.size} icons", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it }) { name ->
                        Box(
                            Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    vm.setIcon(key, IconRef(pack, name))
                                    vm.iconPickerFor = null
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) { PackIcon(vm, pack, name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIcon(vm: LauncherViewModel, pack: String, name: String) {
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }
    val img: ImageBitmap? by produceState<ImageBitmap?>(null, pack, name) {
        value = vm.repo.loadPackDrawable(pack, name, px)
    }
    val b = img
    if (b != null) Image(b, contentDescription = name, modifier = Modifier.size(48.dp))
    else Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun FolderDialog(vm: LauncherViewModel, folder: FolderItem) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var color by remember(folder.id) { mutableStateOf(folder.color) }
    val accent = LocalAccent.current
    AlertDialog(
        onDismissRequest = { vm.renameFolderId = null },
        title = { Text("Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Color", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(accent, selected = color == null, label = "A") { color = null }
                    folderColors.forEach { c -> ColorDot(Color(c), selected = color == c) { color = c } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.renameFolder(folder.id, name)
                vm.setFolderColor(folder.id, color)
                vm.renameFolderId = null
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.renameFolderId = null }) { Text("Cancel") } },
    )
}

@Composable
fun ColorDot(color: Color, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun ResizeDialog(vm: LauncherViewModel, state: LauncherState, widget: HomeItem) {
    var w by remember(widget.id) { mutableIntStateOf(widget.spanX) }
    var h by remember(widget.id) { mutableIntStateOf(widget.spanY) }
    AlertDialog(
        onDismissRequest = { vm.resizeWidgetId = null },
        title = { Text("Resize widget") },
        text = {
            Column {
                Stepper("Width", w, 1, state.settings.columns) { w = it }
                Stepper("Height", h, 1, state.settings.rows) { h = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (vm.resizeWidget(widget.id, w, h)) vm.resizeWidgetId = null }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = { vm.resizeWidgetId = null }) { Text("Cancel") } },
    )
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("−", fontSize = 20.sp) }
        Text("$value", fontSize = 18.sp, modifier = Modifier.width(32.dp))
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun HiddenAppsDialog(vm: LauncherViewModel, state: LauncherState) {
    val apps = vm.apps.value
    Dialog(onDismissRequest = { vm.hiddenAppsOpen = false }) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("Hidden apps", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(
                    "Hidden apps stay out of the drawer and search.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(apps, key = { it.key.id }) { app ->
                        val hidden = app.key.id in state.hidden
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setHidden(app.key, !hidden) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(state.labels[app.key.id] ?: app.label, modifier = Modifier.weight(1f))
                            Checkbox(checked = hidden, onCheckedChange = { vm.setHidden(app.key, it) })
                        }
                    }
                }
                TextButton(
                    onClick = { vm.hiddenAppsOpen = false },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp),
                ) { Text("Done") }
            }
        }
    }
}

// ------------------------------------------------------------------- widgets

private fun Drawable.toPreview(maxPx: Int): ImageBitmap? {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
    return try {
        toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun WidgetPicker(vm: LauncherViewModel, host: LauncherHost) {
    val context = LocalContext.current
    val page = vm.widgetPickerPage ?: return
    val groups: List<Pair<String, List<AppWidgetProviderInfo>>> by produceState(emptyList<Pair<String, List<AppWidgetProviderInfo>>>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            AppWidgetManager.getInstance(context).installedProviders
                .groupBy { info ->
                    try {
                        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                    } catch (e: Exception) {
                        info.provider.packageName
                    }
                }
                .toList()
                .sortedBy { it.first.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = { vm.widgetPickerPage = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Widgets", fontSize = 24.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.widgetPickerPage = null }) { Icon(Icons.Default.Close, "Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search widgets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = groups.filter { query.isBlank() || it.first.contains(query.trim(), ignoreCase = true) }
                if (groups.isEmpty()) {
                    Text("Loading…", modifier = Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.6f))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (query.isBlank() || "androforge".contains(query.trim(), ignoreCase = true)) {
                        item(key = "forge-h") {
                            Text(
                                "AndroForge",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                            )
                        }
                        items(ForgeKind.entries, key = { "forge:" + it.name }) { kind ->
                            ForgeWidgetRow(kind) {
                                vm.widgetPickerPage = null
                                vm.addForgeWidget(kind, page)
                            }
                        }
                    }
                    shown.forEach { (appLabel, infos) ->
                        item(key = "h:$appLabel") {
                            Text(
                                appLabel,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                            )
                        }
                        items(infos, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                            WidgetRow(info, vm) {
                                vm.widgetPickerPage = null
                                host.pickWidget(info, page)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ForgeWidgetRow(kind: ForgeKind, onPick: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pal.background),
            contentAlignment = Alignment.Center,
        ) {
            ForgeKindGlyph(kind, pal)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.label, fontSize = 15.sp)
            Text(kind.blurb, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun WidgetRow(info: AppWidgetProviderInfo, vm: LauncherViewModel, onPick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val maxPx = with(LocalDensity.current) { 180.dp.roundToPx() }
    val preview: ImageBitmap? by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            try {
                (info.loadPreviewImage(context, density) ?: info.loadIcon(context, density))?.toPreview(maxPx)
            } catch (e: Exception) {
                null
            }
        }
    }
    val label = remember(info) { info.loadLabel(context.packageManager) ?: "Widget" }
    val cw = vm.cellWidthPx.coerceAtLeast(1)
    val ch = vm.cellHeightPx.coerceAtLeast(1)
    val spanX = Math.ceil(info.minWidth / cw.toDouble()).toInt().coerceAtLeast(1)
    val spanY = Math.ceil(info.minHeight / ch.toDouble()).toInt().coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            val p = preview
            if (p != null) Image(p, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            Text("$spanX × $spanY", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

// ------------------------------------------------------------------- updates

@Composable
fun UpdateDialog(vm: LauncherViewModel) {
    if (!vm.showUpdateDialog) return
    val ui = vm.update
    val info = when (ui) {
        is UpdateUi.Available -> ui.info
        is UpdateUi.Downloading -> ui.info
        is UpdateUi.Installing -> ui.info
        is UpdateUi.Error -> ui.info
        else -> null
    } ?: return
    val busy = ui is UpdateUi.Downloading || ui is UpdateUi.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) vm.showUpdateDialog = false },
        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        title = { Text("AndroForge ${info.versionName}") },
        text = {
            Column {
                Text(
                    "You're on ${Updater.currentVersionName}.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.heightIn(max = 220.dp)) {
                    LazyColumn {
                        item { Text(info.changelog.ifBlank { "Bug fixes and improvements." }, fontSize = 14.sp) }
                    }
                }
                when (ui) {
                    is UpdateUi.Downloading -> {
                        Spacer(Modifier.height(14.dp))
                        if (ui.progress >= 0f) {
                            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateUi.Installing -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Installing… the launcher will restart.", fontSize = 13.sp)
                    }
                    is UpdateUi.Error -> {
                        Spacer(Modifier.height(14.dp))
                        Text(ui.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.installUpdate(info) }, enabled = !busy) {
                Text(if (ui is UpdateUi.Error) "Retry" else "Update")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.skipUpdate(info) }, enabled = !busy) { Text("Skip") }
                TextButton(onClick = { vm.showUpdateDialog = false }, enabled = !busy) { Text("Later") }
            }
        },
    )
}
