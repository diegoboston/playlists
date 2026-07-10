package com.playlists.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.playlists.app.util.AiCredentialStore

/** Re-reads [AiCredentialStore.isOpenAiKeyReady] on resume (e.g. after Settings). */
@Composable
fun rememberOpenAiKeyReady(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(AiCredentialStore.isOpenAiKeyReady(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            ready = AiCredentialStore.isOpenAiKeyReady(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return ready
}
