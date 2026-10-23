package com.libexil.androforge.ui

import android.app.SearchManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.MenuRequest
import com.libexil.androforge.MenuTarget
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppItem
import com.libexil.androforge.data.DragSource
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.LayoutOps
import com.libexil.androforge.data.newId
import kotlinx.coroutines.delay

@Composable
fun AppDrawer(
    vm: LauncherViewModel,
    state: LauncherState,
    apps: List<AppInfo>,
    drag: DragState,
    drawer: DrawerController,
    onDragEnd: () -> Unit,
) {
    val settings = state.settings
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val fullyOpen = drawer.progress >= 0.999f
    val closed = !drawer.isOpen

    LaunchedEffect(vm.searchFocusTick) {
        if (vm.searchFocusTick > 0) {
            delay(180)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    LaunchedEffect(fullyOpen) {
        if (fullyOpen && settings.drawerAutoFocusSearch) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    LaunchedEffect(closed) {
        if (closed) {
            query = ""
            keyboard?.hide()
            focusManager.clearFocus()
            gridState.scrollToItem(0)
        }
    }

    val labels = state.labels
    fun labelOf(a: AppInfo) = labels[a.key.id] ?: a.label

    val visible = remember(apps, state.hidden, labels) {
        apps.filter { it.key.id !in state.hidden }.sortedBy { labelOf(it).lowercase() }
    }
    val filtered = remember(visible, query) {
        val q = query.trim()
        if (q.isEmpty()) visible
        else visible
            .filter { labelOf(it).contains(q, ignoreCase = true) || it.key.pkg.contains(q, ignoreCase = true) }
            .sortedBy { if (labelOf(it).startsWith(q, ignoreCase = true)) 0 else 1 }
    }
    val suggestions = remember(visible, state.launchCounts) {
        if (!settings.drawerShowSuggestions) emptyList()
        else visible.filter { (state.launchCounts[it.key.id] ?: 0) > 0 }
            .sortedByDescending { state.launchCounts[it.key.id] ?: 0 }
            .take(settings.drawerColumns)
    }

    val connection = remember(drawer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drawer.progress < 1f && available.y < 0f) {
                    drawer.dragBy(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    drawer.dragBy(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.progress < 1f) {
                    drawer.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val hiddenForDrag = drag.moved && drag.source is DragSource.Drawer
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - drawer.progress) * drawer.heightPx
                alpha = if (hiddenForDrag) 0f else 1f
            }
            .background(Color(0xFF0C0C10).copy(alpha = settings.drawerOpacity))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header: drag handle + search. Dragging it down closes the drawer.
        Column(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { drawer.dragBy(it) },
                    onDragStopped = { v -> drawer.settle(v) },
                )
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.35f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search ${visible.size} apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        filtered.firstOrNull()?.let { vm.launch(it.key) }
                    }),
                )
                IconButton(onClick = { vm.settingsOpen = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Launcher settings", tint = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        val cols = settings.drawerColumns
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(connection)
                .padding(horizontal = 8.dp),
        ) {
            if (query.isBlank() && suggestions.isNotEmpty()) {
                item(key = "sugg-title", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("Suggested")
                }
                items(suggestions, key = { "s:" + it.key.id }) { app ->
                    DrawerAppCell(app, labelOf(app), vm, drag, onDragEnd)
                }
                item(key = "sugg-div", span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                }
            }
            items(filtered, key = { "a:" + it.key.id }) { app ->
                DrawerAppCell(app, labelOf(app), vm, drag, onDragEnd)
            }
            if (query.isNotBlank()) {
                item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Search the web for “${query.trim()}”",
                        color = LocalAccent.current,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .pointerInput(query) {
                                detectTapGestures {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_WEB_SEARCH)
                                                .putExtra(SearchManager.QUERY, query.trim())
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    } catch (_: Exception) {
                                        vm.toast("No browser found")
                                    }
                                }
                            },
                    )
                }
            }
            item(key = "bottom-space", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun DrawerAppCell(app: AppInfo, label: String, vm: LauncherViewModel, drag: DragState, onDragEnd: () -> Unit) {
    val settings = LocalSettings.current
    val pos = remember { PosHolder() }
    val haptic = LocalHapticFeedback.current
    val endDrag by rememberUpdatedState(onDragEnd)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .trackPosition(pos)
            .launcherGestures(
                key = app.key.id,
                onTap = { vm.launch(app.key, pos.rect()) },
                onLongPress = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val at = pos.pos + off
                    vm.menu = MenuRequest(at, MenuTarget.DrawerApp(app.key))
                    drag.start(DragSource.Drawer(app.key), AppItem(newId(), app = app.key), at)
                },
                onDrag = { d ->
                    drag.moveBy(d)
                    if (drag.moved) vm.menu = null
                },
                onDragEnd = { endDrag() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.key, settings.iconSizeDp.dp)
        Spacer(Modifier.height(6.dp))
        IconLabel(label, color = Color.White)
    }
}

/** A folder opened from the home screen or dock. */
@Composable
fun FolderOverlay(
    vm: LauncherViewModel,
    state: LauncherState,
    appsById: Map<String, AppInfo>,
    folderId: String,
    drag: DragState,
    onDragEnd: () -> Unit,
) {
    val folder: FolderItem = LayoutOps.findFolder(state, folderId) ?: run {
        LaunchedEffect(folderId) { vm.openFolderId = null }
        return
    }
    val settings = state.settings
    val accent = LocalAccent.current
    val hiddenForDrag = drag.moved && drag.source is DragSource.Folder
    val tint = folder.color?.let { Color(it) } ?: accent
    val appear = remember { androidx.compose.animation.core.Animatable(0.88f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f)) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (hiddenForDrag) 0f else 1f }
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { vm.openFolderId = null } },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                }
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(
                    androidx.core.graphics.ColorUtils.blendARGB(
                        tint.toArgb(), 0xFF15151B.toInt(), 0.8f,
                    ),
                ).copy(alpha = 0.96f),
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    folder.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(folder.id) { detectTapGestures { vm.renameFolderId = folder.id } }
                        .padding(bottom = 14.dp, start = 4.dp),
                )
                val perRow = 4
                folder.apps.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            FolderAppCell(
                                folderId = folder.id,
                                app = key,
                                label = labelFor(key, appsById[key.id]?.label),
                                iconSize = (settings.iconSizeDp - 2).dp,
                                vm = vm,
                                drag = drag,
                                onDragEnd = onDragEnd,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    "Long-press an app to drag it out",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderAppCell(
    folderId: String,
    app: com.libexil.androforge.data.AppKey,
    label: String,
    iconSize: androidx.compose.ui.unit.Dp,
    vm: LauncherViewModel,
    drag: DragState,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    val pos = remember { PosHolder() }
    val haptic = LocalHapticFeedback.current
    val locked = LocalSettings.current.lockLayout
    val endDrag by rememberUpdatedState(onDragEnd)
    Column(
        modifier
            .padding(vertical = 8.dp)
            .trackPosition(pos)
            .launcherGestures(
                key = folderId + app.id,
                onTap = {
                    vm.launch(app, pos.rect())
                    vm.openFolderId = null
                },
                onLongPress = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val at = pos.pos + off
                    vm.menu = MenuRequest(at, MenuTarget.FolderApp(folderId, app))
                    if (!locked) drag.start(DragSource.Folder(folderId, app), AppItem(newId(), app = app), at)
                },
                onDrag = { d ->
                    drag.moveBy(d)
                    if (drag.moved) vm.menu = null
                },
                onDragEnd = { endDrag() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, iconSize)
        Spacer(Modifier.height(6.dp))
        IconLabel(label, color = Color.White, sizeSp = 11)
        Spacer(Modifier.width(1.dp))
    }
}
