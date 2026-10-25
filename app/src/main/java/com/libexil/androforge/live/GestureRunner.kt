package com.libexil.androforge.live

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.LockService
import com.libexil.androforge.SystemActions
import com.libexil.androforge.data.GestureAction
import com.libexil.androforge.data.GestureSlot
import com.libexil.androforge.data.LauncherSettings
import com.libexil.androforge.data.ShadeMode
import com.libexil.androforge.island.activeIslandGeo

/** Runs gesture actions that don't need the launcher's own screen (so shake works anywhere). */
object GestureRunner {
    fun actionFor(s: LauncherSettings, slot: GestureSlot): GestureAction = when (slot) {
        GestureSlot.SWIPE_UP -> s.swipeUp
        GestureSlot.SWIPE_DOWN -> s.swipeDown
        GestureSlot.DOUBLE_TAP -> s.doubleTap
        GestureSlot.TWO_UP -> s.twoFingerUp
        GestureSlot.TWO_DOWN -> s.twoFingerDown
        GestureSlot.PINCH -> s.pinchIn
        GestureSlot.SHAKE -> s.shake
    }

    fun withAction(s: LauncherSettings, slot: GestureSlot, a: GestureAction): LauncherSettings = when (slot) {
        GestureSlot.SWIPE_UP -> s.copy(swipeUp = a)
        GestureSlot.SWIPE_DOWN -> s.copy(swipeDown = a)
        GestureSlot.DOUBLE_TAP -> s.copy(doubleTap = a)
        GestureSlot.TWO_UP -> s.copy(twoFingerUp = a)
        GestureSlot.TWO_DOWN -> s.copy(twoFingerDown = a)
        GestureSlot.PINCH -> s.copy(pinchIn = a)
        GestureSlot.SHAKE -> s.copy(shake = a)
    }

    private fun toast(context: Context, msg: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    private fun needsService(context: Context): Boolean {
        toast(context, "Turn on “AndroForge system features” in Accessibility for this gesture")
        SystemActions.openAccessibilitySettings(context)
        return true
    }

    fun openNotifications(context: Context, s: LauncherSettings) {
        val geo = activeIslandGeo
        if (s.shadeMode == ShadeMode.FORGE && geo != null) geo.openShade() else SystemActions.expandNotifications(context)
    }

    /** Returns false for actions only the launcher screen can do (drawer, search, settings). */
    fun run(context: Context, slot: GestureSlot, s: LauncherSettings = LauncherApp.instance.store.state.value.settings): Boolean {
        when (actionFor(s, slot)) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER, GestureAction.SEARCH, GestureAction.LAUNCHER_SETTINGS -> return false
            GestureAction.NOTIFICATIONS -> openNotifications(context, s)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.FLASHLIGHT -> if (SystemMonitor.hasTorch()) SystemMonitor.toggleTorch() else toast(context, "No flashlight found")
            GestureAction.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    toast(context, "Needs Android 9 or newer")
                } else if (!LockService.isRunning) {
                    needsService(context)
                } else {
                    // Let the finger lift and any menu close first.
                    Handler(Looper.getMainLooper()).postDelayed({ LockService.globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 350)
                }
            }
            GestureAction.RECENTS -> if (!LockService.globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) needsService(context)
            GestureAction.POWER_MENU -> if (!LockService.globalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) needsService(context)
            GestureAction.PLAY_PAUSE -> {
                if (MediaHub.state.value == null) toast(context, "Nothing is playing") else MediaHub.playPause()
            }
            GestureAction.OPEN_APP -> {
                val key = s.gestureApps[slot.name]
                if (key == null) toast(context, "Pick the app for “${slot.label}” in Settings → Gestures")
                else LauncherApp.instance.apps.launch(key)
            }
        }
        return true
    }
}
