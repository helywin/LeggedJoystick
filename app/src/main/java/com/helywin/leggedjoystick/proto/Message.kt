package com.helywin.leggedjoystick.proto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// 设备类型枚举
@Serializable
enum class DeviceType {
    @ProtoNumber(0) DEVICE_TYPE_UNSPECIFIED,
    @ProtoNumber(1) DEVICE_TYPE_SERVER,
    @ProtoNumber(2) DEVICE_TYPE_NAVIGATION,
    @ProtoNumber(3) DEVICE_TYPE_REMOTE_CONTROLLER
}

// 消息类型枚举
@Serializable
enum class MessageType {
    @ProtoNumber(0) MESSAGE_TYPE_UNSPECIFIED,
    @ProtoNumber(1) MESSAGE_TYPE_HEARTBEAT,
    @ProtoNumber(2) MESSAGE_TYPE_BATTERY_INFO,
    @ProtoNumber(3) MESSAGE_TYPE_MODE_SET,
    @ProtoNumber(4) MESSAGE_TYPE_CONTROL_MODE_SET,
    @ProtoNumber(5) MESSAGE_TYPE_VELOCITY_COMMAND,
    @ProtoNumber(6) MESSAGE_TYPE_CURRENT_MODE,
    @ProtoNumber(7) MESSAGE_TYPE_CURRENT_CONTROL_MODE,
    @ProtoNumber(8) MESSAGE_TYPE_ODOMETRY
}

// 模式枚举
@Serializable
enum class Mode (val displayName: String) {
    @ProtoNumber(0) MODE_AUTO("自动模式"),
    @ProtoNumber(1) MODE_MANUAL("手动模式"),
}

// 控制模式枚举
@Serializable
enum class ControlMode(val displayName: String) {
    @ProtoNumber(0) CONTROL_MODE_PASSIVE("阻尼模式"),
    @ProtoNumber(1) CONTROL_MODE_STAND_UP("站立模式"),
    @ProtoNumber(2) CONTROL_MODE_LIE_DOWN("趴下模式"),
    @ProtoNumber(100) CONTROL_MODE_UNSPECIFIED("未指定控制模式"),
}

// 心跳消息体
@Serializable
data class HeartbeatMessage(
    @ProtoNumber(1) val isConnected: Boolean = false
)

// 电池信息消息体
@Serializable
data class BatteryInfoMessage(
    @ProtoNumber(1) val batteryLevel: Int = 0,
    @ProtoNumber(2) val voltage: Float = 0.0f,
    @ProtoNumber(3) val current: Float = 0.0f,
    @ProtoNumber(4) val temperature: Float = 0.0f
)

// 模式设置消息体
@Serializable
data class ModeSetMessage(
    @ProtoNumber(1) val mode: Mode = Mode.MODE_AUTO
)

// 控制模式设置消息体
@Serializable
data class ControlModeSetMessage(
    @ProtoNumber(1) val controlMode: ControlMode = ControlMode.CONTROL_MODE_UNSPECIFIED
)

// 速度指令消息体
@Serializable
data class VelocityCommandMessage(
    @ProtoNumber(1) val vx: Float = 0.0f,
    @ProtoNumber(2) val vy: Float = 0.0f,
    @ProtoNumber(3) val yawRate: Float = 0.0f
)

// 当前模式消息体
@Serializable
data class CurrentModeMessage(
    @ProtoNumber(1) val mode: Mode = Mode.MODE_AUTO
)

// 当前控制模式消息体
@Serializable
data class CurrentControlModeMessage(
    @ProtoNumber(1) val controlMode: ControlMode = ControlMode.CONTROL_MODE_UNSPECIFIED
)

// 3D向量
@Serializable
data class Vector3(
    @ProtoNumber(1) val x: Float = 0.0f,
    @ProtoNumber(2) val y: Float = 0.0f,
    @ProtoNumber(3) val z: Float = 0.0f
)

// 四元数
@Serializable
data class Quaternion(
    @ProtoNumber(1) val x: Float = 0.0f,
    @ProtoNumber(2) val y: Float = 0.0f,
    @ProtoNumber(3) val z: Float = 0.0f,
    @ProtoNumber(4) val w: Float = 1.0f
)

// 里程计消息体
@Serializable
data class OdometryMessage(
    @ProtoNumber(1) val position: Vector3 = Vector3(),
    @ProtoNumber(2) val orientation: Quaternion = Quaternion(),
    @ProtoNumber(3) val linearVelocity: Vector3 = Vector3(),
    @ProtoNumber(4) val angularVelocity: Vector3 = Vector3()
)

// 主消息结构
@Serializable
data class LeggedDriverMessage(
    @ProtoNumber(1) val timestampMs: Long = 0L,
    @ProtoNumber(2) val deviceType: DeviceType = DeviceType.DEVICE_TYPE_UNSPECIFIED,
    @ProtoNumber(3) val deviceId: String = "",
    @ProtoNumber(4) val messageType: MessageType = MessageType.MESSAGE_TYPE_UNSPECIFIED,

    // 消息体 - 只能设置其中一个
    @ProtoNumber(10) val heartbeat: HeartbeatMessage? = null,
    @ProtoNumber(11) val batteryInfo: BatteryInfoMessage? = null,
    @ProtoNumber(12) val modeSet: ModeSetMessage? = null,
    @ProtoNumber(13) val controlModeSet: ControlModeSetMessage? = null,
    @ProtoNumber(14) val velocityCommand: VelocityCommandMessage? = null,
    @ProtoNumber(15) val currentMode: CurrentModeMessage? = null,
    @ProtoNumber(16) val currentControlMode: CurrentControlModeMessage? = null,
    @ProtoNumber(17) val odometry: OdometryMessage? = null,

    // CRC32校验码
    @ProtoNumber(20) val crc32: Int = 0,
)