package com.helywin.leggedjoystick.input.remote.unirc

internal class UniRcFrameAssembler {
    private val buffer = ArrayDeque<Byte>()

    fun append(data: ByteArray): List<ByteArray> {
        data.forEach { buffer.addLast(it) }

        if (buffer.size > MAX_BUFFER_SIZE) {
            trimToLatestPossibleHeader()
        }

        val frames = mutableListOf<ByteArray>()
        while (true) {
            trimUntilHeader()
            if (buffer.size < UniRcProtocolHeader.HEADER_LENGTH) return frames

            val dataLength = readUInt16LE(3)
            val totalLength = UniRcProtocolHeader.HEADER_LENGTH + dataLength + UniRcProtocolHeader.CRC_LENGTH
            if (totalLength > MAX_FRAME_SIZE) {
                buffer.removeFirst()
                continue
            }
            if (buffer.size < totalLength) return frames

            frames += ByteArray(totalLength) { buffer.removeFirst() }
        }
    }

    fun clear() {
        buffer.clear()
    }

    private fun trimUntilHeader() {
        while (buffer.size >= 2) {
            if (buffer[0].toUnsignedInt() == UniRcProtocolHeader.STX_0 &&
                buffer[1].toUnsignedInt() == UniRcProtocolHeader.STX_1
            ) {
                return
            }
            buffer.removeFirst()
        }

        if (buffer.size == 1 && buffer[0].toUnsignedInt() != UniRcProtocolHeader.STX_0) {
            buffer.removeFirst()
        }
    }

    private fun trimToLatestPossibleHeader() {
        while (buffer.size > 1) {
            val first = buffer.removeFirst()
            if (first.toUnsignedInt() == UniRcProtocolHeader.STX_0 &&
                buffer.first().toUnsignedInt() == UniRcProtocolHeader.STX_1
            ) {
                buffer.addFirst(first)
                return
            }
        }
    }

    private fun readUInt16LE(offset: Int): Int {
        return buffer[offset].toUnsignedInt() or (buffer[offset + 1].toUnsignedInt() shl 8)
    }

    private fun Byte.toUnsignedInt(): Int {
        return toInt() and 0xFF
    }

    private object UniRcProtocolHeader {
        const val STX_0 = 0x55
        const val STX_1 = 0x66
        const val HEADER_LENGTH = 8
        const val CRC_LENGTH = 2
    }

    private companion object {
        const val MAX_FRAME_SIZE = 512
        const val MAX_BUFFER_SIZE = 2048
    }
}
