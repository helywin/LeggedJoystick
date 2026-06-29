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
        private const val KEY_HEAD_RTSP_URL = "head_rtsp_url"
        private const val KEY_TAIL_RTSP_URL = "tail_rtsp_url"
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
        private const val KEY_REMOTE_INPUT_HEAD_PITCH_CHANNEL = "remote_input_head_pitch_channel"
        private const val KEY_REMOTE_INPUT_HEAD_PITCH_INVERTED = "remote_input_head_pitch_inverted"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_ENGINEERING_MOCK_ENABLED = "engineering_mock_enabled"
        private const val KEY_SETTINGS_VERSION = "settings_version"

        // 默认配置
        private const val CURRENT_SETTINGS_VERSION = 2
        private const val DEFAULT_ZMQ_IP = "192.168.234.1"
        private const val DEFAULT_ZMQ_PORT = 33445
        private const val DEFAULT_HEAD_RTSP_URL = "rtsp://192.168.234.1:8554/front"
        private const val DEFAULT_TAIL_RTSP_URL = "rtsp://192.168.234.1:8554/back"
        private const val OLD_DEFAULT_HEAD_RTSP_URL = "rtsp://192.168.234.1:8554/head"
        private const val OLD_DEFAULT_TAIL_RTSP_URL = "rtsp://192.168.234.1:8554/tail"
        private const val DEFAULT_REMOTE_INPUT_HOST = "127.0.0.1"
        private const val DEFAULT_REMOTE_INPUT_PORT = 19856
        private const val DEFAULT_REMOTE_INPUT_LOCAL_PORT = 0
        private const val DEFAULT_REMOTE_INPUT_DEAD_ZONE = 0.06f
        private const val DEFAULT_REMOTE_INPUT_TIMEOUT_MS = 300L
        private const val DEFAULT_REMOTE_INPUT_FORWARD_CHANNEL = 3
        private const val DEFAULT_REMOTE_INPUT_STRAFE_RIGHT_CHANNEL = 4
        private const val DEFAULT_REMOTE_INPUT_YAW_RIGHT_CHANNEL = 1
        private const val DEFAULT_REMOTE_INPUT_HEAD_PITCH_CHANNEL = 2
        private const val DEFAULT_REMOTE_INPUT_HEAD_PITCH_INVERTED = true
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
                putString(KEY_HEAD_RTSP_URL, settings.headRtspUrl)
                putString(KEY_TAIL_RTSP_URL, settings.tailRtspUrl)
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
                putInt(KEY_REMOTE_INPUT_HEAD_PITCH_CHANNEL, settings.remoteInputHeadPitchChannel)
                putBoolean(KEY_REMOTE_INPUT_HEAD_PITCH_INVERTED, settings.remoteInputHeadPitchInverted)
                putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
                putBoolean(KEY_ENGINEERING_MOCK_ENABLED, settings.engineeringMockEnabled)
                putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
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
            val storedSettingsVersion = sharedPreferences.getInt(KEY_SETTINGS_VERSION, 1)
            AppSettings(
                zmqIp = sharedPreferences.getString(KEY_ZMQ_IP, DEFAULT_ZMQ_IP) ?: DEFAULT_ZMQ_IP,
                zmqPort = sharedPreferences.getInt(KEY_ZMQ_PORT, DEFAULT_ZMQ_PORT),
                speedLevel = SpeedLevel.SLOW,
                headRtspUrl = normalizeDefaultRtspUrl(
                    sharedPreferences.getString(KEY_HEAD_RTSP_URL, DEFAULT_HEAD_RTSP_URL),
                    OLD_DEFAULT_HEAD_RTSP_URL,
                    DEFAULT_HEAD_RTSP_URL
                ),
                tailRtspUrl = normalizeDefaultRtspUrl(
                    sharedPreferences.getString(KEY_TAIL_RTSP_URL, DEFAULT_TAIL_RTSP_URL),
                    OLD_DEFAULT_TAIL_RTSP_URL,
                    DEFAULT_TAIL_RTSP_URL
                ),
                remoteInputHost = sharedPreferences.getString(
                    KEY_REMOTE_INPUT_HOST,
                    DEFAULT_REMOTE_INPUT_HOST
                ) ?: DEFAULT_REMOTE_INPUT_HOST,
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
                remoteInputHeadPitchChannel = sharedPreferences.getInt(
                    KEY_REMOTE_INPUT_HEAD_PITCH_CHANNEL,
                    DEFAULT_REMOTE_INPUT_HEAD_PITCH_CHANNEL
                ).coerceIn(1, 16),
                remoteInputHeadPitchInverted = loadHeadPitchInverted(storedSettingsVersion),
                keepScreenOn = sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON),
                engineeringMockEnabled = sharedPreferences.getBoolean(KEY_ENGINEERING_MOCK_ENABLED, false)
            ).also {
                if (storedSettingsVersion < CURRENT_SETTINGS_VERSION) {
                    migrateSettingsVersion(it)
                }
                Timber.d("设置已加载: $it")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载设置失败，使用默认设置")
            AppSettings() // 返回默认设置
        }
    }

    private fun loadHeadPitchInverted(storedSettingsVersion: Int): Boolean {
        if (storedSettingsVersion < CURRENT_SETTINGS_VERSION) {
            return DEFAULT_REMOTE_INPUT_HEAD_PITCH_INVERTED
        }
        return sharedPreferences.getBoolean(
            KEY_REMOTE_INPUT_HEAD_PITCH_INVERTED,
            DEFAULT_REMOTE_INPUT_HEAD_PITCH_INVERTED
        )
    }

    private fun migrateSettingsVersion(settings: AppSettings) {
        sharedPreferences.edit {
            putBoolean(KEY_REMOTE_INPUT_HEAD_PITCH_INVERTED, settings.remoteInputHeadPitchInverted)
            putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
        }
        Timber.i("设置已迁移到版本 %d", CURRENT_SETTINGS_VERSION)
    }

    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(): Boolean {
        return !sharedPreferences.contains(KEY_ZMQ_IP)
    }

    private fun normalizeDefaultRtspUrl(
        value: String?,
        oldDefaultValue: String,
        defaultValue: String
    ): String {
        val trimmedValue = value?.trim().orEmpty()
        return when (trimmedValue) {
            "", oldDefaultValue -> defaultValue
            else -> trimmedValue
        }
    }
}
