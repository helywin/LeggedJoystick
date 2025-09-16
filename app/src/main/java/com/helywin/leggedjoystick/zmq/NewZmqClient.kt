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

/**
 * 消息回调函数类型
 */
typealias MessageCallback = (LeggedDriverMessage) -> Unit

/**
 * 连接状态回调函数类型
 */
typealias ConnectionLostCallback = () -> Unit

/**
 * 新的ZMQ客户端实现
 * 使用ExecutorService管理线程池，提供更稳定的连接管理
 */
class NewZmqClient {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1000L
        private const val SOCKET_RECV_TIMEOUT_MS = 100
        private const val SOCKET_SEND_TIMEOUT_MS = 1000
        private const val MAX_SEND_QUEUE_SIZE = 1000 // 限制发送队列大小
        private const val THREAD_SHUTDOWN_TIMEOUT_MS = 5000L
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    // ZMQ相关
    @Volatile
    private var context: ZContext? = null
    @Volatile
    private var socket: ZMQ.Socket? = null
    private var tcpEndpoint = DEFAULT_TCP_ENDPOINT

    // 状态控制
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    
    // 线程池管理
    private val executorService: ExecutorService = Executors.newFixedThreadPool(3) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "ZMQ-Worker-${System.currentTimeMillis()}"
        }
    }
    
    // 任务Future，用于控制任务的取消
    private val receiveFuture = AtomicReference<Future<*>?>()
    private val sendFuture = AtomicReference<Future<*>?>()
    private val heartbeatFuture = AtomicReference<Future<*>?>()

    // 发送队列 - 使用有界队列防止内存泄漏
    private val sendQueue = LinkedBlockingQueue<ByteArray>(MAX_SEND_QUEUE_SIZE)

    // 统计信息
    private val lastHeartbeatTime = AtomicLong(0)
    private val consecutiveFailures = AtomicInteger(0)

    // 客户端信息
    private var deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER
    private var deviceId: String = ""
    private var heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS

    // 回调
    private var messageCallback: MessageCallback? = null
    private var connectionLostCallback: ConnectionLostCallback? = null

    // 服务器状态缓存
    private val serverConnected = AtomicBoolean(false)
    private val currentMode = AtomicReference(Mode.MODE_AUTO)
    private val currentControlMode = AtomicReference(ControlMode.CONTROL_MODE_STAND_UP)
    private val batteryLevel = AtomicReference(0)

    /**
     * 构造函数
     */
    constructor(
        deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
        tcpEndpoint: String = DEFAULT_TCP_ENDPOINT,
        heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS
    ) {
        this.deviceType = deviceType
        this.tcpEndpoint = tcpEndpoint
        this.heartbeatIntervalMs = heartbeatIntervalMs
        this.deviceId = MessageUtils.generateDeviceId(deviceType)
        
        Timber.i("[NewZmqClient] 客户端创建，设备ID: $deviceId, 类型: $deviceType")
    }

    /**
     * 设置连接端点
     */
    fun setEndpoint(endpoint: String) {
        this.tcpEndpoint = endpoint
        Timber.d("[NewZmqClient] 连接端点已设置为: $endpoint")
    }

    /**
     * 连接到服务器
     */
    fun connect(): Boolean {
        if (connected.get()) {
            Timber.w("[NewZmqClient] 客户端已经连接")
            return true
        }

        return try {
            // 创建ZMQ上下文和socket
            val newContext = ZContext()
            val newSocket = newContext.createSocket(SocketType.DEALER).apply {
                receiveTimeOut = SOCKET_RECV_TIMEOUT_MS
                sendTimeOut = SOCKET_SEND_TIMEOUT_MS
                linger = 0
            }

            // 连接到服务器
            if (!newSocket.connect(tcpEndpoint)) {
                Timber.e("[NewZmqClient] 连接失败: $tcpEndpoint")
                newSocket.close()
                newContext.close()
                return false
            }

            // 更新实例变量
            context = newContext
            socket = newSocket
            
            Timber.i("[NewZmqClient] 连接到服务器: $tcpEndpoint")

            // 更新状态并启动工作任务
            connected.set(true)
            running.set(true)
            consecutiveFailures.set(0)

            startWorkerTasks()

            Timber.i("[NewZmqClient] 客户端启动成功")
            true

        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 连接失败")
            cleanup()
            false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        if (!connected.get()) {
            return
        }

        Timber.i("[NewZmqClient] 正在断开连接...")
        
        // 停止状态标志
        running.set(false)
        connected.set(false)

        // 取消所有任务
        cancelAllTasks()

        // 清理资源
        cleanup()

        Timber.i("[NewZmqClient] 客户端已断开连接")
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            socket?.close()
            context?.close()
        } catch (e: Exception) {
            Timber.w(e, "[NewZmqClient] 清理ZMQ资源时出现异常")
        } finally {
            socket = null
            context = null
            sendQueue.clear()
        }
    }

    /**
     * 启动工作任务
     */
    private fun startWorkerTasks() {
        try {
            // 启动接收任务
            receiveFuture.set(executorService.submit { receiveTask() })
            
            // 启动发送任务
            sendFuture.set(executorService.submit { sendTask() })
            
            // 启动心跳任务
            heartbeatFuture.set(executorService.submit { heartbeatTask() })
            
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

        // 关闭执行器服务
        try {
            executorService.shutdown()
            if (!executorService.awaitTermination(THREAD_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Timber.w("[NewZmqClient] 执行器服务未能在规定时间内关闭，强制关闭")
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Timber.w("[NewZmqClient] 等待执行器服务关闭时被中断")
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
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
            connected.set(false)
            
            // 异步通知连接丢失，避免死锁
            try {
                if (!executorService.isShutdown) {
                    executorService.submit {
                        try {
                            connectionLostCallback?.invoke()
                        } catch (e: Exception) {
                            Timber.e(e, "[NewZmqClient] 连接丢失回调异常")
                        }
                    }
                } else {
                    // 如果线程池已关闭，直接在当前线程调用
                    try {
                        connectionLostCallback?.invoke()
                    } catch (e: Exception) {
                        Timber.e(e, "[NewZmqClient] 连接丢失回调异常")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 提交连接丢失回调任务失败")
                // 如果提交失败，直接在当前线程调用
                try {
                    connectionLostCallback?.invoke()
                } catch (callbackException: Exception) {
                    Timber.e(callbackException, "[NewZmqClient] 连接丢失回调异常")
                }
            }
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

    // ========== 公开接口 ==========

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = connected.get()

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
     * 设置连接丢失回调
     */
    fun setConnectionLostCallback(callback: ConnectionLostCallback?) {
        this.connectionLostCallback = callback
    }

    /**
     * 发送心跳
     */
    fun sendHeartbeat() {
        val message = MessageUtils.createHeartbeatMessage(
            deviceType, deviceId, connected.get()
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
        val message = MessageUtils.createVelocityCommandMessage(deviceType, deviceId, vx, vy, yawRate)
        sendMessage(message)
        Timber.v("[NewZmqClient] 发送速度指令: vx=$vx, vy=$vy, yawRate=$yawRate")
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
     * 获取设备类型
     */
    fun getDeviceType(): DeviceType = deviceType

    /**
     * 获取设备ID
     */
    fun getDeviceId(): String = deviceId

    /**
     * 清理资源
     */
    fun close() {
        disconnect()
        
        // 确保ExecutorService被关闭
        if (!executorService.isShutdown) {
            executorService.shutdown()
        }
    }
}