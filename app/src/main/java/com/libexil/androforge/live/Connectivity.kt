package com.libexil.androforge.live

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Optional Shizuku access: runs shell commands with adb-level rights, which is what it takes to
 * switch Wi-Fi/Bluetooth or join a network without going through Android's own screens.
 */
object ShellAccess {
    private const val TAG = "ShellAccess"
    const val REQUEST = 4417
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        running() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun request() {
        try {
            if (running() && !granted()) Shizuku.requestPermission(REQUEST)
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku request failed", e)
        }
    }

    /** Runs [cmd] through Shizuku's shell. Returns stdout, or null when Shizuku isn't usable. */
    suspend fun run(cmd: String): String? = withContext(Dispatchers.IO) {
        if (!granted()) return@withContext null
        try {
            // newProcess is hidden in API 13; it's still the simplest way to run a one-off command.
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            if (err.isNotBlank()) Log.d(TAG, "$cmd -> $err")
            out + err
        } catch (e: Throwable) {
            Log.w(TAG, "Shell command failed: $cmd", e)
            null
        }
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}

data class WifiNet(
    val ssid: String,
    val level: Int, // 0..4
    val secure: Boolean,
    val security: String, // open | wpa2 | wpa3 | owe
    val connected: Boolean,
    val saved: Boolean,
    val band: String,
)

data class BtDevice(
    val address: String,
    val name: String,
    val kind: BtKind,
    val connected: Boolean,
    val battery: Int?,
    val bonded: Boolean,
)

enum class BtKind { HEADPHONES, SPEAKER, CAR, WATCH, PHONE, COMPUTER, INPUT, OTHER }

object WifiControl {
    private lateinit var app: Context
    private val _nets = MutableStateFlow<List<WifiNet>>(emptyList())
    val networks: StateFlow<List<WifiNet>> = _nets.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()
    private var receiver: BroadcastReceiver? = null

    private fun wm(): WifiManager = app.applicationContext.getSystemService(WifiManager::class.java)
    private fun prefs() = app.getSharedPreferences("wifi_passwords", Context.MODE_PRIVATE)

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun enabled(): Boolean = runCatching { wm().isWifiEnabled }.getOrDefault(false)

    fun canScan(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun locationOn(): Boolean = if (Build.VERSION.SDK_INT >= 28) {
        runCatching { app.getSystemService(android.location.LocationManager::class.java).isLocationEnabled }.getOrDefault(true)
    } else {
        true
    }

    @Suppress("DEPRECATION")
    fun currentSsid(): String? = runCatching { wm().connectionInfo?.ssid?.trim('"') }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }

    @Suppress("DEPRECATION")
    fun details(): List<Pair<String, String>> {
        val ci = runCatching { wm().connectionInfo }.getOrNull() ?: return emptyList()
        val ip = ci.ipAddress.let { if (it == 0) null else "%d.%d.%d.%d".format(it and 0xff, it shr 8 and 0xff, it shr 16 and 0xff, it shr 24 and 0xff) }
        return listOfNotNull(
            "Speed" to "${ci.linkSpeed} Mbps",
            "Band" to bandOf(ci.frequency),
            "Signal" to "${ci.rssi} dBm",
            ip?.let { "IP address" to it },
        )
    }

    private fun bandOf(freq: Int) = when {
        freq >= 5925 -> "6 GHz"
        freq >= 4900 -> "5 GHz"
        else -> "2.4 GHz"
    }

    fun savedPassword(ssid: String): String? = prefs().getString(ssid, null)

    /** Starts a scan and keeps [networks] updated as results arrive. */
    @SuppressLint("MissingPermission")
    fun startWatching() {
        if (receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = publish()
            }
            ContextCompat.registerReceiver(
                app, r,
                IntentFilter().apply {
                    addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiver = r
        }
        @Suppress("DEPRECATION")
        runCatching { wm().startScan() }
        publish()
    }

    fun stopWatching() {
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    private fun publish() {
        if (!canScan()) {
            _nets.value = emptyList()
            return
        }
        val current = currentSsid()
        val saved = prefs().all.keys
        val results = runCatching { wm().scanResults }.getOrNull() ?: emptyList()
        _nets.value = results
            .mapNotNull { r ->
                @Suppress("DEPRECATION")
                val ssid = r.SSID?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val caps = r.capabilities ?: ""
                val security = when {
                    "SAE" in caps -> "wpa3"
                    "PSK" in caps || "WPA" in caps -> "wpa2"
                    "OWE" in caps -> "owe"
                    "WEP" in caps -> "wep"
                    else -> "open"
                }
                @Suppress("DEPRECATION")
                val level = WifiManager.calculateSignalLevel(r.level, 5)
                WifiNet(ssid, level, security != "open" && security != "owe", security, ssid == current, ssid in saved || ssid == current, bandOf(r.frequency))
            }
            .groupBy { it.ssid }
            .map { (_, same) -> same.maxBy { it.level } }
            .sortedWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    /** Wi-Fi on/off. Returns false when only Android's own panel can do it. */
    suspend fun setEnabled(on: Boolean): Boolean {
        val out = ShellAccess.run("cmd wifi set-wifi-enabled " + if (on) "enabled" else "disabled") ?: return false
        if (out.contains("Unknown", true) || out.contains("Error", true)) ShellAccess.run("svc wifi " + if (on) "enable" else "disable")
        return true
    }

    /**
     * Joins a network. With Shizuku it connects right away; otherwise Android shows a small
     * "Save this network?" sheet and connects once you accept.
     */
    suspend fun connect(context: Context, net: WifiNet, password: String?): String {
        _busy.value = net.ssid
        try {
            if (!password.isNullOrEmpty()) prefs().edit().putString(net.ssid, password).apply()
            val sec = when (net.security) {
                "wpa3" -> "wpa3"
                "owe" -> "owe"
                "open" -> "open"
                else -> "wpa2"
            }
            val cmd = "cmd wifi connect-network ${ShellAccess.quote(net.ssid)} $sec" + (if (!password.isNullOrEmpty() && sec != "open" && sec != "owe") " " + ShellAccess.quote(password) else "")
            val out = ShellAccess.run(cmd)
            if (out != null) {
                return if (out.contains("Error", true) || out.contains("Exception", true)) "Couldn't connect: ${out.trim().take(80)}" else "Connecting to ${net.ssid}…"
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val b = WifiNetworkSuggestion.Builder().setSsid(net.ssid)
                when (sec) {
                    "wpa3" -> password?.let { b.setWpa3Passphrase(it) }
                    "wpa2" -> password?.let { b.setWpa2Passphrase(it) }
                }
                val list = arrayListOf(b.build())
                val i = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return "Tap Save in the sheet to connect"
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val b = WifiNetworkSuggestion.Builder().setSsid(net.ssid)
                if (sec == "wpa3") password?.let { b.setWpa3Passphrase(it) } else if (sec == "wpa2") password?.let { b.setWpa2Passphrase(it) }
                wm().addNetworkSuggestions(listOf(b.build()))
                return "Added — Android will connect when it's ready"
            }
            return "Needs Android 10+ or Shizuku"
        } catch (e: Exception) {
            return "Couldn't connect: ${e.message}"
        } finally {
            _busy.value = null
        }
    }

    suspend fun disconnect(): Boolean {
        val out = ShellAccess.run("cmd wifi disconnect") ?: return false
        return !out.contains("Unknown", true)
    }

    fun forget(ssid: String) {
        prefs().edit().remove(ssid).apply()
    }
}

object BluetoothControl {
    private const val TAG = "BluetoothControl"
    private lateinit var app: Context
    private var a2dp: BluetoothA2dp? = null
    private var headset: BluetoothHeadset? = null
    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()
    private val _found = MutableStateFlow<List<BtDevice>>(emptyList())
    val found: StateFlow<List<BtDevice>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private var receiver: BroadcastReceiver? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun adapter(): BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    fun allowed(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun canScan(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun enabled(): Boolean = allowed() && runCatching { adapter()?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun startWatching() {
        if (!allowed()) return
        val ad = adapter() ?: return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dp = proxy as BluetoothA2dp
                    BluetoothProfile.HEADSET -> headset = proxy as BluetoothHeadset
                }
                publish()
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) a2dp = null else if (profile == BluetoothProfile.HEADSET) headset = null
            }
        }
        if (a2dp == null) runCatching { ad.getProfileProxy(app, listener, BluetoothProfile.A2DP) }
        if (headset == null) runCatching { ad.getProfileProxy(app, listener, BluetoothProfile.HEADSET) }
        if (receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            @Suppress("DEPRECATION")
                            val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            d?.let { dev ->
                                val name = runCatching { dev.name }.getOrNull() ?: return
                                if (_found.value.none { it.address == dev.address } && runCatching { dev.bondState }.getOrNull() != BluetoothDevice.BOND_BONDED) {
                                    _found.value = _found.value + BtDevice(dev.address, name, kindOf(dev), false, null, false)
                                }
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                        else -> publish()
                    }
                }
            }
            ContextCompat.registerReceiver(
                app, r,
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiver = r
        }
        publish()
    }

    @SuppressLint("MissingPermission")
    fun stopWatching() {
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        if (_scanning.value) runCatching { adapter()?.cancelDiscovery() }
        _scanning.value = false
    }

    private fun kindOf(d: BluetoothDevice): BtKind {
        @SuppressLint("MissingPermission")
        val c = runCatching { d.bluetoothClass }.getOrNull() ?: return BtKind.OTHER
        return when (c.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (c.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BtKind.CAR
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO, BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BtKind.SPEAKER
                else -> BtKind.HEADPHONES
            }
            BluetoothClass.Device.Major.WEARABLE -> BtKind.WATCH
            BluetoothClass.Device.Major.PHONE -> BtKind.PHONE
            BluetoothClass.Device.Major.COMPUTER -> BtKind.COMPUTER
            BluetoothClass.Device.Major.PERIPHERAL -> BtKind.INPUT
            else -> BtKind.OTHER
        }
    }

    @SuppressLint("MissingPermission")
    private fun isConnected(d: BluetoothDevice): Boolean {
        val viaProfiles = (a2dp?.connectedDevices.orEmpty() + headset?.connectedDevices.orEmpty()).any { it.address == d.address }
        if (viaProfiles) return true
        // Watches, keyboards etc.: hidden but long-standing isConnected().
        return runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
    }

    private fun battery(d: BluetoothDevice): Int? =
        runCatching { (d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int).takeIf { it in 0..100 } }.getOrNull()

    @SuppressLint("MissingPermission")
    fun publish() {
        if (!allowed()) {
            _devices.value = emptyList()
            return
        }
        val bonded = runCatching { adapter()?.bondedDevices?.toList() }.getOrNull().orEmpty()
        _devices.value = bonded.map { d ->
            val c = isConnected(d)
            BtDevice(d.address, runCatching { (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name }.getOrNull() ?: d.address, kindOf(d), c, if (c) battery(d) else null, true)
        }.sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
        _found.value = _found.value.filter { f -> bonded.none { it.address == f.address } }
    }

    /** Bluetooth on/off. Returns a message when it had to hand over to Android. */
    @SuppressLint("MissingPermission")
    suspend fun setEnabled(context: Context, on: Boolean): String? {
        val out = ShellAccess.run("cmd bluetooth_manager " + if (on) "enable" else "disable")
            ?: ShellAccess.run("svc bluetooth " + if (on) "enable" else "disable")
        if (out != null) return null
        // Without Shizuku Android asks you to confirm.
        val action = if (on) BluetoothAdapter.ACTION_REQUEST_ENABLE else "android.bluetooth.adapter.action.REQUEST_DISABLE"
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (e: Exception) {
            "Switch Bluetooth from Android's panel"
        }
    }

    private fun device(address: String): BluetoothDevice? = runCatching { adapter()?.getRemoteDevice(address) }.getOrNull()

    /** Connects / disconnects audio for a paired device. Returns null on success, or why not. */
    fun setConnected(dev: BtDevice, connect: Boolean): String? {
        val d = device(dev.address) ?: return "Device not found"
        val name = if (connect) "connect" else "disconnect"
        var ok = false
        for (proxy in listOfNotNull(a2dp, headset)) {
            ok = runCatching {
                val m = proxy.javaClass.getMethod(name, BluetoothDevice::class.java)
                m.isAccessible = true
                m.invoke(proxy, d) as Boolean
            }.onFailure { Log.d(TAG, "$name via ${proxy.javaClass.simpleName} failed: $it") }.getOrDefault(false) || ok
        }
        return if (ok) null else "Android didn't allow it"
    }

    @SuppressLint("MissingPermission")
    fun pair(dev: BtDevice): String? {
        val d = device(dev.address) ?: return "Device not found"
        runCatching { adapter()?.cancelDiscovery() }
        _scanning.value = false
        return if (runCatching { d.createBond() }.getOrDefault(false)) null else "Pairing didn't start"
    }

    @SuppressLint("MissingPermission")
    fun forget(dev: BtDevice): Boolean {
        val d = device(dev.address) ?: return false
        return runCatching { d.javaClass.getMethod("removeBond").invoke(d) as Boolean }.getOrDefault(false).also { publish() }
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!canScan() || !allowed()) return
        _found.value = emptyList()
        _scanning.value = runCatching { adapter()?.startDiscovery() == true }.getOrDefault(false)
    }
}
