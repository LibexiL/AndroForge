package com.libexil.androforge.live

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.libexil.androforge.data.LauncherSettings

/**
 * Turns off the parts of Android's own UI that fight with the island and custom bar.
 * Needs WRITE_SECURE_SETTINGS, which only adb can grant (one command, once).
 */
object SystemOverride {
    private const val TAG = "SystemOverride"
    private const val HEADS_UP = "heads_up_notifications_enabled"
    private const val ICON_BLACKLIST = "icon_blacklist"
    private const val PREFS = "system_override"

    /** Status bar slots to hide (AOSP names; phones ignore the ones they don't have). */
    private val ICONS = listOf(
        "clock", "battery", "wifi", "mobile", "airplane", "bluetooth", "headset", "rotate", "volume", "zen",
        "alarm_clock", "location", "hotspot", "cast", "nfc", "vpn", "ethernet", "data_saver", "managed_profile",
        "speakerphone", "mute", "sensors_off", "screen_record", "call_strength", "ims", "wifi_calling",
        "dnd", "do_not_disturb", "power_saver", "network_speed", "smart_network", "ims_volte", "volte", "vowifi",
    ).joinToString(",")

    fun grantCommand(context: Context) =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private var watching = false

    /**
     * Re-applies the moment anything (Samsung, a settings screen, a reboot) flips Android's
     * pop-ups or icons back on, instead of waiting for the launcher to open again.
     */
    fun watch(context: Context, settings: () -> LauncherSettings) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val s = settings()
                if (s.hideSystemPopups || s.hideSystemIcons) apply(app, s)
            }
        }
        runCatching { app.contentResolver.registerContentObserver(Settings.Global.getUriFor(HEADS_UP), false, observer) }
        runCatching { app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(ICON_BLACKLIST), false, observer) }
        // Also re-check whenever the screen turns on or the phone finishes booting.
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) = apply(app, settings())
        }
        androidx.core.content.ContextCompat.registerReceiver(
            app, receiver,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_ON)
                addAction(android.content.Intent.ACTION_USER_PRESENT)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** True when pop-ups should be hidden but Android's switch is on anyway. */
    fun popupsLeaking(context: Context, s: LauncherSettings): Boolean = s.hideSystemPopups && headsUpOn(context)

    fun headsUpOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, HEADS_UP, 1) != 0

    /** Apply the saved choices (called on start and whenever settings change; Samsung sometimes resets them). */
    fun apply(context: Context, s: LauncherSettings) {
        if (!canWrite(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val wantHeadsUp = !s.hideSystemPopups
            if (headsUpOn(context) != wantHeadsUp) {
                Settings.Global.putInt(context.contentResolver, HEADS_UP, if (wantHeadsUp) 1 else 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't change pop-ups", e)
        }
        try {
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, ICON_BLACKLIST)
            if (s.hideSystemIcons) {
                if (current != ICONS) {
                    // Remember what was there so turning it off restores it.
                    if (!prefs.contains("icons_before")) prefs.edit().putString("icons_before", current ?: "").apply()
                    Settings.Secure.putString(cr, ICON_BLACKLIST, ICONS)
                }
            } else if (prefs.contains("icons_before")) {
                val before = prefs.getString("icons_before", "") ?: ""
                Settings.Secure.putString(cr, ICON_BLACKLIST, before.ifEmpty { null })
                prefs.edit().remove("icons_before").apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't change status bar icons", e)
        }
    }
}
