package com.libexil.androforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libexil.androforge.LauncherHost
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.MenuRequest
import com.libexil.androforge.MenuTarget
import com.libexil.androforge.SystemActions
import com.libexil.androforge.UiEvent
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppItem
import com.libexil.androforge.data.DragSource
import com.libexil.androforge.data.DropTarget
import com.libexil.androforge.data.FolderItem
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.GestureAction
import com.libexil.androforge.data.HomeItem
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.WidgetItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.mutableStateOf
import com.libexil.androforge.data.BELOW_DOCK
import com.libexil.androforge.data.LayoutOps
import android.Manifest
import com.libexil.androforge.ui.forge.ForgeActions
import com.libexil.androforge.ui.forge.ForgeEditor
import com.libexil.androforge.ui.forge.ForgeWidget

/** Mutable holder for layout positions that shouldn't trigger recomposition. */
class PosHolder {
    var pos: Offset = Offset.Zero
    var size: IntSize = IntSize.Zero
    val center: Offset get() = pos + Offset(size.width / 2f, size.height / 2f)
    fun rect(): android.graphics.Rect = android.graphics.Rect(
        pos.x.roundToInt(), pos.y.roundToInt(),
        (pos.x + size.width).roundToInt(), (pos.y + size.height).roundToInt(),
    )
}

fun Modifier.trackPosition(holder: PosHolder): Modifier = onGloballyPositioned {
    holder.pos = it.positionInRoot()
    holder.size = it.size
}

/** Drawer open fraction (0 closed .. 1 open), driven by gestures and springs. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var heightPx = 1f
    var motion: Motion = Motion.Default
    private var job: Job? = null

    val isOpen: Boolean get() = progress > 0.001f
    private var closing = false

    fun dragBy(dy: Float) {
        job?.cancel()
        if (dy != 0f) closing = dy > 0f
        // Closing moves a bit faster than your finger so a short pull is enough.
        val factor = if (dy > 0f) 1.35f else 1f
        progress = (progress - dy * factor / heightPx).coerceIn(0f, 1f)
    }

    /** Decide open/closed from where it is, which way you were moving, and how fast. */
    fun settle(velocityY: Float) {
        val target = when {
            velocityY > 250f -> 0f
            velocityY < -250f -> 1f
            closing -> if (progress < 0.88f) 0f else 1f
            else -> if (progress > 0.15f) 1f else 0f
        }
        animateTo(target)
    }

    fun open() = animateTo(1f)
    fun close() = animateTo(0f)
    fun snapClosed() {
        job?.cancel()
        progress = 0f
    }

    private fun animateTo(target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(progress, target, animationSpec = motion.spring(stiffness = 420f, visibilityThreshold = 0.001f)) { v, _ ->
                progress = v
            }
        }
    }
}

@Composable
fun LauncherScreen(vm: LauncherViewModel, host: LauncherHost) {
    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val settings = state.settings
    val palette = rememberPalette(settings)
    val accent = rememberAccent(settings, palette)
    val appsById = remember(apps) { apps.associateBy { it.key.id } }
    val scope = rememberCoroutineScope()
    val drag = remember { DragState() }
    val drawer = remember { DrawerController(scope) }
    val pagerState = rememberPagerState { state.pageCount }
    val motion = remember(settings.animations, settings.animScale, settings.animBounce) { Motion.of(settings) }
    SideEffect { drawer.motion = motion }
    val context = LocalContext.current
    val density = LocalDensity.current
    drag.moveThresholdPx = with(density) { 12.dp.toPx() }

    val onDragEnd: () -> Unit = {
        if (drag.moved && drag.source is DragSource.Drawer) drawer.snapClosed()
        vm.finishDrag(drag, host)
    }

    fun perform(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> drawer.open()
            GestureAction.SEARCH -> {
                drawer.open()
                vm.searchFocusTick++
            }
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.LAUNCHER_SETTINGS -> vm.settingsOpen = true
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                UiEvent.OpenDrawer -> drawer.open()
                UiEvent.CloseDrawer -> drawer.close()
                is UiEvent.ScrollToPage -> if (e.page < pagerState.pageCount) {
                    pagerState.animateScrollToPage(e.page, animationSpec = motion.spring(stiffness = 300f, bouncy = false))
                }
            }
        }
    }

    // Dragging an app out of the drawer reveals the home screen underneath.
    LaunchedEffect(drag.moved, drag.source) {
        if (drag.moved && drag.source is DragSource.Drawer) drawer.snapClosed()
    }

    // Keep the pager in range when pages are removed.
    LaunchedEffect(state.pageCount) {
        if (pagerState.currentPage >= state.pageCount) pagerState.scrollToPage(state.pageCount - 1)
    }

    BackHandler {
        if (!vm.closeTopOverlay()) {
            if (drawer.isOpen) drawer.close()
            else if (pagerState.currentPage != 0) scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalIconOverrides provides state.icons,
        LocalLabels provides state.labels,
        LocalAccent provides accent,
        LocalPalette provides palette,
        LocalMotion provides motion,
    ) {
        AndroForgeTheme(accent) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                drawer.heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val swipeThreshold = with(density) { 90.dp.toPx() }

                // ---------------- home layer
                val total = remember { floatArrayOf(0f, 0f) } // [0] = distance, [1] = mode
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = drawer.progress
                            alpha = 1f - p
                            scaleX = 1f - 0.06f * p
                            scaleY = 1f - 0.06f * p
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = drag.source == null && vm.movingWidgetId == null,
                            state = rememberDraggableState { d ->
                                total[0] += d
                                if (total[1] == 0f) {
                                    total[1] = if (total[0] < 0 && settings.swipeUp == GestureAction.APP_DRAWER) 1f else 2f
                                }
                                if (total[1] == 1f) drawer.dragBy(d)
                            },
                            onDragStarted = {
                                total[0] = 0f
                                total[1] = 0f
                            },
                            onDragStopped = { v ->
                                if (total[1] == 1f) {
                                    drawer.settle(v)
                                } else if (total[0] > swipeThreshold) {
                                    perform(settings.swipeDown)
                                } else if (total[0] < -swipeThreshold) {
                                    perform(settings.swipeUp)
                                }
                            },
                        ),
                ) {
                    // Widgets that live UNDER the dock, down to the very bottom edge of the screen.
                    val belowRows = settings.belowDockRows
                    SideEffect { drag.belowRows = belowRows }
                    if (belowRows > 0) {
                        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val rowH = with(density) { vm.cellHeightPx.toDp() }.coerceIn(48.dp, 160.dp)
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(rowH * belowRows + navBottom)
                                .padding(horizontal = 6.dp)
                                .onGloballyPositioned { drag.belowRect = it.boundsInRoot() },
                        ) {
                            val showDrop = drag.active && (drag.item is WidgetItem || drag.item is ForgeWidgetItem) &&
                                state.items.none { it.page == BELOW_DOCK }
                            if (showDrop) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Text(
                                        "Drop a widget here to put it under the dock",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                            HomePage(BELOW_DOCK, belowRows, vm, state, appsById, drag, host, onDragEnd, {}, publishCells = false)
                        }
                    } else {
                        SideEffect { drag.belowRect = Rect.Zero }
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        HomePager(
                            vm = vm,
                            state = state,
                            appsById = appsById,
                            drag = drag,
                            pagerState = pagerState,
                            host = host,
                            onDragEnd = onDragEnd,
                            onDoubleTap = { perform(settings.doubleTap) },
                            modifier = Modifier.weight(1f),
                        )
                        if (settings.pageIndicator && state.pageCount > 1) {
                            PageIndicator(pagerState, accent)
                        }
                        if (settings.dockEnabled) {
                            Dock(vm = vm, state = state, appsById = appsById, drag = drag, onDragEnd = onDragEnd)
                        } else {
                            drag.dockRect = Rect.Zero
                            drag.dockSlots = emptyList()
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                DragTargetsBar(drag)

                // ---------------- overlays
                if (drawer.isOpen || (drag.source is DragSource.Drawer)) {
                    AppDrawer(
                        vm = vm,
                        state = state,
                        apps = apps,
                        drag = drag,
                        drawer = drawer,
                        onDragEnd = onDragEnd,
                    )
                }

                vm.openFolderId?.let { id ->
                    FolderOverlay(vm = vm, state = state, appsById = appsById, folderId = id, drag = drag, onDragEnd = onDragEnd)
                }

                if (vm.movingWidgetId != null) MoveWidgetHint(onCancel = { vm.movingWidgetId = null })

                vm.editForgeId?.let { id ->
                    (state.items.firstOrNull { it.id == id } as? ForgeWidgetItem)?.let { item ->
                        ForgeEditor(
                            item = item,
                            pal = palette,
                            onOptions = { t -> vm.updateForge(id) { it.copy(options = t(it.options)) } },
                            onLook = { t -> vm.updateForge(id) { it.copy(look = t(it.look)) } },
                            onRequestLocation = { host.requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            onClose = { vm.editForgeId = null },
                        )
                    } ?: run { vm.editForgeId = null }
                }

                vm.menu?.let { ContextMenu(it, vm, host, state, appsById) }

                Dialogs(vm, state, appsById, host)

                if (vm.widgetPickerPage != null) WidgetPicker(vm, host)

                AnimatedVisibility(
                    visible = vm.settingsOpen,
                    enter = fadeIn(motion.tween(160)) + slideInVertically(motion.tween(260)) { it / 9 },
                    exit = fadeOut(motion.tween(140)) + slideOutVertically(motion.tween(200)) { it / 12 },
                ) { SettingsScreen(vm, host, state, apps) }

                if (vm.selecting) SelectionBar(vm, state)
                if (vm.newFolderDialog) NewFolderDialog(vm)
                vm.folderPickerFor?.let { id -> FolderAppPicker(vm, state, apps, id) }

                if (vm.paletteBrowserOpen) PaletteBrowser(vm)

                UpdateDialog(vm)

                DragGhost(drag, settings.iconSizeDp, vm)
            }
        }
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState, accent: Color) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pagerState.pageCount) { i ->
            val selected = pagerState.currentPage == i
            val w by animateDpAsState(if (selected) 18.dp else 6.dp, LocalMotion.current.spring(stiffness = 700f), label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = w, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.White.copy(alpha = 0.45f))
                    .pointerInput(i) { detectTapGestures { scope.launch { pagerState.animateScrollToPage(i) } } },
            )
        }
    }
}

@Composable
private fun HomePager(
    vm: LauncherViewModel,
    state: LauncherState,
    appsById: Map<String, AppInfo>,
    drag: DragState,
    pagerState: PagerState,
    host: LauncherHost,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier,
) {
    val settings = state.settings
    SideEffect {
        drag.cols = settings.columns
        drag.rows = settings.rows
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            vm.currentPage = it
            drag.page = it
        }
    }

    // Auto-flip pages when an icon is held near the screen edge.
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(drag.active) {
        var dwell = 0L
        var side = 0
        while (drag.active) {
            val p = drag.position
            val r = drag.gridRect
            val s = when {
                r == Rect.Zero || p.y < r.top || p.y > r.bottom -> 0
                p.x < r.left + edge -> -1
                p.x > r.right - edge -> 1
                else -> 0
            }
            if (s != 0 && s == side) {
                dwell += 50
                if (dwell >= 650) {
                    val target = pagerState.currentPage + s
                    if (target >= 0) {
                        if (target >= pagerState.pageCount && pagerState.pageCount < 12) {
                            vm.addPage()
                            delay(60)
                        }
                        if (target < pagerState.pageCount) pagerState.animateScrollToPage(target)
                    }
                    dwell = 0
                }
            } else {
                side = s
                dwell = 0
            }
            delay(50)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp)
            .onGloballyPositioned { drag.gridRect = it.boundsInRoot() },
        beyondViewportPageCount = 1,
        key = { it },
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = LocalMotion.current.spring(stiffness = 380f, visibilityThreshold = 0.5f, bouncy = false),
        ),
    ) { page ->
        HomePage(page, settings.rows, vm, state, appsById, drag, host, onDragEnd, onDoubleTap)
    }
}

@Composable
private fun HomePage(
    page: Int,
    rows: Int,
    vm: LauncherViewModel,
    state: LauncherState,
    appsById: Map<String, AppInfo>,
    drag: DragState,
    host: LauncherHost,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    publishCells: Boolean = true,
) {
    val settings = state.settings
    val cols = settings.columns
    val origin = remember { PosHolder() }
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .trackPosition(origin),
    ) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val cellWpx = constraints.maxWidth.toFloat() / cols
        val cellHpx = constraints.maxHeight.toFloat() / rows
        SideEffect {
            if (publishCells) {
                vm.cellWidthPx = cellWpx.roundToInt()
                vm.cellHeightPx = cellHpx.roundToInt()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(page, cellWpx, cellHpx) {
                    detectTapGestures(
                        onTap = { off ->
                            if (vm.movingWidgetId != null) {
                                vm.moveWidgetTo(page, (off.x / cellWpx).toInt(), (off.y / cellHpx).toInt())
                            }
                        },
                        onDoubleTap = { currentDoubleTap() },
                        onLongPress = { off ->
                            vm.menu = MenuRequest(origin.pos + off, MenuTarget.Background(page))
                        },
                    )
                },
        )

        DropHighlight(drag, page, rows, cellW, cellH, state)

        state.items.filter { it.page == page }.forEach { item ->
            key(item.id) {
                val dragged = drag.active && drag.source is DragSource.Home && drag.item?.id == item.id
                val isWidget = item is WidgetItem || item is ForgeWidgetItem
                val motion = LocalMotion.current
                val a by animateFloatAsState(if (dragged) (if (isWidget) 0.3f else 0f) else 1f, motion.tween(120), label = "lift")
                Box(
                    Modifier
                        .offset(x = cellW * item.x, y = cellH * item.y)
                        .size(width = cellW * item.spanX, height = cellH * item.spanY)
                        .graphicsLayer { alpha = a },
                ) {
                    when (item) {
                        is AppItem -> HomeAppCell(item, appsById[item.app.id]?.label, vm, drag, onDragEnd)
                        is FolderItem -> HomeFolderCell(item, vm, drag, onDragEnd)
                        is ForgeWidgetItem -> ForgeWidgetCell(item, vm, host, drag, onDragEnd)
                        is WidgetItem -> WidgetCell(
                            item, vm, host, drag, onDragEnd,
                            widthDp = (cellW * item.spanX).value.roundToInt(),
                            heightDp = (cellH * item.spanY).value.roundToInt(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropHighlight(
    drag: DragState,
    page: Int,
    rows: Int,
    cellW: androidx.compose.ui.unit.Dp,
    cellH: androidx.compose.ui.unit.Dp,
    state: LauncherState,
) {
    if (!drag.active) return
    val target = drag.target()
    if (target !is DropTarget.HomeCell || target.page != page) return
    val item = drag.item ?: return
    val big = item.spanX > 1 || item.spanY > 1 || item is WidgetItem || item is ForgeWidgetItem
    val sx = if (big) item.spanX.coerceAtMost(state.settings.columns) else 1
    val sy = if (big) item.spanY.coerceAtMost(rows) else 1
    val ok = !big || LayoutOps.fits(state, page, target.x, target.y, sx, sy, state.settings.columns, rows, ignoreId = item.id)
    val color = if (ok) LocalAccent.current else Color(0xFFE5484D)
    val motion = LocalMotion.current
    val x by animateDpAsState(cellW * target.x, motion.spring(stiffness = 900f), label = "hx")
    val y by animateDpAsState(cellH * target.y, motion.spring(stiffness = 900f), label = "hy")
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(cellW * sx, cellH * sy)
            .padding(5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.5.dp, color.copy(alpha = 0.75f), RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun HomeAppCell(item: AppItem, appLabel: String?, vm: LauncherViewModel, drag: DragState, onDragEnd: () -> Unit) {
    val settings = LocalSettings.current
    val pos = remember { PosHolder() }
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(item)
    val locked by rememberUpdatedState(settings.lockLayout)
    val endDrag by rememberUpdatedState(onDragEnd)
    var pressed by remember { mutableStateOf(false) }
    IconWithLabel(
        label = labelFor(item.app, appLabel),
        showLabel = settings.showLabels,
        iconSize = settings.iconSizeDp.dp,
        modifier = Modifier
            .trackPosition(pos)
            .launcherGestures(
                key = item.id,
                onTap = { vm.launch(current.app, pos.rect()) },
                onLongPress = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val at = pos.pos + off
                    vm.menu = MenuRequest(at, MenuTarget.Home(current, inDock = false))
                    if (!locked) drag.start(DragSource.Home(current.id), current, at)
                },
                onDrag = { d ->
                    drag.moveBy(d)
                    if (drag.moved) vm.menu = null
                },
                onDragEnd = { endDrag() },
                onPress = { pressed = it },
            ),
    ) { AppIcon(item.app, settings.iconSizeDp.dp, Modifier.pressScale(pressed)) }
}

@Composable
private fun HomeFolderCell(item: FolderItem, vm: LauncherViewModel, drag: DragState, onDragEnd: () -> Unit) {
    val settings = LocalSettings.current
    val pos = remember { PosHolder() }
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(item)
    val locked by rememberUpdatedState(settings.lockLayout)
    val endDrag by rememberUpdatedState(onDragEnd)
    var pressed by remember { mutableStateOf(false) }
    IconWithLabel(
        label = item.name,
        showLabel = settings.showLabels,
        iconSize = settings.iconSizeDp.dp,
        modifier = Modifier
            .trackPosition(pos)
            .launcherGestures(
                key = item.id,
                onTap = { vm.openFolderId = current.id },
                onLongPress = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val at = pos.pos + off
                    vm.menu = MenuRequest(at, MenuTarget.Home(current, inDock = false))
                    if (!locked) drag.start(DragSource.Home(current.id), current, at)
                },
                onDrag = { d ->
                    drag.moveBy(d)
                    if (drag.moved) vm.menu = null
                },
                onDragEnd = { endDrag() },
                onPress = { pressed = it },
            ),
    ) { FolderIcon(item, settings.iconSizeDp.dp, Modifier.pressScale(pressed)) }
}

/** Long-press a widget for its menu; keep holding and move to drag it anywhere. */
private fun Modifier.widgetDrag(
    item: HomeItem,
    pos: PosHolder,
    vm: LauncherViewModel,
    drag: DragState,
    locked: () -> Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onDragEnd: () -> Unit,
): Modifier = launcherGestures(
    key = item.id,
    onTap = {},
    onLongPress = { off ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val at = pos.pos + off
        vm.menu = MenuRequest(at, MenuTarget.Home(item, inDock = false))
        if (!locked()) drag.start(DragSource.Home(item.id), item, at, grab = off)
    },
    onDrag = { d ->
        drag.moveBy(d)
        if (drag.moved) vm.menu = null
    },
    onDragEnd = onDragEnd,
)

@Composable
private fun ForgeWidgetCell(item: ForgeWidgetItem, vm: LauncherViewModel, host: LauncherHost, drag: DragState, onDragEnd: () -> Unit) {
    val pos = remember { PosHolder() }
    val current by rememberUpdatedState(item)
    val settings = LocalSettings.current
    val locked by rememberUpdatedState(settings.lockLayout)
    val endDrag by rememberUpdatedState(onDragEnd)
    val actions = remember(host, vm) {
        object : ForgeActions {
            override fun requestAudio() = host.requestPermission(Manifest.permission.RECORD_AUDIO)
            override fun requestLocation() = host.requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            override fun openNotificationAccess() = host.openNotificationAccess()
            override fun customize() {
                vm.editForgeId = current.id
            }
        }
    }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxSize()
            .trackPosition(pos)
            .widgetDrag(item, pos, vm, drag, { locked }, haptic) { endDrag() },
    ) {
        ForgeWidget(item, LocalPalette.current, actions)
    }
}

@Composable
private fun WidgetCell(
    item: WidgetItem,
    vm: LauncherViewModel,
    host: LauncherHost,
    drag: DragState,
    onDragEnd: () -> Unit,
    widthDp: Int,
    heightDp: Int,
) {
    val pos = remember { PosHolder() }
    val view = remember(item.widgetId) { host.widgetView(item) }
    val current by rememberUpdatedState(item)
    val settings = LocalSettings.current
    val locked by rememberUpdatedState(settings.lockLayout)
    val endDrag by rememberUpdatedState(onDragEnd)
    val haptic = LocalHapticFeedback.current
    if (view == null) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .trackPosition(pos)
                .widgetDrag(item, pos, vm, drag, { locked }, haptic) { endDrag() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Widget unavailable", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        return
    }
    val padPx = with(LocalDensity.current) { 4.dp.toPx() }
    AndroidView(
        factory = { ctx -> WidgetFrame(ctx).apply { setWidget(view) } },
        update = { frame ->
            frame.onLongPress = { x, y ->
                val grab = Offset(x + padPx, y + padPx)
                val at = pos.pos + Offset(x, y)
                vm.menu = MenuRequest(at, MenuTarget.Home(current, inDock = false))
                if (!locked) drag.start(DragSource.Home(current.id), current, at, grab = grab)
            }
            frame.onDrag = { dx, dy ->
                drag.moveBy(Offset(dx, dy))
                if (drag.moved) vm.menu = null
            }
            frame.onDragEnd = { endDrag() }
            frame.updateSize(widthDp, heightDp)
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .trackPosition(pos),
    )
}

@Composable
private fun DragTargetsBar(drag: DragState) {
    if (!drag.active) {
        drag.removeRect = Rect.Zero
        drag.infoRect = Rect.Zero
        drag.uninstallRect = Rect.Zero
        return
    }
    val isApp = drag.item is AppItem
    val canRemove = drag.source !is DragSource.Drawer
    val hover = drag.target()
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (canRemove) {
            DropTargetChip("Remove", Icons.Default.Close, hover == DropTarget.Remove) { drag.removeRect = it }
        } else {
            drag.removeRect = Rect.Zero
        }
        if (isApp) {
            DropTargetChip("App info", Icons.Default.Info, hover == DropTarget.Info) { drag.infoRect = it }
            DropTargetChip("Uninstall", Icons.Default.Delete, hover == DropTarget.Uninstall) { drag.uninstallRect = it }
        } else {
            drag.infoRect = Rect.Zero
            drag.uninstallRect = Rect.Zero
        }
    }
}

@Composable
private fun DropTargetChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hovered: Boolean,
    onRect: (Rect) -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .onGloballyPositioned { onRect(it.boundsInRoot().inflate(16f)) }
            .clip(RoundedCornerShape(50))
            .background(if (hovered) accent else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun DragGhost(drag: DragState, iconSizeDp: Int, vm: LauncherViewModel) {
    if (!drag.active) return
    val item: HomeItem = drag.item ?: return
    val motion = LocalMotion.current
    val lift = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(item.id) { lift.animateTo(1.1f, motion.spring(stiffness = 900f)) }
    val accent = LocalAccent.current
    if (item is WidgetItem || item is ForgeWidgetItem) {
        val density = LocalDensity.current
        val w = vm.cellWidthPx * item.spanX
        val h = vm.cellHeightPx * item.spanY
        Box(
            Modifier
                .offset { IntOffset((drag.position.x - drag.grab.x).roundToInt(), (drag.position.y - drag.grab.y).roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer {
                    scaleX = 0.94f + (lift.value - 1f) * 0.3f
                    scaleY = scaleX
                }
                .padding(6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(accent.copy(alpha = 0.2f))
                .border(2.dp, accent, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("${item.spanX} × ${item.spanY}", color = Color.White, fontSize = 14.sp)
        }
        return
    }
    val size = (iconSizeDp + 6).dp
    val px = with(LocalDensity.current) { size.toPx() }
    Box(
        Modifier
            .offset { IntOffset((drag.position.x - px / 2).roundToInt(), (drag.position.y - px * 0.75f).roundToInt()) }
            .size(size)
            .graphicsLayer {
                scaleX = lift.value
                scaleY = lift.value
                alpha = 0.95f
            },
    ) {
        when (item) {
            is AppItem -> AppIcon(item.app, size)
            is FolderItem -> FolderIcon(item, size)
            is WidgetItem, is ForgeWidgetItem -> Unit
        }
    }
}

@Composable
private fun MoveWidgetHint(onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(LocalAccent.current)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tap an empty spot to move the widget", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Default.Close, contentDescription = "Cancel", tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .pointerInput(Unit) { detectTapGestures { onCancel() } },
            )
        }
    }
}
