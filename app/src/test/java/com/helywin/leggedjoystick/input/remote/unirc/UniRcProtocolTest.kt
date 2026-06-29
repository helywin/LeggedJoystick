package com.helywin.leggedjoystick.input.remote.unirc

import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteSpeedLevelRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UniRcProtocolTest {
    @Test
    fun createChannelFrequencyFrame_matchesDocumentedCrc() {
        val frame = UniRcProtocol.createChannelFrequencyFrame(
            sequence = 0,
            frequency = UniRcChannelFrequency.HZ_4
        )

        assertArrayEquals(
            byteArrayOf(
                0x55,
                0x66,
                0x01,
                0x01,
                0x00,
                0x00,
                0x00,
                0x42,
                0x02,
                0xB5.toByte(),
                0xC0.toByte()
            ),
            frame
        )
    }

    @Test
    fun parseChannelFrame_extractsSixteenChannels() {
        val channels = List(16) { index -> 1500 + index }
        val frame = UniRcProtocol.parseChannelFrame(createChannelFrame(sequence = 0x99, channels = channels))

        assertEquals(0x99, frame.sequence)
        assertEquals(UniRcProtocol.CMD_CHANNELS, frame.commandId)
        assertEquals(channels, frame.channels)
    }

    @Test
    fun parseChannelFrame_rejectsInvalidCrc() {
        val frame = createChannelFrame(sequence = 1, channels = List(16) { 1500 })
        frame[frame.lastIndex] = 0x00

        assertThrows(IllegalArgumentException::class.java) {
            UniRcProtocol.parseChannelFrame(frame)
        }
    }

    @Test
    fun inspectFrame_recognizesSubscriptionAckAsIgnorableNonChannelFrame() {
        val frame = byteArrayOf(
            0x55,
            0x66,
            0x02,
            0x01,
            0x00,
            0x4B,
            0xC2.toByte(),
            UniRcProtocol.CMD_CHANNELS.toByte(),
            0x01,
            0x40,
            0x10
        )

        val info = UniRcProtocol.inspectFrame(frame)

        assertEquals(0xC24B, info?.sequence)
        assertEquals(1, info?.dataLength)
        assertFalse(UniRcProtocol.isChannelDataFrame(frame))
        assertTrue(UniRcProtocol.isIgnorableNonChannelFrame(frame))
        assertThrows(IllegalArgumentException::class.java) {
            UniRcProtocol.parseChannelFrame(frame)
        }
    }

    @Test
    fun mapMovement_usesDefaultUniRcStickChannels() {
        val channels = MutableList(16) { 1500 }
        channels[0] = 1050
        channels[1] = 1050
        channels[2] = 1950
        channels[3] = 1950

        val result = UniRcProtocol.mapMovement(
            channels = channels,
            config = RemoteInputNormalizationConfig(deadZone = 0f)
        )

        assertEquals(1f, result.movementIntent.forward, FLOAT_DELTA)
        assertEquals(1f, result.movementIntent.strafeRight, FLOAT_DELTA)
        assertEquals(-1f, result.movementIntent.yawRight, FLOAT_DELTA)
        assertEquals(1f, result.headControlIntent.pitchUp, FLOAT_DELTA)
        assertEquals(1f, result.normalizedAxes["headPitchUp"] ?: 0f, FLOAT_DELTA)
    }

    @Test
    fun mapMovement_mapsLeftButtonsFromObservedSpeedSelectorValues() {
        assertEquals(RemoteSpeedLevelRequest.LOW, mapSpeedSelector(raw = 1000))
        assertEquals(RemoteSpeedLevelRequest.MEDIUM, mapSpeedSelector(raw = 1200))
        assertEquals(RemoteSpeedLevelRequest.HIGH, mapSpeedSelector(raw = 1400))
    }

    @Test
    fun mapMovement_ignoresSpeedSelectorOutsideObservedValues() {
        val channels = MutableList(16) { 1500 }

        val result = UniRcProtocol.mapMovement(
            channels = channels,
            config = RemoteInputNormalizationConfig(deadZone = 0f)
        )

        assertEquals(null, result.speedLevelRequest)
    }

    @Test
    fun mapMovement_appliesDeadZone() {
        val channels = MutableList(16) { 1500 }
        channels[2] = 1530

        val result = UniRcProtocol.mapMovement(
            channels = channels,
            config = RemoteInputNormalizationConfig(deadZone = 0.1f)
        )

        assertEquals(0f, result.movementIntent.forward, FLOAT_DELTA)
    }

    private fun createChannelFrame(sequence: Int, channels: List<Int>): ByteArray {
        require(channels.size == 16)

        val header = byteArrayOf(
            0x55,
            0x66,
            0x00,
            0x20,
            0x00,
            (sequence and 0xFF).toByte(),
            ((sequence ushr 8) and 0xFF).toByte(),
            UniRcProtocol.CMD_CHANNELS.toByte()
        )
        val payload = ByteArray(32)
        channels.forEachIndexed { index, value ->
            payload[index * 2] = (value and 0xFF).toByte()
            payload[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        val withoutCrc = header + payload
        val crc = UniRcProtocol.crc16(withoutCrc)
        return withoutCrc + byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
    }

    private fun mapSpeedSelector(raw: Int): RemoteSpeedLevelRequest? {
        val channels = MutableList(16) { 1500 }
        channels[4] = raw
        return UniRcProtocol.mapMovement(
            channels = channels,
            config = RemoteInputNormalizationConfig(deadZone = 0f)
        ).speedLevelRequest
    }

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
