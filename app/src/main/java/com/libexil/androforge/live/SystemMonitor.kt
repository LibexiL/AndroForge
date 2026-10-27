package com.libexil.androforge.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(val level: Int = 0, val charging: Boolean = false, val tempC: Float = 0f, val plugged: Boolean = false)

enum class NetKind { NONE, WIFI, CELL, OTHER }

enum class EventKind { CHARGING, UNPLUGGED, BATTERY_LOW, HEADPHONES, HEADPHONES_OFF, SILENT, VIBRATE, RING, TORCH_ON, TORCH_OFF }

/** A short device event the island pops up for a few seconds. */
data class IslandEvent(
    val kind: EventKind,
    val title: String,
    val detail: String = "",
    val value: Int? = null,
    val at: Long = SystemClock.elapsedRealtime(),
)

/** Battery, network, audio devices, ringer and flashlight - shared by widgets and the island. */
object SystemMonitor {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    private val _battery = MutableStateFlow(BatteryInfo())
    val battery: StateFlow<BatteryInfo> = _battery.asStateFlow()

    private val _net = MutableStateFlow(NetKind.NONE)
    val net: StateFlow<NetKind> = _net.asStateFlow()

    private val _event = MutableStateFlow<IslandEvent?>(null)
    val event: StateFlow<IslandEvent?> = _event.asStateFlow()

    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    private val _ringer = MutableStateFlow(AudioManager.RINGER_MODE_NORMAL)
    val ringer: StateFlow<Int> = _ringer.asStateFlow()

    private var started = false
    private var startedAt = 0L
    private var lowWarned = 101
    private var torchId: String? = null

    fun post(e: IslandEvent) {
        _event.value = e
    }

    fun clearEvent() {
        _event.value = null
    }

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        startedAt = SystemClock.elapsedRealtime()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_BATTERY_CHANGED -> onBattery(i)
                    Intent.ACTION_POWER_CONNECTED -> post(IslandEvent(EventKind.CHARGING, "Charging", value = _battery.value.level))
                    Intent.ACTION_POWER_DISCONNECTED -> post(IslandEvent(EventKind.UNPLUGGED, "Unplugged", value = _battery.value.level))
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val mode = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                        if (mode != _ringer.value) {
                            _ringer.value = mode
                            if (!booting()) post(
                                when (mode) {
                                    AudioManager.RINGER_MODE_SILENT -> IslandEvent(EventKind.SILENT, "Silent")
                                    AudioManager.RINGER_MODE_VIBRATE -> IslandEvent(EventKind.VIBRATE, "Vibrate")
                                    else -> IslandEvent(EventKind.RING, "Ring")
                                },
                            )
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { onBattery(it) }

        val audio = app.getSystemService(AudioManager::class.java)
        _ringer.value = audio.ringerMode
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (booting()) return
                added.firstOrNull { it.isSink && isHeadphones(it) }?.let { d -> onHeadphones(d) }
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (booting()) return
                removed.firstOrNull { it.isSink && isHeadphones(it) }?.let { d ->
                    post(IslandEvent(EventKind.HEADPHONES_OFF, d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Headphones", "Disconnected"))
                }
            }
        }, main)

        try {
            val cm = app.getSystemService(CameraManager::class.java)
            torchId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) _torch.value = enabled
                }
            }, main)
        } catch (_: Exception) {
        }

        val conn = app.getSystemService(ConnectivityManager::class.java)
        fun update(caps: NetworkCapabilities?) {
            _net.value = when {
                caps == null -> NetKind.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELL
                else -> NetKind.OTHER
            }
        }
        try {
            update(conn.getNetworkCapabilities(conn.activeNetwork))
            conn.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(caps)
                override fun onLost(network: Network) = update(null)
            })
        } catch (_: Exception) {
        }
    }

    private fun booting() = SystemClock.elapsedRealtime() - startedAt < 3000

    private fun onBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val pct = if (level >= 0) level * 100 / scale else _battery.value.level
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        _battery.value = BatteryInfo(pct, charging, i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f, plugged)
        if (plugged) {
            lowWarned = 101
        } else if ((pct <= 15 && lowWarned > 15) || (pct <= 5 && lowWarned > 5)) {
            lowWarned = pct
            if (!booting()) post(IslandEvent(EventKind.BATTERY_LOW, "Battery low", "$pct% left", pct))
        }
    }

    private fun isHeadphones(d: AudioDeviceInfo): Boolean {
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        if (Build.VERSION.SDK_INT >= 31) types += AudioDeviceInfo.TYPE_BLE_HEADSET
        return d.type in types
    }

    private fun onHeadphones(d: AudioDeviceInfo) {
        val name = d.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "Headphones"
        post(IslandEvent(EventKind.HEADPHONES, name, "Connected", bluetoothBattery(d)))
        // Earbuds often report their battery a moment after connecting.
        main.postDelayed({
            val level = bluetoothBattery(d) ?: return@postDelayed
            val cur = _event.value
            if (cur?.kind == EventKind.HEADPHONES && cur.title == name) _event.value = cur.copy(value = level)
        }, 2500)
    }

    /** Bluetooth headset battery, when the phone exposes it (needs Nearby devices on Android 12+). */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun bluetoothBattery(d: AudioDeviceInfo): Int? {
        if (Build.VERSION.SDK_INT < 28) return null
        if (d.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && !(Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            val device = adapter.getRemoteDevice(d.address)
            val level = device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
            level.takeIf { it in 0..100 }
        } catch (e: Throwable) {
            null
        }
    }

    fun hasTorch() = torchId != null

    fun toggleTorch() {
        val id = torchId ?: return
        try {
            app.getSystemService(CameraManager::class.java).setTorchMode(id, !_torch.value)
        } catch (_: Exception) {
        }
    }

    /** Cycles ring → vibrate → ring (silent needs Do Not Disturb access, so it's left to the system). */
    fun cycleRinger() {
        try {
            val audio = app.getSystemService(AudioManager::class.java)
            audio.ringerMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        } catch (_: Exception) {
        }
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
