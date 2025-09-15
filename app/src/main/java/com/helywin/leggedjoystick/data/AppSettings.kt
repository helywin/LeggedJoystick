/*********************************************************************************
 * FileName: AppSettings.kt
 * Author: helywin <jiang770882022@hotmail.com>
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
)

/**
 * 机器人模式枚举
 */
enum class RobotMode(val displayName: String, val value: String) {
    PRONE("阻尼模式", "passive"),
    CROUCH("趴下模式", "lieDown"),
    STAND("站立模式", "stand")
}

/**
 * 设置状态管理类
 */
class SettingsState {
    var settings by mutableStateOf(AppSettings())
        private set
    
    var robotMode by mutableStateOf(RobotMode.STAND)
        private set
        
    var isConnected by mutableStateOf(false)
        private set
        
    var batteryLevel by mutableStateOf(80)
        private set
    
    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        Timber.d("Settings updated: $newSettings")
    }
    
    fun updateRobotMode(mode: RobotMode) {
        robotMode = mode
        Timber.d("Robot mode updated: ${mode.displayName}")
    }
    
    fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        Timber.d("Connection status updated: $connected")
    }
    
    fun updateBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
        Timber.d("Battery level updated: $batteryLevel%")
    }
    
    fun toggleRageMode() {
        settings = settings.copy(isRageModeEnabled = !settings.isRageModeEnabled)
        Timber.d("Rage mode toggled: ${settings.isRageModeEnabled}")
    }
}