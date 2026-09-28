package com.helywin.leggedjoystick.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotLinkProfileTest {
    @Test
    fun localIpv4SelectsKnownRobotNetworkWithoutDeviceIdentity() {
        assertEquals(RobotLinkProfile.VIDEO_168, RobotLinkProfile.fromLocalIpv4(listOf("192.168.168.20")))
        assertEquals(RobotLinkProfile.VIDEO_144, RobotLinkProfile.fromLocalIpv4(listOf("192.168.144.20")))
        assertEquals(RobotLinkProfile.WIFI_234, RobotLinkProfile.fromLocalIpv4(listOf("192.168.234.206")))
        assertNull(RobotLinkProfile.fromLocalIpv4(listOf("127.0.0.1", "10.0.0.4")))
        assertNull(RobotLinkProfile.fromLocalIpv4(listOf("192.168.168.bad")))
    }

    @Test
    fun videoNetworksTakePriorityOverWifi() {
        assertEquals(
            RobotLinkProfile.VIDEO_168,
            RobotLinkProfile.fromLocalIpv4(listOf("192.168.234.206", "192.168.144.20", "192.168.168.20"))
        )
        assertEquals(
            RobotLinkProfile.VIDEO_144,
            RobotLinkProfile.fromLocalIpv4(listOf("192.168.234.206", "192.168.144.20"))
        )
    }

    @Test
    fun controlAndDefaultVideoFollowSelectedNetwork() {
        val old = AppSettings()
        RobotLinkProfile.entries.forEach { profile ->
            val settings = profile.applyTo(old)
            assertEquals(profile.robotIp, settings.zmqIp)
            assertEquals(33445, settings.zmqPort)
            assertEquals(33446, settings.controllerPort)
            assertEquals("rtsp://${profile.robotIp}:8554/front", settings.headRtspUrl)
            assertEquals("rtsp://${profile.robotIp}:8554/back", settings.tailRtspUrl)
        }
        val switched = RobotLinkProfile.VIDEO_168.applyTo(
            RobotLinkProfile.VIDEO_144.applyTo(old)
        )
        assertEquals("192.168.168.168", switched.zmqIp)
        assertEquals("rtsp://192.168.168.168:8554/front", switched.headRtspUrl)
    }

    @Test
    fun customAddressesAndPathsRemainUnchanged() {
        val custom = AppSettings(
            zmqIp = "10.0.0.2",
            headRtspUrl = "rtsp://10.0.0.3:9554/custom",
            tailRtspUrl = "rtsp://192.168.234.1:8554/other"
        )
        assertEquals(custom, RobotLinkProfile.VIDEO_168.applyTo(custom))
    }
}
