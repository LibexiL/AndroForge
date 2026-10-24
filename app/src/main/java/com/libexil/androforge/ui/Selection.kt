package com.libexil.androforge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.AppCategory
import androidx.compose.foundation.horizontalScroll
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.data.LayoutOps

/** Bar shown while picking several apps in the drawer. */
@Composable
fun SelectionBar(vm: LauncherViewModel, state: LauncherState) {
    val motion = LocalMotion.current
    val enter = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, motion.spring(stiffness = 700f)) }
    val count = vm.selected.size
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(16.dp)
                .graphicsLayer {
                    translationY = (1f - enter.value) * 160f
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (count == 0) "Tap apps to select" else "$count selected",
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
            TextButton(enabled = count > 0, onClick = { vm.newFolderDialog = true }) { Text("Folder") }
            TextButton(enabled = count > 0, onClick = { vm.addSelectedToHome() }) { Text("Home") }
            TextButton(enabled = count > 0, onClick = { vm.hideSelected() }) { Text("Hide") }
            TextButton(onClick = { vm.stopSelecting() }) { Text("Cancel") }
        }
    }
}

@Composable
fun NewFolderDialog(vm: LauncherViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val all = vm.apps.value
    // Name it after what most of the selected apps are, and offer the rest of that kind.
    val guess = remember(vm.selected) {
        vm.selected.mapNotNull { k -> all.firstOrNull { it.key == k }?.let { AppCategory.of(context, it) } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
    var name by remember { mutableStateOf(guess?.label ?: "") }
    val more = remember(guess, vm.selected) {
        if (guess == null) emptyList() else all.filter { it.key !in vm.selected && AppCategory.of(context, it) == guess }
    }
    AlertDialog(
        onDismissRequest = { vm.newFolderDialog = false },
        title = { Text("New folder") },
        text = {
            Column {
                Text("${vm.selected.size} apps will go in it", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (guess != null && more.isNotEmpty()) {
                    Spacer(Modifier.padding(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("More ${guess.label.lowercase()} apps", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                more.take(5).forEach { AppIcon(it.key, 22.dp) }
                            }
                        }
                        TextButton(onClick = { vm.selected = (vm.selected + more.map { it.key }).distinct() }) { Text("Add ${more.size}") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.createFolder(name, vm.selected)
                vm.newFolderDialog = false
                vm.stopSelecting()
                vm.emit(com.libexil.androforge.UiEvent.CloseDrawer)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { vm.newFolderDialog = false }) { Text("Cancel") } },
    )
}

/**
 * Pick many apps at once - for a brand-new folder ([folderId] == "") or to add/remove apps
 * in an existing one. Suggests apps by kind (Games, Shopping, Social...).
 */
@Composable
fun FolderAppPicker(vm: LauncherViewModel, state: LauncherState, apps: List<AppInfo>, folderId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val existing = if (folderId.isEmpty()) null else LayoutOps.findFolder(state, folderId)
    var picked by remember(folderId) { mutableStateOf(existing?.apps ?: emptyList<AppKey>()) }
    var name by remember(folderId) { mutableStateOf(existing?.name ?: "") }
    var nameTouched by remember(folderId) { mutableStateOf(existing != null) }
    var query by remember { mutableStateOf("") }
    var active by remember(folderId) { mutableStateOf<AppCategory?>(null) }
    val labels = state.labels
    val visible = remember(apps, state.hidden) { apps.filter { it.key.id !in state.hidden } }
    fun labelOf(a: AppInfo) = labels[a.key.id] ?: a.label

    // Work out every app's kind off the main thread.
    val byCategory by androidx.compose.runtime.produceState(emptyMap<AppCategory, List<AppInfo>>(), visible) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            visible.mapNotNull { a -> AppCategory.of(context, a)?.let { it to a } }
                .groupBy({ it.first }, { it.second })
        }
    }
    val categoryOf = remember(byCategory) { byCategory.flatMap { (c, l) -> l.map { it.key to c } }.toMap() }
    val chips = remember(byCategory) { byCategory.entries.filter { it.value.isNotEmpty() }.sortedByDescending { it.value.size } }

    // What the folder looks like it's for: the name you typed, or most of the apps already in it.
    val inferred = remember(name, picked, categoryOf) {
        AppCategory.fromName(name) ?: picked.mapNotNull { categoryOf[it] }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.takeIf { it.value >= 2 || picked.size == 1 }?.key
    }
    val focus = active ?: inferred
    val suggestions = remember(focus, byCategory, picked) {
        focus?.let { c -> byCategory[c].orEmpty().filter { it.key !in picked } }.orEmpty()
    }

    val list = remember(visible, query, labels, focus, categoryOf) {
        visible
            .filter { query.isBlank() || labelOf(it).contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy<AppInfo>({ if (focus != null && categoryOf[it.key] == focus) 0 else 1 }, { labelOf(it).lowercase() }))
    }
    fun close() {
        vm.folderPickerFor = null
    }
    fun pickCategory(c: AppCategory) {
        val members = byCategory[c].orEmpty().map { it.key }
        if (active == c) {
            picked = picked - members.toSet()
            active = null
        } else {
            active = c
            picked = picked + members.filter { it !in picked }
            if (!nameTouched || name.isBlank()) name = c.label
        }
    }
    Dialog(onDismissRequest = ::close) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    if (existing == null) "New folder" else "Apps in \u201c${existing.name}\u201d",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (existing == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(30)
                            nameTouched = true
                        },
                        label = { Text("Folder name") },
                        placeholder = { Text("Try \u201cGames\u201d or \u201cShopping\u201d") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                if (chips.isNotEmpty()) {
                    Text(
                        "SUGGESTED BY KIND",
                        fontSize = 10.5.sp,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp),
                    )
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chips.forEach { (c, members) ->
                            androidx.compose.material3.FilterChip(
                                selected = active == c,
                                onClick = { pickCategory(c) },
                                label = { Text("${c.label}  ${members.size}") },
                            )
                        }
                    }
                }
                if (focus != null && suggestions.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (existing != null || picked.isNotEmpty()) "More ${focus.label.lowercase()} apps" else "${focus.label} apps",
                                fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                suggestions.take(6).forEach { AppIcon(it.key, 24.dp) }
                                if (suggestions.size > 6) Text("+${suggestions.size - 6}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        TextButton(onClick = {
                            picked = picked + suggestions.map { it.key }
                            if (existing == null && name.isBlank()) name = focus.label
                        }) { Text("Add all ${suggestions.size}") }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.key.id }) { app ->
                        val on = app.key in picked
                        val cat = categoryOf[app.key]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked - app.key else picked + app.key }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(labelOf(app), maxLines = 1)
                                if (cat != null) {
                                    Text(
                                        cat.label,
                                        fontSize = 11.sp,
                                        color = if (cat == focus) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f),
                                    )
                                }
                            }
                            Checkbox(checked = on, onCheckedChange = { picked = if (on) picked - app.key else picked + app.key })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${picked.size} selected", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = ::close) { Text("Cancel") }
                    Button(enabled = picked.isNotEmpty() || existing != null, onClick = {
                        if (existing == null) vm.createFolder(name, picked, vm.folderPickerPage) else vm.setFolderApps(existing.id, picked)
                        close()
                    }) { Text(if (existing == null) "Create" else "Save") }
                }
            }
        }
    }
}
