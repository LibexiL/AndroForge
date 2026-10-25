package com.libexil.androforge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder

/**
 * Saves the stack trace when AndroForge crashes, so the next launch can offer to report it
 * as a GitHub issue (nothing is sent unless you tap Report).
 */
object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    appendLine("AndroForge ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                    appendLine()
                    append(sw.toString())
                }
                file(context).writeText(text)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Opens a pre-filled "new issue" page on GitHub. */
    fun report(context: Context, text: String) {
        val firstLine = text.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(90) ?: "Crash"
        val body = "**What were you doing when it crashed?**\n\n\n**Crash log**\n```\n" + text.take(5500) + "\n```"
        val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" +
            URLEncoder.encode("Crash: $firstLine", "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        clear(context)
    }
}
