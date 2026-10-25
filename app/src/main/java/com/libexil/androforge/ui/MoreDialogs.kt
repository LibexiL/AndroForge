package com.libexil.androforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.libexil.androforge.CrashReporter
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.data.AppInfo
import com.libexil.androforge.data.AppKey
import com.libexil.androforge.data.GistBackup
import com.libexil.androforge.data.LauncherState
import com.libexil.androforge.update.Updater

/** Dialogs added with the theming / reliability features. */
@Composable
fun ExtraDialogs(vm: LauncherViewModel, state: LauncherState, apps: List<AppInfo>) {
    vm.whatsNew?.let { WhatsNewDialog(it) { vm.whatsNew = null } }
    if (vm.whatsNew == null) vm.crashLog?.let { CrashDialog(vm, it) }
    if (vm.gistDialogOpen) GistDialog(vm, state)
    if (vm.privateOpen) PrivateAppsSheet(vm, state, apps)
}

@Composable
private fun WhatsNewDialog(text: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(20.dp)) {
                Text("What's new", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                Text("AndroForge ${Updater.currentVersionName}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    text.trim().lines().forEach { line ->
                        when {
                            line.isBlank() -> Spacer(Modifier.height(10.dp))
                            !line.startsWith("•") -> Text(line, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                            else -> Text(line, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Nice") }
            }
        }
    }
}

@Composable
private fun CrashDialog(vm: LauncherViewModel, log: String) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {
            CrashReporter.clear(context)
            vm.crashLog = null
        },
        title = { Text("AndroForge crashed last time") },
        text = {
            Column {
                Text(
                    "Sorry about that. Reporting it opens a pre-filled GitHub issue with the error log below so it can be fixed. Nothing is sent unless you submit it.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    log.lines().take(14).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                CrashReporter.report(context, log)
                vm.crashLog = null
            }) { Text("Report on GitHub") }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashReporter.clear(context)
                vm.crashLog = null
            }) { Text("Dismiss") }
        },
    )
}

@Composable
private fun GistDialog(vm: LauncherViewModel, state: LauncherState) {
    val context = LocalContext.current
    var token by remember { mutableStateOf(GistBackup.token(context) ?: "") }
    var gist by remember { mutableStateOf(GistBackup.gistId(context) ?: "") }
    fun persist() = GistBackup.save(context, token.ifBlank { null }, gist.ifBlank { null })
    Dialog(onDismissRequest = {
        persist()
        vm.gistDialogOpen = false
    }) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("GitHub backup", fontSize = 22.sp)
                Text(
                    "Saves your layout and settings to a private Gist on your GitHub account, so you can restore after a reset or on a new phone.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "1. On github.com: Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token.\n" +
                        "2. Tick only “gist”, generate, and paste it here.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Token (stays on this phone)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gist,
                    onValueChange = { gist = it.trim() },
                    label = { Text("Gist ID (filled in after the first backup)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Restoring on another phone? Paste the same token and gist ID, then tap Restore.",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    vm.updateSettings { it.copy(autoGistBackup = !it.autoGistBackup) }
                }) {
                    Column(Modifier.weight(1f)) {
                        Text("Back up automatically", fontSize = 14.sp)
                        Text("Once a day, when you leave the home screen", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                    Switch(checked = state.settings.autoGistBackup, onCheckedChange = { v -> vm.updateSettings { it.copy(autoGistBackup = v) } })
                }
                if (state.lastGistBackup > 0) {
                    Text(
                        "Last backup: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(state.lastGistBackup)),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !vm.gistBusy && token.isNotBlank(), onClick = {
                        persist()
                        vm.gistBackup()
                    }) { Text(if (vm.gistBusy) "Working…" else "Back up now") }
                    OutlinedButton(enabled = !vm.gistBusy && token.isNotBlank() && gist.isNotBlank(), onClick = {
                        persist()
                        vm.gistRestore()
                    }) { Text("Restore") }
                }
                TextButton(onClick = {
                    persist()
                    vm.gistDialogOpen = false
                }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                // Show the id once the first backup creates it.
                val saved = GistBackup.gistId(context)
                if (saved != null && saved != gist && !vm.gistBusy) gist = saved
            }
        }
    }
}

/** Pick an app (for gestures set to "Open an app"). */
@Composable
fun AppPickerDialog(title: String, apps: List<AppInfo>, labels: Map<String, String>, onPick: (AppKey) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(apps, q) { apps.filter { q.isBlank() || (labels[it.key.id] ?: it.label).contains(q, true) } }
    Dialog(onDismissRequest = onClose) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(title, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.key.id }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.key) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.key, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(labels[app.key.id] ?: app.label)
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Cancel") }
            }
        }
    }
}

/** Per-app island colors. */
@Composable
fun AppColorsDialog(apps: List<AppInfo>, colors: Map<String, Long>, pal: Pal, onSet: (String, Long?) -> Unit, onClose: () -> Unit) {
    val byPkg = remember(apps, colors) { apps.distinctBy { it.key.pkg }.sortedWith(compareBy({ it.key.pkg !in colors }, { it.label.lowercase() })) }
    val choices = remember(pal) {
        (listOf(pal.accent, pal.accent2) + pal.ansi.drop(1).take(6)).map { c ->
            (c.toArgb().toLong() and 0xFFFFFFFFL)
        } + folderColors
    }
    Dialog(onDismissRequest = onClose) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("Island colors per app", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(
                    "The island's glow and accents use this color for that app's pop-ups and live activities.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(byPkg, key = { it.key.pkg }) { app ->
                        val current = colors[app.key.pkg]
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.key, 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(app.label, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ColorDot(Color.White.copy(alpha = 0.12f), selected = current == null, label = "✕") { onSet(app.key.pkg, null) }
                                choices.distinct().forEach { c -> ColorDot(Color(c), selected = current == c) { onSet(app.key.pkg, c) } }
                            }
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Done") }
            }
        }
    }
}
