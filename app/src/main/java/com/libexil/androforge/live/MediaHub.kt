package com.libexil.androforge.live

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(
    val title: String,
    val artist: String,
    val album: String,
    val art: ImageBitmap?,
    val artKey: Int,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
    val pkg: String,
) {
    /** Live position, extrapolated from the last playback state. */
    fun position(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((now - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Follows the active media session (needs notification access, like every launcher music widget). */
object MediaHub {
    private const val TAG = "MediaHub"
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var listening = false

    private val _state = MutableStateFlow<MediaInfo?>(null)
    val state: StateFlow<MediaInfo?> = _state.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            // Another session might have started playing; re-evaluate.
            manager?.let { m -> runCatching { pick(m.getActiveSessions(listenerComponent())) } }
        }
        override fun onSessionDestroyed() {
            detach()
            manager?.let { m -> runCatching { pick(m.getActiveSessions(listenerComponent())) } }
        }
    }

    fun init(context: Context) {
        app = context.applicationContext
        refresh()
    }

    private fun listenerComponent() = ComponentName(app, ForgeNotificationListener::class.java)

    fun accessGranted(context: Context = app): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Call when notification access may have changed. */
    fun refresh() {
        if (!::app.isInitialized) return
        val granted = accessGranted()
        _hasAccess.value = granted
        if (!granted) {
            stopListening()
            return
        }
        try {
            val m = manager ?: app.getSystemService(MediaSessionManager::class.java).also { manager = it }
            if (!listening) {
                m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(), main)
                listening = true
            }
            pick(m.getActiveSessions(listenerComponent()))
        } catch (e: SecurityException) {
            Log.w(TAG, "No session access yet", e)
            _hasAccess.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Media sessions failed", e)
        }
    }

    private fun stopListening() {
        if (listening) runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        listening = false
        detach()
        _state.value = null
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.sessionToken == controller?.sessionToken }
            ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            detach()
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private fun detach() {
        controller?.let { runCatching { it.unregisterCallback(callback) } }
        controller = null
    }

    private var lastArt: Bitmap? = null
    private var lastArtImage: ImageBitmap? = null

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            _state.value = null
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank()) {
            _state.value = null
            return
        }
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp !== lastArt) {
            lastArt = bmp
            lastArtImage = try {
                bmp?.let { scaled(it).asImageBitmap() }
            } catch (e: Exception) {
                null
            }
        }
        val ps = c.playbackState
        _state.value = MediaInfo(
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = lastArtImage,
            artKey = System.identityHashCode(lastArt),
            playing = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            pkg = c.packageName,
        )
    }

    private fun scaled(b: Bitmap): Bitmap {
        val max = 512
        if (b.width <= max && b.height <= max) return b.copy(Bitmap.Config.ARGB_8888, false) ?: b
        val k = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    // ── controls ────────────────────────────────────────────────────
    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)

    fun openPlayer(context: Context) {
        val c = controller ?: return
        try {
            val pi = c.sessionActivity
            if (pi != null) {
                pi.send()
                return
            }
        } catch (_: Exception) {
        }
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(it) }
        }
    }
}
