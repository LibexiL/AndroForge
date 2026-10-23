package com.libexil.androforge.ui.forge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libexil.androforge.data.ColorRole
import com.libexil.androforge.ui.Pal
import kotlin.math.roundToInt

/** Small form controls shared by the widget editor and the settings screen. */

@Composable
fun EdLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun EdSwitch(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun EdSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { "${it.roundToInt()}" },
    live: Boolean = true,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(format(local), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                if (live) onChange(it)
            },
            onValueChangeFinished = { if (!live) onChange(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> EdChips(title: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        if (title != null) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

/** Palette color roles as swatches (the gradient one shows the whole gradient). */
@Composable
fun EdRoleSwatches(title: String, pal: Pal, selected: ColorRole, roles: List<ColorRole> = ColorRole.entries, onSelect: (ColorRole) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(selected.label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            roles.forEach { r ->
                val brush = if (r == ColorRole.GRADIENT) Brush.linearGradient(pal.gradient) else Brush.linearGradient(listOf(pal.role(r), pal.role(r)))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(brush)
                        .then(if (r == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .clickable { onSelect(r) },
                )
            }
        }
    }
}

/** A row of a palette's colors, for theme pickers. */
@Composable
fun PaletteStrip(pal: Pal, modifier: Modifier = Modifier) {
    Row(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))) {
        val colors = listOf(pal.background, pal.surface, pal.accent, pal.accent2) + pal.ansi.subList(1, 7)
        colors.forEach { c -> Box(Modifier.weight(1f).height(14.dp).background(c)) }
    }
}
