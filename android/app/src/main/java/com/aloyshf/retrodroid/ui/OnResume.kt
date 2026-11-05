package com.aloyshf.retrodroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Runs [block] every time the host activity resumes. */
@Composable
fun OnResume(block: () -> Unit) {
    OnLifecycleEvent(Lifecycle.Event.ON_RESUME, block)
}

/** Runs [block] when the host activity stops, for example when the app goes to the background. */
@Composable
fun OnStop(block: () -> Unit) {
    OnLifecycleEvent(Lifecycle.Event.ON_STOP, block)
}

@Composable
private fun OnLifecycleEvent(event: Lifecycle.Event, block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner, event) {
        val observer = LifecycleEventObserver { _, changed ->
            if (changed == event) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
