package com.libexil.androforge.ui.forge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.data.ArtStyle
import com.libexil.androforge.data.ClockStyle
import com.libexil.androforge.data.ForgeKind
import com.libexil.androforge.data.ForgeOptions
import com.libexil.androforge.data.ForgeWidgetItem
import com.libexil.androforge.data.GaugeStyle
import com.libexil.androforge.data.PanelBackground
import com.libexil.androforge.data.PanelFont
import com.libexil.androforge.data.PanelLook
import com.libexil.androforge.data.TempUnits
import com.libexil.androforge.data.VizStyle
import com.libexil.androforge.data.WeatherPlace
import com.libexil.androforge.live.WeatherRepo
import com.libexil.androforge.ui.Pal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bottom panel for customizing an AndroForge widget. Changes apply to the widget on the
 * home screen immediately, so you can watch it update above the panel.
 */
@Composable
fun ForgeEditor(
    item: ForgeWidgetItem,
    pal: Pal,
    onOptions: ((ForgeOptions) -> ForgeOptions) -> Unit,
    onLook: ((PanelLook) -> PanelLook) -> Unit,
    onRequestLocation: () -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.55f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.kind.label, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done") }
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Style") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Look") })
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (tab == 0) StyleTab(item, pal, onOptions, onRequestLocation) else LookTab(item.look, pal, onLook)
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun StyleTab(
    item: ForgeWidgetItem,
    pal: Pal,
    set: ((ForgeOptions) -> ForgeOptions) -> Unit,
    onRequestLocation: () -> Unit,
) {
    val o = item.options
    when (item.kind) {
        ForgeKind.CLOCK -> {
            EdChips("Clock", ClockStyle.entries, o.clockStyle, { it.label }) { v -> set { it.copy(clockStyle = v) } }
            EdSwitch("Seconds", o.showSeconds) { v -> set { it.copy(showSeconds = v) } }
            EdSwitch("Date", o.showDate) { v -> set { it.copy(showDate = v) } }
            EdChips("Hours", listOf(null, true, false), o.twelveHour, { if (it == null) "System" else if (it) "12-hour" else "24-hour" }) { v ->
                set { it.copy(twelveHour = v) }
            }
        }
        ForgeKind.MUSIC -> {
            EdChips("Album art", ArtStyle.entries, o.artStyle, { it.label }) { v -> set { it.copy(artStyle = v) } }
            EdSwitch("Playback buttons", o.showControls) { v -> set { it.copy(showControls = v) } }
            EdSwitch("Progress bar", o.showProgress, "Tap it to seek") { v -> set { it.copy(showProgress = v) } }
            EdSwitch("Visualizer behind", o.musicVisualizer) { v -> set { it.copy(musicVisualizer = v) } }
        }
        ForgeKind.VISUALIZER -> {
            EdChips("Style", VizStyle.entries, o.vizStyle, { it.label }) { v -> set { it.copy(vizStyle = v) } }
            EdRoleSwatches("Color", pal, o.vizColor) { v -> set { it.copy(vizColor = v) } }
            EdSlider("Bars", o.bars.toFloat(), 8f..96f, steps = 0, format = { "${it.roundToInt()}" }, live = false) { v ->
                set { it.copy(bars = v.roundToInt()) }
            }
            EdSlider("Gap", o.gap, 0f..0.7f, format = { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(gap = v) } }
            EdSwitch("Rounded", o.rounded) { v -> set { it.copy(rounded = v) } }
            EdSwitch("Falling peaks", o.peaks) { v -> set { it.copy(peaks = v) } }
        }
        ForgeKind.WEATHER -> {
            EdLabel("Location")
            PlacePicker(o.place, onRequestLocation) { p -> set { it.copy(place = p) } }
            EdChips("Units", listOf<TempUnits?>(null) + TempUnits.entries, o.units, { it?.label ?: "Automatic" }) { v ->
                set { it.copy(units = v) }
            }
            EdSlider("Forecast days", o.forecastDays.toFloat(), 0f..5f, steps = 4) { v -> set { it.copy(forecastDays = v.roundToInt()) } }
        }
        ForgeKind.SYSTEM -> {
            EdChips("Gauges", GaugeStyle.entries, o.gaugeStyle, { it.label }) { v -> set { it.copy(gaugeStyle = v) } }
            Text(
                "Android doesn't let apps read CPU usage, so the gauges show battery, memory and storage.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PlacePicker(place: WeatherPlace?, onRequestLocation: () -> Unit, onPick: (WeatherPlace?) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Text(place?.name ?: "Using this phone's location", fontSize = 15.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a city") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(enabled = query.isNotBlank() && !busy, onClick = {
            busy = true
            error = null
            scope.launch {
                results = try {
                    WeatherRepo.search(query)
                } catch (e: Exception) {
                    error = "Search failed: ${e.message}"
                    emptyList()
                }
                if (results.isEmpty() && error == null) error = "No places found"
                busy = false
            }
        }) { Text(if (busy) "…" else "Find") }
    }
    error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    results.forEach { r ->
        Text(
            r.name,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onPick(r)
                    results = emptyList()
                    query = ""
                }
                .padding(vertical = 10.dp, horizontal = 6.dp),
        )
    }
    if (place != null) {
        TextButton(onClick = {
            onPick(null)
            onRequestLocation()
        }) { Text("Use my location instead") }
    } else {
        TextButton(onClick = onRequestLocation) { Text("Allow location access") }
    }
}

@Composable
private fun LookTab(look: PanelLook, pal: Pal, set: ((PanelLook) -> PanelLook) -> Unit) {
    EdChips("Background", PanelBackground.entries, look.background, { it.label }) { v -> set { it.copy(background = v) } }
    if (look.background != PanelBackground.NONE && look.background != PanelBackground.OUTLINE) {
        EdSlider("Opacity", look.opacity, 0f..1f, format = { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(opacity = v) } }
    }
    EdSlider("Corner radius", look.radius.toFloat(), 0f..40f) { v -> set { it.copy(radius = v.roundToInt()) } }
    EdRoleSwatches("Highlight color", pal, look.highlight) { v -> set { it.copy(highlight = v) } }
    EdSwitch("Border", look.border) { v -> set { it.copy(border = v) } }
    EdSwitch("Breathing glow", look.glow) { v -> set { it.copy(glow = v) } }
    EdSwitch("Glow with the bass", look.pulse, "Music widgets only") { v -> set { it.copy(pulse = v) } }
    EdSwitch("Title", look.showTitle) { v -> set { it.copy(showTitle = v) } }
    if (look.showTitle) {
        OutlinedTextField(
            value = look.title,
            onValueChange = { v -> set { it.copy(title = v.take(24)) } },
            label = { Text("Title text (blank = default)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    EdChips("Font", PanelFont.entries, look.font, { it.label }) { v -> set { it.copy(font = v) } }
    EdSlider("Text size", look.textScale, 0.7f..1.5f, format = { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(textScale = v) } }
    Box(Modifier.padding(top = 6.dp))
}
