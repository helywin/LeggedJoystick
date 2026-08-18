package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.mapping.MappingGridFrame
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import sar.robot_controller.v1.CommandResponse
import sar.robot_controller.v1.ControllerMessage
import sar.robot_controller.v1.HelloResponse
import sar.robot_controller.v1.MappingGridEncoding
import sar.robot_controller.v1.MappingGridMetadata
import sar.robot_controller.v1.MappingMapChunk
import sar.robot_controller.v1.MappingStreamStatus
import sar.robot_controller.v1.MessageKind
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.Pose2D
import sar.robot_controller.v1.ProtocolVersion
import sar.robot_controller.v1.StateSnapshot
import sar.robot_controller.v1.TaskInfo
import sar.robot_controller.v1.TimeSyncStatus
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater

class RobotControllerClientTest {
    @Test
    fun connect_authenticatesReceivesSnapshotAndReassemblesMap() {
        val port = freePort()
        val server = ControllerRouterServer(port).start()
        val connected = CountDownLatch(1)
        val mapReceived = CountDownLatch(1)
        var observedSnapshot: StateSnapshot? = null
        var observedFrame: MappingGridFrame? = null
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 100L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onConnectionState(state: RobotControllerConnectionState, message: String) {
                if (state == RobotControllerConnectionState.CONNECTED) connected.countDown()
            }

            override fun onSnapshot(snapshot: StateSnapshot) {
                observedSnapshot = snapshot
            }

            override fun onMappingFrame(frame: MappingGridFrame) {
                observedFrame = frame
                mapReceived.countDown()
            }
        })

        try {
            client.connect()
            assertTrue(connected.await(3, TimeUnit.SECONDS))
            assertTrue(mapReceived.await(3, TimeUnit.SECONDS))

            assertEquals(9L, observedSnapshot?.state_revision)
            assertEquals(3L, observedFrame?.metadata?.frameSequence)
            assertEquals(listOf(-1, 0, 25, 100), observedFrame?.cells?.map(Byte::toInt))
            assertEquals("secret", server.helloMessages.poll(1, TimeUnit.SECONDS)?.hello_request?.pre_shared_token)
            assertTrue(server.mapRequests.poll(1, TimeUnit.SECONDS)?.get_mapping_map_request != null)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun command_usesAuthoritativeRevisionAndReconnectCreatesFreshSession() {
        val port = freePort()
        val server = ControllerRouterServer(port).start()
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 100L
        )
        client.setListener(EmptyControllerListener())

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            val requestId = client.startMapping("floor-a")
            val command = server.commands.poll(2, TimeUnit.SECONDS)
            assertTrue(requestId > 0L)
            assertEquals(9L, command?.command_request?.expected_state_revision)
            assertEquals("floor-a", command?.command_request?.start_mapping?.draft_name)
            val firstSession = command?.session_id

            client.disconnect()
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            assertTrue(client.finishMapping() > 0L)
            val secondCommand = server.commands.poll(2, TimeUnit.SECONDS)
            assertNotEquals(firstSession, secondCommand?.session_id)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun commandTimeout_reportsRejectedResultForMatchingRequest() {
        val port = freePort()
        val server = ControllerRouterServer(port).start()
        val timedOut = CountDownLatch(1)
        var timedOutRequestId = 0L
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            requestTimeoutMs = 120L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onCommandResponse(requestId: Long, response: CommandResponse) {
                if (response.error_message.contains("超时")) {
                    timedOutRequestId = requestId
                    timedOut.countDown()
                }
            }
        })

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            val requestId = client.startMapping("timeout-map")

            assertTrue(timedOut.await(2, TimeUnit.SECONDS))
            assertEquals(requestId, timedOutRequestId)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    private open class EmptyControllerListener : RobotControllerClientListener {
        override fun onConnectionState(state: RobotControllerConnectionState, message: String) = Unit
        override fun onSnapshot(snapshot: StateSnapshot) = Unit
        override fun onTask(task: TaskInfo) = Unit
        override fun onCommandResponse(requestId: Long, response: CommandResponse) = Unit
        override fun onTimeSyncStatus(status: TimeSyncStatus) = Unit
        override fun onMappingFrame(frame: MappingGridFrame) = Unit
        override fun onMappingError(reason: String) = Unit
    }

    private class ControllerRouterServer(private val port: Int) : AutoCloseable {
        val helloMessages = LinkedBlockingQueue<ControllerMessage>()
        val mapRequests = LinkedBlockingQueue<ControllerMessage>()
        val commands = LinkedBlockingQueue<ControllerMessage>()
        private val running = AtomicBoolean(false)
        private val context = ZContext(1)
        private val socket = context.createSocket(SocketType.ROUTER)
        private lateinit var thread: Thread
        private var sessionCounter = 0

        fun start(): ControllerRouterServer {
            socket.linger = 0
            socket.receiveTimeOut = 100
            socket.bind("tcp://127.0.0.1:$port")
            running.set(true)
            thread = Thread(::run, "ControllerRouterServer").apply {
                isDaemon = true
                start()
            }
            return this
        }

        private fun run() {
            while (running.get()) {
                val identity = socket.recv(0) ?: continue
                val payload = socket.recv(0) ?: continue
                val message = ControllerMessage.ADAPTER.decode(payload)
                when {
                    message.hello_request != null -> {
                        helloMessages.offer(message)
                        sessionCounter += 1
                        val session = "session-$sessionCounter"
                        send(
                            identity,
                            ControllerMessage(
                                version = VERSION,
                                kind = MessageKind.MESSAGE_KIND_RESPONSE,
                                session_id = session,
                                device_id = message.device_id,
                                request_id = message.request_id,
                                hello_response = HelloResponse(
                                    selected_version = VERSION,
                                    session_id = session,
                                    snapshot = snapshot()
                                )
                            )
                        )
                    }
                    message.get_mapping_map_request != null -> {
                        mapRequests.offer(message)
                        mapChunks(message.session_id, message.request_id).forEach { send(identity, it) }
                    }
                    message.command_request != null -> commands.offer(message)
                }
            }
        }

        private fun send(identity: ByteArray, message: ControllerMessage) {
            socket.send(identity, ZMQ.SNDMORE)
            socket.send(message.encode(), 0)
        }

        override fun close() {
            running.set(false)
            socket.close()
            context.close()
            thread.join(1_000L)
        }

        private fun snapshot() = StateSnapshot(
            state_revision = 9L,
            operation_mode = OperationMode.OPERATION_MODE_MAPPING_RUNNING,
            mapping_stream = MappingStreamStatus(
                available = true,
                latest_frame_sequence = 3L,
                latest_source_time_ns = 123L
            )
        )

        private fun mapChunks(session: String, requestId: Long): List<ControllerMessage> {
            val cells = byteArrayOf(-1, 0, 25, 100)
            val compressed = deflate(cells)
            val metadata = MappingGridMetadata(
                frame_sequence = 3L,
                frame_id = "map",
                source_time_ns = 123L,
                resolution_m = 0.05,
                width_cells = 2,
                height_cells = 2,
                origin = Pose2D(),
                encoding = MappingGridEncoding.MAPPING_GRID_ENCODING_ZLIB_INT8,
                uncompressed_size_bytes = cells.size.toLong(),
                compressed_size_bytes = compressed.size.toLong(),
                sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(compressed)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            )
            val midpoint = compressed.size / 2
            return listOf(compressed.copyOfRange(0, midpoint), compressed.copyOfRange(midpoint, compressed.size))
                .mapIndexed { index, bytes ->
                    ControllerMessage(
                        version = VERSION,
                        kind = MessageKind.MESSAGE_KIND_RESPONSE,
                        session_id = session,
                        device_id = "sar-remote",
                        request_id = requestId,
                        mapping_map_chunk = MappingMapChunk(
                            metadata = metadata,
                            chunk_index = index,
                            chunk_count = 2,
                            data_ = bytes.toByteString()
                        )
                    )
                }
        }

        private fun deflate(data: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_SPEED)
            return try {
                deflater.setInput(data)
                deflater.finish()
                ByteArray(data.size + 64).let { output -> output.copyOf(deflater.deflate(output)) }
            } finally {
                deflater.end()
            }
        }
    }

    private companion object {
        val VERSION = ProtocolVersion(major = 1, minor = 0)

        fun freePort(): Int = ServerSocket(0).use { it.localPort }

        fun waitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (predicate()) return true
                Thread.sleep(10L)
            }
            return predicate()
        }
    }
}
