package com.libexil.androforge.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bumps whenever a runtime permission or special access might have changed. */
object PermissionBus {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun changed() {
        _version.value = _version.value + 1
        AudioViz.permissionChanged()
        MediaHub.refresh()
    }
}
