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
    var name by remember { mutableStateOf("") }
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
 * in an existing one.
 */
@Composable
fun FolderAppPicker(vm: LauncherViewModel, state: LauncherState, apps: List<AppInfo>, folderId: String) {
    val existing = if (folderId.isEmpty()) null else LayoutOps.findFolder(state, folderId)
    var picked by remember(folderId) { mutableStateOf(existing?.apps ?: emptyList<AppKey>()) }
    var name by remember(folderId) { mutableStateOf(existing?.name ?: "") }
    var query by remember { mutableStateOf("") }
    val labels = state.labels
    val list = remember(apps, query, labels, state.hidden) {
        apps.filter { it.key.id !in state.hidden }
            .sortedBy { (labels[it.key.id] ?: it.label).lowercase() }
            .filter { query.isBlank() || (labels[it.key.id] ?: it.label).contains(query.trim(), ignoreCase = true) }
    }
    fun close() {
        vm.folderPickerFor = null
    }
    Dialog(onDismissRequest = ::close) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    if (existing == null) "New folder" else "Apps in “${existing.name}”",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (existing == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(list, key = { it.key.id }) { app ->
                        val on = app.key in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked - app.key else picked + app.key }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(labels[app.key.id] ?: app.label, modifier = Modifier.weight(1f))
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
