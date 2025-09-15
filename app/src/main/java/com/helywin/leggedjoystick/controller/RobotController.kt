/*********************************************************************************
 * FileName: RobotController.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 机器人控制器，管理ZMQ连接和机器人状态
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.controller

import androidx.compose.runtime.*
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.RobotMode
import com.helywin.leggedjoystick.data.SettingsState
import com.helywin.leggedjoystick.ui.joystick.JoystickValue
import com.helywin.leggedjoystick.zmq.ClientType
import com.helywin.leggedjoystick.zmq.HighLevelZmqClient
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * 机器人控制器
 */
class RobotController {
    private var zmqClient: HighLevelZmqClient? = null
    private var controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var batteryUpdateJob: Job? = null
    private var zeroSpeedJob: Job? = null
    
    val settingsState = SettingsState()
    
    // 当前运动状态
    private var lastMovementTime = 0L
    private var isCurrentlyMoving = false
    
    /**
     * 连接到机器人
     */
    fun connect(settings: AppSettings): Boolean {
        disconnect() // 先断开现有连接
        
        val endpoint = "tcp://${settings.zmqIp}:${settings.zmqPort}"
        zmqClient = HighLevelZmqClient(ClientType.REMOTE_CONTROLLER, endpoint)
        
        val success = zmqClient?.connect() ?: false
        settingsState.updateConnectionStatus(success)
        
        if (success) {
            startHeartbeat()
            startBatteryUpdate()
            Timber.i("成功连接到机器人: $endpoint")
        } else {
            Timber.e("连接机器人失败: $endpoint")
        }
        
        return success
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        stopHeartbeat()
        stopBatteryUpdate()
        stopZeroSpeedSending()
        
        zmqClient?.disconnect()
        zmqClient = null
        settingsState.updateConnectionStatus(false)
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
        controllerScope.launch {
            try {
                when (mode) {
                    RobotMode.PRONE -> zmqClient?.passive()
                    RobotMode.CROUCH -> zmqClient?.lieDown() 
                    RobotMode.STAND -> zmqClient?.standUp()
                }
                settingsState.updateRobotMode(mode)
                Timber.i("机器人模式已切换到: ${mode.displayName}")
            } catch (e: Exception) {
                Timber.e(e, "切换机器人模式失败")
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
                        settingsState.updateConnectionStatus(false)
                        break
                    }
                    delay(5000) // 5秒心跳间隔
                } catch (e: Exception) {
                    Timber.e(e, "心跳异常")
                    settingsState.updateConnectionStatus(false)
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
     * 检查是否连接
     */
    fun isConnected(): Boolean = settingsState.isConnected
}