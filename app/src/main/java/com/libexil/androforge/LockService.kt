package com.libexil.androforge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import com.libexil.androforge.island.IslandOverlay
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional accessibility service ("AndroForge system features"): hosts the dynamic island /
 * custom status bar overlay, and locks the screen or pulls down shades from gestures.
 */
class LockService : AccessibilityService() {
    private var island: IslandOverlay? = null
    private var shake: com.libexil.androforge.live.ShakeDetector? = null
    private val scope = kotlinx.coroutines.MainScope()
    private val screen = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) shake?.stop() else syncShake()
        }
    }

    private fun syncShake() {
        val s = LauncherApp.instance.store.state.value.settings
        if (s.shake == com.libexil.androforge.data.GestureAction.NONE) {
            shake?.stop()
            return
        }
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm?.isInteractive == false) return
        val d = shake ?: com.libexil.androforge.live.ShakeDetector(
            this,
            { LauncherApp.instance.store.state.value.settings.shakeSensitivity },
        ) {
            if (!com.libexil.androforge.live.GestureRunner.run(this, com.libexil.androforge.data.GestureSlot.SHAKE)) {
                // Drawer / search / settings: bring the launcher up.
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(it) }
                }
            }
        }.also { shake = it }
        d.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        island = IslandOverlay(this).also { it.start() }
        androidx.core.content.ContextCompat.registerReceiver(
            this, screen,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            LauncherApp.instance.store.state
                .map { it.settings.shake }
                .distinctUntilChanged()
                .collect { syncShake() }
        }
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
        shake?.stop()
        shake = null
        runCatching { unregisterReceiver(screen) }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
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
