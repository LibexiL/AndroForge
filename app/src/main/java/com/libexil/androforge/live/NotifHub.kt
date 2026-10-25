package com.libexil.androforge.live

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class CallState { NONE, INCOMING, ONGOING }

/** What a live activity is, so the island can draw it the right way. */
enum class LiveKind { TIMER, STOPWATCH, NAVIGATION, DELIVERY, RECORDING, PROGRESS, OTHER }

private val navApps = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura", "com.mapbox.navigation", "net.osmand", "net.osmand.plus", "com.citymapper.app.release")
private val deliveryApps = setOf(
    "com.dd.doordash", "com.ubercab", "com.ubercab.eats", "com.ubercab.driver", "me.lyft.android", "com.grubhub.android",
    "com.instacart.client", "com.postmates.android", "com.gopuff.consumer", "com.amazon.mShop.android.shopping",
    "com.deliveroo.orderapp", "com.global.foodpanda.android", "com.joelapenna.foursquared", "com.bolt.client", "ee.mtakso.client",
    "com.dominospizza", "com.pizzahut.android", "com.chipotle.ordering", "com.starbucks.mobilecard", "com.mcdonalds.app",
    "com.wolt.android", "com.justeat.app.uk", "com.glovoapp", "com.rappi.storekeeper", "com.didiglobal.passenger",
)
private val clockApps = setOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock", "com.oneplus.deskclock", "com.miui.clock")

class NotifAction(
    val title: String,
    val intent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>?,
    val semantic: Int,
) {
    val isReply: Boolean get() = remoteInputs?.any { it.allowFreeFormInput } == true
    private val t = title.lowercase()
    val isAnswer: Boolean get() = t.contains("answer") || t.contains("accept") || t == "pick up"
    val isDecline: Boolean get() = t.contains("decline") || t.contains("reject") || t.contains("hang up") ||
        t.contains("end call") || t == "end" || t.contains("dismiss")
}

data class NotifEntry(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val smallIcon: ImageBitmap?,
    val largeIcon: ImageBitmap?,
    val color: Int,
    val contentIntent: PendingIntent?,
    val actions: List<NotifAction>,
    val postTime: Long,
    val ongoing: Boolean,
    val clearable: Boolean,
    val category: String?,
    val call: CallState,
    /** Wall-clock base of a running chronometer (elapsed = now - base; countdown = base - now). */
    val chronoBase: Long?,
    val countDown: Boolean,
    /** 0..1, or -1 for indeterminate; null when there is no progress bar. */
    val progress: Float?,
    val urgent: Boolean,
    val subText: String? = null,
) {
    val liveKind: LiveKind
        get() = when {
            category == "navigation" || pkg in navApps -> LiveKind.NAVIGATION
            pkg in deliveryApps -> LiveKind.DELIVERY
            chronoBase != null && countDown -> LiveKind.TIMER
            chronoBase != null && (category == "stopwatch" || pkg in clockApps) -> LiveKind.STOPWATCH
            chronoBase != null -> LiveKind.RECORDING
            progress != null -> LiveKind.PROGRESS
            else -> LiveKind.OTHER
        }

    /** A timer, stopwatch, recording, download or navigation the island can show live. */
    val isLiveActivity: Boolean
        get() = ongoing && call == CallState.NONE && !urgent &&
            (chronoBase != null || progress != null || category == "navigation" || category == "stopwatch" || pkg in navApps || pkg in deliveryApps)
}

/**
 * Everything the island knows about notifications. Filled by [ForgeNotificationListener],
 * which also performs dismissals.
 */
object NotifHub {
    private val _entries = MutableStateFlow<List<NotifEntry>>(emptyList())
    val entries: StateFlow<List<NotifEntry>> = _entries.asStateFlow()

    /** The newest notification worth popping up, and when it arrived. */
    private val _peek = MutableStateFlow<Pair<NotifEntry, Long>?>(null)
    val peek: StateFlow<Pair<NotifEntry, Long>?> = _peek.asStateFlow()

    internal var listener: WeakReference<ForgeNotificationListener>? = null

    /** Do Not Disturb on (interruption filter isn't ALL). */
    private val _dnd = MutableStateFlow(false)
    val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

    internal fun setDnd(on: Boolean) {
        _dnd.value = on
    }

    fun toggleDnd() {
        val l = listener?.get() ?: return
        runCatching {
            l.requestInterruptionFilter(
                if (_dnd.value) android.service.notification.NotificationListenerService.INTERRUPTION_FILTER_ALL
                else android.service.notification.NotificationListenerService.INTERRUPTION_FILTER_PRIORITY,
            )
        }
    }

    fun setEntries(list: List<NotifEntry>) {
        _entries.value = list
        // Keep the peek up to date (e.g. a new message in the same conversation) or drop it if removed.
        _peek.value?.let { (e, at) ->
            val fresh = list.firstOrNull { it.key == e.key }
            _peek.value = fresh?.let { it to at }
        }
    }

    fun postPeek(e: NotifEntry) {
        _peek.value = e to SystemClock.elapsedRealtime()
    }

    fun clearPeek(key: String? = null) {
        if (key == null || _peek.value?.first?.key == key) _peek.value = null
    }

    fun dismiss(key: String) {
        clearPeek(key)
        listener?.get()?.let { runCatching { it.cancelNotification(key) } }
    }

    fun clearAll() {
        _peek.value = null
        listener?.get()?.let { runCatching { it.cancelAllNotifications() } }
    }

    fun open(e: NotifEntry, context: Context) {
        clearPeek(e.key)
        if (!send(e.contentIntent, context)) {
            context.packageManager.getLaunchIntentForPackage(e.pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it) }
            }
        }
    }

    /** Runs a notification button; [reply] fills its text field (inline reply). */
    fun runAction(a: NotifAction, context: Context, reply: String? = null): Boolean {
        val pi = a.intent ?: return false
        val inputs = a.remoteInputs
        if (reply != null && inputs != null && inputs.isNotEmpty()) {
            val fill = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, reply) }
            RemoteInput.addResultsToIntent(inputs, fill, results)
            return send(pi, context, fill)
        }
        return send(pi, context)
    }

    fun send(pi: PendingIntent?, context: Context, fill: Intent? = null): Boolean {
        if (pi == null) return false
        return try {
            val opts = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pi.send(context, 0, fill, null, null, null, opts)
            true
        } catch (e: Exception) {
            false
        }
    }
}
