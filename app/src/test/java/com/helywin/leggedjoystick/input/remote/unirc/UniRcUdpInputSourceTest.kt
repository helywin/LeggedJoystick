package com.helywin.leggedjoystick.input.remote.unirc

import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class UniRcUdpInputSourceTest {
    @Test
    fun start_subscribesReceivesSnapshotAndEmitsTimeout() {
        TestUdpPeer().use { peer ->
            val listener = RecordingRemoteInputListener()
            val source = UniRcUdpInputSource(
                UniRcUdpInputConfig(
                    remoteHost = "127.0.0.1",
                    remotePort = peer.localPort,
                    localPort = 0,
                    subscribeRepeatCount = 1,
                    receiveTimeoutMs = 10,
                    resubscribeIntervalMs = 1000L,
                    normalization = RemoteInputNormalizationConfig(
                        deadZone = 0f,
                        timeoutMs = 120L
                    )
                )
            )

            try {
                source.start(listener)

                val subscribePacket = peer.receivePacket()
                assertEquals(
                    UniRcProtocol.createChannelFrequencyFrame(0, UniRcChannelFrequency.HZ_50).toList(),
                    subscribePacket.data.toList()
                )

                peer.send(
                    data = createSubscriptionAckFrame(sequence = 0xC24B),
                    target = subscribePacket.sender
                )
                peer.send(
                    data = createChannelFrame(
                        sequence = 7,
                        channels = MutableList(16) { 1500 }.also {
                            it[2] = 1950
                            it[3] = 1050
                            it[0] = 1050
                        }
                    ),
                    target = subscribePacket.sender
                )

                val snapshot = listener.awaitSnapshot()
                assertEquals(7, snapshot.sequence)
                assertEquals(1f, snapshot.movementIntent.forward, FLOAT_DELTA)
                assertEquals(-1f, snapshot.movementIntent.strafeRight, FLOAT_DELTA)
                assertEquals(-1f, snapshot.movementIntent.yawRight, FLOAT_DELTA)

                val timeoutStatus = listener.awaitStatus(RemoteInputStatus.TIMEOUT, timeoutMs = 1200L)
                assertEquals("UniRC 输入超时", timeoutStatus.message)
            } finally {
                source.stop()
            }
        }
    }

    @Test
    fun stop_doesNotSendStopFrequencyFrame() {
        TestUdpPeer().use { peer ->
            val listener = RecordingRemoteInputListener()
            val source = UniRcUdpInputSource(
                UniRcUdpInputConfig(
                    remoteHost = "127.0.0.1",
                    remotePort = peer.localPort,
                    localPort = 0,
                    subscribeRepeatCount = 1,
                    receiveTimeoutMs = 10,
                    resubscribeIntervalMs = 1000L,
                    normalization = RemoteInputNormalizationConfig(timeoutMs = 500L)
                )
            )

            source.start(listener)
            peer.receivePacket()
            source.stop()

            val stopFrame = UniRcProtocol.createChannelFrequencyFrame(0, UniRcChannelFrequency.STOP).toList()
            val extraPackets = peer.receivePacketsDuring(windowMs = 250L).map { it.data.toList() }

            assertFalse(
                "未验证 freq = 0 影响范围前，停止输入源不得发送关闭频率帧",
                extraPackets.any { it == stopFrame }
            )
        }
    }

    private class RecordingRemoteInputListener : RemoteInputListener {
        private val statuses = LinkedBlockingQueue<StatusEvent>()
        private val snapshots = LinkedBlockingQueue<RemoteInputSnapshot>()

        override fun onStatusChanged(
            descriptor: RemoteInputSourceDescriptor,
            status: RemoteInputStatus,
            message: String
        ) {
            statuses.offer(StatusEvent(status = status, message = message))
        }

        override fun onSnapshot(snapshot: RemoteInputSnapshot) {
            snapshots.offer(snapshot)
        }

        fun awaitStatus(status: RemoteInputStatus, timeoutMs: Long): StatusEvent {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val event = statuses.poll(50, TimeUnit.MILLISECONDS) ?: continue
                if (event.status == status) return event
            }
            throw AssertionError("未收到输入状态: $status")
        }

        fun awaitSnapshot(timeoutMs: Long = 1200L): RemoteInputSnapshot {
            return snapshots.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw AssertionError("未收到输入快照")
        }
    }

    private data class StatusEvent(
        val status: RemoteInputStatus,
        val message: String
    )

    private class TestUdpPeer : AutoCloseable {
        private val socket = DatagramSocket(0).apply {
            soTimeout = 100
        }
        val localPort: Int = socket.localPort

        fun receivePacket(timeoutMs: Int = 1200): ReceivedPacket {
            val originalTimeout = socket.soTimeout
            socket.soTimeout = timeoutMs
            return try {
                val buffer = ByteArray(128)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                ReceivedPacket(
                    data = packet.data.copyOf(packet.length),
                    sender = packet.socketAddress as InetSocketAddress
                )
            } finally {
                socket.soTimeout = originalTimeout
            }
        }

        fun receivePacketsDuring(windowMs: Long): List<ReceivedPacket> {
            val deadline = System.currentTimeMillis() + windowMs
            val packets = mutableListOf<ReceivedPacket>()

            while (System.currentTimeMillis() < deadline) {
                try {
                    packets += receivePacket(timeoutMs = 50)
                } catch (_: SocketTimeoutException) {
                    // 继续等到观察窗口结束。
                }
            }

            return packets
        }

        fun send(data: ByteArray, target: InetSocketAddress) {
            socket.send(DatagramPacket(data, data.size, target))
        }

        override fun close() {
            socket.close()
        }
    }

    private data class ReceivedPacket(
        val data: ByteArray,
        val sender: InetSocketAddress
    )

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

    private fun createSubscriptionAckFrame(sequence: Int): ByteArray {
        val withoutCrc = byteArrayOf(
            0x55,
            0x66,
            0x02,
            0x01,
            0x00,
            (sequence and 0xFF).toByte(),
            ((sequence ushr 8) and 0xFF).toByte(),
            UniRcProtocol.CMD_CHANNELS.toByte(),
            0x01
        )
        val crc = UniRcProtocol.crc16(withoutCrc)
        return withoutCrc + byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
    }

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
