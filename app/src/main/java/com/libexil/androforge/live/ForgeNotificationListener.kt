package com.libexil.androforge.live

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.libexil.androforge.LauncherApp
import java.lang.ref.WeakReference

/**
 * Notification access powers the music widgets (media sessions), the island's alerts, calls
 * and live activities, and the custom status bar's notification icons.
 */
class ForgeNotificationListener : NotificationListenerService() {
    private val iconCache = HashMap<String, Pair<ImageBitmap?, ImageBitmap?>>()
    private val appNames = HashMap<String, String>()
    private val lastSeen = HashMap<String, String>() // key -> title|text, to spot real updates

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotifHub.listener = WeakReference(this)
        NotifHub.setDnd(currentInterruptionFilter.let { it != INTERRUPTION_FILTER_ALL && it != INTERRUPTION_FILTER_UNKNOWN })
        MediaHub.refresh()
        publish()
        // Don't pop up everything that was already there.
        NotifHub.entries.value.forEach { lastSeen[it.key] = it.title + "|" + it.text }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotifHub.listener = null
        NotifHub.setEntries(emptyList())
        MediaHub.refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val entries = publish()
        val entry = entries.firstOrNull { it.key == sbn.key } ?: return
        val sig = entry.title + "|" + entry.text
        val previous = lastSeen.put(sbn.key, sig)
        if (previous == sig) return // only progress/timer changed
        if (previous != null && sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        if (shouldPeek(sbn, entry)) NotifHub.postPeek(entry)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastSeen.remove(sbn.key)
        iconCache.keys.removeAll { it.startsWith(sbn.key + "@") }
        NotifHub.clearPeek(sbn.key)
        publish()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        NotifHub.setDnd(interruptionFilter != INTERRUPTION_FILTER_ALL && interruptionFilter != INTERRUPTION_FILTER_UNKNOWN)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        publish()
    }

    private fun shouldPeek(sbn: StatusBarNotification, e: NotifEntry): Boolean {
        val settings = LauncherApp.instance.store.state.value.settings
        if (!settings.islandNotifications) return false
        if (sbn.packageName in settings.islandExcludedApps) return false
        if (e.ongoing || e.call != CallState.NONE || e.urgent) return false
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS) return false
        return try {
            val ranking = Ranking()
            !currentRanking.getRanking(sbn.key, ranking) || ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT
        } catch (e: Exception) {
            true
        }
    }

    private fun publish(): List<NotifEntry> {
        val list = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        val entries = list
            .filter { it.packageName != packageName }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { !it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
            .mapNotNull { toEntry(it) }
            .sortedByDescending { it.postTime }
        NotifHub.setEntries(entries)
        return entries
    }

    private fun appName(pkg: String): String = appNames.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun toEntry(sbn: StatusBarNotification): NotifEntry? {
        val n = sbn.notification
        val x = n.extras
        val title = (x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: x.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim()
        val text = (x.getCharSequence(Notification.EXTRA_TEXT)
            ?: x.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: x.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.replace('\n', ' ')?.trim() ?: ""
        if (title.isNullOrBlank() && text.isBlank()) return null

        val actions = (n.actions ?: emptyArray()).map { a ->
            NotifAction(
                title = a.title?.toString() ?: "",
                intent = a.actionIntent,
                remoteInputs = a.remoteInputs,
                semantic = if (android.os.Build.VERSION.SDK_INT >= 28) a.semanticAction else 0,
            )
        }.filter { it.title.isNotBlank() }

        val callType = x.getInt("android.callType", 0)
        val call = when {
            callType == 1 || callType == 3 -> CallState.INCOMING
            callType == 2 -> CallState.ONGOING
            n.category == Notification.CATEGORY_CALL -> when {
                actions.any { it.isAnswer } -> CallState.INCOMING
                else -> CallState.ONGOING
            }
            else -> CallState.NONE
        }
        val ongoing = sbn.isOngoing
        val urgent = call == CallState.NONE && ongoing &&
            (n.category == Notification.CATEGORY_ALARM || (n.fullScreenIntent != null && n.category == Notification.CATEGORY_REMINDER))
        val chrono = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) || (call == CallState.ONGOING && n.`when` > 0)
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = when {
            x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) && ongoing -> -1f
            max > 0 -> x.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max
            else -> null
        }

        val cacheKey = sbn.key + "@" + sbn.postTime
        val (small, large) = iconCache.getOrPut(cacheKey) {
            val s = n.smallIcon?.let { render(it, 48, tint = true) }
            val l = n.getLargeIcon()?.let { render(it, 112, tint = false) }
            s to l
        }

        return NotifEntry(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appName(sbn.packageName),
            title = title ?: appName(sbn.packageName),
            text = text,
            smallIcon = small,
            largeIcon = large,
            color = n.color,
            contentIntent = n.contentIntent,
            actions = actions,
            postTime = sbn.postTime,
            ongoing = ongoing,
            clearable = sbn.isClearable,
            category = n.category,
            call = call,
            chronoBase = if (chrono && n.`when` > 0) n.`when` else null,
            countDown = x.getBoolean("android.chronometerCountDown", false),
            progress = progress,
            urgent = urgent,
        )
    }

    private fun render(icon: Icon, size: Int, tint: Boolean): ImageBitmap? = try {
        val d = icon.loadDrawable(this) ?: return null
        if (tint) d.setTint(android.graphics.Color.WHITE)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(b))
        b.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
