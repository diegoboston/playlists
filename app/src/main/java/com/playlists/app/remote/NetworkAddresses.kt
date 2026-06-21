package com.playlists.app.remote

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddresses {
    fun localLanIp(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }
}
