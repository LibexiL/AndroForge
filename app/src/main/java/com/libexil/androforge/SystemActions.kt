package com.libexil.androforge

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object SystemActions {
    /** Until this time, Android's own shade was opened on purpose and must not be replaced. */
    @Volatile
    var systemShadeAllowedUntil = 0L

    fun allowSystemShade() {
        systemShadeAllowedUntil = android.os.SystemClock.elapsedRealtime() + 4000
    }

    @SuppressLint("WrongConstant")
    private fun statusBar(context: Context, method: String): Boolean = try {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        true
    } catch (e: Exception) {
        false
    }

    fun expandNotifications(context: Context) {
        allowSystemShade()
        if (!statusBar(context, "expandNotificationsPanel")) {
            LockService.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun expandQuickSettings(context: Context) {
        allowSystemShade()
        if (!statusBar(context, "expandSettingsPanel")) {
            LockService.globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    fun lockScreen(context: Context) {
        if (!LockService.lockScreen()) {
            Toast.makeText(
                context,
                "Turn on \"AndroForge system features\" in Accessibility to use this gesture",
                Toast.LENGTH_LONG,
            ).show()
            openAccessibilitySettings(context)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}
