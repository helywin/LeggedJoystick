package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.proto.MessageUtils
import legged_driver.AppMode
import legged_driver.CommandCode
import legged_driver.DeviceType
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.RobotStateMessage
import legged_driver.SpeedLevel
import legged_driver.SportMode
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

typealias MessageCallback = (LeggedDriverMessage) -> Unit
typealias ConnectionStateCallback = (ConnectionState) -> Unit

/**
 * ZMQ DEALER 客户端。
 *
 * 所有 ZMQ socket 读写都在同一个 I/O 线程内完成，避免跨线程复用 socket 导致偶发连接卡死。
 */
class NewZmqClient(
    private val deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
    var tcpEndpoint: String = DEFAULT_TCP_ENDPOINT,
    private val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS
) {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://192.168.234.1:33445"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1000L
        private const val SOCKET_RECV_TIMEOUT_MS = 0
        private const val SOCKET_SEND_TIMEOUT_MS = 100
        private const val MAX_SEND_QUEUE_SIZE = 256
        private const val MAX_DRAIN_SEND_PER_TICK = 32
        private const val MAX_DRAIN_RECV_PER_TICK = 64
        private const val IO_IDLE_SLEEP_MS = 10L
        private const val CONNECTION_VERIFY_TIMEOUT_MS = 2500L
        private const val SERVER_MESSAGE_TIMEOUT_MS = 3000L
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_MS = 1200L
    }

    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    private val sendQueue = LinkedBlockingQueue<ByteArray>(MAX_SEND_QUEUE_SIZE)
    private val connectionAttemptId = AtomicLong(0)

    @Volatile
    private var ioExecutor: ExecutorService? = null

    @Volatile
    private var ioFuture: Future<*>? = null

    private val deviceId = MessageUtils.generateDeviceId(deviceType)
    private val lastHeartbeatTime = AtomicLong(0)
    private val lastServerMessageTime = AtomicLong(0)
    private val consecutiveFailures = AtomicInteger(0)
    private val serverConnected = AtomicBoolean(false)
    private val currentAppMode = AtomicReference(AppMode.APP_MODE_AUTO)
    private val currentSportMode = AtomicReference(SportMode.SPORT_MODE_GENERAL)
    private val batteryLevel = AtomicReference(0)

    private var messageCallback: MessageCallback? = null
    private var connectionStateCallback: ConnectionStateCallback? = null

    fun setEndpoint(endpoint: String) {
        synchronized(lifecycleLock) {
            tcpEndpoint = endpoint
        }
        Timber.i("[ZMQ] 连接端点已设置为: %s", endpoint)
    }

    fun connect() {
        synchronized(lifecycleLock) {
            when (connectionState.get()) {
                ConnectionState.CONNECTING -> {
                    Timber.w("[ZMQ] 正在连接中，忽略重复连接点击")
                    return
                }
                ConnectionState.CONNECTED -> {
                    Timber.w("[ZMQ] 已连接，忽略重复连接点击")
                    return
                }
                else -> Unit
            }

            stopCurrentAttemptLocked()
            resetRuntimeState()

            val attemptId = connectionAttemptId.incrementAndGet()
            val endpointSnapshot = tcpEndpoint
            running.set(true)
            updateConnectionState(ConnectionState.CONNECTING)

            ioExecutor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "LeggedZmqIo-${System.currentTimeMillis()}").apply {
                    isDaemon = true
                }
            }
            ioFuture = ioExecutor?.submit { runIoLoop(endpointSnapshot, attemptId) }
            Timber.i("[ZMQ] 开始新的连接尝试: %s", endpointSnapshot)
        }
    }

    fun disconnect() {
        synchronized(lifecycleLock) {
            stopCurrentAttemptLocked()
            updateConnectionState(ConnectionState.DISCONNECTED)
            Timber.i("[ZMQ] 已断开连接")
        }
    }

    fun close() {
        disconnect()
    }

    fun getConnectionState(): ConnectionState = connectionState.get()

    fun isServerConnected(): Boolean = serverConnected.get()

    fun getLastHeartbeatTime(): Long = lastHeartbeatTime.get()

    fun getConsecutiveFailures(): Int = consecutiveFailures.get()

    fun getSendQueueSize(): Int = sendQueue.size

    fun getCurrentMode(): AppMode = currentAppMode.get()

    fun getCurrentControlMode(): SportMode = currentSportMode.get()

    fun getBatteryLevel(): Int = batteryLevel.get()

    fun setMessageCallback(callback: MessageCallback?) {
        messageCallback = callback
    }

    fun setConnectionStateCallback(callback: ConnectionStateCallback?) {
        connectionStateCallback = callback
    }

    fun sendHeartbeat() {
        enqueueMessage(
            MessageUtils.createHeartbeatMessage(
                deviceType = deviceType,
                deviceId = deviceId
            )
        )
    }

    fun subscribeDefaultTopics() {
        enqueueMessage(
            MessageUtils.createSubscriptionRequestMessage(
                deviceType = deviceType,
                deviceId = deviceId
            )
        )
    }

    fun setMode(mode: AppMode): Boolean {
        return enqueueMessage(
            MessageUtils.createSetAppModeCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                mode = mode
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置 AppMode: %s", mode)
        }
    }

    fun setControlMode(sportMode: SportMode): Boolean {
        return enqueueMessage(
            MessageUtils.createSetSportModeCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                mode = sportMode
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置 SportMode: %s", sportMode)
        }
    }

    fun setSpeedLevel(speedLevel: SpeedLevel): Boolean {
        return enqueueMessage(
            MessageUtils.createSetSpeedLevelCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                speedLevel = speedLevel
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置速度档位: %s", speedLevel)
        }
    }

    fun takeControl(): Boolean {
        return enqueueMessage(
            MessageUtils.createTakeControlCommand(
                deviceType = deviceType,
                deviceId = deviceId
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求接管控制权")
        }
    }

    fun releaseControl(): Boolean {
        return enqueueMessage(
            MessageUtils.createReleaseControlCommand(
                deviceType = deviceType,
                deviceId = deviceId
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求释放控制权")
        }
    }

    fun sendSimpleCommand(commandCode: CommandCode): Boolean {
        return enqueueMessage(
            MessageUtils.createSimpleCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                commandCode = commandCode
            )
        )
    }

    fun setFrontLight(on: Boolean): Boolean {
        return enqueueMessage(
            MessageUtils.createFrontLightCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                on = on
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置前补光灯: %s", on)
        }
    }

    fun setBackLight(on: Boolean): Boolean {
        return enqueueMessage(
            MessageUtils.createBackLightCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                on = on
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置后补光灯: %s", on)
        }
    }

    fun setAutoModeLight(on: Boolean): Boolean {
        return enqueueMessage(
            MessageUtils.createAutoModeLightCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                on = on
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置自动补光: %s", on)
        }
    }

    fun controlHead(leftRight: Float, upDown: Float): Boolean {
        return enqueueMessage(
            MessageUtils.createControlHeadCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                leftRight = leftRight,
                upDown = upDown
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求控制头部: leftRight=%s, upDown=%s", leftRight, upDown)
        }
    }

    fun setHighLowStance(stance: Int): Boolean {
        return enqueueMessage(
            MessageUtils.createHighLowStanceCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                stance = stance
            )
        ).also {
            if (it) Timber.i("[ZMQ] 请求设置高低站姿: %s", stance)
        }
    }

    fun sendMoveCommand(leftRight: Float, forwardBack: Float, yaw: Float) {
        enqueueMessage(
            MessageUtils.createMoveCommand(
                deviceType = deviceType,
                deviceId = deviceId,
                leftRight = leftRight,
                forwardBack = forwardBack,
                yaw = yaw
            )
        )
    }

    fun sendOperatorMoveCommand(strafeRight: Float, forward: Float, yawRight: Float) {
        enqueueMessage(
            MessageUtils.createMoveCommandFromOperatorIntent(
                deviceType = deviceType,
                deviceId = deviceId,
                strafeRight = strafeRight,
                forward = forward,
                yawRight = yawRight
            )
        )
    }

    private fun runIoLoop(endpoint: String, attemptId: Long) {
        var context: ZContext? = null
        var socket: ZMQ.Socket? = null
        val attemptStartedAt = System.currentTimeMillis()

        try {
            context = ZContext()
            socket = context.createSocket(SocketType.DEALER).apply {
                receiveTimeOut = SOCKET_RECV_TIMEOUT_MS
                sendTimeOut = SOCKET_SEND_TIMEOUT_MS
                linger = 0
                identity = deviceId.toByteArray(Charsets.UTF_8)
                connect(endpoint)
            }
            Timber.i("[ZMQ] I/O 线程已创建 socket: %s", endpoint)

            sendDirect(socket, MessageUtils.createHeartbeatMessage(deviceType, deviceId))
            sendDirect(socket, MessageUtils.createSubscriptionRequestMessage(deviceType, deviceId))
            lastHeartbeatTime.set(System.currentTimeMillis())

            while (running.get() && isCurrentAttempt(attemptId) && !Thread.currentThread().isInterrupted) {
                val now = System.currentTimeMillis()

                drainSendQueue(socket, attemptId)
                drainReceive(socket, attemptId)
                sendHeartbeatIfNeeded(socket, now)
                checkConnectionTimeouts(now, attemptStartedAt, attemptId)

                Thread.sleep(IO_IDLE_SLEEP_MS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Timber.i("[ZMQ] I/O 线程被中断")
        } catch (e: Exception) {
            Timber.e(e, "[ZMQ] I/O 线程异常退出")
            if (running.get() && isCurrentAttempt(attemptId)) {
                updateConnectionState(ConnectionState.CONNECTION_FAILED)
            }
        } finally {
            if (isCurrentAttempt(attemptId)) {
                running.set(false)
                sendQueue.clear()
            }
            closeSocketAndContext(socket, context)
            Timber.i("[ZMQ] I/O 线程结束并已释放 ZMQ 资源")
        }
    }

    private fun drainSendQueue(socket: ZMQ.Socket, attemptId: Long) {
        repeat(MAX_DRAIN_SEND_PER_TICK) {
            val data = sendQueue.poll() ?: return
            if (!sendRaw(socket, data)) {
                Timber.w("[ZMQ] 发送失败，丢弃当前消息")
                incrementFailureCount(attemptId)
                return
            }
        }
    }

    private fun drainReceive(socket: ZMQ.Socket, attemptId: Long) {
        repeat(MAX_DRAIN_RECV_PER_TICK) {
            val data = try {
                socket.recv(ZMQ.NOBLOCK)
            } catch (e: ZMQException) {
                if (e.errorCode != ZMQ.Error.EAGAIN.code) {
                    Timber.w(e, "[ZMQ] 接收消息失败")
                    incrementFailureCount(attemptId)
                }
                null
            } ?: return

            processIncomingData(data, attemptId)
        }
    }

    private fun sendHeartbeatIfNeeded(socket: ZMQ.Socket, now: Long) {
        if (now - lastHeartbeatTime.get() < heartbeatIntervalMs) return

        sendDirect(socket, MessageUtils.createHeartbeatMessage(deviceType, deviceId))
        lastHeartbeatTime.set(now)
    }

    private fun checkConnectionTimeouts(now: Long, attemptStartedAt: Long, attemptId: Long) {
        if (!isCurrentAttempt(attemptId)) return

        val state = connectionState.get()
        val lastServerTime = lastServerMessageTime.get()

        if (state == ConnectionState.CONNECTING && now - attemptStartedAt > CONNECTION_VERIFY_TIMEOUT_MS) {
            Timber.w("[ZMQ] 连接验证超时，准备释放本次连接资源")
            updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)
            running.set(false)
            return
        }

        if (state == ConnectionState.CONNECTED &&
            lastServerTime > 0 &&
            now - lastServerTime > SERVER_MESSAGE_TIMEOUT_MS
        ) {
            Timber.w("[ZMQ] 服务器消息超时，准备释放本次连接资源")
            updateConnectionState(ConnectionState.CONNECTION_FAILED)
            running.set(false)
        }
    }

    private fun processIncomingData(data: ByteArray, attemptId: Long) {
        val message = try {
            MessageUtils.deserializeMessage(data)
        } catch (e: Exception) {
            Timber.w(e, "[ZMQ] 协议反序列化失败")
            incrementFailureCount(attemptId)
            return
        }

        if (!MessageUtils.verifyMessage(message)) {
            incrementFailureCount(attemptId)
            return
        }

        if (!isCurrentAttempt(attemptId)) {
            return
        }

        consecutiveFailures.set(0)
        lastServerMessageTime.set(System.currentTimeMillis())
        processReceivedMessage(message)
        messageCallback?.invoke(message)

        if (connectionState.get() == ConnectionState.CONNECTING) {
            serverConnected.set(true)
            updateConnectionState(ConnectionState.CONNECTED)
            Timber.i("[ZMQ] 已收到服务端有效消息，连接验证成功")
        }
    }

    private fun processReceivedMessage(message: LeggedDriverMessage) {
        when (message.message_type) {
            MessageType.MESSAGE_TYPE_HEARTBEAT -> {
                message.heartbeat?.let { heartbeat ->
                    serverConnected.set(heartbeat.robot_connected)
                    currentAppMode.set(heartbeat.app_mode)
                }
            }
            MessageType.MESSAGE_TYPE_CONNECTION_STATE -> {
                message.connection_state?.let { state ->
                    serverConnected.set(state.robot_connected)
                }
            }
            MessageType.MESSAGE_TYPE_APP_MODE_STATE -> {
                message.app_mode_state?.app_mode?.let(currentAppMode::set)
            }
            MessageType.MESSAGE_TYPE_ROBOT_STATE -> {
                message.robot_state?.let { robotState ->
                    currentSportMode.set(robotState.sport_mode)
                    batteryLevel.set(robotState.toBatteryPercent())
                }
            }
            MessageType.MESSAGE_TYPE_CONTROL_LOST -> {
                serverConnected.set(false)
                Timber.w("[ZMQ] 收到控制权丢失通知")
            }
            MessageType.MESSAGE_TYPE_CONTROL_AVAILABLE -> {
                Timber.i("[ZMQ] 收到控制权可用通知")
            }
            else -> Unit
        }
    }

    private fun enqueueMessage(message: LeggedDriverMessage): Boolean {
        if (!running.get()) {
            Timber.w("[ZMQ] 当前没有运行中的连接，忽略消息: %s", message.message_type)
            return false
        }

        val data = try {
            MessageUtils.serializeMessage(message)
        } catch (e: Exception) {
            Timber.e(e, "[ZMQ] 消息序列化失败")
            return false
        }

        if (sendQueue.offer(data)) {
            return true
        }

        sendQueue.poll()
        val accepted = sendQueue.offer(data)
        if (!accepted) {
            Timber.w("[ZMQ] 发送队列已满，丢弃消息: %s", message.message_type)
        }
        return accepted
    }

    private fun sendDirect(socket: ZMQ.Socket, message: LeggedDriverMessage): Boolean {
        return sendRaw(socket, MessageUtils.serializeMessage(message))
    }

    private fun sendRaw(socket: ZMQ.Socket, data: ByteArray): Boolean {
        return try {
            socket.send(data, ZMQ.NOBLOCK)
        } catch (e: ZMQException) {
            Timber.w(e, "[ZMQ] socket 发送异常")
            false
        }
    }

    private fun incrementFailureCount(attemptId: Long? = null) {
        if (attemptId != null && !isCurrentAttempt(attemptId)) return

        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= 3) {
            Timber.w("[ZMQ] 连续失败 %d 次，释放当前连接资源", failures)
            updateConnectionState(ConnectionState.CONNECTION_FAILED)
            running.set(false)
        }
    }

    private fun resetRuntimeState() {
        sendQueue.clear()
        consecutiveFailures.set(0)
        lastHeartbeatTime.set(0)
        lastServerMessageTime.set(0)
        serverConnected.set(false)
    }

    private fun stopCurrentAttemptLocked() {
        connectionAttemptId.incrementAndGet()
        running.set(false)
        ioFuture?.cancel(true)
        ioFuture = null

        ioExecutor?.shutdownNow()
        try {
            ioExecutor?.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            ioExecutor = null
        }

        sendQueue.clear()
    }

    private fun closeSocketAndContext(socket: ZMQ.Socket?, context: ZContext?) {
        try {
            socket?.close()
        } catch (e: Exception) {
            Timber.w(e, "[ZMQ] 关闭 socket 失败")
        }

        try {
            context?.close()
        } catch (e: Exception) {
            Timber.w(e, "[ZMQ] 关闭 context 失败")
        }
    }

    private fun updateConnectionState(newState: ConnectionState) {
        val oldState = connectionState.getAndSet(newState)
        if (oldState == newState) return

        Timber.i("[ZMQ] 连接状态变更: %s -> %s", oldState, newState)
        connectionStateCallback?.invoke(newState)
    }

    private fun isCurrentAttempt(attemptId: Long): Boolean {
        return connectionAttemptId.get() == attemptId
    }

    private fun RobotStateMessage.toBatteryPercent(): Int {
        val batteryData = battery ?: return 0
        val values = buildList {
            if (batteryData.present1) add(batteryData.power1)
            if (batteryData.present2) add(batteryData.power2)
        }

        if (values.isEmpty()) return 0
        return values.average().roundToInt().coerceIn(0, 100)
    }
}
