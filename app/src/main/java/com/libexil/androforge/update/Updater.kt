package com.libexil.androforge.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.libexil.androforge.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
)

sealed interface CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data class Failed(val message: String) : CheckResult
}

/**
 * Self-updater backed by GitHub Releases. CI attaches an `update.json` to every release,
 * so "latest/download/update.json" always describes the newest build.
 */
object Updater {
    private const val REPO = BuildConfig.UPDATE_REPO
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val MANIFEST_URL = "https://github.com/$REPO/releases/latest/download/update.json"

    private val json = Json { ignoreUnknownKeys = true }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = open(MANIFEST_URL)
            try {
                when (val code = conn.responseCode) {
                    200 -> {
                        val info = json.decodeFromString(UpdateInfo.serializer(), conn.inputStream.bufferedReader().readText())
                        if (info.versionCode > currentVersionCode) CheckResult.Available(info) else CheckResult.UpToDate
                    }
                    404 -> CheckResult.UpToDate // no releases published yet
                    else -> CheckResult.Failed("GitHub answered $code")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "Network error")
        }
    }

    /** Downloads the APK, reporting progress 0..1 (or -1 when the size is unknown). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "AndroForge-${info.versionCode}.apk")
            val conn = open(info.apkUrl)
            try {
                if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (read - lastReport > 128 * 1024) {
                                lastReport = read
                                onProgress(if (total > 0) read.toFloat() / total else -1f)
                            }
                        }
                    }
                }
                onProgress(1f)
            } finally {
                conn.disconnect()
            }
            verify(context, target, info)
            target
        }

    private fun verify(context: Context, apk: File, info: UpdateInfo) {
        val pm = context.packageManager
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.path, 0)
            ?: error("Downloaded file is not a valid APK")
        if (archive.packageName != context.packageName) {
            error("Update is for ${archive.packageName}, not this app")
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode.toInt() else archive.versionCode
        if (code < info.versionCode) error("Downloaded build is older than advertised")
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to PackageInstaller. On Android 12+ later updates can apply without a prompt. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AndroForge/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
}
