package com.libexil.androforge.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.libexil.androforge.LauncherApp
import java.util.concurrent.TimeUnit

/** Background check every few hours; posts a notification when a new release exists. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = LauncherApp.instance.store
        if (!store.state.value.settings.autoCheckUpdates) return Result.success()
        return when (val result = Updater.check()) {
            is CheckResult.Available -> {
                store.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                if (result.info.versionCode != store.state.value.skippedVersion) {
                    UpdateNotifier.notify(applicationContext, result.info)
                }
                Result.success()
            }
            CheckResult.UpToDate -> {
                store.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                Result.success()
            }
            is CheckResult.Failed -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "androforge-update-check"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
