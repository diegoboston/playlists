package com.playlists.app.remote

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddresses {

    data class LanIpv4(
        val address: String,
        val interfaceName: String,
    ) {
        val isLikelyHotspot: Boolean
            get() {
                val name = interfaceName.lowercase()
                return name.contains("ap") ||
                    name.contains("softap") ||
                    name.contains("rndis") ||
                    name.contains("hotspot")
            }
    }

    fun localLanIp(): String? = localLanIpv4Addresses().firstOrNull()?.address

    fun localLanIpv4Addresses(): List<LanIpv4> {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        val results = linkedSetOf<LanIpv4>()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    results.add(LanIpv4(address = host, interfaceName = iface.name))
                }
            }
        }
        return results.sortedWith(
            compareBy<LanIpv4> { it.isLikelyHotspot }.thenBy { it.address },
        )
    }
}
