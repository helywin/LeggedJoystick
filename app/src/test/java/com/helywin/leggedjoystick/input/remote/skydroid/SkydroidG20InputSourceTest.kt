package com.helywin.leggedjoystick.input.remote.skydroid

import com.helywin.leggedjoystick.input.remote.RemoteControllerIdentity
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkydroidG20InputSourceTest {
    @Test
    fun factory_selectsG20ByRcSdkDeviceTypeAndFallsBackToUniRc() {
        val factory = RemoteInputSourceFactory()

        assertEquals(
            "skydroid_g20_rcsdk",
            factory.selectProviderId(
                RemoteControllerIdentity(
                    rcSdkDeviceType = "G20",
                    model = "Bengal for arm64",
                    boardVariant = "0"
                )
            )
        )
        assertEquals(
            "unirc_udp",
            factory.selectProviderId(
                RemoteControllerIdentity(
                    rcSdkDeviceType = "UNKNOWN",
                    model = "Standard-10inch_A2",
                    boardVariant = ""
                )
            )
        )
    }

    @Test
    fun channelMapper_usesG20RangeAndNeverRequestsSpeedLevel() {
        val config = SkydroidG20InputConfig()
        val descriptor = RemoteInputSourceDescriptor(
            id = "skydroid_g20_rcsdk",
            displayName = "云卓 G20 RCSDK",
            transport = "rcsdk"
        )
        val channels = MutableList(SkydroidG20InputConfig.CHANNEL_COUNT) {
            SkydroidG20InputConfig.CHANNEL_CENTER
        }.also {
            it[0] = SkydroidG20InputConfig.CHANNEL_MAX
            it[1] = SkydroidG20InputConfig.CHANNEL_MIN
            it[2] = SkydroidG20InputConfig.CHANNEL_MAX
            it[3] = SkydroidG20InputConfig.CHANNEL_MAX
            it[4] = SkydroidG20InputConfig.CHANNEL_MAX
        }

        val snapshot = SkydroidG20ChannelMapper.toSnapshot(
            channels = channels,
            sequence = 7,
            receivedAtMs = 1234L,
            descriptor = descriptor,
            normalization = config.normalization
        )

        assertEquals(1f, snapshot.movementIntent.forward, FLOAT_DELTA)
        assertEquals(1f, snapshot.movementIntent.strafeRight, FLOAT_DELTA)
        assertEquals(1f, snapshot.movementIntent.yawRight, FLOAT_DELTA)
        assertEquals(1f, snapshot.headControlIntent.pitchUp, FLOAT_DELTA)
        assertNull("G20 没有实体速度档键，不得从通道生成速度请求", snapshot.speedLevelRequest)
        assertEquals(7, snapshot.sequence)
        assertEquals(1234L, snapshot.receivedAtMs)
    }

    @Test
    fun channelMapper_centersMissingChannelsAndProducesZeroIntent() {
        val config = SkydroidG20InputConfig()
        val snapshot = SkydroidG20ChannelMapper.toSnapshot(
            channels = emptyList(),
            sequence = 1,
            receivedAtMs = 1L,
            descriptor = RemoteInputSourceDescriptor(
                id = "skydroid_g20_rcsdk",
                displayName = "云卓 G20 RCSDK",
                transport = "rcsdk"
            ),
            normalization = config.normalization
        )

        assertTrue(snapshot.movementIntent.isZero)
        assertTrue(snapshot.headControlIntent.isZero)
        assertEquals(
            List(SkydroidG20InputConfig.CHANNEL_COUNT) {
                SkydroidG20InputConfig.CHANNEL_CENTER
            },
            snapshot.rawChannels
        )
    }

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
