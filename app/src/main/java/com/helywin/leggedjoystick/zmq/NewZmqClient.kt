/*********************************************************************************
 * FileName: NewZmqClient.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-16
 * Description: 新的ZMQ客户端，使用异步发送和接收线程，不使用请求应答模式
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.proto.*
import kotlinx.coroutines.*
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * 消息回调函数类型
 */
typealias MessageCallback = (LeggedDriverMessage) -> Unit

/**
 * 新的ZMQ客户端实现
 * 参考C++版本，使用异步发送和接收线程
 */
class NewZmqClient : CoroutineScope {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1000L // 1Hz心跳频率
        private const val SOCKET_RECV_TIMEOUT_MS = 100 // 接收超时100ms
        private const val SOCKET_SEND_TIMEOUT_MS = 1000 // 发送超时1秒
    }

    // 协程相关
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // ZMQ相关
    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var tcpEndpoint = DEFAULT_TCP_ENDPOINT

    // 状态控制
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    
    // 线程Job
    private var receiveJob: Job? = null
    private var sendJob: Job? = null
    private var heartbeatJob: Job? = null

    // 发送队列
    private val sendQueue = ConcurrentLinkedQueue<ByteArray>()

    // 客户端信息
    private var deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER
    private var deviceId: String = ""
    private var heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS

    // 消息回调
    private var messageCallback: MessageCallback? = null

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
     * 连接到服务器
     */
    fun connect(): Boolean {
        if (connected.get()) {
            Timber.w("[NewZmqClient] 客户端已经连接")
            return true
        }

        try {
            // 创建ZMQ上下文和socket
            context = ZContext()
            socket = context!!.createSocket(SocketType.DEALER)
            
            // 设置socket选项
            socket?.apply {
                receiveTimeOut = SOCKET_RECV_TIMEOUT_MS
                sendTimeOut = SOCKET_SEND_TIMEOUT_MS
                linger = 0 // 立即关闭socket
            }

            // 连接到服务器
            val success = socket?.connect(tcpEndpoint) ?: false
            if (!success) {
                Timber.e("[NewZmqClient] 连接失败: $tcpEndpoint")
                cleanup()
                return false
            }

            Timber.i("[NewZmqClient] 连接到服务器: $tcpEndpoint")

            connected.set(true)
            running.set(true)

            // 启动协程
            startCoroutines()

            Timber.i("[NewZmqClient] 客户端启动成功")
            return true

        } catch (e: Exception) {
            Timber.e(e, "[NewZmqClient] 连接失败")
            cleanup()
            return false
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
        
        running.set(false)
        connected.set(false)

        // 取消所有协程
        receiveJob?.cancel()
        sendJob?.cancel()
        heartbeatJob?.cancel()

        // 等待协程结束
        runBlocking {
            receiveJob?.join()
            sendJob?.join()
            heartbeatJob?.join()
        }

        cleanup()
        Timber.i("[NewZmqClient] 客户端已断开连接")
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        socket?.close()
        context?.close()
        socket = null
        context = null
        sendQueue.clear()
    }

    /**
     * 启动协程
     */
    private fun startCoroutines() {
        receiveJob = launch { receiveLoop() }
        sendJob = launch { sendLoop() }
        heartbeatJob = launch { heartbeatLoop() }
    }

    /**
     * 接收循环协程
     */
    private suspend fun receiveLoop() {
        Timber.i("[NewZmqClient] 接收协程启动")

        while (running.get() && isActive) {
            try {
                val socket = this@NewZmqClient.socket ?: break
                
                val data = socket.recv(ZMQ.NOBLOCK)
                if (data != null) {
                    // 解析ProtoBuf消息
                    try {
                        val message = MessageUtils.deserializeMessage(data)
                        
                        // 验证CRC32
                        if (!MessageUtils.verifyMessage(message)) {
                            Timber.e("[NewZmqClient] CRC32校验失败")
                            continue
                        }

                        // 处理消息
                        processReceivedMessage(message)

                        // 调用用户回调
                        messageCallback?.invoke(message)
                        
                    } catch (e: Exception) {
                        Timber.e(e, "[NewZmqClient] 消息解析失败")
                    }
                } else {
                    // 没有消息，短暂延迟
                    delay(10)
                }

            } catch (e: ZMQException) {
                if (e.errorCode != ZMQ.Error.EAGAIN.code) {
                    Timber.e(e, "[NewZmqClient] 接收循环错误")
                }
                delay(10)
            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 接收循环异常")
                delay(100)
            }
        }

        Timber.i("[NewZmqClient] 接收协程结束")
    }

    /**
     * 发送循环协程
     */
    private suspend fun sendLoop() {
        Timber.i("[NewZmqClient] 发送协程启动")

        while (running.get() && isActive) {
            try {
                val socket = this@NewZmqClient.socket ?: break

                // 发送队列中的所有消息
                while (sendQueue.isNotEmpty() && running.get()) {
                    val data = sendQueue.poll()
                    if (data != null) {
                        try {
                            val success = socket.send(data, ZMQ.NOBLOCK)
                            if (!success) {
                                // 发送失败，可能是缓冲区满，稍后重试
                                sendQueue.offer(data) // 重新入队
                                delay(10)
                                break
                            }
                        } catch (e: ZMQException) {
                            Timber.e(e, "[NewZmqClient] 发送消息失败")
                            delay(10)
                        }
                    }
                }

                delay(10) // 防止忙等待

            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 发送循环异常")
                delay(100)
            }
        }

        Timber.i("[NewZmqClient] 发送协程结束")
    }

    /**
     * 心跳循环协程
     */
    private suspend fun heartbeatLoop() {
        Timber.i("[NewZmqClient] 心跳协程启动")

        while (running.get() && isActive) {
            try {
                sendHeartbeat()
                delay(heartbeatIntervalMs)
            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 心跳异常")
                delay(heartbeatIntervalMs)
            }
        }

        Timber.i("[NewZmqClient] 心跳协程结束")
    }

    /**
     * 处理接收到的消息
     */
    private fun processReceivedMessage(message: LeggedDriverMessage) {
        when (message.messageType) {
            MessageType.MESSAGE_TYPE_HEARTBEAT -> handleHeartbeatMessage(message)
            MessageType.MESSAGE_TYPE_BATTERY_INFO -> handleBatteryInfoMessage(message)
            MessageType.MESSAGE_TYPE_CURRENT_MODE -> handleCurrentModeMessage(message)
            MessageType.MESSAGE_TYPE_CURRENT_CONTROL_MODE -> handleCurrentControlModeMessage(message)
            MessageType.MESSAGE_TYPE_ODOMETRY -> handleOdometryMessage(message)
            else -> Timber.d("[NewZmqClient] 收到消息类型: ${message.messageType}")
        }
    }

    /**
     * 处理心跳消息
     */
    private fun handleHeartbeatMessage(message: LeggedDriverMessage) {
        message.heartbeat?.let { heartbeat ->
            serverConnected.set(heartbeat.isConnected)
            Timber.d("[NewZmqClient] 收到服务器心跳，连接状态: ${heartbeat.isConnected}")
        }
    }

    /**
     * 处理电池信息消息
     */
    private fun handleBatteryInfoMessage(message: LeggedDriverMessage) {
        message.batteryInfo?.let { batteryInfo ->
            batteryLevel.set(batteryInfo.batteryLevel)
            Timber.d("[NewZmqClient] 收到电池信息，电量: ${batteryInfo.batteryLevel}%")
        }
    }

    /**
     * 处理当前模式消息
     */
    private fun handleCurrentModeMessage(message: LeggedDriverMessage) {
        message.currentMode?.let { currentModeMsg ->
            currentMode.set(currentModeMsg.mode)
            Timber.d("[NewZmqClient] 收到当前模式: ${currentModeMsg.mode}")
        }
    }

    /**
     * 处理当前控制模式消息
     */
    private fun handleCurrentControlModeMessage(message: LeggedDriverMessage) {
        message.currentControlMode?.let { currentControlModeMsg ->
            currentControlMode.set(currentControlModeMsg.controlMode)
            Timber.d("[NewZmqClient] 收到当前控制模式: ${currentControlModeMsg.controlMode}")
        }
    }

    /**
     * 处理里程计消息
     */
    private fun handleOdometryMessage(message: LeggedDriverMessage) {
        message.odometry?.let { odom ->
            Timber.d("[NewZmqClient] 收到里程计信息: pos(${odom.position.x},${odom.position.y},${odom.position.z})")
        }
    }

    /**
     * 发送消息
     */
    private fun sendMessage(message: LeggedDriverMessage) {
        try {
            val data = MessageUtils.serializeMessage(message)
            sendQueue.offer(data)
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
     * 设置消息回调
     */
    fun setMessageCallback(callback: MessageCallback?) {
        this.messageCallback = callback
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
        job.cancel()
    }
}