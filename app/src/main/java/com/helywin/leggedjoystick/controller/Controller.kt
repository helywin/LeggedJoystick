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
import com.helywin.leggedjoystick.data.ControlOwnershipState
import com.helywin.leggedjoystick.data.HighLowStance
import com.helywin.leggedjoystick.data.SettingsManager
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputAxisMapping
import com.helywin.leggedjoystick.input.remote.RemoteInputChannelMapping
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
import legged_driver.CommandCode
import legged_driver.CommandResultStage
import legged_driver.CtrlSource
import legged_driver.FillLightStatus
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.RobotStateMessage
import legged_driver.SportMode
import kotlinx.coroutines.*
import kotlin.math.hypot
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

    // 当前线速度值，单位由 driver 状态消息保持一致。
    var currentSpeedValue by mutableStateOf(0.0)
        private set

    // 补光灯和头部状态来自 RobotStateMessage。
    var frontLightOn by mutableStateOf(false)
        private set

    var backLightOn by mutableStateOf(false)
        private set

    var autoModeLightOn by mutableStateOf(false)
        private set

    var headAngle by mutableStateOf(0.0)
        private set

    var highLowStance by mutableStateOf(HighLowStance.NORMAL)
        private set

    // 当前控制权状态，只有已接管时才允许发送运动和动作命令。
    var controlOwnershipState by mutableStateOf(ControlOwnershipState.UNKNOWN)
        private set

    var controlOwnershipMessage by mutableStateOf("")
        private set

    // 应用设置
    var settings by mutableStateOf(AppSettings())
        private set

    // 外部遥控输入状态
    var remoteInputState by mutableStateOf(RemoteInputRuntimeState())
        private set

    var lastCommandName by mutableStateOf("无")
        private set

    var lastCommandDetail by mutableStateOf("")
        private set

    var lastCommandAtMs by mutableStateOf(0L)
        private set

    // 模式切换状态
    var isRobotModeChanging by mutableStateOf(false)
        private set

    var isRobotCtrlModeChanging by mutableStateOf(false)
        private set

    // 衍生状态
    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    val hasControl: Boolean
        get() = controlOwnershipState == ControlOwnershipState.OWNED

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

    fun updateCurrentSpeedValue(value: Double) {
        currentSpeedValue = value.coerceAtLeast(0.0)
    }

    fun updateRobotAuxiliaryState(robotState: RobotStateMessage) {
        updateFillLightState(robotState.front_fill_light) { frontLightOn = it }
        updateFillLightState(robotState.back_fill_light) { backLightOn = it }
        autoModeLightOn = robotState.auto_mode_light
        headAngle = robotState.head_angle
    }

    fun updateFrontLightState(on: Boolean) {
        frontLightOn = on
    }

    fun updateBackLightState(on: Boolean) {
        backLightOn = on
    }

    fun updateAutoModeLightState(on: Boolean) {
        autoModeLightOn = on
    }

    fun updateHighLowStance(stance: HighLowStance) {
        highLowStance = stance
    }

    fun updateControlOwnership(newState: ControlOwnershipState, message: String = "") {
        controlOwnershipState = newState
        controlOwnershipMessage = message
    }

    fun updateControlOwnershipFromSource(source: CtrlSource) {
        val nextState = when (source) {
            CtrlSource.CTRL_SOURCE_APP -> ControlOwnershipState.OWNED
            CtrlSource.CTRL_SOURCE_SDK,
            CtrlSource.CTRL_SOURCE_OTHER -> ControlOwnershipState.OCCUPIED
            CtrlSource.CTRL_SOURCE_UNKNOWN -> ControlOwnershipState.AVAILABLE
        }

        if (controlOwnershipState == ControlOwnershipState.TAKING && nextState != ControlOwnershipState.OWNED) {
            return
        }
        if (controlOwnershipState == ControlOwnershipState.RELEASING && nextState != ControlOwnershipState.AVAILABLE) {
            return
        }

        controlOwnershipState = nextState
        controlOwnershipMessage = ""
    }

    private fun updateFillLightState(status: FillLightStatus, update: (Boolean) -> Unit) {
        when (status) {
            FillLightStatus.FILL_LIGHT_STATUS_ON -> update(true)
            FillLightStatus.FILL_LIGHT_STATUS_OFF -> update(false)
            FillLightStatus.FILL_LIGHT_STATUS_UNKNOWN -> Unit
        }
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

    fun updateLastCommand(name: String, detail: String = "") {
        lastCommandName = name
        lastCommandDetail = detail
        lastCommandAtMs = System.currentTimeMillis()
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
    fun takeControl()
    fun releaseControl()
    fun setMode(mode: AppMode)
    fun setControlMode(controlMode: SportMode)
    fun setSpeedLevel(level: SpeedLevel)
    fun performAction(action: RobotAction)
    fun setFrontLight(on: Boolean)
    fun setBackLight(on: Boolean)
    fun setAutoModeLight(on: Boolean)
    fun controlHead(leftRight: Float, upDown: Float)
    fun setHighLowStance(stance: HighLowStance)
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
    private var headControlStopJob: Job? = null
    private var headControlActive = false

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
                    settingsState.updateCurrentSpeedValue(robotState.toLinearSpeedValue())
                    settingsState.updateRobotAuxiliaryState(robotState)
                    settingsState.updateControlOwnershipFromSource(robotState.control_source)
                    Timber.d(
                        "[Controller] 收到机器人状态，运动模式: %s，控制来源: %s",
                        robotState.sport_mode,
                        robotState.control_source
                    )
                }
            }
            MessageType.MESSAGE_TYPE_TAKE_CONTROL_ACK -> {
                message.take_control_ack?.let { ack ->
                    if (ack.error_code == 0) {
                        markControlOwned("接管成功")
                    } else {
                        settingsState.updateControlOwnership(
                            ControlOwnershipState.DENIED,
                            ack.reason.ifEmpty { "接管被拒绝: ${ack.error_code}" }
                        )
                    }
                    Timber.i("[Controller] 收到接管 ACK，错误码: %s，原因: %s", ack.error_code, ack.reason)
                }
            }
            MessageType.MESSAGE_TYPE_RELEASE_CONTROL_ACK -> {
                message.release_control_ack?.let { ack ->
                    if (ack.error_code == 0) {
                        settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "已释放控制权")
                    } else {
                        settingsState.updateControlOwnership(
                            ControlOwnershipState.OWNED,
                            ack.reason.ifEmpty { "释放失败: ${ack.error_code}" }
                        )
                    }
                    Timber.i("[Controller] 收到释放 ACK，错误码: %s，原因: %s", ack.error_code, ack.reason)
                }
            }
            MessageType.MESSAGE_TYPE_COMMAND_RESULT -> {
                message.command_result?.let { result ->
                    handleCommandResult(
                        commandCode = result.command_code,
                        stage = result.stage,
                        errorCode = result.error_code,
                        errorMessage = result.error_message
                    )
                }
            }
            MessageType.MESSAGE_TYPE_CONTROL_LOST -> {
                stopVelocityLoop()
                stopHeadControl()
                settingsState.updateControlOwnership(ControlOwnershipState.LOST, "控制权已丢失")
                Timber.w("[Controller] 收到控制权丢失通知")
            }
            MessageType.MESSAGE_TYPE_CONTROL_AVAILABLE -> {
                settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "控制权可用")
                Timber.i("[Controller] 收到控制权可用通知")
            }
            else -> {
//                Timber.d("[Controller] 收到其他消息类型: ${message.message_type}")
            }
        }
    }

    private fun handleCommandResult(
        commandCode: CommandCode,
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (commandCode) {
            CommandCode.COMMAND_CODE_TAKE_CONTROL -> handleTakeControlResult(stage, errorCode, errorMessage)
            CommandCode.COMMAND_CODE_RELEASE_CONTROL -> handleReleaseControlResult(stage, errorCode, errorMessage)
            else -> Unit
        }
    }

    private fun handleTakeControlResult(
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (stage) {
            CommandResultStage.COMMAND_RESULT_STAGE_ACCEPTED -> {
                if (!settingsState.hasControl) {
                    settingsState.updateControlOwnership(ControlOwnershipState.TAKING, "接管请求已接受")
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_COMPLETED -> {
                markControlOwned("已接管控制权")
            }
            CommandResultStage.COMMAND_RESULT_STAGE_REJECTED -> {
                if (!settingsState.hasControl) {
                    settingsState.updateControlOwnership(
                        ControlOwnershipState.DENIED,
                        errorMessage.ifEmpty { "接管被拒绝: $errorCode" }
                    )
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_UNSPECIFIED -> Unit
        }
    }

    private fun handleReleaseControlResult(
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (stage) {
            CommandResultStage.COMMAND_RESULT_STAGE_ACCEPTED -> {
                if (settingsState.controlOwnershipState != ControlOwnershipState.AVAILABLE) {
                    settingsState.updateControlOwnership(ControlOwnershipState.RELEASING, "释放请求已接受")
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_COMPLETED -> {
                settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "已释放控制权")
            }
            CommandResultStage.COMMAND_RESULT_STAGE_REJECTED -> {
                if (settingsState.controlOwnershipState != ControlOwnershipState.AVAILABLE) {
                    settingsState.updateControlOwnership(
                        ControlOwnershipState.OWNED,
                        errorMessage.ifEmpty { "释放被拒绝: $errorCode" }
                    )
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_UNSPECIFIED -> Unit
        }
    }

    private fun markControlOwned(message: String) {
        val wasOwned = settingsState.hasControl
        settingsState.updateControlOwnership(ControlOwnershipState.OWNED, message)
        if (velocitySendJob?.isActive != true) {
            startVelocityLoop()
        }
        if (!wasOwned) {
            sendInitialCommandsAfterTake()
        }
    }

    private fun sendInitialCommandsAfterTake() {
        zmqClient.setMode(AppMode.APP_MODE_MANUAL)
        zmqClient.setSpeedLevel(settingsState.settings.speedLevel.protocolSpeedLevel)
        if (settingsState.robotCtrlMode == SportMode.SPORT_MODE_UNKNOWN) {
            zmqClient.setControlMode(SportMode.SPORT_MODE_GENERAL)
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
                settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "已连接，等待接管")
                startVelocityLoop()
            } else {
                stopVelocityLoop()
                settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
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
        settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")

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
        stopHeadControl()
        zmqClient.disconnect()
        settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
        settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
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
            settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
        }
    }

    override fun takeControl() {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法接管控制权")
            return
        }

        when (settingsState.controlOwnershipState) {
            ControlOwnershipState.OWNED -> {
                Timber.i("[Controller] 已拥有控制权，忽略重复接管")
                return
            }
            ControlOwnershipState.TAKING,
            ControlOwnershipState.RELEASING -> {
                Timber.w("[Controller] 控制权请求处理中，忽略重复操作")
                return
            }
            else -> Unit
        }

        settingsState.updateControlOwnership(ControlOwnershipState.TAKING, "正在接管")
        scope.launch {
            val success = zmqClient.takeControl()
            if (!success) {
                settingsState.updateControlOwnership(ControlOwnershipState.DENIED, "接管请求发送失败")
            } else {
                settingsState.updateLastCommand("控制权", "接管")
            }
        }
    }

    override fun releaseControl() {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法释放控制权")
            return
        }

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 当前没有控制权，忽略释放请求")
            return
        }

        stopVelocityLoop()
        stopHeadControl()
        settingsState.updateControlOwnership(ControlOwnershipState.RELEASING, "正在释放")
        scope.launch {
            val success = zmqClient.releaseControl()
            if (!success) {
                settingsState.updateControlOwnership(ControlOwnershipState.OWNED, "释放请求发送失败")
                startVelocityLoop()
            } else {
                settingsState.updateLastCommand("控制权", "释放")
            }
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

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 未接管控制权，无法设置模式")
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
                    settingsState.updateLastCommand("AppMode", mode.name)
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

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 未接管控制权，无法设置运动模式")
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
                    settingsState.updateLastCommand("运动模式", controlMode.name)
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
        if (settingsState.isConnected && settingsState.hasControl) {
            if (zmqClient.setSpeedLevel(level.protocolSpeedLevel)) {
                settingsState.updateLastCommand("速度档位", level.displayName)
            }
        } else if (settingsState.isConnected) {
            Timber.w("[Controller] 未接管控制权，仅保存速度档位: %s", level.displayName)
        }
        // 自动保存更新后的设置
        saveSettings(settingsState.settings)
        Timber.i("[Controller] 已切换到${level.displayName}并保存设置")
    }

    /**
     * 发送主控页底部动作按钮命令。
     */
    override fun performAction(action: RobotAction) {
        if (!canSendControlledCommand("动作命令: ${action.displayName}")) return

        scope.launch {
            try {
                val success = zmqClient.sendSimpleCommand(action.commandCode)
                if (success) {
                    settingsState.updateLastCommand("动作", action.displayName)
                    Timber.i("[Controller] 动作命令已发送: %s", action.displayName)
                } else {
                    Timber.w("[Controller] 动作命令入队失败: %s", action.displayName)
                }
            } catch (e: Exception) {
                Timber.e(e, "[Controller] 发送动作命令异常: %s", action.displayName)
            }
        }
    }

    override fun setFrontLight(on: Boolean) {
        if (!canSendControlledCommand("前补光灯")) return

        scope.launch {
            val success = zmqClient.setFrontLight(on)
            if (success) {
                settingsState.updateFrontLightState(on)
                settingsState.updateLastCommand("前补光灯", if (on) "开" else "关")
                Timber.i("[Controller] 前补光灯请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 前补光灯请求入队失败: %s", on)
            }
        }
    }

    override fun setBackLight(on: Boolean) {
        if (!canSendControlledCommand("后补光灯")) return

        scope.launch {
            val success = zmqClient.setBackLight(on)
            if (success) {
                settingsState.updateBackLightState(on)
                settingsState.updateLastCommand("后补光灯", if (on) "开" else "关")
                Timber.i("[Controller] 后补光灯请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 后补光灯请求入队失败: %s", on)
            }
        }
    }

    override fun setAutoModeLight(on: Boolean) {
        if (!canSendControlledCommand("自动补光")) return

        scope.launch {
            val success = zmqClient.setAutoModeLight(on)
            if (success) {
                settingsState.updateAutoModeLightState(on)
                settingsState.updateLastCommand("自动补光", if (on) "开" else "关")
                Timber.i("[Controller] 自动补光请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 自动补光请求入队失败: %s", on)
            }
        }
    }

    override fun controlHead(leftRight: Float, upDown: Float) {
        if (!canSendControlledCommand("头部控制")) return
        if (!settingsState.isInPlaceModeForAuxCommand("头部控制")) return

        val clampedLeftRight = leftRight.coerceIn(-1f, 1f)
        val clampedUpDown = upDown.coerceIn(-1f, 1f)

        scope.launch {
            val success = zmqClient.controlHead(clampedLeftRight, clampedUpDown)
            if (!success) {
                Timber.w("[Controller] 头部控制请求入队失败: leftRight=%s, upDown=%s", clampedLeftRight, clampedUpDown)
                return@launch
            }

            val isStopCommand = clampedLeftRight == 0f && clampedUpDown == 0f
            headControlActive = !isStopCommand
            headControlStopJob?.cancel()

            if (isStopCommand) {
                settingsState.updateLastCommand("头部控制", "停止")
                Timber.i("[Controller] 头部控制停止请求已发送")
            } else {
                settingsState.updateLastCommand(
                    "头部控制",
                    "左右 %.2f，俯仰 %.2f".format(clampedLeftRight, clampedUpDown)
                )
                Timber.i("[Controller] 头部控制短脉冲已发送: leftRight=%s, upDown=%s", clampedLeftRight, clampedUpDown)
                headControlStopJob = scope.launch {
                    delay(HEAD_CONTROL_PULSE_MS)
                    stopHeadControl()
                }
            }
        }
    }

    override fun setHighLowStance(stance: HighLowStance) {
        if (!canSendControlledCommand("高低站姿")) return
        if (!settingsState.isInPlaceModeForAuxCommand("高低站姿")) return

        scope.launch {
            val success = zmqClient.setHighLowStance(stance.protocolValue)
            if (success) {
                settingsState.updateHighLowStance(stance)
                settingsState.updateLastCommand("高低站姿", stance.displayName)
                Timber.i("[Controller] 高低站姿请求已发送: %s", stance.displayName)
            } else {
                Timber.w("[Controller] 高低站姿请求入队失败: %s", stance.displayName)
            }
        }
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
        stopHeadControl()
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
                    // 只有已接管且处于手动模式时才发送移动指令。
                    if (settingsState.hasControl && settingsState.robotMode == AppMode.APP_MODE_MANUAL) {
                        val intent = currentMovementIntent.clamped()

                        // 只有当外部遥控器有非零输入时才发送移动指令
                        if (!intent.isZero) {
                            zmqClient.sendOperatorMoveCommand(
                                strafeRight = intent.strafeRight,
                                forward = intent.forward,
                                yawRight = intent.yawRight
                            )
                            settingsState.updateLastCommand(
                                "移动",
                                "前进 %.2f，平移 %.2f，转向 %.2f".format(
                                    intent.forward,
                                    intent.strafeRight,
                                    intent.yawRight
                                )
                            )
                            Timber.v(
                                "[Controller] 发送移动指令: forward=${intent.forward}, strafeRight=${intent.strafeRight}, yawRight=${intent.yawRight}"
                            )
                            lastCommandSent = true
                        } else if (lastCommandSent) {
                            // 只有之前发送过指令，且现在摇杆都在中心位置时，才发送一次停止指令
                            zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
                            settingsState.updateLastCommand("移动", "停止")
                            Timber.v("[Controller] 发送停止移动指令")
                            lastCommandSent = false
                        }
                        // 如果摇杆都在中心位置且之前没有发送过指令，则不发送任何指令
                    } else if (lastCommandSent) {
                        zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
                        settingsState.updateLastCommand("移动", "停止")
                        Timber.v("[Controller] 控制权或手动模式不满足，发送停止移动指令")
                        lastCommandSent = false
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
            settingsState.updateLastCommand("移动", "停止")
        }
        currentMovementIntent = MovementIntent.ZERO
        velocitySendJob?.cancel()
        velocitySendJob = null
        lastCommandSent = false  // 重置命令发送标志
    }

    private fun stopHeadControl() {
        headControlStopJob?.cancel()
        headControlStopJob = null
        if (headControlActive) {
            zmqClient.controlHead(0f, 0f)
            headControlActive = false
            Timber.v("[Controller] 发送头部停止指令")
        }
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
                    timeoutMs = settings.remoteInputTimeoutMs,
                    mapping = RemoteInputChannelMapping(
                        forward = RemoteInputAxisMapping(
                            channel = settings.remoteInputForwardChannel,
                            inverted = settings.remoteInputForwardInverted
                        ),
                        strafeRight = RemoteInputAxisMapping(
                            channel = settings.remoteInputStrafeRightChannel,
                            inverted = settings.remoteInputStrafeRightInverted
                        ),
                        yawRight = RemoteInputAxisMapping(
                            channel = settings.remoteInputYawRightChannel,
                            inverted = settings.remoteInputYawRightInverted
                        )
                    )
                )
            )
        )
    }

    private fun canSendControlledCommand(commandName: String): Boolean {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法发送%s", commandName)
            return false
        }

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 未接管控制权，无法发送%s", commandName)
            return false
        }

        return true
    }

    private fun ControllerState.isInPlaceModeForAuxCommand(commandName: String): Boolean {
        if (robotCtrlMode == SportMode.SPORT_MODE_IN_PLACE) return true

        Timber.w("[Controller] 当前不是原地模式，无法发送%s", commandName)
        return false
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

    private fun RobotStateMessage.toLinearSpeedValue(): Double {
        val speedData = speed ?: return 0.0
        return hypot(speedData.line, speedData.translation)
    }

    private companion object {
        private const val HEAD_CONTROL_PULSE_MS = 250L
    }
}
