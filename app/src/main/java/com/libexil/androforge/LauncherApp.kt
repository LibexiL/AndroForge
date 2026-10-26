package com.libexil.androforge

import android.app.Application
import com.libexil.androforge.data.AppRepository
import com.libexil.androforge.data.LayoutStore
import com.libexil.androforge.live.AudioViz
import com.libexil.androforge.live.MediaHub
import com.libexil.androforge.live.SystemMonitor
import com.libexil.androforge.update.UpdateNotifier
import com.libexil.androforge.update.UpdateWorker

class LauncherApp : Application() {
    lateinit var store: LayoutStore
        private set
    lateinit var apps: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)
        store = LayoutStore(this)
        apps = AppRepository(this, store)
        SystemMonitor.init(this)
        MediaHub.init(this)
        AudioViz.init(this)
        com.libexil.androforge.live.SystemOverride.apply(this, store.state.value.settings)
        com.libexil.androforge.live.SystemOverride.watch(this) { store.state.value.settings }
        com.libexil.androforge.data.Palettes.checkWallpaperChanged(this)
        @Suppress("DEPRECATION")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) = onWallpaperMaybeChanged()
            },
            android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        UpdateNotifier.createChannel(this)
        UpdateWorker.schedule(this, store.state.value.settings.autoCheckUpdates)
    }

    /** New wallpaper: re-theme to it if "Follow wallpaper" is on. */
    fun onWallpaperMaybeChanged() {
        if (!com.libexil.androforge.data.Palettes.checkWallpaperChanged(this)) return
        val s = store.state.value.settings
        if (s.followWallpaper && s.paletteSource != com.libexil.androforge.data.PaletteSource.WALLPAPER) {
            store.updateSettings {
                it.copy(
                    paletteSource = com.libexil.androforge.data.PaletteSource.WALLPAPER,
                    themeMode = com.libexil.androforge.data.ThemeMode.PALETTE,
                )
            }
        }
        if (s.followWallpaper) {
            android.widget.Toast.makeText(this, "Re-themed to your new wallpaper", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        lateinit var instance: LauncherApp
            private set
    }
}
