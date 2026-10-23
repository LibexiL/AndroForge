package com.libexil.androforge.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * cava for Android: taps the phone's global audio output (session 0) with
 * [Visualizer] and turns the FFT into log-spaced 0..1 bands. Reference counted, so it only
 * runs while something on screen is drawing it.
 */
object AudioViz {
    enum class Status { IDLE, RUNNING, NO_PERMISSION, UNSUPPORTED }

    const val BANDS = 96
    private const val TAG = "AudioViz"
    private val main = Handler(Looper.getMainLooper())
    private var viz: Visualizer? = null
    private var users = 0
    private lateinit var app: Context

    /** Latest band levels (0..1), BANDS long. Read every frame by renderers. */
    @Volatile
    var levels: FloatArray = FloatArray(BANDS)
        private set

    /** Overall loudness of the low end, smoothed (drives "pulse with the bass"). */
    @Volatile
    var bass: Float = 0f
        private set

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var gain = 1f
    private var samplingRateHz = 44100

    fun init(context: Context) {
        app = context.applicationContext
        if (!hasPermission()) _status.value = Status.NO_PERMISSION
    }

    fun hasPermission(): Boolean = ::app.isInitialized &&
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun acquire() {
        main.post {
            users++
            start()
        }
    }

    fun release() {
        main.post {
            users = (users - 1).coerceAtLeast(0)
            if (users == 0) main.postDelayed(stopIfUnused, 1500)
        }
    }

    /** Try again after the user grants the microphone permission. */
    fun permissionChanged() {
        main.post {
            if (!hasPermission()) {
                stop()
                _status.value = Status.NO_PERMISSION
            } else if (users > 0) {
                start()
            } else {
                _status.value = Status.IDLE
            }
        }
    }

    private val stopIfUnused = Runnable { if (users == 0) stop() }

    private fun start() {
        main.removeCallbacks(stopIfUnused)
        if (viz != null) return
        if (!hasPermission()) {
            _status.value = Status.NO_PERMISSION
            return
        }
        try {
            val v = Visualizer(0)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 1024.coerceIn(range[0], range[1])
            samplingRateHz = (v.samplingRate / 1000).coerceAtLeast(8000)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) process(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            v.enabled = true
            viz = v
            _status.value = Status.RUNNING
        } catch (e: Throwable) {
            Log.w(TAG, "Visualizer unavailable", e)
            viz = null
            _status.value = Status.UNSUPPORTED
        }
    }

    private fun stop() {
        try {
            viz?.enabled = false
            viz?.release()
        } catch (_: Throwable) {
        }
        viz = null
        levels = FloatArray(BANDS)
        bass = 0f
        if (_status.value == Status.RUNNING) _status.value = Status.IDLE
    }

    private fun process(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 4) return
        val mags = FloatArray(n)
        for (k in 1 until n) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            mags[k] = sqrt(re * re + im * im)
        }
        val nyquist = samplingRateHz / 2f
        val lo = 45f
        val hi = minOf(16000f, nyquist)
        val out = FloatArray(BANDS)
        var peak = 0f
        for (b in 0 until BANDS) {
            val f0 = lo * (hi / lo).pow(b / BANDS.toFloat())
            val f1 = lo * (hi / lo).pow((b + 1) / BANDS.toFloat())
            var i0 = (f0 / nyquist * n).toInt().coerceIn(1, n - 1)
            val i1 = (f1 / nyquist * n).toInt().coerceIn(i0, n - 1)
            if (i1 < i0) i0 = i1
            var sum = 0f
            var cnt = 0
            for (i in i0..i1) {
                sum = max(sum, mags[i])
                cnt++
            }
            // Log compression + gentle high-frequency boost, like cava's default curve.
            val tilt = 1f + b / BANDS.toFloat() * 1.6f
            val v = ln(1f + sum * tilt) / ln(1f + 128f)
            out[b] = v
            if (v > peak) peak = v
        }
        // Auto gain so quiet and loud songs both fill the widget.
        gain = if (peak * gain > 1f) 1f / peak else (gain * 1.004f).coerceAtMost(3f)
        var low = 0f
        for (b in 0 until BANDS) {
            out[b] = (out[b] * gain).coerceIn(0f, 1f)
            if (b < BANDS / 8) low += out[b]
        }
        low /= (BANDS / 8)
        bass = bass * 0.6f + low * 0.4f
        levels = out
    }
}
