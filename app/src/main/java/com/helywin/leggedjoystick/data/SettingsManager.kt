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
        private const val KEY_SPEED_LEVEL = "speed_level"
        private const val KEY_RTSP_URL = "rtsp_url"
        private const val KEY_REMOTE_INPUT_HOST = "remote_input_host"
        private const val KEY_REMOTE_INPUT_PORT = "remote_input_port"
        private const val KEY_REMOTE_INPUT_LOCAL_PORT = "remote_input_local_port"
        private const val KEY_REMOTE_INPUT_DEAD_ZONE = "remote_input_dead_zone"
        private const val KEY_REMOTE_INPUT_TIMEOUT_MS = "remote_input_timeout_ms"
        private const val KEY_REMOTE_INPUT_FORWARD_CHANNEL = "remote_input_forward_channel"
        private const val KEY_REMOTE_INPUT_FORWARD_INVERTED = "remote_input_forward_inverted"
        private const val KEY_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL = "remote_input_strafe_right_channel"
        private const val KEY_REMOTE_INPUT_STRAFE_RIGHT_INVERTED = "remote_input_strafe_right_inverted"
        private const val KEY_REMOTE_INPUT_YAW_RIGHT_CHANNEL = "remote_input_yaw_right_channel"
        private const val KEY_REMOTE_INPUT_YAW_RIGHT_INVERTED = "remote_input_yaw_right_inverted"
        private const val KEY_MAIN_TITLE = "main_title"
        private const val KEY_LOGO_PATH = "logo_path"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        // 默认配置
        private const val DEFAULT_ZMQ_IP = "192.168.234.1"
        private const val DEFAULT_ZMQ_PORT = 33445
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.234.1:8554/test"
        private const val LEGACY_REMOTE_INPUT_HOST = "192.168.144.20"
        private const val DEFAULT_REMOTE_INPUT_HOST = "127.0.0.1"
        private const val DEFAULT_REMOTE_INPUT_PORT = 19856
        private const val DEFAULT_REMOTE_INPUT_LOCAL_PORT = 0
        private const val DEFAULT_REMOTE_INPUT_DEAD_ZONE = 0.06f
        private const val DEFAULT_REMOTE_INPUT_TIMEOUT_MS = 300L
        private const val DEFAULT_REMOTE_INPUT_FORWARD_CHANNEL = 3
        private const val DEFAULT_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL = 4
        private const val DEFAULT_REMOTE_INPUT_YAW_RIGHT_CHANNEL = 1
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
                putString(KEY_SPEED_LEVEL, settings.speedLevel.name)
                putString(KEY_RTSP_URL, settings.rtspUrl)
                putString(KEY_REMOTE_INPUT_HOST, settings.remoteInputHost)
                putInt(KEY_REMOTE_INPUT_PORT, settings.remoteInputPort)
                putInt(KEY_REMOTE_INPUT_LOCAL_PORT, settings.remoteInputLocalPort)
                putFloat(KEY_REMOTE_INPUT_DEAD_ZONE, settings.remoteInputDeadZone)
                putLong(KEY_REMOTE_INPUT_TIMEOUT_MS, settings.remoteInputTimeoutMs)
                putInt(KEY_REMOTE_INPUT_FORWARD_CHANNEL, settings.remoteInputForwardChannel)
                putBoolean(KEY_REMOTE_INPUT_FORWARD_INVERTED, settings.remoteInputForwardInverted)
                putInt(KEY_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL, settings.remoteInputStrafeRightChannel)
                putBoolean(KEY_REMOTE_INPUT_STRAFE_RIGHT_INVERTED, settings.remoteInputStrafeRightInverted)
                putInt(KEY_REMOTE_INPUT_YAW_RIGHT_CHANNEL, settings.remoteInputYawRightChannel)
                putBoolean(KEY_REMOTE_INPUT_YAW_RIGHT_INVERTED, settings.remoteInputYawRightInverted)
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
            val speedLevelName = sharedPreferences.getString(KEY_SPEED_LEVEL, SpeedLevel.SLOW.name)
            val speedLevel = try {
                SpeedLevel.valueOf(speedLevelName ?: SpeedLevel.SLOW.name)
            } catch (e: IllegalArgumentException) {
                Timber.w("无效的速度档位: $speedLevelName，使用默认值")
                SpeedLevel.SLOW
            }
            val remoteInputHost = sharedPreferences.getString(
                KEY_REMOTE_INPUT_HOST,
                DEFAULT_REMOTE_INPUT_HOST
            ) ?: DEFAULT_REMOTE_INPUT_HOST

            AppSettings(
                zmqIp = sharedPreferences.getString(KEY_ZMQ_IP, DEFAULT_ZMQ_IP) ?: DEFAULT_ZMQ_IP,
                zmqPort = sharedPreferences.getInt(KEY_ZMQ_PORT, DEFAULT_ZMQ_PORT),
                speedLevel = speedLevel,
                rtspUrl = sharedPreferences.getString(KEY_RTSP_URL, DEFAULT_RTSP_URL) ?: DEFAULT_RTSP_URL,
                remoteInputHost = remoteInputHost.migrateLegacyRemoteInputHost(),
                remoteInputPort = sharedPreferences.getInt(KEY_REMOTE_INPUT_PORT, DEFAULT_REMOTE_INPUT_PORT),
                remoteInputLocalPort = sharedPreferences.getInt(
                    KEY_REMOTE_INPUT_LOCAL_PORT,
                    DEFAULT_REMOTE_INPUT_LOCAL_PORT
                ),
                remoteInputDeadZone = sharedPreferences.getFloat(
                    KEY_REMOTE_INPUT_DEAD_ZONE,
                    DEFAULT_REMOTE_INPUT_DEAD_ZONE
                ),
                remoteInputTimeoutMs = sharedPreferences.getLong(
                    KEY_REMOTE_INPUT_TIMEOUT_MS,
                    DEFAULT_REMOTE_INPUT_TIMEOUT_MS
                ),
                remoteInputForwardChannel = sharedPreferences.getInt(
                    KEY_REMOTE_INPUT_FORWARD_CHANNEL,
                    DEFAULT_REMOTE_INPUT_FORWARD_CHANNEL
                ).coerceIn(1, 16),
                remoteInputForwardInverted = sharedPreferences.getBoolean(
                    KEY_REMOTE_INPUT_FORWARD_INVERTED,
                    false
                ),
                remoteInputStrafeRightChannel = sharedPreferences.getInt(
                    KEY_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL,
                    DEFAULT_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL
                ).coerceIn(1, 16),
                remoteInputStrafeRightInverted = sharedPreferences.getBoolean(
                    KEY_REMOTE_INPUT_STRAFE_RIGHT_INVERTED,
                    false
                ),
                remoteInputYawRightChannel = sharedPreferences.getInt(
                    KEY_REMOTE_INPUT_YAW_RIGHT_CHANNEL,
                    DEFAULT_REMOTE_INPUT_YAW_RIGHT_CHANNEL
                ).coerceIn(1, 16),
                remoteInputYawRightInverted = sharedPreferences.getBoolean(
                    KEY_REMOTE_INPUT_YAW_RIGHT_INVERTED,
                    false
                ),
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

    private fun String.migrateLegacyRemoteInputHost(): String {
        return if (this == LEGACY_REMOTE_INPUT_HOST) {
            DEFAULT_REMOTE_INPUT_HOST
        } else {
            this
        }
    }

    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(): Boolean {
        return !sharedPreferences.contains(KEY_ZMQ_IP)
    }
}
