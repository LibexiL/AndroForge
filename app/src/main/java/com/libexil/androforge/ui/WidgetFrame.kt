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
 * Hosts an AppWidgetHostView and adds launcher-style long-press detection on top of it
 * (widgets normally swallow touches, so we intercept once the long-press fires).
 */
class WidgetFrame(context: Context) : FrameLayout(context) {
    var onLongPress: (() -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var lastW = -1
    private var lastH = -1

    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
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
                longPressed = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPressRunnable)
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
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
