package com.libexil.androforge.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.libexil.androforge.LauncherViewModel
import com.libexil.androforge.data.SmartSearch
import com.libexil.androforge.live.PermissionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class ContactHit(val id: Long, val lookup: String, val name: String, val number: String?)

/** Math answers, unit conversions, contacts and settings shortcuts above the app results. */
@Composable
fun SmartResults(query: String, vm: LauncherViewModel) {
    val s = LocalSettings.current
    val accent = LocalAccent.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val q = query.trim()
    val math = remember(q, s.searchCalculator) { if (s.searchCalculator) SmartSearch.calculate(q) else null }
    val unit = remember(q, s.searchCalculator) { if (s.searchCalculator) SmartSearch.convert(q) else null }
    val settingsHits = remember(q, s.searchSettings) { if (s.searchSettings) SmartSearch.matchSettings(q) else emptyList() }
    val permTick by PermissionBus.version.collectAsState()
    val contactsOk = remember(permTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val wantsContacts = s.searchContacts && q.length >= 2 && math == null && unit == null
    val contacts by produceState(emptyList<ContactHit>(), q, contactsOk, wantsContacts) {
        if (!wantsContacts || !contactsOk) {
            value = emptyList()
            return@produceState
        }
        delay(120) // wait for typing to settle
        value = withContext(Dispatchers.IO) { findContacts(context, q) }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        (unit ?: math?.let { "$q = $it" })?.let { answer ->
            val result = unit?.substringAfter("= ") ?: math ?: ""
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .clickable {
                        clipboard.setText(AnnotatedString(result.replace(",", "")))
                        vm.toast("Copied $result")
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (unit != null) "Conversion" else "Calculator", color = accent, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(result, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(answer.substringBefore(" = ") + " =", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Tap to copy", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        }
        contacts.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookup)
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    c.number?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) }
                }
                if (c.number != null) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF30A46C))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        if (wantsContacts && !contactsOk && q.any { it.isLetter() }) {
            Row(
                Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { vm.host?.requestPermission(Manifest.permission.READ_CONTACTS) }
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Person, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search your contacts too", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        if (settingsHits.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settingsHits.forEach { link ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable {
                                try {
                                    context.startActivity(Intent(link.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: Exception) {
                                    runCatching {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Settings, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(link.label, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun findContacts(context: android.content.Context, q: String): List<ContactHit> = try {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
    val out = ArrayList<ContactHit>()
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
        null, null, ContactsContract.Contacts.TIMES_CONTACTED + " DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 4) {
            val id = c.getLong(0)
            val name = c.getString(2)
            if (name != null) {
                val number = if (c.getInt(3) > 0) firstNumber(context, id) else null
                out += ContactHit(id, c.getString(1) ?: "", name, number)
            }
        }
    }
    out
} catch (e: Exception) {
    emptyList()
}

private fun firstNumber(context: android.content.Context, id: Long): String? = try {
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
        arrayOf(id.toString()),
        ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC",
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
} catch (e: Exception) {
    null
}

@Suppress("unused")
private fun contactUri(id: Long) = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
