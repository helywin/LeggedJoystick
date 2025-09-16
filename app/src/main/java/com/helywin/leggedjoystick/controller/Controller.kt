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
import com.helywin.leggedjoystick.proto.MessageUtils
import legged_driver.*
import com.helywin.leggedjoystick.ui.joystick.JoystickValue
import com.helywin.leggedjoystick.zmq.NewZmqClient
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 应用状态管理类
 */
@Stable
class ControllerState {
    // 连接状态
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set
    
    // 机器人模式（自动/手动）
    var robotMode by mutableStateOf(Mode.MODE_AUTO)
        private set
        
    // 机器人控制模式（站立/趴下/阻尼）
    var robotCtrlMode by mutableStateOf(ControlMode.CONTROL_MODE_STAND_UP)
        private set
    
    // 电池电量
    var batteryLevel by mutableStateOf(0)
        private set
    
    // 应用设置
    var settings by mutableStateOf(AppSettings())
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
    
    fun updateRobotMode(newMode: Mode) {
        robotMode = newMode
        // 模式更新时清除切换状态
        if (isRobotModeChanging) {
            isRobotModeChanging = false
        }
    }
    
    fun updateRobotCtrlMode(newMode: ControlMode) {
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
    
    fun toggleRageMode() {
        settings = settings.copy(isRageModeEnabled = !settings.isRageModeEnabled)
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
    fun setMode(mode: Mode)
    fun setControlMode(controlMode: ControlMode)
    fun updateLeftJoystick(joystickValue: JoystickValue)
    fun updateRightJoystick(joystickValue: JoystickValue)
    fun onLeftJoystickReleased()
    fun onRightJoystickReleased()
    fun toggleRageMode()
    fun updateSettings(settings: AppSettings)
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
    
    // 摇杆状态
    private var currentLeftJoystick = JoystickValue.ZERO  // 左摇杆：vx, vy
    private var currentRightJoystick = JoystickValue.ZERO  // 右摇杆：yawRate
    private var lastCommandSent = false  // 跟踪是否发送过速度指令
    
    // 速度发送任务
    private var velocitySendJob: Job? = null
    
    init {
        // 设置ZMQ客户端回调
        zmqClient.setMessageCallback { message ->
            handleIncomingMessage(message)
        }
        
        // 设置连接丢失回调
        zmqClient.setConnectionLostCallback {
            handleConnectionLost()
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
                    Timber.d("[Controller] 收到服务器心跳，连接状态: ${heartbeat.is_connected}")
                }
            }
            MessageType.MESSAGE_TYPE_BATTERY_INFO -> {
                message.battery_info?.let { batteryInfo ->
                    settingsState.updateBatteryLevel(batteryInfo.battery_level)
                    Timber.d("[Controller] 收到电池信息，电量: ${batteryInfo.battery_level}%")
                }
            }
            MessageType.MESSAGE_TYPE_CURRENT_MODE -> {
                message.current_mode?.let { currentModeMsg ->
                    settingsState.updateRobotMode(currentModeMsg.mode)
                    Timber.d("[Controller] 收到当前模式: ${currentModeMsg.mode}")
                }
            }
            MessageType.MESSAGE_TYPE_CURRENT_CONTROL_MODE -> {
                message.current_control_mode?.let { currentControlModeMsg ->
                    settingsState.updateRobotCtrlMode(currentControlModeMsg.control_mode)
                    Timber.d("[Controller] 收到当前控制模式: ${currentControlModeMsg.control_mode}")
                }
            }
            else -> {
                Timber.d("[Controller] 收到其他消息类型: ${message.message_type}")
            }
        }
    }
    
    /**
     * 处理连接丢失
     */
    private fun handleConnectionLost() {
        scope.launch {
            Timber.w("[Controller] 检测到连接丢失，自动断开连接")
            settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
            stopVelocityLoop()
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
                val success = zmqClient.connect()
                
                if (success) {
                    settingsState.updateConnectionState(ConnectionState.CONNECTED)
                    Timber.i("[Controller] 连接成功")
                    
                    // 连接成功后开始速度发送循环（如果是手动模式）
                    startVelocityLoop()
                    
                } else {
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    Timber.e("[Controller] 连接失败")
                }
                
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
    }
    
    /**
     * 设置机器人模式（自动/手动）
     */
    override fun setMode(mode: Mode) {
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
    override fun setControlMode(controlMode: ControlMode) {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法设置控制模式")
            return
        }
        
        if (settingsState.isRobotCtrlModeChanging) {
            Timber.w("[Controller] 正在切换控制模式中，请等待")
            return
        }
        
        settingsState.updateRobotCtrlModeChangingState(true)
        
        scope.launch {
            try {
                val success = zmqClient.setControlMode(controlMode)
                if (success) {
                    Timber.i("[Controller] 控制模式设置请求已发送: $controlMode")
                    // 实际控制模式更新由消息回调处理
                } else {
                    settingsState.updateRobotCtrlModeChangingState(false)
                    Timber.e("[Controller] 控制模式设置失败: $controlMode")
                }
            } catch (e: Exception) {
                settingsState.updateRobotCtrlModeChangingState(false)
                Timber.e(e, "[Controller] 设置控制模式异常: $controlMode")
            }
        }
    }
    
    /**
     * 更新左摇杆（移动控制：vx, vy）
     */
    override fun updateLeftJoystick(joystickValue: JoystickValue) {
        currentLeftJoystick = joystickValue
        Timber.v("[Controller] 左摇杆更新: vx=${joystickValue.x}, vy=${joystickValue.y}")
    }
    
    /**
     * 更新右摇杆（转向控制：yawRate）
     */
    override fun updateRightJoystick(joystickValue: JoystickValue) {
        currentRightJoystick = joystickValue
        Timber.v("[Controller] 右摇杆更新: yawRate=${joystickValue.x}")
    }
    
    /**
     * 左摇杆释放回调
     */
    override fun onLeftJoystickReleased() {
        currentLeftJoystick = JoystickValue.ZERO
        Timber.d("[Controller] 左摇杆已释放")
    }
    
    /**
     * 右摇杆释放回调
     */
    override fun onRightJoystickReleased() {
        currentRightJoystick = JoystickValue.ZERO
        Timber.d("[Controller] 右摇杆已释放")
    }
    
    /**
     * 切换狂暴模式
     */
    override fun toggleRageMode() {
        settingsState.toggleRageMode()
        // 自动保存更新后的设置
        saveSettings(settingsState.settings)
        val mode = if (settingsState.settings.isRageModeEnabled) "狂暴模式" else "普通模式"
        Timber.i("[Controller] 已切换到${mode}并保存设置")
    }
    
    /**
     * 更新设置
     */
    override fun updateSettings(settings: AppSettings) {
        settingsState.updateSettings(settings)
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
    
    /**
     * 开始速度发送循环
     */
    private fun startVelocityLoop() {
        stopVelocityLoop()
        
        velocitySendJob = scope.launch {
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有在手动模式下才发送速度指令
                    if (settingsState.robotMode == Mode.MODE_MANUAL) {
                        // 检查是否有摇杆被按下（不在中心位置）
                        val leftJoystickPressed = !currentLeftJoystick.isCenter
                        val rightJoystickPressed = !currentRightJoystick.isCenter
                        
                        // 只有当至少有一个摇杆被按下时才发送速度指令
                        if (leftJoystickPressed || rightJoystickPressed) {
                            // 计算速度参数
                            val maxSpeed = if (settingsState.settings.isRageModeEnabled) 2f else 1f
                            val vx = currentLeftJoystick.x * maxSpeed
                            val vy = currentLeftJoystick.y * maxSpeed
                            val yawRate = currentRightJoystick.x * maxSpeed // 使用右摇杆的X轴作为角速度
                            
                            // 发送速度指令
                            zmqClient.sendVelocityCommand(vx, vy, yawRate)
                            Timber.v("[Controller] 发送速度指令: vx=$vx, vy=$vy, yawRate=$yawRate")
                            lastCommandSent = true
                        } else if (lastCommandSent) {
                            // 只有之前发送过指令，且现在摇杆都在中心位置时，才发送一次停止指令
                            zmqClient.sendVelocityCommand(0f, 0f, 0f)
                            Timber.v("[Controller] 发送停止指令")
                            lastCommandSent = false
                        }
                        // 如果摇杆都在中心位置且之前没有发送过指令，则不发送任何指令
                    }
                    
                    delay(50) // 20Hz发送频率
                    
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
        velocitySendJob?.cancel()
        velocitySendJob = null
        lastCommandSent = false  // 重置命令发送标志
    }
    
    /**
     * 清理资源
     */
    override fun cleanup() {
        disconnect()
        supervisorJob.cancel()
    }
}

