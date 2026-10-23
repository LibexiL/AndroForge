package com.libexil.androforge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import com.libexil.androforge.island.IslandOverlay
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Optional accessibility service ("AndroForge system features"): hosts the dynamic island /
 * custom status bar overlay, and locks the screen or pulls down shades from gestures.
 */
class LockService : AccessibilityService() {
    private var island: IslandOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        island = IslandOverlay(this).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        island?.onConfigurationChanged(newConfig)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        island?.stop()
        island = null
        instance = null
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
