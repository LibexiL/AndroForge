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
        store = LayoutStore(this)
        apps = AppRepository(this, store)
        SystemMonitor.init(this)
        MediaHub.init(this)
        AudioViz.init(this)
        UpdateNotifier.createChannel(this)
        UpdateWorker.schedule(this, store.state.value.settings.autoCheckUpdates)
    }

    companion object {
        lateinit var instance: LauncherApp
            private set
    }
}
