package com.playlists.app.remote

enum class RemotePlayMode {
    STABLE,
    CLOUDFLARE,
    LAN,
    ;

    fun usesCloudflareTunnel(): Boolean =
        this == STABLE || this == CLOUDFLARE
}
