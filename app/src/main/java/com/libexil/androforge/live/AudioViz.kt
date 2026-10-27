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

    /** A kick just landed: jumps toward 1 on a beat and decays (for pulses and flashes). */
    @Volatile
    var beat: Float = 0f
        private set

    private val norm = FloatArray(BANDS) { 0.25f }
    private var bassAvg = 0f
    private var lastBeat = 0L

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
        val lo = 40f
        val hi = minOf(15000f, nyquist)
        val out = FloatArray(BANDS)
        var globalPeak = 0f
        for (b in 0 until BANDS) {
            val f0 = lo * (hi / lo).pow(b / BANDS.toFloat())
            val f1 = lo * (hi / lo).pow((b + 1) / BANDS.toFloat())
            val p0 = f0 / nyquist * n
            val p1 = f1 / nyquist * n
            val raw = if (p1 - p0 < 1f) {
                // Narrower than one FFT bin (the low end): interpolate so neighbouring bars differ.
                val c = ((p0 + p1) / 2f).coerceIn(1f, n - 1.001f)
                val i = c.toInt()
                val t = c - i
                mags[i] * (1f - t) + mags[(i + 1).coerceAtMost(n - 1)] * t
            } else {
                var m = 0f
                var sum = 0f
                val i0 = p0.toInt().coerceIn(1, n - 1)
                val i1 = p1.toInt().coerceIn(i0, n - 1)
                for (i in i0..i1) {
                    m = max(m, mags[i])
                    sum += mags[i]
                }
                // Mostly the loudest bin, a bit of the average so wide bands aren't spiky.
                m * 0.7f + sum / (i1 - i0 + 1) * 0.3f
            }
            val tilt = 1f + b / BANDS.toFloat() * 2.2f
            val v = (ln(1f + raw * tilt) / ln(1f + 128f) - 0.06f).coerceAtLeast(0f) // gate the hiss floor
            out[b] = v
            if (v > globalPeak) globalPeak = v
        }
        // Each band learns its own loudness (slowly), blended with the overall level, so
        // highs move as much as lows without everything maxing out.
        gain = if (globalPeak * gain > 1f) (gain * 0.85f).coerceAtLeast(1f / globalPeak.coerceAtLeast(0.01f)) else (gain * 1.006f).coerceAtMost(3f)
        for (b in 0 until BANDS) {
            norm[b] = max(norm[b] * 0.996f, out[b]).coerceAtLeast(0.08f)
            val local = out[b] / (norm[b] * 0.55f + 0.45f / gain.coerceAtLeast(0.34f))
            out[b] = local.coerceIn(0f, 1f).pow(1.15f)
        }
        // Light smoothing across neighbours (like cava's monstercat filter, but gentle).
        val sm = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            val l = out[(b - 1).coerceAtLeast(0)]
            val r = out[(b + 1).coerceAtMost(BANDS - 1)]
            sm[b] = max(out[b], (l + r) * 0.42f)
        }
        var low = 0f
        val lowBands = BANDS / 10
        for (b in 0 until lowBands) low += sm[b]
        low /= lowBands
        // Fast attack, slower release.
        bass = if (low > bass) bass + (low - bass) * 0.7f else bass * 0.82f + low * 0.18f
        bassAvg = bassAvg * 0.94f + low * 0.06f
        val now = android.os.SystemClock.elapsedRealtime()
        beat = if (low > bassAvg * 1.35f + 0.08f && now - lastBeat > 180) {
            lastBeat = now
            1f
        } else {
            beat * 0.8f
        }
        levels = sm
    }
}
