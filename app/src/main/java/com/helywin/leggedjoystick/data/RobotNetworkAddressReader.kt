package com.helywin.leggedjoystick.data

import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

/** 读取已启用网卡的本地 IPv4，包含没有互联网能力的图传网卡。 */
object RobotNetworkAddressReader {
    fun readActiveIpv4(): List<String> {
        return try {
            val result = mutableListOf<String>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        result += address.hostAddress.orEmpty()
                    }
                }
            }
            result
        } catch (error: Exception) {
            Timber.w(error, "读取活动网卡 IPv4 地址失败")
            emptyList()
        }
    }
}
