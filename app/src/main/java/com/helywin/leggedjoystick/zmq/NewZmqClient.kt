/*********************************************************************************
 * FileName: NewZmqClient.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-16
 * Description: 新的ZMQ客户端，使用异步发送和接收线程，不使用请求应答模式
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

import legged_driver.*
import com.helywin.leggedjoystick.proto.MessageUtils
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
 * 参考C++版本，使用原生线程进行异步发送和接收
 */
class NewZmqClient {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1000L // 1Hz心跳频率
        private const val SOCKET_RECV_TIMEOUT_MS = 100 // 接收超时100ms
        private const val SOCKET_SEND_TIMEOUT_MS = 1000 // 发送超时1秒
    }

    // ZMQ相关
    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var tcpEndpoint = DEFAULT_TCP_ENDPOINT

    // 状态控制
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    
    // 工作线程
    private var receiveThread: Thread? = null
    private var sendThread: Thread? = null
    private var heartbeatThread: Thread? = null

    // 发送队列
    private val sendQueue = ConcurrentLinkedQueue<ByteArray>()

    // 客户端信息
    private var deviceType: DeviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER
    private var deviceId: String = ""
    private var heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS

    // 消息回调
    private var messageCallback: MessageCallback? = null

    // 连接丢失回调
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

            // 启动工作线程
            startThreads()

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

        // 中断并等待所有线程结束
        receiveThread?.interrupt()
        sendThread?.interrupt()
        heartbeatThread?.interrupt()

        // 等待线程结束
        try {
            receiveThread?.join(5000) // 最多等待5秒
            sendThread?.join(5000)
            heartbeatThread?.join(5000)
        } catch (e: InterruptedException) {
            Timber.w("[NewZmqClient] 等待线程结束时被中断")
            Thread.currentThread().interrupt() // 恢复中断状态
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
        
        // 清理线程引用
        receiveThread = null
        sendThread = null
        heartbeatThread = null
    }

    /**
     * 启动工作线程
     */
    private fun startThreads() {
        receiveThread = Thread({ receiveLoop() }, "ZMQ-Receive")
        sendThread = Thread({ sendLoop() }, "ZMQ-Send")
        heartbeatThread = Thread({ heartbeatLoop() }, "ZMQ-Heartbeat")
        
        receiveThread?.start()
        sendThread?.start()
        heartbeatThread?.start()
    }

    /**
     * 接收循环线程
     */
    private fun receiveLoop() {
        Timber.i("[NewZmqClient] 接收线程启动")

        while (running.get() && !Thread.currentThread().isInterrupted) {
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
                    // 没有消息，短暂休眠
                    Thread.sleep(10)
                }

            } catch (e: InterruptedException) {
                Timber.i("[NewZmqClient] 接收线程被中断")
                break
            } catch (e: ZMQException) {
                if (e.errorCode != ZMQ.Error.EAGAIN.code) {
                    Timber.e(e, "[NewZmqClient] 接收循环错误")
                }
                try {
                    Thread.sleep(10)
                } catch (ie: InterruptedException) {
                    Timber.i("[NewZmqClient] 接收线程休眠时被中断")
                    break
                }
            } catch (e: Exception) {
                Timber.e(e, "[NewZmqClient] 接收循环异常")
                try {
                    Thread.sleep(100)
                } catch (ie: InterruptedException) {
                    Timber.i("[NewZmqClient] 接收线程休眠时被中断")
                    break
                }
            }
        }

        Timber.i("[NewZmqClient] 接收线程结束")
    }

    /**
     * 发送循环线程
     */
    private fun sendLoop() {
        Timber.i("[NewZmqClient] 发送线程启动")
        
        var consecutiveFailures = 0
        val maxFailures = 5 // 连续失败5次后认为连接异常

        while (running.get() && !Thread.currentThread().isInterrupted) {
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
                                Thread.sleep(10)
                                break
                            } else {
                                consecutiveFailures = 0 // 重置失败计数
                            }
                        } catch (e: ZMQException) {
                            consecutiveFailures++
                            Timber.e(e, "[NewZmqClient] 发送消息失败 ($consecutiveFailures/$maxFailures)")
                            
                            // 连续失败达到上限时，通知连接丢失
                            if (consecutiveFailures >= maxFailures) {
                                Timber.e("[NewZmqClient] 发送连续失败${maxFailures}次，判断连接丢失")
                                running.set(false)
                                connected.set(false)
                                notifyConnectionLost()
                                break
                            }
                            Thread.sleep(10)
                        }
                    }
                }

                Thread.sleep(10) // 防止忙等待

            } catch (e: InterruptedException) {
                Timber.i("[NewZmqClient] 发送线程被中断")
                break
            } catch (e: Exception) {
                consecutiveFailures++
                Timber.e(e, "[NewZmqClient] 发送循环异常 ($consecutiveFailures/$maxFailures)")
                
                if (consecutiveFailures >= maxFailures) {
                    Timber.e("[NewZmqClient] 发送循环连续异常${maxFailures}次，判断连接丢失")
                    running.set(false)
                    connected.set(false)
                    notifyConnectionLost()
                    break
                }
                try {
                    Thread.sleep(100)
                } catch (ie: InterruptedException) {
                    Timber.i("[NewZmqClient] 发送线程休眠时被中断")
                    break
                }
            }
        }

        Timber.i("[NewZmqClient] 发送线程结束")
    }

    /**
     * 心跳循环线程
     */
    private fun heartbeatLoop() {
        Timber.i("[NewZmqClient] 心跳线程启动")
        
        var consecutiveFailures = 0
        val maxFailures = 3 // 连续失败3次后断开连接

        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                sendHeartbeat()
                consecutiveFailures = 0 // 重置失败计数
                Thread.sleep(heartbeatIntervalMs)
            } catch (e: InterruptedException) {
                Timber.i("[NewZmqClient] 心跳线程被中断")
                break
            } catch (e: Exception) {
                consecutiveFailures++
                Timber.e(e, "[NewZmqClient] 心跳异常 ($consecutiveFailures/$maxFailures)")
                
                // 连续失败达到上限时，断开连接
                if (consecutiveFailures >= maxFailures) {
                    Timber.e("[NewZmqClient] 心跳连续失败${maxFailures}次，自动断开连接")
                    
                    // 设置状态为断开，触发连接失败
                    running.set(false)
                    connected.set(false)
                    
                    // 通知上层连接失败
                    notifyConnectionLost()
                    break
                }
                
                try {
                    Thread.sleep(heartbeatIntervalMs)
                } catch (ie: InterruptedException) {
                    Timber.i("[NewZmqClient] 心跳线程休眠时被中断")
                    break
                }
            }
        }

        Timber.i("[NewZmqClient] 心跳线程结束")
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
     * 设置连接丢失回调
     */
    fun setConnectionLostCallback(callback: ConnectionLostCallback?) {
        this.connectionLostCallback = callback
    }

    /**
     * 通知连接丢失
     */
    private fun notifyConnectionLost() {
        connectionLostCallback?.invoke()
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
    }
}