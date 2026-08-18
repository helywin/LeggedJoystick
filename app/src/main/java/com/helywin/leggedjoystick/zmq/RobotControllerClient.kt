package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.mapping.MappingGridAssembler
import com.helywin.leggedjoystick.mapping.MappingGridChunkModel
import com.helywin.leggedjoystick.mapping.MappingGridFrame
import com.helywin.leggedjoystick.mapping.MappingGridMetadataModel
import com.helywin.leggedjoystick.mapping.MappingPose
import com.helywin.leggedjoystick.mapping.MappingClock
import com.helywin.leggedjoystick.proto.ControllerProtocol
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import sar.robot_controller.v1.CommandResponse
import sar.robot_controller.v1.CommandStage
import sar.robot_controller.v1.ControllerMessage
import sar.robot_controller.v1.ErrorCode
import sar.robot_controller.v1.MappingMapChunk
import sar.robot_controller.v1.StateSnapshot
import sar.robot_controller.v1.TaskInfo
import sar.robot_controller.v1.TimeSyncStatus
import sar.robot_controller.v1.TimeSyncState
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class RobotControllerConnectionState(val displayName: String) {
    DISCONNECTED("主控已断开"),
    CONNECTING("主控连接中"),
    CONNECTED("主控已连接"),
    AUTHENTICATION_FAILED("主控鉴权失败"),
    CONNECTION_FAILED("主控连接异常")
}

interface RobotControllerClientListener {
    fun onConnectionState(state: RobotControllerConnectionState, message: String)
    fun onSnapshot(snapshot: StateSnapshot)
    fun onTask(task: TaskInfo)
    fun onCommandResponse(requestId: Long, response: CommandResponse)
    fun onTimeSyncStatus(status: TimeSyncStatus)
    fun onMappingFrame(frame: MappingGridFrame)
    fun onMappingError(reason: String)
}

/**
 * 机器人业务主控 DEALER 客户端。socket 仅由单一 I/O 线程访问。
 */
class RobotControllerClient(
    endpoint: String = DEFAULT_ENDPOINT,
    token: String = DEFAULT_TOKEN,
    private val heartbeatIntervalMs: Long = 1_000L,
    private val serverTimeoutMs: Long = 3_500L,
    private val helloRetryMs: Long = 700L,
    private val requestTimeoutMs: Long = 3_500L
) {
    private enum class PendingKind { COMMAND, MAPPING, SNAPSHOT, TIME_SYNC }

    private data class PendingRequest(val kind: PendingKind, val createdAtMs: Long)

    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val connectionState = AtomicReference(RobotControllerConnectionState.DISCONNECTED)
    private val outbound = LinkedBlockingDeque<ControllerMessage>(MAX_OUTBOUND_MESSAGES)
    private val generation = AtomicLong(0L)
    private val latestCompleteMapSequence = AtomicLong(0L)
    private val mapRequestQueued = AtomicBoolean(false)
    private val mappingRequestId = AtomicLong(0L)
    private val pendingRequests = ConcurrentHashMap<Long, PendingRequest>()
    private val assembler = MappingGridAssembler(nowMs = MappingClock::elapsedRealtimeMs)

    @Volatile
    private var endpoint = endpoint

    @Volatile
    private var token = token

    @Volatile
    private var executor = createIoExecutor()

    @Volatile
    private var ioFuture: Future<*>? = null

    @Volatile
    private var listener: RobotControllerClientListener? = null

    @Volatile
    private var protocol = ControllerProtocol()

    @Volatile
    private var sessionId = ""

    @Volatile
    private var latestStateRevision = 0L

    fun setListener(listener: RobotControllerClientListener?) {
        this.listener = listener
    }

    fun configure(endpoint: String, token: String) {
        require(endpoint.startsWith("tcp://")) { "主控 endpoint 必须使用 tcp://" }
        synchronized(lifecycleLock) {
            this.endpoint = endpoint
            this.token = token
        }
    }

    fun connect() {
        synchronized(lifecycleLock) {
            if (running.get()) return
            if (executor.isShutdown) {
                executor = createIoExecutor()
            }
            resetSession()
            running.set(true)
            val currentGeneration = generation.incrementAndGet()
            updateConnectionState(RobotControllerConnectionState.CONNECTING, "等待主控握手")
            ioFuture = executor.submit { runIoLoop(currentGeneration, endpoint, token) }
        }
    }

    fun disconnect() {
        synchronized(lifecycleLock) {
            running.set(false)
            generation.incrementAndGet()
            ioFuture?.cancel(true)
            ioFuture = null
            executor.shutdownNow()
            executor.awaitTermination(500L, TimeUnit.MILLISECONDS)
            resetSession()
            updateConnectionState(RobotControllerConnectionState.DISCONNECTED, "主动断开")
        }
    }

    fun getConnectionState(): RobotControllerConnectionState = connectionState.get()

    fun startMapping(draftName: String): Long = enqueueCommand {
        startMapping(sessionId, latestStateRevision, draftName)
    }

    fun finishMapping(): Long = enqueueCommand {
        finishMapping(sessionId, latestStateRevision)
    }

    fun saveMap(displayName: String): Long = enqueueCommand {
        saveMap(sessionId, latestStateRevision, displayName)
    }

    fun discardMap(): Long = enqueueCommand {
        discardMap(sessionId, latestStateRevision)
    }

    fun manualTakeover(): Long = enqueueCommand {
        manualTakeover(sessionId, latestStateRevision)
    }

    fun requestLatestMap(): Boolean {
        if (sessionId.isEmpty() || !mapRequestQueued.compareAndSet(false, true)) return false
        val message = protocol.getLatestMappingMap(
            sessionId = sessionId,
            lastCompleteFrameSequence = latestCompleteMapSequence.get()
        )
        mappingRequestId.set(message.request_id)
        if (outbound.offerLast(message)) {
            pendingRequests[message.request_id] = PendingRequest(PendingKind.MAPPING, nowMs())
            return true
        }
        mapRequestQueued.set(false)
        mappingRequestId.set(0L)
        listener?.onMappingError("主控发送队列已满，无法请求最新地图")
        return false
    }

    private fun enqueueCommand(create: ControllerProtocol.() -> ControllerMessage): Long {
        if (connectionState.get() != RobotControllerConnectionState.CONNECTED || sessionId.isEmpty()) {
            return 0L
        }
        val message = protocol.create()
        if (!outbound.offerFirst(message)) {
            Timber.e("[主控ZMQ] 命令发送队列已满，request_id=%d", message.request_id)
            return 0L
        }
        pendingRequests[message.request_id] = PendingRequest(PendingKind.COMMAND, nowMs())
        return message.request_id
    }

    private fun runIoLoop(expectedGeneration: Long, endpoint: String, token: String) {
        val context = ZContext(1)
        val socket = context.createSocket(SocketType.DEALER)
        try {
            socket.identity = protocol.deviceId.toByteArray()
            socket.linger = 0
            socket.receiveTimeOut = 0
            socket.sendTimeOut = 100
            socket.connect(endpoint)
            var lastHelloAt = 0L
            var lastHeartbeatAt = 0L
            var lastServerAt = System.currentTimeMillis()

            while (running.get() && generation.get() == expectedGeneration) {
                val now = System.currentTimeMillis()
                if (sessionId.isEmpty() && now - lastHelloAt >= helloRetryMs) {
                    send(socket, protocol.hello(token))
                    lastHelloAt = now
                }
                if (sessionId.isNotEmpty() && now - lastHeartbeatAt >= heartbeatIntervalMs) {
                    send(socket, protocol.heartbeat(sessionId))
                    lastHeartbeatAt = now
                }

                var sent = 0
                while (sent < MAX_SEND_PER_TICK) {
                    val next = outbound.pollFirst() ?: break
                    send(socket, next)
                    sent += 1
                }

                var received = 0
                while (received < MAX_RECEIVE_PER_TICK) {
                    val bytes = socket.recv(ZMQ.DONTWAIT) ?: break
                    lastServerAt = now
                    handleIncoming(bytes)
                    received += 1
                }

                val expiry = assembler.expireIncomplete()
                if (expiry is MappingGridAssembler.Result.Rejected) {
                    listener?.onMappingError(expiry.reason)
                    requestLatestMap()
                }
                expirePendingRequests(now)
                if (sessionId.isNotEmpty() && now - lastServerAt > serverTimeoutMs) {
                    Timber.w("[主控ZMQ] 会话超时，重新握手")
                    resetSession()
                    updateConnectionState(RobotControllerConnectionState.CONNECTING, "主控会话超时，正在重连")
                    lastHelloAt = 0L
                    lastServerAt = now
                }
                Thread.sleep(IO_IDLE_MS)
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            if (running.get() && generation.get() == expectedGeneration) {
                Timber.e(error, "[主控ZMQ] I/O 循环异常，endpoint=%s", endpoint)
                updateConnectionState(RobotControllerConnectionState.CONNECTION_FAILED, "主控连接异常")
            }
        } finally {
            socket.close()
            context.close()
        }
    }

    private fun send(socket: ZMQ.Socket, message: ControllerMessage) {
        if (!socket.send(message.encode(), 0)) {
            Timber.w("[主控ZMQ] 消息发送失败，request_id=%d", message.request_id)
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        val message = try {
            ControllerMessage.ADAPTER.decode(bytes)
        } catch (error: Exception) {
            Timber.w(error, "[主控ZMQ] 无法解析主控消息")
            return
        }
        if (message.version?.major != 1) {
            Timber.w("[主控ZMQ] 拒绝不兼容协议版本")
            return
        }

        message.hello_response?.let { hello ->
            if (hello.selected_version?.major != 1 || hello.session_id.isEmpty()) {
                updateConnectionState(
                    RobotControllerConnectionState.AUTHENTICATION_FAILED,
                    "主控握手被拒绝或协议不兼容"
                )
                return
            }
            sessionId = hello.session_id
            hello.snapshot?.let(::publishSnapshot)
            updateConnectionState(RobotControllerConnectionState.CONNECTED, "主控会话已建立")
            return
        }

        if (sessionId.isEmpty() && message.command_response != null) {
            updateConnectionState(
                RobotControllerConnectionState.AUTHENTICATION_FAILED,
                message.command_response.error_message.ifEmpty { "主控握手被拒绝" }
            )
            return
        }

        if (sessionId.isEmpty() || message.session_id != sessionId) {
            Timber.w("[主控ZMQ] 丢弃旧会话消息")
            return
        }
        if (message.kind == sar.robot_controller.v1.MessageKind.MESSAGE_KIND_RESPONSE &&
            message.request_id != 0L &&
            message.mapping_map_chunk == null &&
            !pendingRequests.containsKey(message.request_id)
        ) {
            Timber.w("[主控ZMQ] 丢弃未登记或已完成响应: request=%d", message.request_id)
            return
        }
        message.state_snapshot?.let {
            pendingRequests.remove(message.request_id)
            publishSnapshot(it)
        }
        message.state_event?.snapshot?.let(::publishSnapshot)
        message.task_event?.task?.let { listener?.onTask(it) }
        message.command_response?.let { response ->
            pendingRequests.remove(message.request_id)
            if (mappingRequestId.compareAndSet(message.request_id, 0L)) {
                mapRequestQueued.set(false)
                listener?.onMappingError(
                    response.error_message.ifEmpty { "主控暂未提供实时地图" }
                )
            }
            if (response.error_code.value == 3) {
                updateConnectionState(
                    RobotControllerConnectionState.AUTHENTICATION_FAILED,
                    response.error_message
                )
            }
            if (response.error_code == ErrorCode.ERROR_CODE_STATE_CONFLICT) {
                requestSnapshot()
            }
            listener?.onCommandResponse(message.request_id, response)
        }
        message.time_sync_challenge?.let { challenge ->
            val commit = protocol.timeSyncCommit(sessionId, challenge)
            if (outbound.offerFirst(commit)) {
                pendingRequests[commit.request_id] = PendingRequest(PendingKind.TIME_SYNC, nowMs())
            }
        }
        message.time_sync_status?.let {
            pendingRequests.remove(message.request_id)
            listener?.onTimeSyncStatus(it)
        }
        message.mapping_map_chunk?.let { handleMappingChunk(message, it) }
    }

    private fun publishSnapshot(snapshot: StateSnapshot) {
        latestStateRevision = snapshot.state_revision
        listener?.onSnapshot(snapshot)
        val stream = snapshot.mapping_stream
        if (stream?.available == true &&
            stream.latest_frame_sequence > latestCompleteMapSequence.get()
        ) {
            requestLatestMap()
        }
    }

    private fun expirePendingRequests(now: Long) {
        pendingRequests.entries.forEach { entry ->
            if (now - entry.value.createdAtMs <= requestTimeoutMs ||
                !pendingRequests.remove(entry.key, entry.value)
            ) {
                return@forEach
            }
            when (entry.value.kind) {
                PendingKind.COMMAND -> listener?.onCommandResponse(
                    entry.key,
                    CommandResponse(
                        stage = CommandStage.COMMAND_STAGE_REJECTED,
                        error_code = ErrorCode.ERROR_CODE_INTERNAL,
                        error_message = "等待主控命令响应超时",
                        retryable = true,
                        suggested_action = "刷新主控状态后重试"
                    )
                )
                PendingKind.MAPPING -> {
                    mappingRequestId.compareAndSet(entry.key, 0L)
                    mapRequestQueued.set(false)
                    listener?.onMappingError("等待最新实时地图超时")
                    requestLatestMap()
                }
                PendingKind.SNAPSHOT -> updateConnectionState(
                    RobotControllerConnectionState.CONNECTED,
                    "刷新主控状态快照超时"
                )
                PendingKind.TIME_SYNC -> listener?.onTimeSyncStatus(
                    TimeSyncStatus(
                        state = TimeSyncState.TIME_SYNC_STATE_FAILED,
                        error_code = ErrorCode.ERROR_CODE_INTERNAL,
                        message = "等待主控对时结果超时"
                    )
                )
            }
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun requestSnapshot() {
        if (sessionId.isEmpty()) return
        val message = protocol.getSnapshot(sessionId)
        if (outbound.offerFirst(message)) {
            pendingRequests[message.request_id] = PendingRequest(PendingKind.SNAPSHOT, nowMs())
        }
    }

    private fun handleMappingChunk(message: ControllerMessage, chunk: MappingMapChunk) {
        if (message.kind == sar.robot_controller.v1.MessageKind.MESSAGE_KIND_RESPONSE &&
            message.request_id != 0L && !pendingRequests.containsKey(message.request_id)
        ) {
            Timber.w("[主控ZMQ] 丢弃未登记地图响应: request=%d", message.request_id)
            return
        }
        val metadata = chunk.metadata
        if (metadata == null) {
            listener?.onMappingError("实时地图分片缺少元数据")
            requestLatestMap()
            return
        }
        val origin = metadata.origin
        val result = assembler.accept(
            MappingGridChunkModel(
                metadata = MappingGridMetadataModel(
                    frameSequence = metadata.frame_sequence,
                    frameId = metadata.frame_id,
                    sourceTimeNs = metadata.source_time_ns,
                    resolutionM = metadata.resolution_m,
                    widthCells = metadata.width_cells,
                    heightCells = metadata.height_cells,
                    origin = MappingPose(
                        x = origin?.x ?: Double.NaN,
                        y = origin?.y ?: Double.NaN,
                        yaw = origin?.yaw ?: Double.NaN
                    ),
                    encodingValue = metadata.encoding.value,
                    uncompressedSizeBytes = metadata.uncompressed_size_bytes,
                    compressedSizeBytes = metadata.compressed_size_bytes,
                    sha256 = metadata.sha256
                ),
                chunkIndex = chunk.chunk_index,
                chunkCount = chunk.chunk_count,
                data = chunk.data_.toByteArray()
            )
        )
        when (result) {
            is MappingGridAssembler.Result.Complete -> {
                pendingRequests.remove(message.request_id)
                mappingRequestId.compareAndSet(message.request_id, 0L)
                mapRequestQueued.set(false)
                latestCompleteMapSequence.set(result.frame.metadata.frameSequence)
                listener?.onMappingFrame(result.frame)
            }
            is MappingGridAssembler.Result.Rejected -> {
                pendingRequests.remove(message.request_id)
                mappingRequestId.compareAndSet(message.request_id, 0L)
                mapRequestQueued.set(false)
                listener?.onMappingError(result.reason)
                if (result.requestLatest) requestLatestMap()
            }
            MappingGridAssembler.Result.IgnoredOldFrame,
            MappingGridAssembler.Result.Pending -> Unit
        }
    }

    private fun resetSession() {
        sessionId = ""
        latestStateRevision = 0L
        latestCompleteMapSequence.set(0L)
        mapRequestQueued.set(false)
        mappingRequestId.set(0L)
        outbound.clear()
        pendingRequests.clear()
        assembler.reset()
        protocol = ControllerProtocol()
    }

    private fun updateConnectionState(state: RobotControllerConnectionState, message: String) {
        if (connectionState.getAndSet(state) != state) {
            Timber.i("[主控ZMQ] %s：%s", state.displayName, message)
        }
        listener?.onConnectionState(state, message)
    }

    private fun createIoExecutor() = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RobotControllerZmqIo").apply { isDaemon = true }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "tcp://192.168.234.1:33446"
        const val DEFAULT_TOKEN = "change-me-before-deploy"
        const val MAX_OUTBOUND_MESSAGES = 128
        const val MAX_SEND_PER_TICK = 16
        const val MAX_RECEIVE_PER_TICK = 64
        const val IO_IDLE_MS = 10L
    }
}
