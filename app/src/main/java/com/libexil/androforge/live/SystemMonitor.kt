package com.libexil.androforge.live

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(val level: Int = 0, val charging: Boolean = false, val tempC: Float = 0f, val plugged: Boolean = false)

enum class NetKind { NONE, WIFI, CELL, OTHER }

data class Peek(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val icon: ImageBitmap?,
    val tint: Int,
    val intent: PendingIntent?,
    val at: Long = SystemClock.elapsedRealtime(),
)

data class NotifIcon(val key: String, val pkg: String, val icon: ImageBitmap)

/** Battery, network, memory and notification state shared by widgets and the island. */
object SystemMonitor {
    private val _battery = MutableStateFlow(BatteryInfo())
    val battery: StateFlow<BatteryInfo> = _battery.asStateFlow()

    private val _net = MutableStateFlow(NetKind.NONE)
    val net: StateFlow<NetKind> = _net.asStateFlow()

    /** Bumps on every charger plug-in (the island shows a charging animation). */
    private val _chargeEvent = MutableStateFlow(0L)
    val chargeEvent: StateFlow<Long> = _chargeEvent.asStateFlow()

    private val _peek = MutableStateFlow<Peek?>(null)
    val peek: StateFlow<Peek?> = _peek.asStateFlow()

    private val _icons = MutableStateFlow<List<NotifIcon>>(emptyList())
    val icons: StateFlow<List<NotifIcon>> = _icons.asStateFlow()

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        _battery.value = BatteryInfo(
                            level = if (level >= 0) level * 100 / scale else _battery.value.level,
                            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
                            plugged = plugged,
                        )
                    }
                    Intent.ACTION_POWER_CONNECTED -> _chargeEvent.value = SystemClock.elapsedRealtime()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { receiver.onReceive(app, it) }

        val cm = app.getSystemService(ConnectivityManager::class.java)
        fun update(caps: NetworkCapabilities?) {
            _net.value = when {
                caps == null -> NetKind.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELL
                else -> NetKind.OTHER
            }
        }
        try {
            update(cm.getNetworkCapabilities(cm.activeNetwork))
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(caps)
                override fun onLost(network: Network) = update(null)
            })
        } catch (_: Exception) {
        }
    }

    fun postPeek(p: Peek) {
        _peek.value = p
    }

    fun clearPeek(key: String? = null) {
        if (key == null || _peek.value?.key == key) _peek.value = null
    }

    fun setIcons(list: List<NotifIcon>) {
        _icons.value = list
    }

    // ── one-shot readings for the system gauges ─────────────────────
    data class Snapshot(
        val ramUsed: Long, val ramTotal: Long,
        val storageUsed: Long, val storageTotal: Long,
        val uptimeMs: Long,
    )

    fun snapshot(context: Context): Snapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: Exception) {
            null
        }
        val total = st?.totalBytes ?: 0L
        val free = st?.availableBytes ?: 0L
        return Snapshot(mi.totalMem - mi.availMem, mi.totalMem, total - free, total, SystemClock.elapsedRealtime())
    }
}
