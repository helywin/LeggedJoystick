package com.helywin.leggedjoystick.input.remote.unirc

import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.normalizeRemoteChannel

data class UniRcChannelFrame(
    val ctrl: Int,
    val sequence: Int,
    val commandId: Int,
    val channels: List<Int>,
    val rawPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniRcChannelFrame) return false
        return ctrl == other.ctrl &&
            sequence == other.sequence &&
            commandId == other.commandId &&
            channels == other.channels &&
            rawPayload.contentEquals(other.rawPayload)
    }

    override fun hashCode(): Int {
        var result = ctrl
        result = 31 * result + sequence
        result = 31 * result + commandId
        result = 31 * result + channels.hashCode()
        result = 31 * result + rawPayload.contentHashCode()
        return result
    }
}

data class UniRcMovementMappingResult(
    val movementIntent: MovementIntent,
    val normalizedAxes: Map<String, Float>
)

data class UniRcFrameInfo(
    val ctrl: Int,
    val sequence: Int,
    val commandId: Int,
    val dataLength: Int,
    val expectedLength: Int
)

object UniRcProtocol {
    const val CMD_CHANNELS = 0x42
    const val CHANNEL_COUNT = 16
    const val CHANNEL_PAYLOAD_LENGTH = CHANNEL_COUNT * 2

    private const val STX_0 = 0x55
    private const val STX_1 = 0x66
    private const val HEADER_LENGTH = 8
    private const val CRC_LENGTH = 2

    fun inspectFrame(data: ByteArray): UniRcFrameInfo? {
        if (data.size < HEADER_LENGTH + CRC_LENGTH) return null
        if (data[0].toUnsignedInt() != STX_0 || data[1].toUnsignedInt() != STX_1) return null

        val dataLength = data.readUInt16LE(3)
        val expectedLength = HEADER_LENGTH + dataLength + CRC_LENGTH
        if (data.size < expectedLength) return null

        return UniRcFrameInfo(
            ctrl = data[2].toUnsignedInt(),
            sequence = data.readUInt16LE(5),
            commandId = data[7].toUnsignedInt(),
            dataLength = dataLength,
            expectedLength = expectedLength
        )
    }

    fun isChannelDataFrame(data: ByteArray): Boolean {
        val info = inspectFrame(data) ?: return false
        return info.commandId == CMD_CHANNELS && info.dataLength == CHANNEL_PAYLOAD_LENGTH
    }

    fun isIgnorableNonChannelFrame(data: ByteArray): Boolean {
        val info = inspectFrame(data) ?: return false
        if (info.commandId == CMD_CHANNELS && info.dataLength == CHANNEL_PAYLOAD_LENGTH) return false

        val expectedCrc = data.readUInt16LE(HEADER_LENGTH + info.dataLength)
        val actualCrc = crc16(data, info.expectedLength - CRC_LENGTH)
        return expectedCrc == actualCrc
    }

    fun parseChannelFrame(data: ByteArray): UniRcChannelFrame {
        require(data.size >= HEADER_LENGTH + CRC_LENGTH) { "UniRC 帧长度不足" }
        require(data[0].toUnsignedInt() == STX_0 && data[1].toUnsignedInt() == STX_1) {
            "UniRC 帧头错误"
        }

        val dataLength = data.readUInt16LE(3)
        val expectedLength = HEADER_LENGTH + dataLength + CRC_LENGTH
        require(data.size >= expectedLength) { "UniRC 帧长度与 Data_len 不一致" }

        val commandId = data[7].toUnsignedInt()
        require(commandId == CMD_CHANNELS) { "UniRC 命令号不是通道数据" }
        require(dataLength == CHANNEL_PAYLOAD_LENGTH) { "UniRC 通道数据长度不是 32 字节" }

        val expectedCrc = data.readUInt16LE(HEADER_LENGTH + dataLength)
        val actualCrc = crc16(data, expectedLength - CRC_LENGTH)
        require(expectedCrc == actualCrc) { "UniRC CRC16 校验失败" }

        val payload = data.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + dataLength)
        val channels = List(CHANNEL_COUNT) { index ->
            payload.readInt16LE(index * 2)
        }

        return UniRcChannelFrame(
            ctrl = data[2].toUnsignedInt(),
            sequence = data.readUInt16LE(5),
            commandId = commandId,
            channels = channels,
            rawPayload = payload
        )
    }

    fun createChannelFrequencyFrame(sequence: Int, frequency: UniRcChannelFrequency): ByteArray {
        val frameWithoutCrc = byteArrayOf(
            STX_0.toByte(),
            STX_1.toByte(),
            0x01,
            0x01,
            0x00,
            (sequence and 0xFF).toByte(),
            ((sequence ushr 8) and 0xFF).toByte(),
            CMD_CHANNELS.toByte(),
            frequency.value.toByte()
        )
        val crc = crc16(frameWithoutCrc, frameWithoutCrc.size)
        return frameWithoutCrc + byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
    }

    fun crc16(data: ByteArray, length: Int = data.size): Int {
        require(length in 0..data.size) { "CRC16 计算长度越界" }
        var crc = 0

        for (index in 0 until length) {
            crc = crc xor (data[index].toUnsignedInt() shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }

        return crc and 0xFFFF
    }

    fun mapMovement(
        channels: List<Int>,
        config: RemoteInputNormalizationConfig
    ): UniRcMovementMappingResult {
        require(channels.size >= CHANNEL_COUNT) { "UniRC 通道数量不足" }

        val forward = normalizeAxis(channels, config.mapping.forward, config)
        val strafeRight = normalizeAxis(channels, config.mapping.strafeRight, config)
        val yawRight = normalizeAxis(channels, config.mapping.yawRight, config)

        return UniRcMovementMappingResult(
            movementIntent = MovementIntent(
                forward = forward,
                strafeRight = strafeRight,
                yawRight = yawRight
            ).clamped(),
            normalizedAxes = mapOf(
                "forward" to forward,
                "strafeRight" to strafeRight,
                "yawRight" to yawRight
            )
        )
    }

    private fun normalizeAxis(
        channels: List<Int>,
        axisMapping: com.helywin.leggedjoystick.input.remote.RemoteInputAxisMapping,
        config: RemoteInputNormalizationConfig
    ): Float {
        require(axisMapping.channel in 1..CHANNEL_COUNT) { "通道号必须在 1 到 16 之间" }
        return normalizeRemoteChannel(
            raw = channels[axisMapping.channel - 1],
            axisMapping = axisMapping,
            config = config
        )
    }

    private fun ByteArray.readUInt16LE(offset: Int): Int {
        return this[offset].toUnsignedInt() or (this[offset + 1].toUnsignedInt() shl 8)
    }

    private fun ByteArray.readInt16LE(offset: Int): Int {
        val unsigned = readUInt16LE(offset)
        return if ((unsigned and 0x8000) != 0) unsigned - 0x10000 else unsigned
    }

    private fun Byte.toUnsignedInt(): Int {
        return toInt() and 0xFF
    }
}

enum class UniRcChannelFrequency(val value: Int) {
    STOP(0),
    HZ_2(1),
    HZ_4(2),
    HZ_5(3),
    HZ_10(4),
    HZ_20(5),
    HZ_50(6),
    HZ_100(7)
}
