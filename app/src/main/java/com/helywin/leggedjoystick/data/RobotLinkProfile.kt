package com.helywin.leggedjoystick.data

/** 根据遥控器活动网段选择的底盘通信入口。 */
enum class RobotLinkProfile(val localSubnet: String, val robotIp: String) {
    VIDEO_168("192.168.168", "192.168.168.168"),
    VIDEO_144("192.168.144", "192.168.144.144"),
    WIFI_234("192.168.234", "192.168.234.1");

    fun applyTo(settings: AppSettings): AppSettings {
        val robotAddress = if (settings.zmqIp.trim().isEmpty() || isStandardRobotIp(settings.zmqIp)) {
            robotIp
        } else {
            settings.zmqIp
        }
        return settings.copy(
            zmqIp = robotAddress,
            headRtspUrl = resolveRtspUrl(settings.headRtspUrl, front = true),
            tailRtspUrl = resolveRtspUrl(settings.tailRtspUrl, front = false)
        )
    }

    private fun resolveRtspUrl(savedUrl: String, front: Boolean): String {
        val path = if (front) "front" else "back"
        val oldPath = if (front) "head" else "tail"
        val value = savedUrl.trim()
        if (value.isEmpty()) return "rtsp://$robotIp:8554/$path"
        val isStandardUrl = entries.any { profile ->
            value == "rtsp://${profile.robotIp}:8554/$path" ||
                value == "rtsp://${profile.robotIp}:8554/$oldPath"
        }
        return if (isStandardUrl) "rtsp://$robotIp:8554/$path" else savedUrl
    }

    companion object {
        fun fromLocalIpv4(addresses: Iterable<String>): RobotLinkProfile? {
            val subnets = addresses.mapNotNull { address ->
                val octets = address.split('.')
                if (octets.size != 4 || octets.any { part ->
                        val number = part.toIntOrNull()
                        number == null || number !in 0..255
                    }) {
                    null
                } else {
                    octets.take(3).joinToString(".")
                }
            }.toSet()
            return entries.firstOrNull { it.localSubnet in subnets }
        }

        private fun isStandardRobotIp(value: String): Boolean {
            return entries.any { it.robotIp == value.trim() }
        }
    }
}
