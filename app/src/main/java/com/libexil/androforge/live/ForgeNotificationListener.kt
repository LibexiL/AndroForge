package com.libexil.androforge.live

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.libexil.androforge.LauncherApp

/**
 * Notification access powers three things: the music widgets (media sessions), the island's
 * notification peeks, and the custom status bar's notification icons.
 */
class ForgeNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaHub.refresh()
        publishIcons()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SystemMonitor.setIcons(emptyList())
        MediaHub.refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishIcons()
        if (!shouldPeek(sbn)) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
        val large: Icon? = if (Build.VERSION.SDK_INT >= 28) n.getLargeIcon() else null
        val icon = (large ?: n.smallIcon)?.let { render(it, 96, tint = large == null) }
        SystemMonitor.postPeek(
            Peek(
                key = sbn.key,
                pkg = sbn.packageName,
                title = title.toString(),
                text = text.toString().replace('\n', ' '),
                icon = icon,
                tint = n.color,
                intent = n.contentIntent,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        SystemMonitor.clearPeek(sbn.key)
        publishIcons()
    }

    private fun shouldPeek(sbn: StatusBarNotification): Boolean {
        val settings = LauncherApp.instance.store.state.value.settings
        if (!settings.islandNotifications) return false
        val n = sbn.notification
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS) return false
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        // Ignore silent updates to an existing notification.
        if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && System.currentTimeMillis() - sbn.postTime > 2000) return false
        return try {
            val ranking = Ranking()
            currentRanking.getRanking(sbn.key, ranking) && ranking.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT
        } catch (e: Exception) {
            true
        }
    }

    private fun publishIcons() {
        val list = try {
            activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        val seen = HashSet<String>()
        val icons = list
            .sortedByDescending { it.postTime }
            .filter { seen.add(it.packageName) }
            .take(6)
            .mapNotNull { sbn ->
                val img = sbn.notification.smallIcon?.let { render(it, 48, tint = true) } ?: return@mapNotNull null
                NotifIcon(sbn.key, sbn.packageName, img)
            }
        SystemMonitor.setIcons(icons)
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
