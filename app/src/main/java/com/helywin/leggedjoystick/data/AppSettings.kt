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
 * 应用设置数据类
 */
data class AppSettings(
    val zmqIp: String = "127.0.0.1",
    val zmqPort: Int = 33445,
    val isRageModeEnabled: Boolean = false
)
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