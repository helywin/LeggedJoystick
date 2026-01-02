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
        private const val KEY_RTSP_URL = "rtsp_url"
        private const val KEY_MAIN_TITLE = "main_title"
        private const val KEY_LOGO_PATH = "logo_path"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        // 默认配置
        private const val DEFAULT_ZMQ_IP = "127.0.0.1"
        private const val DEFAULT_ZMQ_PORT = 33445
        private const val DEFAULT_RAGE_MODE_ENABLED = false
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.234.1:8554/test"
        private const val DEFAULT_MAIN_TITLE = "机器狗遥控器"
        private const val DEFAULT_LOGO_PATH = ""
        private const val DEFAULT_KEEP_SCREEN_ON = true
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
                putString(KEY_RTSP_URL, settings.rtspUrl)
                putString(KEY_MAIN_TITLE, settings.mainTitle)
                putString(KEY_LOGO_PATH, settings.logoPath)
                putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
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
                isRageModeEnabled = sharedPreferences.getBoolean(KEY_RAGE_MODE_ENABLED, DEFAULT_RAGE_MODE_ENABLED),
                rtspUrl = sharedPreferences.getString(KEY_RTSP_URL, DEFAULT_RTSP_URL) ?: DEFAULT_RTSP_URL,
                mainTitle = sharedPreferences.getString(KEY_MAIN_TITLE, DEFAULT_MAIN_TITLE) ?: DEFAULT_MAIN_TITLE,
                logoPath = sharedPreferences.getString(KEY_LOGO_PATH, DEFAULT_LOGO_PATH) ?: DEFAULT_LOGO_PATH,
                keepScreenOn = sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)
            ).also {
                Timber.d("设置已加载: $it")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载设置失败，使用默认设置")
            AppSettings() // 返回默认设置
        }
    }

    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(): Boolean {
        return !sharedPreferences.contains(KEY_ZMQ_IP)
    }
}
