/*********************************************************************************
 * FileName: NewZmqClient.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.1.0
 * Date: 2025-09-16
 * Description: 重构后的ZMQ客户端，使用更稳定的线程管理和错误处理机制
 * Others: 修复了可能导致App卡死的线程同步和资源管理问题
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

import legged_driver.*
import com.helywin.leggedjoystick.proto.MessageUtils
import com.helywin.leggedjoystick.data.ConnectionState
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import timber.log.Timber
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import kotlin.getValue

/**
 * 消息回调函数类型
 */
typealias MessageCallback = (LeggedDriverMessage) -> Unit

/**
 * 连接状态回调函数类型
 */
typealias ConnectionStateCallback = (ConnectionState) -> Unit

/**
 * 连接丢失回调函数类型
 */
typealias ConnectionLostCallback = () -> Unit

/**
 * 连接状态回调函数类型
 */
typealias ConnectionStateChangeCallback = (ConnectionState) -> Unit

/**
 * 新的ZMQ客户端实现
 * 使用ExecutorService管理线程池，提供更稳定的连接管理
 */
class NewZmqClient(
    val deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
    var tcpEndpoint: String = DEFAULT_TCP_ENDPOINT,
    val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS
) {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1000L
        private const val SOCKET_RECV_TIMEOUT_MS = 100
        private const val SOCKET_SEND_TIMEOUT_MS = 1000
        private const val MAX_SEND_QUEUE_SIZE = 1000 // 限制发送队列大小
        private const val THREAD_SHUTDOWN_TIMEOUT_MS = 5000L
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val CONNECTION_VERIFY_TIMEOUT_MS = 2000L // 连接验证超时时间
        private const val HEARTBEAT_RESPONSE_TIMEOUT_MS = 2500L // 心跳响应超时时间，超过此时间未收到服务器心跳则判断连接丢失
    }

    // ZMQ相关
    @Volatile
    private var zmqContext: ZContext? = null

    @Volatile
    private var socket: ZMQ.Socket? = null

    // 状态控制
    private val running = AtomicBoolean(false)
    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)

    // 线程池管理
    @Volatile
    private var executorService: ExecutorService? = null

    // 任务Future，用于控制任务的取消
    private val receiveFuture = AtomicReference<Future<*>?>()
    private val sendFuture = AtomicReference<Future<*>?>()
    private val heartbeatFuture = AtomicReference<Future<*>?>()

    // 发送队列 - 使用有界队列防止内存泄漏
    private val sendQueue = LinkedBlockingQueue<ByteArray>(MAX_SEND_QUEUE_SIZE)

    // 统计信息
    private val lastHeartbeatTime = AtomicLong(0)
    private val lastServerHeartbeatTime = AtomicLong(0) // 上次收到服务器心跳的时间
    private val consecutiveFailures = AtomicInteger(0)

    // 客户端信息
    private var deviceId: String = MessageUtils.generateDeviceId(deviceType)

    // 回调
    private var messageCallback: MessageCallback? = null
    private var connectionStateCallback: ConnectionStateCallback? = null

    // 服务器状态缓存
    private val serverConnected = AtomicBoolean(false)
    private val currentMode = AtomicReference(Mode.MODE_AUTO)
    private val currentControlMode = AtomicReference(ControlMode.CONTROL_MODE_STAND_UP)
    private val batteryLevel = AtomicReference(0)

    /**
     * 设置连接端点
     */
    fun setEndpoint(endpoint: String) {
        tcpEndpoint = endpoint
        Timber.d("[NewZmqClient] 连接端点已设置为: $endpoint")
    }

    /**
     * 连接到服务器
     */
    fun connect() {
        val currentState = connectionState.get()
        if (currentState == ConnectionState.CONNECTED) {
            Timber.w("[NewZmqClient] 客户端已经连接")
        }

        if (currentState == ConnectionState.CONNECTING) {
            Timber.w("[NewZmqClient] 正在连接中，请稍候")
        }

        // 设置连接中状态
        updateConnectionState(ConnectionState.CONNECTING)

        Timber.i("[NewZmqClient] 开始连接到服务器: $tcpEndpoint")

        // 确保线程池可用
        ensureExecutorService()

        // 创建ZMQ上下文和socket
        val newContext = ZContext()
        val newSocket = newContext.createSocket(SocketType.DEALER).apply {
            receiveTimeOut = SOCKET_RECV_TIMEOUT_MS
            sendTimeOut = SOCKET_SEND_TIMEOUT_MS
            linger = 0
        }

        // ZMQ的connect是异步的，总是返回true
        newSocket.connect(tcpEndpoint)

        // 更新实例变量
        zmqContext = newContext
        socket = newSocket

        Timber.i("[NewZmqClient] ZMQ socket已建立，开始验证服务器连接...")

        // 重置状态变量
        resetConnectionState()

        // 验证连接
        startConnectionVerification()

        // 启动工作任务但不设置为已连接
        running.set(true)
        startWorkerTasks()


    }

    /**
     * 断开连接
     */
    fun disconnect() {
        val currentState = connectionState.get()
        if (currentState == ConnectionState.DISCONNECTED ||
            currentState == ConnectionState.CONNECTION_FAILED ||
            currentState == ConnectionState.CONNECTION_TIMEOUT
        ) {
            Timber.w("[NewZmqClient] 客户端已经断开连接")
            return
        }

        Timber.i("[NewZmqClient] 正在断开连接...")

        // 停止状态标志
        running.set(false)
        updateConnectionState(ConnectionState.DISCONNECTED)

        // 取消所有任务
        cancelAllTasks()

        // 清理资源
        cleanup()

        Timber.i("[NewZmqClient] 客户端已断开连接")
    }

    /**
     * 更新连接状态并通知回调
     */
    private fun updateConnectionState(newState: ConnectionState) {
        val oldState = connectionState.getAndSet(newState)
        if (oldState != newState) {
            Timber.d("[NewZmqClient] 连接状态变更: $oldState -> $newState")
            connectionStateCallback?.invoke(newState)
        }
    }

    /**
     * 启动异步连接验证
     */
    private fun startConnectionVerification() {
        val executor = executorService ?: return

        executor.submit {
            try {
                Timber.d("[NewZmqClient] 开始异步验证连接，发送测试心跳...")

                // 发送测试心跳
                sendHeartbeat()

                // 等待服务器响应
                val startTime = System.currentTimeMillis()
                var connectionVerified = false

                while (System.currentTimeMillis() - startTime < CONNECTION_VERIFY_TIMEOUT_MS && running.get()) {
                    if (serverConnected.get()) {
                        connectionVerified = true
                        break
                    }
                    Thread.sleep(100)
                }

                if (connectionVerified) {
                    updateConnectionState(ConnectionState.CONNECTED)
                    Timber.i("[NewZmqClient] 服务器连接验证成功")
                } else {
                    Timber.w("[NewZmqClient] 连接验证超时，未收到服务器响应")
                    updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)

                    // 清理资源
                    running.set(false)
                    cancelAllTasks()
                    cleanup()
                }

            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 连接验证过程中发生异常")
                updateConnectionState(ConnectionState.CONNECTION_FAILED)

                // 清理资源
                running.set(false)
                cancelAllTasks()
                cleanup()
            }
        }
    }

    /**
     * 启动带回调的连接验证
     */
    private fun startConnectionVerificationWithCallback(result: CompletableFuture<Boolean>) {
        val executor = executorService ?: return

        executor.submit {
            try {
                Timber.d("[NewZmqClient] 开始验证连接，发送测试心跳...")

                // 发送测试心跳
                sendHeartbeat()

                // 等待服务器响应
                val startTime = System.currentTimeMillis()
                var connectionVerified = false

                while (System.currentTimeMillis() - startTime < CONNECTION_VERIFY_TIMEOUT_MS && running.get()) {
                    if (serverConnected.get()) {
                        connectionVerified = true
                        break
                    }
                    Thread.sleep(100)
                }

                if (connectionVerified) {
                    updateConnectionState(ConnectionState.CONNECTED)
                    Timber.i("[NewZmqClient] 服务器连接验证成功")
                    result.complete(true)
                } else {
                    Timber.w("[NewZmqClient] 连接验证超时，未收到服务器响应")
                    updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)

                    // 清理资源
                    running.set(false)
                    cancelAllTasks()
                    cleanup()
                    result.complete(false)
                }

            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 连接验证过程中发生异常")
                updateConnectionState(ConnectionState.CONNECTION_FAILED)

                // 清理资源
                running.set(false)
                cancelAllTasks()
                cleanup()
                result.complete(false)
            }
        }
    }

    /**
     * 验证连接是否真的建立成功（已弃用，保留用于兼容）
     */
    @Deprecated("使用异步验证替代")
    private fun verifyConnection(): Boolean {
        Timber.d("[NewZmqClient] 开始验证连接，发送测试心跳...")

        try {
            // 发送测试心跳
            sendHeartbeat()

            // 等待服务器响应
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < CONNECTION_VERIFY_TIMEOUT_MS) {
                if (serverConnected.get()) {
                    Timber.d("[NewZmqClient] 连接验证成功，收到服务器响应")
                    return true
                }
                Thread.sleep(100)
            }

            Timber.w("[NewZmqClient] 连接验证超时，未收到服务器响应")
            return false

        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 连接验证过程中发生异常")
            return false
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            socket?.close()
            zmqContext?.close()
        } catch (e: Exception) {
            Timber.w(e, "[NewZmqClient] 清理ZMQ资源时出现异常")
        } finally {
            socket = null
            zmqContext = null
            sendQueue.clear()
        }
    }

    /**
     * 确保线程池可用
     */
    private fun ensureExecutorService() {
        val currentExecutor = executorService
        if (currentExecutor == null || currentExecutor.isShutdown) {
            executorService = Executors.newFixedThreadPool(3) { runnable ->
                Thread(runnable).apply {
                    isDaemon = true
                    name = "ZMQ-Worker-${System.currentTimeMillis()}"
                }
            }
            Timber.d("[NewZmqClient] 创建新的线程池")
        }
    }

    /**
     * 重置连接状态
     */
    private fun resetConnectionState() {
        consecutiveFailures.set(0)
        lastHeartbeatTime.set(0)
        lastServerHeartbeatTime.set(0) // 重置服务器心跳时间
        serverConnected.set(false)
        sendQueue.clear()
    }

    /**
     * 启动工作任务
     */
    private fun startWorkerTasks() {
        try {
            val executor =
                executorService ?: throw IllegalStateException("ExecutorService 未初始化")

            // 启动接收任务
            receiveFuture.set(executor.submit { receiveTask() })

            // 启动发送任务
            sendFuture.set(executor.submit { sendTask() })

            // 启动心跳任务
            heartbeatFuture.set(executor.submit { heartbeatTask() })

            Timber.d("[NewZmqClient] 所有工作任务已启动")
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 启动工作任务失败")
            throw e
        }
    }

    /**
     * 取消所有任务
     */
    private fun cancelAllTasks() {
        val futures = listOf(receiveFuture, sendFuture, heartbeatFuture)

        futures.forEach { futureRef ->
            futureRef.get()?.let { future ->
                if (!future.isDone) {
                    future.cancel(true)
                }
            }
            futureRef.set(null)
        }
    }

    /**
     * 关闭线程池（仅在完全关闭客户端时调用）
     */
    private fun shutdownExecutorService() {
        val executor = executorService ?: return

        try {
            executor.shutdown()
            if (!executor.awaitTermination(THREAD_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Timber.w("[NewZmqClient] 执行器服务未能在规定时间内关闭，强制关闭")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Timber.w("[NewZmqClient] 等待执行器服务关闭时被中断")
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            executorService = null
        }
    }

    /**
     * 接收任务
     */
    private fun receiveTask() {
        Timber.i("[NewZmqClient] 接收任务启动")

        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                processReceiveOnce()

                // 短暂休眠避免CPU占用过高
                Thread.sleep(10)
            }
        } catch (e: InterruptedException) {
            Timber.i("[NewZmqClient] 接收任务被中断")
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 接收任务异常退出")
            handleTaskFailure("接收任务")
        } finally {
            Timber.i("[NewZmqClient] 接收任务结束")
        }
    }

    /**
     * 处理单次接收操作
     */
    private fun processReceiveOnce() {
        try {
            val currentSocket = socket ?: return

            val data = currentSocket.recv(ZMQ.NOBLOCK)
            if (data != null) {
                val message = MessageUtils.deserializeMessage(data)

                if (MessageUtils.verifyMessage(message)) {
                    processReceivedMessage(message)
                    messageCallback?.invoke(message)

                    // 重置失败计数
                    consecutiveFailures.set(0)
                } else {
                    Timber.w("[NewZmqClient] CRC32校验失败")
                }
            }

        } catch (e: ZMQException) {
            if (e.errorCode != ZMQ.Error.EAGAIN.code) {
                handleReceiveError(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 消息处理异常")
            incrementFailureCount()
        }
    }

    /**
     * 发送任务
     */
    private fun sendTask() {
        Timber.i("[NewZmqClient] 发送任务启动")

        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                processSendOnce()
            }
        } catch (e: InterruptedException) {
            Timber.i("[NewZmqClient] 发送任务被中断")
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 发送任务异常退出")
            handleTaskFailure("发送任务")
        } finally {
            Timber.i("[NewZmqClient] 发送任务结束")
        }
    }

    /**
     * 处理单次发送操作
     */
    private fun processSendOnce() {
        try {
            val data = sendQueue.poll(100, TimeUnit.MILLISECONDS) ?: return
            val currentSocket = socket ?: return

            if (currentSocket.send(data, ZMQ.NOBLOCK)) {
                consecutiveFailures.set(0)
            } else {
                // 发送失败，重新入队（如果队列未满）
                if (!sendQueue.offer(data)) {
                    Timber.w("[NewZmqClient] 发送队列已满，丢弃消息")
                }
                incrementFailureCount()
            }

        } catch (e: ZMQException) {
            Timber.e(e, "[NewZmqClient] 发送消息失败")
            incrementFailureCount()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 发送操作异常")
            incrementFailureCount()
        }
    }

    /**
     * 心跳任务
     */
    private fun heartbeatTask() {
        Timber.i("[NewZmqClient] 心跳任务启动")

        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                sendHeartbeat()
                lastHeartbeatTime.set(System.currentTimeMillis())

                // 检查服务器心跳响应超时
                // 只有在已连接状态下才检测超时
                if (connectionState.get() == ConnectionState.CONNECTED) {
                    val lastServerTime = lastServerHeartbeatTime.get()
                    val currentTime = System.currentTimeMillis()

                    // 如果上次收到服务器心跳的时间不为0（已经收到过心跳），且超时
                    if (lastServerTime > 0 && (currentTime - lastServerTime) > HEARTBEAT_RESPONSE_TIMEOUT_MS) {
                        Timber.w("[NewZmqClient] 服务器心跳响应超时，距离上次心跳已超过 ${HEARTBEAT_RESPONSE_TIMEOUT_MS}ms，判断连接丢失")
                        handleConnectionLost()
                        break
                    }
                }

                Thread.sleep(heartbeatIntervalMs)
            }
        } catch (e: InterruptedException) {
            Timber.i("[NewZmqClient] 心跳任务被中断")
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 心跳任务异常退出")
            handleTaskFailure("心跳任务")
        } finally {
            Timber.i("[NewZmqClient] 心跳任务结束")
        }
    }

    /**
     * 处理接收错误
     */
    private fun handleReceiveError(e: ZMQException) {
        Timber.e(e, "[NewZmqClient] 接收错误")
        incrementFailureCount()
    }

    /**
     * 递增失败计数
     */
    private fun incrementFailureCount() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            Timber.e("[NewZmqClient] 连续失败${failures}次，判断连接丢失")
            handleConnectionLost()
        }
    }

    /**
     * 处理任务失败
     */
    private fun handleTaskFailure(taskName: String) {
        Timber.e("[NewZmqClient] $taskName 失败")
        handleConnectionLost()
    }

    /**
     * 处理连接丢失
     */
    private fun handleConnectionLost() {
        if (running.compareAndSet(true, false)) {
            updateConnectionState(ConnectionState.CONNECTION_FAILED)
        }
    }

    /**
     * 处理接收到的消息
     */
    private fun processReceivedMessage(message: LeggedDriverMessage) {
        when (message.message_type) {
            MessageType.MESSAGE_TYPE_HEARTBEAT -> handleHeartbeatMessage(message)
            MessageType.MESSAGE_TYPE_BATTERY_INFO -> handleBatteryInfoMessage(message)
            MessageType.MESSAGE_TYPE_CURRENT_MODE -> handleCurrentModeMessage(message)
            MessageType.MESSAGE_TYPE_CURRENT_CONTROL_MODE -> handleCurrentControlModeMessage(message)
            MessageType.MESSAGE_TYPE_ODOMETRY -> handleOdometryMessage(message)
            else -> {
                // 其他消息类型暂不处理
//                Timber.d("[NewZmqClient] 收到消息类型: ${message.message_type}")
            }
        }
    }

    /**
     * 处理心跳消息
     */
    private fun handleHeartbeatMessage(message: LeggedDriverMessage) {
        message.heartbeat?.let { heartbeat ->
            serverConnected.set(heartbeat.is_connected)
            // 记录收到服务器心跳的时间
            lastServerHeartbeatTime.set(System.currentTimeMillis())
//            Timber.d("[NewZmqClient] 收到服务器心跳，连接状态: ${heartbeat.is_connected}")
        }
    }

    /**
     * 处理电池信息消息
     */
    private fun handleBatteryInfoMessage(message: LeggedDriverMessage) {
        message.battery_info?.let { batteryInfo ->
            batteryLevel.set(batteryInfo.battery_level)
//            Timber.d("[NewZmqClient] 收到电池信息，电量: ${batteryInfo.battery_level}%")
        }
    }

    /**
     * 处理当前模式消息
     */
    private fun handleCurrentModeMessage(message: LeggedDriverMessage) {
        message.current_mode?.let { currentModeMsg ->
            currentMode.set(currentModeMsg.mode)
//            Timber.d("[NewZmqClient] 收到当前模式: ${currentModeMsg.mode}")
        }
    }

    /**
     * 处理当前控制模式消息
     */
    private fun handleCurrentControlModeMessage(message: LeggedDriverMessage) {
        message.current_control_mode?.let { currentControlModeMsg ->
            currentControlMode.set(currentControlModeMsg.control_mode)
//            Timber.d("[NewZmqClient] 收到当前控制模式: ${currentControlModeMsg.control_mode}")
        }
    }

    /**
     * 处理里程计消息
     */
    private fun handleOdometryMessage(message: LeggedDriverMessage) {
        message.odometry?.let { odom ->
//            Timber.d("[NewZmqClient] 收到里程计信息: pos(${odom.position?.x},${odom.position?.y},${odom.position?.z})")
        }
    }

    /**
     * 发送消息
     */
    private fun sendMessage(message: LeggedDriverMessage) {
        if (!running.get()) {
            Timber.w("[NewZmqClient] 客户端未运行，忽略消息发送")
            return
        }

        try {
            val data = MessageUtils.serializeMessage(message)

            // 尝试添加到发送队列
            if (!sendQueue.offer(data)) {
                Timber.w("[NewZmqClient] 发送队列已满，丢弃最旧的消息")
                sendQueue.poll() // 移除最旧的消息
                sendQueue.offer(data) // 重新尝试添加
            }
        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 序列化消息失败")
        }
    }

    /**
     * 获取当前连接状态
     */
    fun getConnectionState(): ConnectionState = connectionState.get()

    /**
     * 服务器是否已连接
     */
    fun isServerConnected(): Boolean = serverConnected.get()

    /**
     * 获取最后心跳时间
     */
    fun getLastHeartbeatTime(): Long = lastHeartbeatTime.get()

    /**
     * 获取连续失败次数
     */
    fun getConsecutiveFailures(): Int = consecutiveFailures.get()

    /**
     * 获取发送队列大小
     */
    fun getSendQueueSize(): Int = sendQueue.size

    /**
     * 设置消息回调
     */
    fun setMessageCallback(callback: MessageCallback?) {
        this.messageCallback = callback
    }

    /**
     * 设置连接状态回调
     */
    fun setConnectionStateCallback(callback: ConnectionStateCallback?) {
        this.connectionStateCallback = callback
    }

    /**
     * 发送心跳
     */
    fun sendHeartbeat() {
        val message = MessageUtils.createHeartbeatMessage(
            deviceType, deviceId, true
        )
        sendMessage(message)
    }

    /**
     * 设置模式（只有遥控器端可调用）
     */
    fun setMode(mode: Mode): Boolean {
        if (deviceType != DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER) {
            Timber.w("[NewZmqClient] 只有遥控器客户端可以设置模式")
            return false
        }

        val message = MessageUtils.createModeSetMessage(deviceType, deviceId, mode)
        sendMessage(message)
        Timber.i("[NewZmqClient] 发送模式设置: $mode")
        return true
    }

    /**
     * 设置控制模式（只有遥控器端可调用）
     */
    fun setControlMode(controlMode: ControlMode): Boolean {
        if (deviceType != DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER) {
            Timber.w("[NewZmqClient] 只有遥控器客户端可以设置控制模式")
            return false
        }

        val message = MessageUtils.createControlModeSetMessage(deviceType, deviceId, controlMode)
        sendMessage(message)
        Timber.i("[NewZmqClient] 发送控制模式设置: $controlMode")
        return true
    }

    /**
     * 发送速度指令
     */
    fun sendVelocityCommand(vx: Float, vy: Float, yawRate: Float) {
        // 应用速度限制
        val filteredVx = applyVxLimit(vx)
        val filteredVy = applyVyLimit(vy)

        val message = MessageUtils.createVelocityCommandMessage(
            deviceType,
            deviceId,
            filteredVx,
            filteredVy,
            yawRate
        )
        sendMessage(message)
        Timber.v("[NewZmqClient] 发送速度指令: vx=$filteredVx, vy=$filteredVy, yawRate=$yawRate")
    }

    /**
     * 应用vx速度限制：绝对值范围0.05-3，小于0.05设置为0
     */
    private fun applyVxLimit(vx: Float): Float {
        val absVx = kotlin.math.abs(vx)
        return when {
            absVx < 0.05f -> 0.0f
            absVx > 3.0f -> if (vx > 0) 3.0f else -3.0f
            else -> vx
        }
    }

    /**
     * 应用vy速度限制：绝对值范围0.1-1，小于0.1设置为0
     */
    private fun applyVyLimit(vy: Float): Float {
        val absVy = kotlin.math.abs(vy)
        return when {
            absVy < 0.1f -> 0.0f
            absVy > 1.0f -> if (vy > 0) 1.0f else -1.0f
            else -> vy
        }
    }

    /**
     * 获取当前模式
     */
    fun getCurrentMode(): Mode = currentMode.get()

    /**
     * 获取当前控制模式
     */
    fun getCurrentControlMode(): ControlMode = currentControlMode.get()

    /**
     * 获取电池电量
     */
    fun getBatteryLevel(): Int = batteryLevel.get()

    /**
     * 清理资源
     */
    fun close() {
        disconnect()

        // 关闭线程池
        shutdownExecutorService()
    }
}
