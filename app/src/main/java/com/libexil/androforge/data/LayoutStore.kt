package com.libexil.androforge.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** Holds the whole launcher layout + settings and persists it as JSON (debounced). */
class LayoutStore(context: Context) {
    private val file = File(context.filesDir, "layout.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    init {
        scope.launch {
            saveRequests.collectLatest {
                delay(400)
                write(_state.value)
            }
        }
    }

    fun update(transform: (LauncherState) -> LauncherState) {
        _state.update(transform)
        saveRequests.tryEmit(Unit)
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) =
        update { it.copy(settings = transform(it.settings)) }

    /** Write immediately (called when the launcher goes to the background). */
    fun flush() {
        scope.launch { write(_state.value) }
    }

    fun exportJson(): String = json.encodeToString(LauncherState.serializer(), _state.value)

    fun importJson(text: String): Boolean = try {
        val imported = json.decodeFromString(LauncherState.serializer(), text)
        update { imported.copy(initialized = true) }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Import failed", e)
        false
    }

    private fun load(): LauncherState = try {
        if (file.exists()) json.decodeFromString(LauncherState.serializer(), file.readText())
        else LauncherState()
    } catch (e: Exception) {
        Log.w(TAG, "Layout unreadable, starting fresh", e)
        LauncherState()
    }

    @Synchronized
    private fun write(state: LauncherState) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LauncherState.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Saving layout failed", e)
        }
    }

    companion object {
        private const val TAG = "LayoutStore"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
            prettyPrint = false
        }
    }
}
