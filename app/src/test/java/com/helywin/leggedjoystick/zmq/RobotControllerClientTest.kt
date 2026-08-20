package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.mapping.MappingGridFrame
import com.helywin.leggedjoystick.mapping.MapPreviewData
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import sar.robot_controller.v1.CommandResponse
import sar.robot_controller.v1.ControllerMessage
import sar.robot_controller.v1.ErrorCode
import sar.robot_controller.v1.HelloResponse
import sar.robot_controller.v1.MappingGridEncoding
import sar.robot_controller.v1.MappingGridMetadata
import sar.robot_controller.v1.MappingMapChunk
import sar.robot_controller.v1.MappingStreamStatus
import sar.robot_controller.v1.MapIdentity
import sar.robot_controller.v1.MapInfo
import sar.robot_controller.v1.MapListResponse
import sar.robot_controller.v1.MapPreviewChunk
import sar.robot_controller.v1.MessageKind
import sar.robot_controller.v1.NavigationPath
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.Pose2D
import sar.robot_controller.v1.PathPreview
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

    @Test
    fun savedMapAndNavigationWorkflowUsesBoundedRequestsAndPathEvents() {
        val port = freePort()
        val server = ControllerRouterServer(
            port = port,
            respondToNavigationRequests = true
        ).start()
        val mapListReceived = CountDownLatch(1)
        val previewReceived = CountDownLatch(1)
        val pathReceived = CountDownLatch(1)
        val navigationPathReceived = CountDownLatch(1)
        var observedMaps = emptyList<MapInfo>()
        var observedPreview: MapPreviewData? = null
        var observedPath: PathPreview? = null
        var observedNavigationPath: NavigationPath? = null
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onMapList(requestId: Long, maps: List<MapInfo>) {
                observedMaps = maps
                mapListReceived.countDown()
            }

            override fun onMapPreview(preview: MapPreviewData) {
                observedPreview = preview
                previewReceived.countDown()
            }

            override fun onPathPreview(preview: PathPreview) {
                observedPath = preview
                pathReceived.countDown()
            }

            override fun onNavigationPath(path: NavigationPath) {
                observedNavigationPath = path
                navigationPathReceived.countDown()
            }
        })

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            assertTrue(client.requestMapList() > 0L)
            assertTrue(mapListReceived.await(2L, TimeUnit.SECONDS))
            assertEquals(0.625, observedMaps.single().origin_yaw_rad, 0.0)

            assertTrue(client.requestMapPreview("map-a", 7L) > 0L)
            assertTrue(previewReceived.await(2L, TimeUnit.SECONDS))
            assertEquals("map-a", observedPreview?.key?.map?.mapId)
            assertTrue(observedPreview!!.bytes.contentEquals("saved-map-preview".toByteArray()))

            assertTrue(client.switchMap("map-a", 7L) > 0L)
            assertTrue(client.setInitialPose("map-a", 7L, 1.0, 2.0, 0.3) > 0L)
            assertTrue(client.previewGoal("map-a", 7L, 3.0, 4.0, 0.5) > 0L)
            assertTrue(client.startNavigation("map-a", 7L, 3.0, 4.0, 0.5) > 0L)
            assertTrue(client.cancelNavigation("task-nav") > 0L)
            assertTrue(client.stopRuntime() > 0L)
            assertTrue(pathReceived.await(2L, TimeUnit.SECONDS))
            assertEquals("plan-task", observedPath?.task_id)
            assertTrue(navigationPathReceived.await(2L, TimeUnit.SECONDS))
            assertEquals("task-nav", observedNavigationPath?.task_id)
            assertEquals(12L, observedNavigationPath?.path_sequence)

            val commands = generateSequence { server.commands.poll(200L, TimeUnit.MILLISECONDS) }
                .take(6)
                .toList()
            assertEquals(6, commands.size)
            assertTrue(commands.any { it.command_request?.switch_map?.map?.revision == 7L })
            assertTrue(commands.any { it.command_request?.set_initial_pose?.pose?.yaw == 0.3 })
            assertTrue(commands.any { it.command_request?.start_navigation?.goal?.pose?.x == 3.0 })
            assertTrue(commands.any { it.command_request?.cancel_navigation?.task_id == "task-nav" })
            assertTrue(commands.any { it.command_request?.stop_runtime != null })
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun repeatedMapListRefreshIsCoalescedAndReportsBoundedTimeout() {
        val port = freePort()
        val server = ControllerRouterServer(port).start()
        val errors = LinkedBlockingQueue<String>()
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            requestTimeoutMs = 120L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onMapNavigationError(reason: String) {
                errors.offer(reason)
            }
        })

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            val first = client.requestMapList()
            val repeated = client.requestMapList()

            assertTrue(first > 0L)
            assertEquals(first, repeated)
            assertTrue(errors.poll(2L, TimeUnit.SECONDS).contains("地图列表超时"))
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun reconnectNeverResendsPreviousNavigationGoal() {
        val port = freePort()
        var server = ControllerRouterServer(
            port = port,
            respondToNavigationRequests = true
        ).start()
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            serverTimeoutMs = 250L,
            helloRetryMs = 50L
        )
        client.setListener(EmptyControllerListener())

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })
            assertTrue(client.startNavigation("map-a", 7L, 3.0, 4.0, 0.5) > 0L)
            assertTrue(server.commands.poll(2L, TimeUnit.SECONDS)?.command_request?.start_navigation != null)

            server.close()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTING })
            server = ControllerRouterServer(
                port = port,
                respondToNavigationRequests = true
            ).start()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })

            assertNull(server.commands.poll(300L, TimeUnit.MILLISECONDS))
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun unsolicitedCompleteMap_satisfiesOutstandingLatestMapRequest() {
        val port = freePort()
        val server = ControllerRouterServer(
            port = port,
            respondToMapRequestAsUnsolicited = true
        ).start()
        val mapReceived = CountDownLatch(1)
        val mappingErrors = LinkedBlockingQueue<String>()
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            requestTimeoutMs = 120L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onMappingFrame(frame: MappingGridFrame) {
                mapReceived.countDown()
            }

            override fun onMappingError(reason: String) {
                mappingErrors.offer(reason)
            }
        })

        try {
            client.connect()
            assertTrue(mapReceived.await(2, TimeUnit.SECONDS))

            // 实时推送已经满足“获取最新地图”，旧补拉请求不得在稍后制造假超时。
            assertNull(mappingErrors.poll(300L, TimeUnit.MILLISECONDS))
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun controllerRestart_discardsOldTransportQueueBeforeFreshHandshake() {
        val port = freePort()
        var server = ControllerRouterServer(port).start()
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            serverTimeoutMs = 250L,
            helloRetryMs = 50L
        )
        client.setListener(EmptyControllerListener())

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })

            server.close()
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTING })
            Thread.sleep(150L)

            server = ControllerRouterServer(port).start()
            assertTrue(server.helloMessages.poll(2L, TimeUnit.SECONDS) != null)
            assertTrue(waitUntil { client.getConnectionState() == RobotControllerConnectionState.CONNECTED })

            // 每个新 transport 只允许一个在途 HELLO，避免主控反复创建并替换 session。
            assertNull(server.helloMessages.poll(150L, TimeUnit.MILLISECONDS))
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun sessionMismatchResponse_recreatesTransportAndEstablishesNewSession() {
        val port = freePort()
        val server = ControllerRouterServer(
            port = port,
            rejectFirstHeartbeatWithSessionMismatch = true
        ).start()
        val connectedTwice = CountDownLatch(2)
        val client = RobotControllerClient(
            endpoint = "tcp://127.0.0.1:$port",
            token = "secret",
            heartbeatIntervalMs = 50L,
            serverTimeoutMs = 500L,
            helloRetryMs = 20L
        )
        client.setListener(object : EmptyControllerListener() {
            override fun onConnectionState(state: RobotControllerConnectionState, message: String) {
                if (state == RobotControllerConnectionState.CONNECTED) {
                    connectedTwice.countDown()
                }
            }
        })

        try {
            client.connect()
            assertTrue(connectedTwice.await(2L, TimeUnit.SECONDS))
            assertTrue(server.helloMessages.size >= 2)
            assertEquals(RobotControllerConnectionState.CONNECTED, client.getConnectionState())
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

    private class ControllerRouterServer(
        private val port: Int,
        private val respondToMapRequestAsUnsolicited: Boolean = false,
        private val rejectFirstHeartbeatWithSessionMismatch: Boolean = false,
        private val respondToNavigationRequests: Boolean = false
    ) : AutoCloseable {
        val helloMessages = LinkedBlockingQueue<ControllerMessage>()
        val mapRequests = LinkedBlockingQueue<ControllerMessage>()
        val commands = LinkedBlockingQueue<ControllerMessage>()
        private val running = AtomicBoolean(false)
        private val ready = CountDownLatch(1)
        private lateinit var thread: Thread
        private var sessionCounter = 0
        private var heartbeatRejected = false

        fun start(): ControllerRouterServer {
            running.set(true)
            thread = Thread(::run, "ControllerRouterServer").apply {
                isDaemon = true
                start()
            }
            assertTrue("测试主控 ROUTER 未按时启动", ready.await(2L, TimeUnit.SECONDS))
            return this
        }

        private fun run() {
            val context = ZContext(1)
            val socket = context.createSocket(SocketType.ROUTER)
            try {
                socket.linger = 0
                socket.receiveTimeOut = 100
                socket.bind("tcp://127.0.0.1:$port")
                ready.countDown()
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
                            socket,
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
                        val responseRequestId = if (respondToMapRequestAsUnsolicited) {
                            0L
                        } else {
                            message.request_id
                        }
                        mapChunks(message.session_id, responseRequestId).forEach {
                            send(socket, identity, it)
                        }
                    }
                    message.list_maps_request != null && respondToNavigationRequests -> {
                        send(socket, identity, mapListResponse(message))
                    }
                    message.get_map_preview_request != null && respondToNavigationRequests -> {
                        mapPreviewChunks(message).reversed().forEach { send(socket, identity, it) }
                    }
                    message.heartbeat != null &&
                        rejectFirstHeartbeatWithSessionMismatch &&
                        !heartbeatRejected -> {
                        heartbeatRejected = true
                        send(
                            socket,
                            identity,
                            ControllerMessage(
                                version = VERSION,
                                kind = MessageKind.MESSAGE_KIND_RESPONSE,
                                session_id = "session-$sessionCounter",
                                device_id = message.device_id,
                                request_id = message.request_id,
                                command_response = CommandResponse(
                                    error_code = ErrorCode.ERROR_CODE_SESSION_MISMATCH,
                                    error_message = "会话已失效",
                                    retryable = true
                                )
                            )
                        )
                    }
                    message.command_request != null -> {
                        commands.offer(message)
                        if (respondToNavigationRequests) {
                            send(
                                socket,
                                identity,
                                ControllerMessage(
                                    version = VERSION,
                                    kind = MessageKind.MESSAGE_KIND_RESPONSE,
                                    session_id = message.session_id,
                                    device_id = message.device_id,
                                    request_id = message.request_id,
                                    command_response = CommandResponse(
                                        stage = sar.robot_controller.v1.CommandStage.COMMAND_STAGE_ACCEPTED,
                                        task_id = if (message.command_request.preview_goal != null) {
                                            "plan-task"
                                        } else {
                                            "task-nav"
                                        },
                                        error_code = ErrorCode.ERROR_CODE_OK
                                    )
                                )
                            )
                            if (message.command_request.preview_goal != null) {
                                send(socket, identity, pathPreviewEvent(message.session_id))
                            } else if (message.command_request.start_navigation != null) {
                                send(socket, identity, navigationPathEvent(message.session_id))
                            }
                        }
                    }
                    }
                }
            } finally {
                socket.close()
                context.close()
                ready.countDown()
            }
        }

        private fun send(socket: ZMQ.Socket, identity: ByteArray, message: ControllerMessage) {
            socket.send(identity, ZMQ.SNDMORE)
            socket.send(message.encode(), 0)
        }

        override fun close() {
            running.set(false)
            thread.join(2_000L)
            check(!thread.isAlive) { "测试主控 ROUTER 未在超时内停止" }
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

        private fun mapListResponse(request: ControllerMessage): ControllerMessage {
            return ControllerMessage(
                version = VERSION,
                kind = MessageKind.MESSAGE_KIND_RESPONSE,
                session_id = request.session_id,
                device_id = request.device_id,
                request_id = request.request_id,
                map_list_response = MapListResponse(
                    maps = listOf(
                        MapInfo(
                            identity = MapIdentity(map_id = "map-a", revision = 7L),
                            display_name = "地图 A",
                            preview_hash = "sha256:${"0".repeat(64)}",
                            resolution_m = 0.05,
                            origin_x_m = -1.0,
                            origin_y_m = -2.0,
                            width_cells = 100,
                            height_cells = 80,
                            origin_yaw_rad = 0.625
                        )
                    )
                )
            )
        }

        private fun mapPreviewChunks(request: ControllerMessage): List<ControllerMessage> {
            val bytes = "saved-map-preview".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val midpoint = bytes.size / 2
            return listOf(bytes.copyOfRange(0, midpoint), bytes.copyOfRange(midpoint, bytes.size))
                .mapIndexed { index, part ->
                    ControllerMessage(
                        version = VERSION,
                        kind = MessageKind.MESSAGE_KIND_RESPONSE,
                        session_id = request.session_id,
                        device_id = request.device_id,
                        request_id = request.request_id,
                        map_preview_chunk = MapPreviewChunk(
                            map = request.get_map_preview_request?.map,
                            chunk_index = index,
                            chunk_count = 2,
                            total_size_bytes = bytes.size.toLong(),
                            sha256 = hash,
                            data_ = part.toByteString()
                        )
                    )
                }
        }

        private fun pathPreviewEvent(session: String): ControllerMessage {
            return ControllerMessage(
                version = VERSION,
                kind = MessageKind.MESSAGE_KIND_EVENT,
                session_id = session,
                device_id = "sar-remote",
                path_preview = PathPreview(
                    task_id = "plan-task",
                    map = MapIdentity(map_id = "map-a", revision = 7L),
                    points = listOf(Pose2D(x = 1.0, y = 2.0), Pose2D(x = 3.0, y = 4.0)),
                    length_m = 3.0
                )
            )
        }

        private fun navigationPathEvent(session: String): ControllerMessage {
            return ControllerMessage(
                version = VERSION,
                kind = MessageKind.MESSAGE_KIND_EVENT,
                session_id = session,
                device_id = "sar-remote",
                navigation_path = NavigationPath(
                    task_id = "task-nav",
                    map = MapIdentity(map_id = "map-a", revision = 7L),
                    path_sequence = 12L,
                    source_time_ns = 123_000L,
                    frame_id = "map",
                    points = listOf(
                        Pose2D(x = 1.0, y = 2.0),
                        Pose2D(x = 3.0, y = 4.0)
                    ),
                    length_m = 3.0,
                    active = true
                )
            )
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
        val VERSION = ProtocolVersion(major = 1, minor = 1)

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
