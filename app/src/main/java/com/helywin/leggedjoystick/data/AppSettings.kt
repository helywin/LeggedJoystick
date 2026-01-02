/*********************************************************************************
 * FileName: AppSettings.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 应用设置数据类
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.data

/**
 * 速度档位枚举
 */
enum class SpeedLevel(val displayName: String, val maxLinearSpeed: Float) {
    SLOW("慢速", 0.5f),
    MEDIUM("中速", 1.0f),
    FAST("快速", 2.0f)
}

/**
 * 应用设置数据类
 */
data class AppSettings(
    val zmqIp: String = "127.0.0.1",
    val zmqPort: Int = 33445,
    val speedLevel: SpeedLevel = SpeedLevel.MEDIUM,
    val rtspUrl: String = "rtsp://192.168.234.1:8554/test",
    val mainTitle: String = "机器狗遥控器",
    val logoPath: String = "",
    val keepScreenOn: Boolean = true
) {
    // 保持向后兼容的属性，狂暴模式现在等同于快速模式
    val isRageModeEnabled: Boolean
        get() = speedLevel == SpeedLevel.FAST
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
