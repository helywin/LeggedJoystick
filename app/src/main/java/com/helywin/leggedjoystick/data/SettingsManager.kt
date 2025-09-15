/*********************************************************************************
 * FileName: SettingsManager.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 应用设置管理器，负责配置的持久化存储
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.data

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import androidx.core.content.edit

/**
 * 设置管理器，负责配置的保存和加载
 */
class SettingsManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "legged_joystick_settings"
        private const val KEY_ZMQ_IP = "zmq_ip"
        private const val KEY_ZMQ_PORT = "zmq_port"
        private const val KEY_RAGE_MODE_ENABLED = "rage_mode_enabled"
        private const val KEY_ROBOT_MODE = "robot_mode"
        private const val KEY_CONTROL_MODE = "control_mode"
        
        // 默认配置
        private const val DEFAULT_ZMQ_IP = "127.0.0.1"
        private const val DEFAULT_ZMQ_PORT = 33445
        private const val DEFAULT_RAGE_MODE_ENABLED = false
        private val DEFAULT_ROBOT_MODE = RobotMode.STAND
        private val DEFAULT_CONTROL_MODE = ControlMode.MANUAL
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 保存应用设置
     */
    fun saveSettings(settings: AppSettings) {
        try {
            sharedPreferences.edit {
                putString(KEY_ZMQ_IP, settings.zmqIp)
                putInt(KEY_ZMQ_PORT, settings.zmqPort)
                putBoolean(KEY_RAGE_MODE_ENABLED, settings.isRageModeEnabled)
                apply()
            }
            Timber.d("设置已保存: $settings")
        } catch (e: Exception) {
            Timber.e(e, "保存设置失败")
        }
    }
    
    /**
     * 加载应用设置
     */
    fun loadSettings(): AppSettings {
        return try {
            AppSettings(
                zmqIp = sharedPreferences.getString(KEY_ZMQ_IP, DEFAULT_ZMQ_IP) ?: DEFAULT_ZMQ_IP,
                zmqPort = sharedPreferences.getInt(KEY_ZMQ_PORT, DEFAULT_ZMQ_PORT),
                isRageModeEnabled = sharedPreferences.getBoolean(KEY_RAGE_MODE_ENABLED, DEFAULT_RAGE_MODE_ENABLED)
            ).also {
                Timber.d("设置已加载: $it")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载设置失败，使用默认设置")
            AppSettings() // 返回默认设置
        }
    }
    
    /**
     * 保存机器人模式
     */
    fun saveRobotMode(mode: RobotMode) {
        try {
            sharedPreferences.edit {
                putString(KEY_ROBOT_MODE, mode.value)
            }
            Timber.d("机器人模式已保存: ${mode.displayName}")
        } catch (e: Exception) {
            Timber.e(e, "保存机器人模式失败")
        }
    }
    
    /**
     * 加载机器人模式
     */
    fun loadRobotMode(): RobotMode {
        return try {
            val modeValue = sharedPreferences.getString(KEY_ROBOT_MODE, DEFAULT_ROBOT_MODE.value)
            RobotMode.entries.find { it.value == modeValue } ?: DEFAULT_ROBOT_MODE
        } catch (e: Exception) {
            Timber.e(e, "加载机器人模式失败，使用默认模式")
            DEFAULT_ROBOT_MODE
        }
    }
    
    /**
     * 保存控制模式
     */
    fun saveControlMode(mode: ControlMode) {
        try {
            sharedPreferences.edit {
                putString(KEY_CONTROL_MODE, mode.value)
            }
            Timber.d("控制模式已保存: ${mode.displayName}")
        } catch (e: Exception) {
            Timber.e(e, "保存控制模式失败")
        }
    }
    
    /**
     * 加载控制模式
     */
    fun loadControlMode(): ControlMode {
        return try {
            val modeValue = sharedPreferences.getString(KEY_CONTROL_MODE, DEFAULT_CONTROL_MODE.value)
            ControlMode.entries.find { it.value == modeValue } ?: DEFAULT_CONTROL_MODE
        } catch (e: Exception) {
            Timber.e(e, "加载控制模式失败，使用默认模式")
            DEFAULT_CONTROL_MODE
        }
    }
    
    /**
     * 清空所有设置
     */
    fun clearAllSettings() {
        try {
            sharedPreferences.edit { clear() }
            Timber.d("所有设置已清空")
        } catch (e: Exception) {
            Timber.e(e, "清空设置失败")
        }
    }
    
    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(): Boolean {
        return !sharedPreferences.contains(KEY_ZMQ_IP)
    }
}