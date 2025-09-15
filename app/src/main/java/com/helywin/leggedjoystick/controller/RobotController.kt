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
import com.helywin.leggedjoystick.data.RobotMode
import com.helywin.leggedjoystick.data.RobotCtrlMode
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
    private var healthCheckJob: Job? = null

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
                val success = withTimeout(5000L) { // 10秒超时
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
                    startHealthCheck() // 启动健康检查
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
        stopHealthCheck() // 停止健康检查
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
    fun setRobotCtrlMode(mode: RobotCtrlMode) {
        if (!settingsState.isConnected || settingsState.isRobotCtrlModeChanging) return

        settingsState.updateRobotModeChangingState(true)
        controllerScope.launch {
            try {
                val success = when (mode) {
                    RobotCtrlMode.PASSIVE -> zmqClient?.passive() == 0u     // 0表示成功
                    RobotCtrlMode.LIE_DOWN -> zmqClient?.lieDown() == 0u    // 0表示成功
                    RobotCtrlMode.STAND -> zmqClient?.standUp() == 0u       // 0表示成功
                }

                if (success) {
                    Timber.i("机器人模式切换请求已发送: ${mode.displayName}")
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
    fun setMode(mode: RobotMode) {
        if (!settingsState.isConnected) return

        controllerScope.launch {
            try {
                val success = zmqClient?.setMode(mode) ?: false
                if (success) {
                    settingsState.updateMode(mode)
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

                val result = zmqClient?.move(vx, vy, yawRate) ?: 1u  // 默认1表示失败
                if (result != 0u) {  // 0表示成功，非0表示失败
                    Timber.w("机器人移动命令发送失败或返回错误: vx=$vx, vy=$vy, yawRate=$yawRate, result=$result")
                } else {
                    Timber.d("机器人移动命令已发送: vx=$vx, vy=$vy, yawRate=$yawRate")
                }

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
            var consecutiveFailures = 0
            val maxFailures = 3 // 连续失败3次后断开连接
            
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有在连接状态下才发送心跳
                    if (!settingsState.isConnected) {
                        Timber.d("连接已断开，停止心跳")
                        break
                    }

                    val success = zmqClient?.sendHeartbeat() ?: false
                    if (!success) {
                        consecutiveFailures++
                        Timber.w("心跳发送失败 ($consecutiveFailures/$maxFailures)")
                        
                        if (consecutiveFailures >= maxFailures) {
                            Timber.e("心跳连续失败，断开连接")
                            settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                            break
                        }
                    } else {
                        consecutiveFailures = 0 // 重置失败计数
                        Timber.d("心跳发送成功")
                    }
                    
                    delay(1000) // 1秒心跳间隔
                } catch (e: Exception) {
                    consecutiveFailures++
                    Timber.e(e, "心跳异常 ($consecutiveFailures/$maxFailures)")
                    
                    if (consecutiveFailures >= maxFailures) {
                        settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                        break
                    }
                    
                    delay(1000)
                }
            }
            Timber.d("心跳任务已结束")
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
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有在连接状态下才进行查询
                    if (!settingsState.isConnected) {
                        Timber.d("连接已断开，停止电量更新")
                        break
                    }

                    val batteryLevel = zmqClient?.getBatteryPower()?.toInt()
                    if (batteryLevel != null) {
                        settingsState.updateBatteryLevel(batteryLevel)
                    } else {
                        Timber.w("获取电量失败，可能连接已断开")
                        // 获取失败时检查连接状态
                        if (zmqClient?.isConnected() != true) {
                            settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                            break
                        }
                    }
                    
                    delay(10000) // 10秒更新一次电量
                } catch (e: Exception) {
                    Timber.e(e, "更新电量失败")
                    // 电量更新异常可能表示连接问题
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    break
                }
            }
            Timber.d("电量更新任务已结束")
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
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有在连接状态下才进行查询
                    if (!settingsState.isConnected) {
                        Timber.d("连接已断开，停止状态更新")
                        break
                    }

                    // 获取当前控制模式 (导航模式 vs 遥控器模式)
                    zmqClient?.let { client ->
                        val controlMode = client.getCurrentMode()
                        if (controlMode != null) {
                            // 只有在状态真正改变时才更新，这样可以清除changing状态
                            if (controlMode != settingsState.robotMode || settingsState.isRobotModeChanging) {
                                settingsState.updateMode(controlMode)
                                Timber.v("控制模式更新: ${controlMode.displayName}")
                            }
                        } else {
                            Timber.w("获取控制模式失败，可能连接已断开")
                            // 获取失败时检查连接状态
                            if (!client.isConnected()) {
                                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                break
                            }
                        }

                        // 获取当前机器人控制模式 (阻尼/趴下/站立)
                        val currentCtrlMode = client.getCurrentCtrlmode()
                        if (currentCtrlMode != null) {
                            // 只有在状态真正改变时才更新，这样可以清除changing状态
                            if (currentCtrlMode != settingsState.robotCtrlMode || settingsState.isRobotCtrlModeChanging) {
                                settingsState.updateRobotCtrlMode(currentCtrlMode)
                                Timber.v("机器人模式更新: ${currentCtrlMode.displayName}")
                            }
                        } else {
                            Timber.w("获取机器人控制模式失败，可能连接已断开")
                            // 获取失败时检查连接状态
                            if (!client.isConnected()) {
                                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                break
                            }
                        }
                    }

                    delay(1000) // 1秒更新一次
                } catch (e: Exception) {
                    Timber.e(e, "更新状态失败")
                    // 状态更新异常可能表示连接问题
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    break
                }
            }
            Timber.d("状态更新任务已结束")
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
     * 开始健康检查（每30秒进行一次连接状态检查）
     */
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = controllerScope.launch {
            while (isActive && settingsState.isConnected) {
                try {
                    delay(30000) // 30秒检查一次
                    
                    // 只有在连接状态下才进行健康检查
                    if (!settingsState.isConnected) {
                        Timber.d("连接已断开，停止健康检查")
                        break
                    }

                    val isHealthy = zmqClient?.performHealthCheck() ?: false
                    if (!isHealthy) {
                        Timber.w("健康检查失败，连接可能已断开")
                        settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                        break
                    } else {
                        Timber.d("健康检查通过")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "健康检查异常")
                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                    break
                }
            }
            Timber.d("健康检查任务已结束")
        }
    }

    /**
     * 停止健康检查
     */
    private fun stopHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
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