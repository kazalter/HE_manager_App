package com.hemanager.mobile.ui.host

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

private class RetainedScreenOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

/** Pause a retained screen behind another tab, then resume it on return. */
@Composable
internal fun rememberRetainedScreenLifecycle(active: Boolean): LifecycleOwner {
    val host = LocalLifecycleOwner.current
    val owner = remember(host) { RetainedScreenOwner() }
    val isActive by rememberUpdatedState(active)
    fun sync() {
        val state = host.lifecycle.currentState
        owner.registry.currentState = if (!isActive && state == Lifecycle.State.RESUMED) Lifecycle.State.STARTED else state
    }
    DisposableEffect(host, owner) {
        val observer = LifecycleEventObserver { _, _ -> sync() }
        host.lifecycle.addObserver(observer)
        sync()
        onDispose { host.lifecycle.removeObserver(observer); owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
    LaunchedEffect(active, owner) { sync() }
    return owner
}
