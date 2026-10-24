package com.libexil.androforge.island

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.libexil.androforge.LauncherApp
import com.libexil.androforge.data.BarMode
import com.libexil.androforge.data.LauncherSettings
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lifecycle for Compose views living in service windows. */
class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun create() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Root that reports taps outside the island window (to collapse it). */
@SuppressLint("ViewConstructor")
private class OutsideFrame(context: Context, val onOutside: () -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return false
        }
        return super.dispatchTouchEvent(ev)
    }
}

/** Geometry shared with the island/bar composables. */
class IslandGeometry {
    val statusBarPx = mutableIntStateOf(0)
    val screenWidthPx = mutableIntStateOf(0)
    val cutoutCenterPx = mutableIntStateOf(-1)
    val cutoutWidthPx = mutableIntStateOf(0)
    val collapseTick = mutableIntStateOf(0)
    val visible = mutableStateOf(true)
    /** True while typing an inline reply: the island window must take keyboard focus. */
    val wantsFocus = mutableStateOf(false)
}

/**
 * Draws the dynamic island and the custom status bar as accessibility overlays. These are
 * the only windows an app may place over the system status bar without root.
 */
class IslandOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private val geo = IslandGeometry()
    private var islandRoot: OutsideFrame? = null
    private var barView: ComposeView? = null
    private var settings: LauncherSettings = LauncherApp.instance.store.state.value.settings
    private var screenReceiver: BroadcastReceiver? = null

    fun start() {
        owner.create()
        owner.resume()
        measure()
        scope.launch {
            LauncherApp.instance.store.state
                .map { it.settings }
                .distinctUntilChanged()
                .collect {
                    settings = it
                    apply()
                }
        }
        scope.launch {
            snapshotFlow { geo.wantsFocus.value }.collect {
                islandRoot?.let { root -> runCatching { wm.updateViewLayout(root, islandParams()) } }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> owner.pause()
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> owner.resume()
                }
            }
        }
        ContextCompat.registerReceiver(
            service, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiver = receiver
    }

    fun stop() {
        screenReceiver?.let { runCatching { service.unregisterReceiver(it) } }
        screenReceiver = null
        removeIsland()
        removeBar()
        owner.destroy()
        scope.cancel()
    }

    fun onConfigurationChanged(@Suppress("UNUSED_PARAMETER") config: Configuration) {
        measure()
        apply()
    }

    private fun landscape() = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun measure() {
        @SuppressLint("InternalInsetResource", "DiscouragedApi")
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val sb = if (id > 0) service.resources.getDimensionPixelSize(id) else (24 * service.resources.displayMetrics.density).toInt()
        geo.statusBarPx.intValue = sb
        val width: Int
        var cutout: Rect? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            width = metrics.bounds.width()
            // Some phones (Samsung) make the status bar taller than the framework dimen.
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            if (inset > geo.statusBarPx.intValue) geo.statusBarPx.intValue = inset
            cutout = metrics.windowInsets.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }
        } else {
            width = service.resources.displayMetrics.widthPixels
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                cutout = wm.defaultDisplay.cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            }
        }
        geo.screenWidthPx.intValue = width
        geo.cutoutCenterPx.intValue = cutout?.centerX() ?: -1
        geo.cutoutWidthPx.intValue = cutout?.width() ?: 0
    }

    private fun apply() {
        val hide = settings.islandHideLandscape && landscape()
        geo.visible.value = !hide
        when (settings.barMode) {
            BarMode.OFF -> {
                removeIsland()
                removeBar()
            }
            BarMode.ISLAND -> {
                removeBar()
                ensureIsland()
            }
            BarMode.ISLAND_BAR -> {
                ensureBar()
                ensureIsland() // added after the bar so it sits on top
            }
        }
        islandRoot?.let { root ->
            root.visibility = if (hide) View.GONE else View.VISIBLE
            runCatching { wm.updateViewLayout(root, islandParams()) }
        }
        barView?.let { v ->
            v.visibility = if (hide) View.GONE else View.VISIBLE
            runCatching { wm.updateViewLayout(v, barParams()) }
        }
    }

    private fun baseParams(w: Int, h: Int, touchable: Boolean, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }

    private fun islandParams() = baseParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        touchable = true,
        focusable = geo.wantsFocus.value,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (geo.wantsFocus.value) {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        val density = service.resources.displayMetrics.density
        val center = geo.cutoutCenterPx.intValue
        val follow = settings.islandFollowCamera && center >= 0
        x = (if (follow) center - geo.screenWidthPx.intValue / 2 else 0) + (settings.islandOffsetX * density).toInt()
        y = (settings.islandOffsetY * density).toInt()
    }

    private fun barParams() = baseParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        geo.statusBarPx.intValue,
        touchable = false,
    ).apply {
        gravity = Gravity.TOP
    }

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(service).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent(content)
    }

    private fun ensureIsland() {
        if (islandRoot != null) return
        val root = OutsideFrame(service) { geo.collapseTick.intValue++ }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(composeView { IslandContent(service, geo) })
        try {
            wm.addView(root, islandParams())
            islandRoot = root
        } catch (e: Exception) {
            android.util.Log.w("IslandOverlay", "Couldn't add island", e)
        }
    }

    private fun ensureBar() {
        if (barView != null) return
        val v = composeView { StatusBarContent(geo) }
        try {
            wm.addView(v, barParams())
            barView = v
            // Keep the island above the bar.
            islandRoot?.let { r ->
                runCatching { wm.removeView(r) }
                islandRoot = null
            }
        } catch (e: Exception) {
            android.util.Log.w("IslandOverlay", "Couldn't add bar", e)
        }
    }

    private fun removeIsland() {
        islandRoot?.let { runCatching { wm.removeView(it) } }
        islandRoot = null
    }

    private fun removeBar() {
        barView?.let { runCatching { wm.removeView(it) } }
        barView = null
    }
}
