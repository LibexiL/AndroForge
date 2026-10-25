package com.libexil.androforge.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Two quick jolts within half a second count as a shake. Runs from the accessibility service
 * when it's on (so it works in any app), otherwise only while the launcher is open.
 */
class ShakeDetector(context: Context, private val sensitivity: () -> Float, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var running = false
    private var firstJolt = 0L
    private var lastShake = 0L

    fun start() {
        if (running) return
        val acc = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        running = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        sm?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        var g = sqrt(x * x + y * y + z * z)
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) g = kotlin.math.abs(g - SensorManager.GRAVITY_EARTH)
        // Sensitivity 0..1 maps to a jolt of 22..9 m/s².
        val threshold = 22f - 13f * sensitivity().coerceIn(0f, 1f)
        if (g < threshold) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShake < 1200) return
        if (firstJolt != 0L && now - firstJolt in 120..600) {
            lastShake = now
            firstJolt = 0L
            onShake()
        } else if (firstJolt == 0L || now - firstJolt > 600) {
            firstJolt = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
