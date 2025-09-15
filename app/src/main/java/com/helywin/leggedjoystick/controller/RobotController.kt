/*********************************************************************************
 * FileName: RobotController.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 机器人控制器，管理ZMQ连接和机器人状态
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.controller

import android.content.Context
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.data.ControlMode
import com.helywin.leggedjoystick.data.RobotMode
import com.helywin.leggedjoystick.data.SettingsState
import com.helywin.leggedjoystick.data.SettingsManager
import com.helywin.leggedjoystick.ui.joystick.JoystickValue
import com.helywin.leggedjoystick.zmq.ClientType
import com.helywin.leggedjoystick.zmq.HighLevelZmqClient
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * 机器人控制器
 */
class RobotController(private val context: Context) {
    private var zmqClient: HighLevelZmqClient? = null
    private var controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var batteryUpdateJob: Job? = null
    private var zeroSpeedJob: Job? = null
    private var connectionJob: Job? = null
    private var statusUpdateJob: Job? = null
    
    // 设置管理器
    private val settingsManager = SettingsManager(context)
    
    val settingsState = SettingsState()
    
    // 当前运动状态
    private var lastMovementTime = 0L
    private var isCurrentlyMoving = false
    
    init {
        // 初始化时加载保存的设置
        loadSettings()
    }
    
    /**
     * 异步连接到机器人，支持超时和中断
     */
    fun connectAsync(settings: AppSettings) {
        // 如果已经在连接中，先取消
        cancelConnection()
        
        settingsState.updateConnectionState(ConnectionState.CONNECTING)
        
        connectionJob = controllerScope.launch {
            try {
                val endpoint = "tcp://${settings.zmqIp}:${settings.zmqPort}"
                Timber.i("开始连接到机器人: $endpoint")
                
                // 使用withTimeout来设置连接超时
                val success = withTimeout(10000L) { // 10秒超时
                    val client = HighLevelZmqClient(ClientType.REMOTE_CONTROLLER, endpoint)
                    val connected = client.connect()
                    if (connected) {
                        zmqClient = client
                        true
                    } else {
                        false
                    }
                }
                
                if (success) {
                    settingsState.updateConnectionState(ConnectionState.CONNECTED)
                    startHeartbeat()
                    startBatteryUpdate()
                    startStatusUpdate()
                    Timber.i("成功连接到机器人: $endpoint")
                } else {
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    Timber.e("连接机器人失败: $endpoint")
                }
                
            } catch (e: TimeoutCancellationException) {
                settingsState.updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)
                Timber.e("连接机器人超时")
            } catch (e: CancellationException) {
                settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
                Timber.i("连接已被用户取消")
            } catch (e: Exception) {
                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                Timber.e(e, "连接机器人异常")
            }
        }
    }
    
    /**
     * 取消正在进行的连接
     */
    fun cancelConnection() {
        connectionJob?.cancel()
        connectionJob = null
        if (settingsState.connectionState == ConnectionState.CONNECTING) {
            settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
            Timber.i("连接已取消")
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        cancelConnection() // 取消正在进行的连接
        stopHeartbeat()
        stopBatteryUpdate()
        stopStatusUpdate()
        stopZeroSpeedSending()
        
        zmqClient?.disconnect()
        zmqClient = null
        settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
        Timber.i("已断开机器人连接")
    }
    
    /**
     * 更新设置
     */
    fun updateSettings(settings: AppSettings) {
        settingsState.updateSettings(settings)
    }
    
    /**
     * 设置机器人模式
     */
    fun setRobotMode(mode: RobotMode) {
        if (!settingsState.isConnected || settingsState.isRobotModeChanging) return
        
        settingsState.updateRobotModeChangingState(true)
        controllerScope.launch {
            try {
                val success = when (mode) {
                    RobotMode.PASSIVE -> zmqClient?.passive() != 0u
                    RobotMode.LIE_DOWN -> zmqClient?.lieDown() != 0u
                    RobotMode.STAND -> zmqClient?.standUp() != 0u
                }
                
                if (success) {
                    Timber.i("机器人模式切换请求已发送: ${mode.displayName}")
                    // 保存模式到文件
                    saveRobotMode(mode)
                    // 不立即更新状态，等待状态查询确认
                } else {
                    settingsState.updateRobotModeChangingState(false)
                    Timber.e("机器人模式切换失败: ${mode.displayName}")
                }
            } catch (e: Exception) {
                settingsState.updateRobotModeChangingState(false)
                Timber.e(e, "切换机器人模式失败")
            }
        }
    }
    
    /**
     * 设置控制模式
     */
    fun setControlMode(mode: ControlMode) {
        if (!settingsState.isConnected) return
        
        controllerScope.launch {
            try {
                val success = zmqClient?.setMode(mode.value) ?: false
                if (success) {
                    settingsState.updateControlMode(mode)
                    // 保存控制模式到文件
                    saveControlMode(mode)
                    Timber.i("控制模式已切换到: ${mode.displayName}")
                } else {
                    Timber.e("切换控制模式失败: ${mode.displayName}")
                }
            } catch (e: Exception) {
                Timber.e(e, "切换控制模式失败")
            }
        }
    }
    
    /**
     * 控制机器人移动
     */
    fun moveRobot(joystickValue: JoystickValue) {
        if (!settingsState.isConnected) return
        
        controllerScope.launch {
            try {
                val maxSpeed = if (settingsState.settings.isRageModeEnabled) 2f else 1f
                val vx = joystickValue.x * maxSpeed
                val vy = joystickValue.y * maxSpeed
                val yawRate = 0f // 摇杆控制不包含旋转，可以根据需要调整
                
                zmqClient?.move(vx, vy, yawRate)
                
                // 更新运动状态
                val isMoving = abs(vx) > 0.01f || abs(vy) > 0.01f
                if (isMoving) {
                    lastMovementTime = System.currentTimeMillis()
                    isCurrentlyMoving = true
                    stopZeroSpeedSending() // 停止发送零速度
                } else if (isCurrentlyMoving) {
                    // 刚停止移动，开始发送零速度
                    startZeroSpeedSending()
                    isCurrentlyMoving = false
                }
                
            } catch (e: Exception) {
                Timber.e(e, "控制机器人移动失败")
            }
        }
    }
    
    /**
     * 摇杆释放回调，开始发送零速度
     */
    fun onJoystickReleased() {
        if (!settingsState.isConnected) return
        
        Timber.d("摇杆已释放，开始发送零速度")
        startZeroSpeedSending()
    }
    
    /**
     * 切换狂暴模式
     */
    fun toggleRageMode() {
        settingsState.toggleRageMode()
        val mode = if (settingsState.settings.isRageModeEnabled) "狂暴模式" else "普通模式"
        Timber.i("已切换到$mode")
    }
    
    /**
     * 开始心跳
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = controllerScope.launch {
            while (isActive) {
                try {
                    val success = zmqClient?.sendHeartbeat() ?: false
                    if (!success) {
                        Timber.w("心跳发送失败")
                        settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                        break
                    }
                    delay(1000) // 1秒心跳间隔
                } catch (e: Exception) {
                    Timber.e(e, "心跳异常")
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    break
                }
            }
        }
    }
    
    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /**
     * 开始电量更新
     */
    private fun startBatteryUpdate() {
        batteryUpdateJob?.cancel()
        batteryUpdateJob = controllerScope.launch {
            while (isActive) {
                try {
                    val batteryLevel = zmqClient?.getBatteryPower()?.toInt() ?: 0
                    settingsState.updateBatteryLevel(batteryLevel)
                    delay(10000) // 10秒更新一次电量
                } catch (e: Exception) {
                    Timber.e(e, "更新电量失败")
                    delay(10000)
                }
            }
        }
    }
    
    /**
     * 停止电量更新
     */
    private fun stopBatteryUpdate() {
        batteryUpdateJob?.cancel()
        batteryUpdateJob = null
    }
    
    /**
     * 开始状态更新（每1秒获取当前模式和控制模式）
     */
    private fun startStatusUpdate() {
        statusUpdateJob?.cancel()
        statusUpdateJob = controllerScope.launch {
            while (isActive) {
                try {
                    // 获取当前控制模式
                    val currentControlMode = zmqClient?.getCurrentMode() ?: "remote_controller"
                    val controlMode = when (currentControlMode) {
                        "navigation" -> ControlMode.AUTO      // 导航模式（自动）
                        "remote_controller" -> ControlMode.MANUAL  // 遥控器模式（手动）
                        else -> ControlMode.MANUAL             // 默认为手动模式
                    }
                    settingsState.updateControlMode(controlMode)
                    
                    // 获取当前机器人控制模式
                    val currentCtrlMode = zmqClient?.getCurrentCtrlmode()?.toInt() ?: 0
                    val robotMode = when (currentCtrlMode) {
                        0 -> RobotMode.PASSIVE    // 阻尼模式
                        1 -> RobotMode.LIE_DOWN   // 趴下模式
                        2 -> RobotMode.STAND      // 站立模式
                        else -> settingsState.robotMode // 保持当前状态
                    }
                    
                    // 只有在状态真正改变时才更新，这样可以清除changing状态
                    if (robotMode != settingsState.robotMode || settingsState.isRobotModeChanging) {
                        settingsState.updateRobotMode(robotMode)
                    }
                    
                    delay(1000) // 1秒更新一次
                } catch (e: Exception) {
                    Timber.e(e, "更新状态失败")
                    delay(1000)
                }
            }
        }
    }
    
    /**
     * 停止状态更新
     */
    private fun stopStatusUpdate() {
        statusUpdateJob?.cancel()
        statusUpdateJob = null
    }
    
    /**
     * 开始发送零速度（持续0.5秒）
     */
    private fun startZeroSpeedSending() {
        stopZeroSpeedSending() // 先停止之前的任务
        
        zeroSpeedJob = controllerScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startTime < 500) {
                    // 检查是否有新的运动指令
                    if (isCurrentlyMoving) {
                        Timber.d("检测到新的运动指令，停止发送零速度")
                        return@launch
                    }
                    
                    zmqClient?.move(0f, 0f, 0f)
                    delay(50) // 20Hz频率
                }
                Timber.d("零速度发送完成")
            } catch (e: Exception) {
                Timber.e(e, "发送零速度失败")
            }
        }
    }
    
    /**
     * 停止发送零速度
     */
    private fun stopZeroSpeedSending() {
        zeroSpeedJob?.cancel()
        zeroSpeedJob = null
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnect()
        controllerScope.cancel()
    }
    
    /**
     * 从文件加载设置
     */
    private fun loadSettings() {
        try {
            val settings = settingsManager.loadSettings()
            settingsState.updateSettings(settings)
            
            val robotMode = settingsManager.loadRobotMode()
            settingsState.updateRobotMode(robotMode)
            
            val controlMode = settingsManager.loadControlMode()
            settingsState.updateControlMode(controlMode)
            
            Timber.d("配置加载完成: IP=${settings.zmqIp}, Port=${settings.zmqPort}")
        } catch (e: Exception) {
            Timber.e(e, "加载配置失败")
        }
    }
    
    /**
     * 保存应用设置到文件
     */
    fun saveAppSettings(settings: AppSettings) {
        try {
            settingsManager.saveSettings(settings)
            settingsState.updateSettings(settings)
            Timber.d("应用设置已保存")
        } catch (e: Exception) {
            Timber.e(e, "保存应用设置失败")
        }
    }
    
    /**
     * 保存机器人模式到文件
     */
    fun saveRobotMode(mode: RobotMode) {
        try {
            settingsManager.saveRobotMode(mode)
            Timber.d("机器人模式已保存: ${mode.displayName}")
        } catch (e: Exception) {
            Timber.e(e, "保存机器人模式失败")
        }
    }
    
    /**
     * 保存控制模式到文件
     */
    fun saveControlMode(mode: ControlMode) {
        try {
            settingsManager.saveControlMode(mode)
            Timber.d("控制模式已保存: ${mode.displayName}")
        } catch (e: Exception) {
            Timber.e(e, "保存控制模式失败")
        }
    }
    
    /**
     * 获取当前设置
     */
    fun getCurrentSettings(): AppSettings {
        return settingsState.settings
    }
    
    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(): Boolean {
        return settingsManager.isFirstLaunch()
    }
    
    /**
     * 检查是否连接
     */
    fun isConnected(): Boolean = settingsState.isConnected
}