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
enum class SpeedLevel(
    val displayName: String,
    val protocolSpeedLevel: legged_driver.SpeedLevel
) {
    SLOW("低速", legged_driver.SpeedLevel.SPEED_LEVEL_SLOW),
    MEDIUM("中速", legged_driver.SpeedLevel.SPEED_LEVEL_MEDIUM),
    FAST("高速", legged_driver.SpeedLevel.SPEED_LEVEL_HIGH)
}

/**
 * 高低站姿命令枚举。
 */
enum class HighLowStance(
    val displayName: String,
    val protocolValue: Int
) {
    NORMAL("恢复", 0),
    HIGH("高站姿", 1),
    LOW("低站姿", 2)
}

/**
 * 应用设置数据类
 */
data class AppSettings(
    val zmqIp: String = "192.168.234.1",
    val zmqPort: Int = 33445,
    val speedLevel: SpeedLevel = SpeedLevel.SLOW,
    val headRtspUrl: String = "rtsp://192.168.234.1:8554/front",
    val tailRtspUrl: String = "rtsp://192.168.234.1:8554/back",
    val remoteInputHost: String = "127.0.0.1",
    val remoteInputPort: Int = 19856,
    val remoteInputLocalPort: Int = 0,
    val remoteInputDeadZone: Float = 0.06f,
    val remoteInputTimeoutMs: Long = 300L,
    val remoteInputForwardChannel: Int = 3,
    val remoteInputForwardInverted: Boolean = false,
    val remoteInputStrafeRightChannel: Int = 4,
    val remoteInputStrafeRightInverted: Boolean = false,
    val remoteInputYawRightChannel: Int = 1,
    val remoteInputYawRightInverted: Boolean = false,
    val keepScreenOn: Boolean = true,
    val engineeringMockEnabled: Boolean = false
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

/**
 * 机器狗控制权状态。
 */
enum class ControlOwnershipState(val displayName: String) {
    UNKNOWN("未知"),
    AVAILABLE("可接管"),
    TAKING("接管中"),
    OWNED("已接管"),
    RELEASING("释放中"),
    OCCUPIED("被占用"),
    DENIED("接管失败"),
    LOST("已丢失")
}
