package com.libexil.androforge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/** Optional accessibility service, used only to lock the screen / pull down shades from gestures. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        val isRunning: Boolean get() = instance != null

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun globalAction(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
