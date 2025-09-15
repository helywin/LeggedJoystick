/*********************************************************************************
 * FileName: AppSettings.kt
 * Au    fun updateBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
        Timber.d("Battery level updated: $batteryLevel%")
    }
}ywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 应用设置数据类
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.data

import androidx.compose.runtime.*
import timber.log.Timber

/**
 * 应用设置数据类
 */
data class AppSettings(
    val zmqIp: String = "127.0.0.1",
    val zmqPort: Int = 33445,
    val isRageModeEnabled: Boolean = false
) {
    /**
     * 创建默认设置的构造函数
     */
    constructor() : this(
        zmqIp = "127.0.0.1",
        zmqPort = 33445,
        isRageModeEnabled = false
    )
}

/**
 * 机器人模式枚举
 */
enum class RobotMode(val displayName: String, val value: String) {
    PASSIVE("阻尼模式", "passive"),
    LIE_DOWN("趴下模式", "lieDown"),
    STAND("站立模式", "stand")
}

/**
 * 控制模式枚举
 */
enum class ControlMode(val displayName: String, val value: String) {
    MANUAL("手动模式", "remote_controller"),  // 遥控器模式
    AUTO("自动模式", "navigation")            // 导航模式
}

/**
 * 连接状态枚举
 */
enum class ConnectionState(val displayName: String) {
    DISCONNECTED("已断开"),
    CONNECTING("连接中..."),
    CONNECTED("已连接"),
    CONNECTION_FAILED("连接失败"),
    CONNECTION_TIMEOUT("连接超时")
}

/**
 * 设置状态管理类
 */
class SettingsState {
    var settings by mutableStateOf(AppSettings())
        private set
    
    var robotMode by mutableStateOf(RobotMode.STAND)
        private set
        
    var controlMode by mutableStateOf(ControlMode.MANUAL)
        private set
        
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set
        
    var batteryLevel by mutableStateOf(80)
        private set
        
    var isRobotModeChanging by mutableStateOf(false)
        private set
    
    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        Timber.d("Settings updated: $newSettings")
    }
    
    fun updateRobotMode(mode: RobotMode) {
        robotMode = mode
        isRobotModeChanging = false
        Timber.d("Robot mode updated: ${mode.displayName}")
    }
    
    fun updateControlMode(mode: ControlMode) {
        controlMode = mode
        Timber.d("Control mode updated: ${mode.displayName}")
    }
    
    fun updateRobotModeChangingState(changing: Boolean) {
        isRobotModeChanging = changing
        Timber.d("Robot mode changing: $changing")
    }
    
    fun updateConnectionStatus(connected: Boolean) {
        connectionState = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        Timber.d("Connection status updated: ${connectionState.displayName}")
    }
    
    fun updateConnectionState(state: ConnectionState) {
        connectionState = state
        Timber.d("Connection state updated: ${state.displayName}")
    }
    
    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED
    
    fun updateBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
        Timber.d("Battery level updated: $batteryLevel%")
    }
    
    fun toggleRageMode() {
        settings = settings.copy(isRageModeEnabled = !settings.isRageModeEnabled)
        Timber.d("Rage mode toggled: ${settings.isRageModeEnabled}")
    }
}