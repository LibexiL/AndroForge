package com.libexil.androforge.ui

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Hosts an AppWidgetHostView and adds launcher-style hold-and-drag on top of it. Widgets
 * normally swallow touches, so once the long press fires we take over the gesture and
 * report finger movement until it lifts.
 */
class WidgetFrame(context: Context) : FrameLayout(context) {
    /** Local x/y of the press, in px. */
    var onLongPress: ((Float, Float) -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var longPressed = false
    private var lastW = -1
    private var lastH = -1

    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // Stop the widget's own press state.
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        for (i in 0 until childCount) getChildAt(i).dispatchTouchEvent(cancel)
        cancel.recycle()
        parent?.requestDisallowInterceptTouchEvent(true)
        onLongPress?.invoke(downX, downY)
    }

    init {
        clipChildren = true
        clipToPadding = true
    }

    fun setWidget(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        removeAllViews()
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        lastW = -1
        lastH = -1
    }

    /** Tell the widget provider how big it is (in dp) so it can pick a layout. */
    @Suppress("DEPRECATION")
    fun updateSize(widthDp: Int, heightDp: Int) {
        if (widthDp == lastW && heightDp == lastH) return
        lastW = widthDp
        lastH = heightDp
        val host = getChildAt(0) as? AppWidgetHostView ?: return
        try {
            host.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        } catch (_: Exception) {
        }
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                longPressed = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) removeCallbacks(longPressRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN ->
                removeCallbacks(longPressRunnable)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return longPressed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) track(ev)
        if (longPressed) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastRawX
                    val dy = ev.rawY - lastRawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    if (dx != 0f || dy != 0f) onDrag?.invoke(dx, dy)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressed = false
                    onDragEnd?.invoke()
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        if (longPressed) {
            longPressed = false
            onDragEnd?.invoke()
        }
        super.onDetachedFromWindow()
    }
}
