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
import com.helywin.leggedjoystick.zmq.HighLevelZmqClient
import com.helywin.leggedjoystick.zmq.ClientType
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * 机器人控制器
 */
class RobotController(private val context: Context) {
    private var zmqClient: HighLevelZmqClient = HighLevelZmqClient()
    private var controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null
    private var statusUpdateJob: Job? = null
    private var healthCheckJob: Job? = null
    private var continuousSpeedJob: Job? = null // 持续速度发送任务
    private var heartbeatThread: Thread? = null // 心跳独立线程
    private var heartbeatRunning = false // 心跳线程运行状态

    // 设置管理器
    private val settingsManager = SettingsManager(context)

    val settingsState = SettingsState()

    // 摇杆状态
    private var currentLeftJoystick = JoystickValue.ZERO  // 左摇杆：vx, vy
    private var currentRightJoystick = JoystickValue.ZERO  // 右摇杆：yawRate

    init {
        // 初始化时加载保存的设置
        loadSettings()
    }

    /**
     * 异步连接到机器人，支持超时、重试和中断
     */
    fun connectAsync(settings: AppSettings) {
        // 如果已经在连接中，先取消
        cancelConnection()

        settingsState.updateConnectionState(ConnectionState.CONNECTING)

        connectionJob = controllerScope.launch {
            val endpoint = "tcp://${settings.zmqIp}:${settings.zmqPort}"
            val maxRetries = 3
            val retryDelayMs = 2000L // 2秒重试间隔
            val connectionTimeoutMs = 8000L // 8秒连接超时

            Timber.i("开始连接到机器人: $endpoint (最多重试 $maxRetries 次)")

            repeat(maxRetries) { attempt ->
                if (!isActive) {
                    Timber.i("连接任务已取消")
                    return@launch
                }

                try {
                    Timber.i("连接尝试 ${attempt + 1}/$maxRetries: $endpoint")

                    // 使用withTimeout来设置连接超时
                    val success = withTimeout(connectionTimeoutMs) {
                        zmqClient.connect(ClientType.REMOTE_CONTROLLER, endpoint)
                    }

                    if (success) {
                        settingsState.updateConnectionState(ConnectionState.CONNECTED)
                        startHeartbeat()

                        // 只在手动模式下启动持续速度发送
                        if (settingsState.robotMode == RobotMode.MANUAL) {
                            startContinuousSpeedSending()
                            Timber.i("手动模式：已启动持续速度发送")
                        } else {
                            Timber.i("非手动模式：不启动持续速度发送")
                        }

                        Timber.i("成功连接到机器人: $endpoint (尝试 ${attempt + 1}/$maxRetries)")
                        return@launch // 成功连接，退出重试循环
                    } else {
                        Timber.w("连接尝试 ${attempt + 1}/$maxRetries 失败: $endpoint")
                        if (attempt == maxRetries - 1) {
                            // 最后一次尝试失败
                            settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                            Timber.e("所有连接尝试均失败: $endpoint")
                        } else {
                            // 等待重试
                            delay(retryDelayMs)
                        }
                    }

                } catch (e: TimeoutCancellationException) {
                    Timber.w("连接尝试 ${attempt + 1}/$maxRetries 超时: $endpoint")
                    if (attempt == maxRetries - 1) {
                        // 最后一次尝试超时
                        settingsState.updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)
                        Timber.e("连接机器人超时，所有重试均失败: $endpoint")
                    } else {
                        // 等待重试
                        delay(retryDelayMs)
                    }
                } catch (e: CancellationException) {
                    settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
                    Timber.i("连接已被用户取消")
                    return@launch
                } catch (e: Exception) {
                    Timber.w(e, "连接尝试 ${attempt + 1}/$maxRetries 异常: $endpoint")
                    if (attempt == maxRetries - 1) {
                        // 最后一次尝试异常
                        settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                        Timber.e(e, "连接机器人异常，所有重试均失败: $endpoint")
                    } else {
                        // 等待重试
                        delay(retryDelayMs)
                    }
                }
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
        stopContinuousSpeedSending() // 停止持续速度发送

        zmqClient.disconnect()
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
                    RobotCtrlMode.PASSIVE -> zmqClient.passive() == 0u     // 0表示成功
                    RobotCtrlMode.LIE_DOWN -> zmqClient.lieDown() == 0u    // 0表示成功
                    RobotCtrlMode.STAND -> zmqClient.standUp() == 0u       // 0表示成功
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
                val success = zmqClient.setMode(mode)
                if (success) {
                    settingsState.updateMode(mode)

                    // 根据模式控制速度发送
                    when (mode) {
                        RobotMode.MANUAL -> {
                            // 切换到手动模式，启动持续速度发送
                            if (continuousSpeedJob?.isActive != true) {
                                startContinuousSpeedSending()
                                Timber.i("切换到手动模式，启动持续速度发送")
                            }
                        }

                        RobotMode.AUTO -> {
                            // 切换到自动模式，停止持续速度发送
                            stopContinuousSpeedSending()
                            Timber.i("切换到自动模式，停止持续速度发送")
                        }
                    }

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
     * 更新左摇杆（移动控制：vx, vy）
     */
    fun updateLeftJoystick(joystickValue: JoystickValue) {
        currentLeftJoystick = joystickValue
        Timber.d("左摇杆更新: vx=${joystickValue.x}, vy=${joystickValue.y}")
    }

    /**
     * 更新右摇杆（转向控制：yawRate）
     */
    fun updateRightJoystick(joystickValue: JoystickValue) {
        currentRightJoystick = joystickValue
        Timber.d("右摇杆更新: yawRate=${joystickValue.x}")
    }

    /**
     * 左摇杆释放回调
     */
    fun onLeftJoystickReleased() {
        currentLeftJoystick = JoystickValue.ZERO
        Timber.d("左摇杆已释放")
    }

    /**
     * 右摇杆释放回调
     */
    fun onRightJoystickReleased() {
        currentRightJoystick = JoystickValue.ZERO
        Timber.d("右摇杆已释放")
    }

    /**
     * 开始持续速度发送（20Hz）
     * 只在手动模式下运行
     */
    private fun startContinuousSpeedSending() {
//        continuousSpeedJob?.cancel()
//        continuousSpeedJob = controllerScope.launch {
//            Timber.i("持续速度发送任务已启动")
//            while (isActive && settingsState.isConnected) {
//                try {
//                    // 检查连接状态和模式
//                    if (!settingsState.isConnected) {
//                        Timber.d("连接已断开，停止速度发送")
//                        break
//                    }
//
//                    // 检查是否为手动模式
//                    if (settingsState.robotMode != RobotMode.MANUAL) {
//                        Timber.i("当前为非手动模式(${settingsState.robotMode.displayName})，停止速度发送")
//                        break
//                    }
//
//                    // 计算速度参数
//                    val maxSpeed = if (settingsState.settings.isRageModeEnabled) 2f else 1f
//                    val vx = currentLeftJoystick.x * maxSpeed
//                    val vy = currentLeftJoystick.y * maxSpeed
//                    val yawRate = currentRightJoystick.x * maxSpeed // 使用右摇杆的X轴作为角速度
//
//                    // 发送移动指令
//                    val result = zmqClient.move(vx, vy, yawRate)
//                    if (result != 0u) {
//                        Timber.v("速度指令发送失败: vx=$vx, vy=$vy, yawRate=$yawRate, result=$result")
//                    } else {
//                        // 只有在有运动时才打印日志，避免日志过多
//                        val isMoving = abs(vx) > 0.01f || abs(vy) > 0.01f || abs(yawRate) > 0.01f
//                        if (isMoving) {
//                            Timber.v("速度指令已发送: vx=$vx, vy=$vy, yawRate=$yawRate")
//                        }
//                    }
//
//                    delay(50) // 20Hz = 1000ms / 20 = 50ms
//                } catch (e: Exception) {
//                    Timber.e(e, "发送速度指令失败")
//                    // 速度发送异常可能表示连接问题
//                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
//                    break
//                }
//            }
//            Timber.i("持续速度发送任务已结束")
//        }
    }

    /**
     * 停止持续速度发送
     */
    private fun stopContinuousSpeedSending() {
        continuousSpeedJob?.cancel()
        continuousSpeedJob = null
        // 重置摇杆状态
        currentLeftJoystick = JoystickValue.ZERO
        currentRightJoystick = JoystickValue.ZERO
        Timber.i("持续速度发送已停止")
    }

    // ========== 兼容性方法（向后兼容） ==========

    /**
     * 控制机器人移动（兼容旧接口）
     * @deprecated 使用 updateLeftJoystick 和 updateRightJoystick 代替
     */
    @Deprecated("使用 updateLeftJoystick 和 updateRightJoystick 代替")
    fun moveRobot(joystickValue: JoystickValue) {
        updateLeftJoystick(joystickValue)
    }

    /**
     * 摇杆释放回调（兼容旧接口）
     * @deprecated 使用 onLeftJoystickReleased 和 onRightJoystickReleased 代替
     */
    @Deprecated("使用 onLeftJoystickReleased 和 onRightJoystickReleased 代替")
    fun onJoystickReleased() {
        onLeftJoystickReleased()
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
     * 开始心跳（使用独立线程）
     */
    private fun startHeartbeat() {
        stopHeartbeat() // 先停止现有的心跳

        heartbeatRunning = true
        heartbeatThread = Thread({
            var consecutiveFailures = 0
            val maxFailures = 3 // 连续失败3次后断开连接
            val heartbeatInterval = 1000L // 1秒心跳间隔

            Timber.i("心跳线程已启动，间隔${heartbeatInterval}ms")

            while (heartbeatRunning && settingsState.isConnected) {
                try {
                    // 双重检查连接状态
                    if (!settingsState.isConnected || !heartbeatRunning) {
                        Timber.i("连接已断开或心跳已停止，退出心跳线程")
                        break
                    }

                    // 首先进行本地连接检查
                    if (!zmqClient.isConnected()) {
                        consecutiveFailures++
                        Timber.w("本地连接检查失败 ($consecutiveFailures/$maxFailures)")

                        if (consecutiveFailures >= maxFailures) {
                            Timber.e("本地连接检查连续失败，断开连接")
                            controllerScope.launch {
                                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                            }
                            break
                        }
                    } else {
                        // 发送心跳
                        val success = zmqClient.sendHeartbeat()
                        if (success) {
                            consecutiveFailures = 0 // 重置失败计数
                            Timber.v("心跳发送成功")

                        } else {
                            consecutiveFailures++
                            Timber.w("心跳发送失败 ($consecutiveFailures/$maxFailures)")

                            if (consecutiveFailures >= maxFailures) {
                                Timber.e("心跳连续失败，断开连接")
                                controllerScope.launch {
                                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                }
                                break
                            }
                        }

                        val battery = zmqClient.getBatteryPower();
                        if (battery != null) {
                            settingsState.updateBatteryLevel(battery.toInt())
                        } else {
                            Timber.w("获取电量失败，可能连接已断开")
                            // 获取失败时检查连接状态
                            if (!zmqClient.isConnected()) {
                                controllerScope.launch {
                                    settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                }
                                break
                            }
                        }

                        val controlMode = zmqClient.getCurrentMode()
                        if (controlMode != null) {
                            // 只有在状态真正改变时才更新，这样可以清除changing状态
                            if (controlMode != settingsState.robotMode || settingsState.isRobotModeChanging) {
                                val previousMode = settingsState.robotMode
                                settingsState.updateMode(controlMode)
                                Timber.v("控制模式更新: ${controlMode.displayName}")

                                // 检查模式是否发生变化，相应地启动或停止速度发送
                                if (previousMode != controlMode) {
                                    when (controlMode) {
                                        RobotMode.MANUAL -> {
                                            if (continuousSpeedJob?.isActive != true) {
                                                startContinuousSpeedSending()
                                                Timber.i("模式切换到手动，启动持续速度发送")
                                            }
                                        }

                                        RobotMode.AUTO -> {
                                            if (continuousSpeedJob?.isActive == true) {
                                                stopContinuousSpeedSending()
                                                Timber.i("模式切换到自动，停止持续速度发送")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Timber.w("获取控制模式失败，可能连接已断开")
                            // 获取失败时检查连接状态
                            if (!zmqClient.isConnected()) {
                                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                break
                            }
                        }

                        val currentCtrlMode = zmqClient.getCurrentCtrlmode()
                        if (currentCtrlMode != null) {
                            // 只有在状态真正改变时才更新，这样可以清除changing状态
                            if (currentCtrlMode != settingsState.robotCtrlMode || settingsState.isRobotCtrlModeChanging) {
                                settingsState.updateRobotCtrlMode(currentCtrlMode)
                                Timber.v("机器人模式更新: ${currentCtrlMode.displayName}")
                            }
                        } else {
                            Timber.w("获取机器人控制模式失败，可能连接已断开")
                            // 获取失败时检查连接状态
                            if (!zmqClient.isConnected()) {
                                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                                break
                            }
                        }
                    }

                    Thread.sleep(heartbeatInterval)
                } catch (e: InterruptedException) {
                    Timber.i("心跳线程被中断")
                    break
                } catch (e: Exception) {
                    consecutiveFailures++
                    Timber.e(e, "心跳异常 ($consecutiveFailures/$maxFailures)")

                    if (consecutiveFailures >= maxFailures) {
                        Timber.e("心跳连续异常，断开连接")
                        controllerScope.launch {
                            settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                        }
                        break
                    }

                    try {
                        Thread.sleep(heartbeatInterval)
                    } catch (ie: InterruptedException) {
                        break
                    }
                }
            }
            Timber.i("心跳线程已结束")
        }, "HeartbeatThread")

        heartbeatThread?.start()
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatRunning = false
        heartbeatThread?.interrupt()
        heartbeatThread?.let { thread ->
            try {
                thread.join(2000) // 最多等待2秒
            } catch (e: InterruptedException) {
                Timber.w("等待心跳线程结束被中断")
            }
        }
        heartbeatThread = null
        heartbeatJob?.cancel()
        heartbeatJob = null
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