package com.libexil.androforge.ui.forge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.ui.Pal
import kotlinx.coroutines.delay

/** The widgets a stack can hold (anything but another stack). */
val stackableKinds: List<ForgeKind> get() = ForgeKind.entries.filter { it != ForgeKind.STACK }

/** Several AndroForge widgets in one spot; swipe up or down to flip, like iOS's Smart Stack. */
@Composable
fun StackWidget(item: ForgeWidgetItem, pal: Pal, actions: ForgeActions) {
    val kinds = item.options.stackKinds.filter { it != ForgeKind.STACK }.distinct().ifEmpty { listOf(ForgeKind.CLOCK) }
    val pager = rememberPagerState { kinds.size }
    val media by MediaHub.state.collectAsState()
    val playing = media?.playing == true
    // Jump to the music widget when something starts playing.
    LaunchedEffect(playing, item.options.stackAutoMusic) {
        if (playing && item.options.stackAutoMusic) {
            val i = kinds.indexOf(ForgeKind.MUSIC)
            if (i >= 0 && pager.currentPage != i) pager.animateScrollToPage(i)
        }
    }
    LaunchedEffect(item.options.stackAutoRotate, kinds.size, pager.currentPage, playing) {
        if (!item.options.stackAutoRotate || kinds.size < 2) return@LaunchedEffect
        if (playing && item.options.stackAutoMusic && kinds.getOrNull(pager.currentPage) == ForgeKind.MUSIC) return@LaunchedEffect
        delay(12_000)
        pager.animateScrollToPage((pager.currentPage + 1) % kinds.size)
    }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { kinds[it].name }) { page ->
            val kind = kinds[page]
            ForgeWidget(item.copy(id = item.id + ":" + kind.name, kind = kind), pal, actions)
        }
        if (kinds.size > 1) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                kinds.indices.forEach { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier
                            .size(width = 4.dp, height = if (on) 12.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (on) pal.accent else pal.muted.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}
