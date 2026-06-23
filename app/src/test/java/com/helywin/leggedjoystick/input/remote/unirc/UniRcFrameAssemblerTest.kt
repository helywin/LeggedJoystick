package com.helywin.leggedjoystick.input.remote.unirc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniRcFrameAssemblerTest {
    @Test
    fun append_returnsFrameAfterSplitPacketsComplete() {
        val frame = createChannelFrame(sequence = 1)
        val assembler = UniRcFrameAssembler()

        assertTrue(assembler.append(frame.copyOfRange(0, 7)).isEmpty())
        assertTrue(assembler.append(frame.copyOfRange(7, 20)).isEmpty())
        val frames = assembler.append(frame.copyOfRange(20, frame.size))

        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames[0])
    }

    @Test
    fun append_extractsMultipleFramesFromOnePacketWithLeadingNoise() {
        val frame1 = createChannelFrame(sequence = 11)
        val frame2 = createChannelFrame(sequence = 12)
        val packet = byteArrayOf(0x01, 0x02, 0x03, 0x55) + frame1 + frame2
        val assembler = UniRcFrameAssembler()

        val frames = assembler.append(packet)

        assertEquals(2, frames.size)
        assertArrayEquals(frame1, frames[0])
        assertArrayEquals(frame2, frames[1])
    }

    private fun createChannelFrame(sequence: Int): ByteArray {
        val channels = List(16) { 1500 }
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
}
