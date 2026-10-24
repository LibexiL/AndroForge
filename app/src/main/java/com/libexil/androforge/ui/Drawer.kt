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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.VisibilityThreshold
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
    var letter by remember { mutableStateOf<Char?>(null) }
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
    val dragging = drag.source != null
    LaunchedEffect(closed, dragging) {
        if (closed) vm.stopSelecting()
        if (closed && !dragging) {
            letter = null
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
    LaunchedEffect(query) { if (query.isNotBlank()) letter = null }
    val filtered = remember(visible, query, letter) {
        val q = query.trim()
        val l = letter
        if (q.isEmpty() && l != null) visible.filter { letterOf(labelOf(it)) == l }
        else if (q.isEmpty()) visible
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

    // Pull-down-to-close only when the list was already at the top when your finger went down,
    // so scrolling back up through your apps never closes the drawer by accident.
    val atTopAtDown = remember { booleanArrayOf(true) }
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
                if (available.y > 0f && source == NestedScrollSource.UserInput && atTopAtDown[0]) {
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

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (drawer.progress > 0.001f && drawer.progress < 0.999f) drawer.settle(available.y)
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
                    .fillMaxWidth()
                    .height(22.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { drawer.close() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.4f)),
                )
            }
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
        val showSugg = query.isBlank() && letter == null && suggestions.isNotEmpty()
        val fastScroll = settings.drawerFastScroll && query.isBlank() && visible.size > cols * 4
        val available = remember(visible) { visible.map { letterOf(labelOf(it)) }.toSet() }
        val scope = rememberCoroutineScope()
        val motion = LocalMotion.current
        val stretch = if (settings.stretchScroll) rememberStretch(top = false, bottom = true) else null
        fun itemAnim(m: Modifier, scope: androidx.compose.foundation.lazy.grid.LazyGridItemScope): Modifier = with(scope) {
            m.animateItem(
                fadeInSpec = motion.tween(160),
                placementSpec = motion.spring(stiffness = 700f, visibilityThreshold = IntOffset.VisibilityThreshold),
                fadeOutSpec = motion.tween(100),
            )
        }
        LaunchedEffect(letter) { if (letter != null) gridState.scrollToItem(0) }
        // While scrubbing the A-Z bar the apps stretch and travel with your finger.
        var scrub by remember { mutableStateOf<Float?>(null) }
        val kick = remember { androidx.compose.animation.core.Animatable(0f) }
        val follow = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(scrub) {
            val f = scrub
            val target = if (f == null) 0f else {
                val n = ALPHABET.size
                (f * n - (f * n).toInt().coerceAtMost(n - 1)) - 0.5f
            }
            follow.animateTo(target, motion.spring(stiffness = 900f))
        }
        val travelPx = with(LocalDensity.current) { 70.dp.toPx() }
        Box(Modifier.fillMaxSize()) {
        NoPlatformOverscroll(stretch != null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stretch) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val top = !gridState.canScrollBackward
                        atTopAtDown[0] = top
                        // Not starting at the top: overscrolling the top just stretches instead.
                        stretch?.top = !top
                    }
                }
                .nestedScroll(connection)
                .stretchy(stretch)
                .graphicsLayer {
                    val k = kick.value
                    val w = follow.value
                    translationY = k * travelPx + w * travelPx * 0.35f
                    scaleY = 1f + kotlin.math.abs(k) * 0.12f + kotlin.math.abs(w) * 0.05f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (k + w >= 0f) 0f else 1f)
                }
                .padding(start = 8.dp, end = if (fastScroll) 26.dp else 8.dp),
        ) {
            letter?.let { l ->
                item(key = "letter-head", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(LocalAccent.current),
                            contentAlignment = Alignment.Center,
                        ) { Text(l.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (l == '#') "Numbers & symbols" else "Apps starting with $l", color = Color.White, fontSize = 15.sp)
                            Text("${filtered.size} apps", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        Text(
                            "Show all",
                            color = LocalAccent.current,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { letter = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (showSugg) {
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
                Box(itemAnim(Modifier, this)) { DrawerAppCell(app, labelOf(app), vm, drag, onDragEnd) }
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
            item(key = "bottom-space", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(if (vm.selecting) 96.dp else 24.dp)) }
        }
        }
        if (fastScroll) {
            AlphaScroller(
                available = available,
                current = letter,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                onClear = { letter = null },
                onScrub = { f -> scrub = f },
            ) { l, _ ->
                if (l in available) {
                    val old = letter
                    letter = l
                    if (old != null && old != l) {
                        val dir = if (ALPHABET.indexOf(l) > ALPHABET.indexOf(old)) 1f else -1f
                        scope.launch {
                            kick.snapTo(dir)
                            kick.animateTo(0f, motion.spring(stiffness = 380f))
                        }
                    }
                }
            }
        }
        }
    }
}

private val ALPHABET: List<Char> = listOf('#') + ('A'..'Z').toList()

private fun letterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** The A-Z strip on the side of the drawer: letters bulge out under your finger like a magnifier. */
@Composable
private fun AlphaScroller(
    available: Set<Char>,
    current: Char?,
    modifier: Modifier,
    onClear: () -> Unit,
    onScrub: (Float?) -> Unit,
    onLetter: (Char, Boolean) -> Unit,
) {
    val currentLetter by rememberUpdatedState(current)
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    val motion = LocalMotion.current
    var active by remember { mutableStateOf<Char?>(null) }
    val finger = remember { androidx.compose.animation.core.Animatable(0f) }
    val zoom = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val letters = ALPHABET
    val heightPx = remember { floatArrayOf(1f) }
    val bulgePx = with(density) { 36.dp.toPx() }
    Box(modifier.width(24.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(24.dp)
                .onSizeChanged { heightPx[0] = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(available) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        scope.launch { zoom.animateTo(1f, motion.spring(stiffness = 700f)) }
                        fun pick(y: Float) {
                            val yy = y.coerceIn(0f, size.height.toFloat())
                            scope.launch { finger.snapTo(yy) }
                            val frac = yy / size.height
                            onScrub(frac)
                            val i = (frac * letters.size).toInt().coerceIn(0, letters.size - 1)
                            val l = letters[i]
                            if (l != active) {
                                active = l
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLetter(l, false)
                            }
                        }
                        val before = currentLetter
                        pick(down.position.y)
                        val startedOn = active
                        var changed = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            val prev = active
                            pick(ch.position.y)
                            if (active != prev) changed = true
                        }
                        // Tapping the letter that's already shown goes back to all apps.
                        if (!changed && startedOn != null && startedOn == before) onClear()
                        active = null
                        onScrub(null)
                        scope.launch { zoom.animateTo(0f, motion.spring(stiffness = 500f)) }
                    }
                },
        ) {
            letters.forEachIndexed { idx, l ->
                val on = l in available
                val highlight = l == (active ?: current)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Fisheye: letters near the finger grow and slide out from the edge.
                            val h = heightPx[0]
                            val cy = (idx + 0.5f) * h / letters.size
                            val d = (cy - finger.value) / (h * 0.11f)
                            val f = kotlin.math.exp(-d * d) * zoom.value
                            scaleX = 1f + 1.25f * f
                            scaleY = 1f + 1.25f * f
                            translationX = -bulgePx * f
                            alpha = 0.55f + 0.45f * maxOf(f, if (on) 0.6f else 0f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        l.toString(),
                        fontSize = 10.5.sp,
                        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            highlight -> accent
                            on -> Color.White
                            else -> Color.White.copy(alpha = 0.35f)
                        },
                    )
                }
            }
        }
        // Big letter bubble beside your finger.
        androidx.compose.animation.AnimatedVisibility(
            visible = active != null,
            enter = androidx.compose.animation.scaleIn(motion.spring(stiffness = 900f)) + androidx.compose.animation.fadeIn(motion.tween(90)),
            exit = androidx.compose.animation.scaleOut(motion.tween(120)) + androidx.compose.animation.fadeOut(motion.tween(120)),
            modifier = Modifier.offset {
                IntOffset(with(density) { (-104).dp.roundToPx() }, (finger.value - with(density) { 30.dp.toPx() }).roundToInt())
            },
        ) {
            var shown by remember { mutableStateOf(' ') }
            active?.let { shown = it }
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 12))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) { Text(shown.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
