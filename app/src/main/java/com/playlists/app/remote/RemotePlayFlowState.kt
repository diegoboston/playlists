package com.playlists.app.remote

sealed interface RemotePlayFlowState {
    data object ChooseMode : RemotePlayFlowState

    data class Starting(val mode: RemotePlayMode) : RemotePlayFlowState

    data class Started(val url: String, val mode: RemotePlayMode) : RemotePlayFlowState
}
