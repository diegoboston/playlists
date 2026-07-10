package com.playlists.app.remote

sealed interface RemotePlayFlowState {
    data object ChooseMode : RemotePlayFlowState

    data class Starting(
        val mode: RemotePlayMode,
        val phase: RemotePlayStartPhase = RemotePlayStartPhase.STARTING_TUNNEL,
    ) : RemotePlayFlowState

    data class Started(val url: String, val mode: RemotePlayMode) : RemotePlayFlowState
}
