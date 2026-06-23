/*********************************************************************************
 * FileName: Controller.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-16
 * Description: 机器人控制器，管理ZMQ连接和机器人状态，状态管理类
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.controller

import android.content.Context
import androidx.compose.runtime.*
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.data.SettingsManager
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputRuntimeState
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputConfig
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputSource
import com.helywin.leggedjoystick.input.remote.unirc.SiyiUdpBridgeController
import com.helywin.leggedjoystick.zmq.NewZmqClient
import legged_driver.AppMode
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.RobotStateMessage
import legged_driver.SportMode
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * 应用状态管理类
 */
@Stable
class ControllerState {
    // 连接状态
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    // 机器人模式（自动/手动）
    var robotMode by mutableStateOf(AppMode.APP_MODE_AUTO)
        private set

    // 运动模式（普通/原地/楼梯）
    var robotCtrlMode by mutableStateOf(SportMode.SPORT_MODE_GENERAL)
        private set

    // 电池电量
    var batteryLevel by mutableStateOf(0)
        private set

    // 应用设置
    var settings by mutableStateOf(AppSettings())
        private set

    // 外部遥控输入状态
    var remoteInputState by mutableStateOf(RemoteInputRuntimeState())
        private set

    // 模式切换状态
    var isRobotModeChanging by mutableStateOf(false)
        private set

    var isRobotCtrlModeChanging by mutableStateOf(false)
        private set

    // 衍生状态
    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    // 更新方法
    fun updateConnectionState(newState: ConnectionState) {
        connectionState = newState
    }

    fun updateRobotMode(newMode: AppMode) {
        robotMode = newMode
        // 模式更新时清除切换状态
        if (isRobotModeChanging) {
            isRobotModeChanging = false
        }
    }

    fun updateRobotCtrlMode(newMode: SportMode) {
        robotCtrlMode = newMode
        // 控制模式更新时清除切换状态
        if (isRobotCtrlModeChanging) {
            isRobotCtrlModeChanging = false
        }
    }

    fun updateBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
    }

    fun updateRobotModeChangingState(changing: Boolean) {
        isRobotModeChanging = changing
    }

    fun updateRobotCtrlModeChangingState(changing: Boolean) {
        isRobotCtrlModeChanging = changing
    }

    fun setSpeedLevel(level: SpeedLevel) {
        settings = settings.copy(speedLevel = level)
    }

    fun updateRemoteInputStatus(
        descriptor: RemoteInputSourceDescriptor,
        status: RemoteInputStatus,
        message: String = ""
    ) {
        val resetSnapshot = when (status) {
            RemoteInputStatus.TIMEOUT,
            RemoteInputStatus.ERROR,
            RemoteInputStatus.STOPPED -> RemoteInputSnapshot(
                descriptor = descriptor,
                movementIntent = MovementIntent.ZERO,
                normalizedAxes = mapOf(
                    "forward" to 0f,
                    "strafeRight" to 0f,
                    "yawRight" to 0f
                )
            )
            else -> remoteInputState.latestSnapshot
        }

        remoteInputState = remoteInputState.copy(
            status = status,
            sourceName = descriptor.displayName,
            lastError = message,
            latestSnapshot = resetSnapshot
        )
    }

    fun updateRemoteInputSnapshot(snapshot: RemoteInputSnapshot) {
        remoteInputState = remoteInputState.copy(
            status = RemoteInputStatus.RUNNING,
            sourceName = snapshot.descriptor.displayName,
            lastFrameAtMs = snapshot.receivedAtMs,
            lastError = "",
            latestSnapshot = snapshot
        )
    }
}

/**
 * 全局状态实例
 */
val settingsState = ControllerState()

/**
 * 机器人控制器接口
 */
interface Controller {
    fun connect()
    fun disconnect()
    fun cancelConnection()
    fun setMode(mode: AppMode)
    fun setControlMode(controlMode: SportMode)
    fun setSpeedLevel(level: SpeedLevel)
    fun updateSettings(settings: AppSettings)
    fun pauseMovementOutput()
    fun resumeMovementOutput()
    fun isConnected(): Boolean
    fun cleanup()
    fun loadSettings()
    fun saveSettings(settings: AppSettings)
}

/**
 * 机器人控制器实现类
 */
class RobotControllerImpl(private val context: Context) : Controller {
    private val zmqClient = NewZmqClient()
    private val settingsManager = SettingsManager(context)

    // 协程相关
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)

    // 连接任务
    private var connectJob: Job? = null

    private var remoteInputSource: RemoteInputSource = buildRemoteInputSource(settingsState.settings)
    private val siyiUdpBridgeController = SiyiUdpBridgeController(context)
    private var remoteInputRequested = false
    private var currentMovementIntent = MovementIntent.ZERO
    private var lastCommandSent = false  // 跟踪是否发送过速度指令

    // 速度发送任务
    private var velocitySendJob: Job? = null

    init {
        // 设置ZMQ客户端回调
        zmqClient.setMessageCallback { message ->
            handleIncomingMessage(message)
        }

        zmqClient.setConnectionStateCallback {
            handleConnectionState(it)
        }

        // 启动时加载设置
        loadSettings()
    }

    /**
     * 处理接收到的消息
     */
    private fun handleIncomingMessage(message: LeggedDriverMessage) {
        when (message.message_type) {
            MessageType.MESSAGE_TYPE_HEARTBEAT -> {
                message.heartbeat?.let { heartbeat ->
                    settingsState.updateRobotMode(heartbeat.app_mode)
                    Timber.d("[Controller] 收到服务器心跳，机器连接状态: ${heartbeat.robot_connected}")
                }
            }
            MessageType.MESSAGE_TYPE_APP_MODE_STATE -> {
                message.app_mode_state?.let { appModeState ->
                    settingsState.updateRobotMode(appModeState.app_mode)
                    Timber.d("[Controller] 收到当前 AppMode: ${appModeState.app_mode}")
                }
            }
            MessageType.MESSAGE_TYPE_ROBOT_STATE -> {
                message.robot_state?.let { robotState ->
                    settingsState.updateRobotCtrlMode(robotState.sport_mode)
                    settingsState.updateBatteryLevel(robotState.toBatteryPercent())
                    Timber.d("[Controller] 收到机器人状态，运动模式: ${robotState.sport_mode}")
                }
            }
            else -> {
//                Timber.d("[Controller] 收到其他消息类型: ${message.message_type}")
            }
        }
    }

    /**
     * 处理连接状态
     */
    private fun handleConnectionState(state: ConnectionState) {
        scope.launch {
            if (settingsState.connectionState == state) {
                // 状态未变化，忽略
                return@launch
            }
            if (state == ConnectionState.CONNECTED) {
                startVelocityLoop()
            } else {
                stopVelocityLoop()
            }
            settingsState.updateConnectionState(state)
            Timber.i("[Controller] 连接状态更新: $state")
        }
    }

    /**
     * 连接到机器人
     */
    override fun connect() {
        if (settingsState.connectionState == ConnectionState.CONNECTING) {
            Timber.w("[Controller] 正在连接中，忽略重复连接请求")
            return
        }

        if (settingsState.connectionState == ConnectionState.CONNECTED) {
            Timber.w("[Controller] 已经连接，忽略重复连接请求")
            return
        }

        cancelConnection() // 取消之前的连接任务

        settingsState.updateConnectionState(ConnectionState.CONNECTING)

        connectJob = scope.launch {
            try {
                Timber.i("[Controller] 开始连接到机器人...")

                // 构建连接地址
                val endpoint = "tcp://${settingsState.settings.zmqIp}:${settingsState.settings.zmqPort}"
                Timber.i("[Controller] 连接地址: $endpoint")

                // 设置连接地址
                zmqClient.setEndpoint(endpoint)

                // 进行连接
                zmqClient.connect()


            } catch (e: CancellationException) {
                settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
                Timber.i("[Controller] 连接已取消")
            } catch (e: Exception) {
                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                Timber.e(e, "[Controller] 连接异常")
            }
        }
    }

    /**
     * 断开连接
     */
    override fun disconnect() {
        cancelConnection()
        stopVelocityLoop()
        zmqClient.disconnect()
        settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
        Timber.i("[Controller] 已断开连接")
    }

    /**
     * 取消连接
     */
    override fun cancelConnection() {
        connectJob?.cancel()
        connectJob = null
        if (settingsState.connectionState == ConnectionState.CONNECTING) {
            zmqClient.disconnect()
            settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * 设置机器人模式（自动/手动）
     */
    override fun setMode(mode: AppMode) {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法设置模式")
            return
        }

        if (settingsState.isRobotModeChanging) {
            Timber.w("[Controller] 正在切换模式中，请等待")
            return
        }

        settingsState.updateRobotModeChangingState(true)

        scope.launch {
            try {
                val success = zmqClient.setMode(mode)
                if (success) {
                    Timber.i("[Controller] 模式设置请求已发送: $mode")
                    // 实际模式更新由消息回调处理
                } else {
                    settingsState.updateRobotModeChangingState(false)
                    Timber.e("[Controller] 模式设置失败: $mode")
                }
            } catch (e: Exception) {
                settingsState.updateRobotModeChangingState(false)
                Timber.e(e, "[Controller] 设置模式异常: $mode")
            }
        }
    }

    /**
     * 设置机器人控制模式（站立/趴下/阻尼）
     */
    override fun setControlMode(controlMode: SportMode) {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法设置运动模式")
            return
        }

        if (settingsState.isRobotCtrlModeChanging) {
            Timber.w("[Controller] 正在切换运动模式中，请等待")
            return
        }

        settingsState.updateRobotCtrlModeChangingState(true)

        scope.launch {
            try {
                val success = zmqClient.setControlMode(controlMode)
                if (success) {
                    Timber.i("[Controller] 运动模式设置请求已发送: $controlMode")
                    // 实际运动模式更新由消息回调处理
                } else {
                    settingsState.updateRobotCtrlModeChangingState(false)
                    Timber.e("[Controller] 运动模式设置失败: $controlMode")
                }
            } catch (e: Exception) {
                settingsState.updateRobotCtrlModeChangingState(false)
                Timber.e(e, "[Controller] 设置运动模式异常: $controlMode")
            }
        }
    }

    /**
     * 设置速度档位
     */
    override fun setSpeedLevel(level: SpeedLevel) {
        settingsState.setSpeedLevel(level)
        if (settingsState.isConnected) {
            zmqClient.setSpeedLevel(level.protocolSpeedLevel)
        }
        // 自动保存更新后的设置
        saveSettings(settingsState.settings)
        Timber.i("[Controller] 已切换到${level.displayName}并保存设置")
    }

    /**
     * 更新设置
     */
    override fun updateSettings(settings: AppSettings) {
        settingsState.updateSettings(settings)
        rebuildRemoteInputSource(settings)
        // 自动保存设置
        saveSettings(settings)
        Timber.d("[Controller] 设置已更新并保存")
    }

    /**
     * 加载设置
     */
    override fun loadSettings() {
        try {
            val settings = settingsManager.loadSettings()
            settingsState.updateSettings(settings)
            rebuildRemoteInputSource(settings)
            Timber.i("[Controller] 设置已从存储中加载: $settings")
        } catch (e: Exception) {
            Timber.e(e, "[Controller] 加载设置失败，使用默认设置")
        }
    }

    /**
     * 保存设置
     */
    override fun saveSettings(settings: AppSettings) {
        try {
            settingsManager.saveSettings(settings)
            Timber.d("[Controller] 设置已保存到存储")
        } catch (e: Exception) {
            Timber.e(e, "[Controller] 保存设置失败")
        }
    }

    /**
     * 检查是否连接
     */
    override fun isConnected(): Boolean {
        return settingsState.isConnected
    }

    override fun pauseMovementOutput() {
        currentMovementIntent = MovementIntent.ZERO
        stopVelocityLoop()
        Timber.i("[Controller] 已暂停移动输出")
    }

    override fun resumeMovementOutput() {
        startRemoteInput()
        if (settingsState.isConnected) {
            startVelocityLoop()
        }
        Timber.i("[Controller] 已恢复输入采集")
    }

    /**
     * 开始速度发送循环
     */
    private fun startVelocityLoop() {
        stopVelocityLoop()

        velocitySendJob = scope.launch {
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有在手动模式下才发送移动指令
                    if (settingsState.robotMode == AppMode.APP_MODE_MANUAL) {
                        val intent = currentMovementIntent.clamped()

                        // 只有当外部遥控器有非零输入时才发送移动指令
                        if (!intent.isZero) {
                            zmqClient.sendOperatorMoveCommand(
                                strafeRight = intent.strafeRight,
                                forward = intent.forward,
                                yawRight = intent.yawRight
                            )
                            Timber.v(
                                "[Controller] 发送移动指令: forward=${intent.forward}, strafeRight=${intent.strafeRight}, yawRight=${intent.yawRight}"
                            )
                            lastCommandSent = true
                        } else if (lastCommandSent) {
                            // 只有之前发送过指令，且现在摇杆都在中心位置时，才发送一次停止指令
                            zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
                            Timber.v("[Controller] 发送停止移动指令")
                            lastCommandSent = false
                        }
                        // 如果摇杆都在中心位置且之前没有发送过指令，则不发送任何指令
                    }

                    delay(40) // 25Hz 发送频率

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Timber.e(e, "[Controller] 速度发送异常")
                    delay(100) // 出错时稍微等待一下
                }
            }
        }
    }

    /**
     * 停止速度发送循环
     */
    private fun stopVelocityLoop() {
        if (lastCommandSent) {
            zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
        }
        currentMovementIntent = MovementIntent.ZERO
        velocitySendJob?.cancel()
        velocitySendJob = null
        lastCommandSent = false  // 重置命令发送标志
    }

    /**
     * 清理资源
     */
    override fun cleanup() {
        disconnect()
        stopRemoteInput()
        supervisorJob.cancel()
    }

    private fun startRemoteInput() {
        remoteInputRequested = true
        if (SiyiUdpBridgeController.shouldUseForHost(settingsState.settings.remoteInputHost)) {
            siyiUdpBridgeController.ensureBridgeOpen()
        }
        remoteInputSource.start(remoteInputListener)
    }

    private fun stopRemoteInput() {
        remoteInputRequested = false
        remoteInputSource.stop()
        siyiUdpBridgeController.release()
        currentMovementIntent = MovementIntent.ZERO
    }

    private fun rebuildRemoteInputSource(settings: AppSettings) {
        val shouldRestart = remoteInputRequested
        if (shouldRestart) {
            remoteInputSource.stop()
            siyiUdpBridgeController.release()
        }

        remoteInputSource = buildRemoteInputSource(settings)

        if (shouldRestart) {
            startRemoteInput()
        }
    }

    private fun buildRemoteInputSource(settings: AppSettings): RemoteInputSource {
        return UniRcUdpInputSource(
            UniRcUdpInputConfig(
                remoteHost = settings.remoteInputHost,
                remotePort = settings.remoteInputPort,
                localPort = settings.remoteInputLocalPort,
                normalization = RemoteInputNormalizationConfig(
                    deadZone = settings.remoteInputDeadZone,
                    timeoutMs = settings.remoteInputTimeoutMs
                )
            )
        )
    }

    private val remoteInputListener = object : RemoteInputListener {
        override fun onStatusChanged(
            descriptor: RemoteInputSourceDescriptor,
            status: RemoteInputStatus,
            message: String
        ) {
            if (status == RemoteInputStatus.TIMEOUT || status == RemoteInputStatus.ERROR) {
                currentMovementIntent = MovementIntent.ZERO
            }
            Timber.d("[Controller] 外部遥控输入状态: %s %s", status, message)

            scope.launch {
                settingsState.updateRemoteInputStatus(descriptor, status, message)
            }
        }

        override fun onSnapshot(snapshot: RemoteInputSnapshot) {
            currentMovementIntent = snapshot.movementIntent.clamped()
            scope.launch {
                settingsState.updateRemoteInputSnapshot(snapshot)
            }
        }
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
