package com.libexil.androforge.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backs the layout up to a private GitHub Gist. The token and gist id stay in this phone's
 * private app storage and are never part of the backup itself.
 */
object GistBackup {
    private const val FILE = "androforge-backup.json"
    private fun prefs(c: Context) = c.getSharedPreferences("gist_backup", Context.MODE_PRIVATE)

    fun token(c: Context): String? = prefs(c).getString("token", null)?.takeIf { it.isNotBlank() }
    fun gistId(c: Context): String? = prefs(c).getString("gist", null)?.takeIf { it.isNotBlank() }

    fun save(c: Context, token: String?, gistId: String?) {
        prefs(c).edit().putString("token", token?.trim()).putString("gist", gistId?.trim()?.substringAfterLast('/')).apply()
    }

    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Failed(val message: String) : Result
    }

    private fun open(url: String, token: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AndroForge")
        }

    private fun explain(code: Int): String = when (code) {
        401 -> "GitHub didn't accept the token. Make a new one with the “gist” permission."
        403 -> "The token doesn't allow gists. Give it the “gist” permission."
        404 -> "Backup gist not found. Clear the gist ID to start a new one."
        else -> "GitHub answered $code"
    }

    suspend fun backup(c: Context, json: String): Result = withContext(Dispatchers.IO) {
        val token = token(c) ?: return@withContext Result.Failed("Add a GitHub token first")
        val existing = gistId(c)
        val body = buildJsonObject {
            put("description", "AndroForge launcher backup")
            if (existing == null) put("public", false)
            putJsonObject("files") { putJsonObject(FILE) { put("content", json) } }
        }.toString()
        try {
            val conn = if (existing == null) open("https://api.github.com/gists", token, "POST")
            // Android's HttpURLConnection (OkHttp underneath) accepts PATCH.
            else open("https://api.github.com/gists/$existing", token, "PATCH")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext Result.Failed(explain(code))
            val reply = conn.inputStream.bufferedReader().readText()
            val id = Json.parseToJsonElement(reply).jsonObject["id"]?.jsonPrimitive?.content
            if (id != null && existing == null) save(c, token, id)
            Result.Ok(if (existing == null) "Backed up to a new private gist" else "Backup updated on GitHub")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Network error")
        }
    }

    /** Returns the backup's JSON text. */
    suspend fun fetch(c: Context): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val token = token(c) ?: return@withContext null to "Add a GitHub token first"
        val id = gistId(c) ?: return@withContext null to "No backup yet — back up first, or paste your gist ID"
        try {
            val conn = open("https://api.github.com/gists/$id", token, "GET")
            val code = conn.responseCode
            if (code != 200) return@withContext null to explain(code)
            val reply = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val file = (reply["files"] as? JsonObject)?.get(FILE) as? JsonObject
                ?: return@withContext null to "That gist has no AndroForge backup in it"
            val truncated = (file["truncated"] as? JsonPrimitive)?.content == "true"
            val content = if (truncated) {
                val raw = file["raw_url"]?.jsonPrimitive?.content ?: return@withContext null to "Backup too large to read"
                open(raw, token, "GET").inputStream.bufferedReader().readText()
            } else {
                file["content"]?.jsonPrimitive?.content
            }
            content to null
        } catch (e: Exception) {
            null to (e.message ?: "Network error")
        }
    }
}
