package com.helywin.leggedjoystick.proto

import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.util.zip.CRC32
import java.util.UUID

object MessageUtils {
    private val protoBuf = ProtoBuf

    /**
     * 计算CRC32校验码
     */
    fun calculateCRC32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    /**
     * 生成设备ID
     */
    fun generateDeviceId(deviceType: DeviceType): String {
        val prefix = when (deviceType) {
            DeviceType.DEVICE_TYPE_SERVER -> "server"
            DeviceType.DEVICE_TYPE_NAVIGATION -> "nav"
            DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER -> "remote"
            else -> "unknown"
        }
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "${prefix}_$uuid"
    }

    /**
     * 创建消息并计算CRC32
     */
    fun createMessageWithCRC(
        timestampMs: Long,
        deviceType: DeviceType,
        deviceId: String,
        messageType: MessageType,
        heartbeat: HeartbeatMessage? = null,
        batteryInfo: BatteryInfoMessage? = null,
        modeSet: ModeSetMessage? = null,
        controlModeSet: ControlModeSetMessage? = null,
        velocityCommand: VelocityCommandMessage? = null,
        currentMode: CurrentModeMessage? = null,
        currentControlMode: CurrentControlModeMessage? = null,
        odometry: OdometryMessage? = null
    ): LeggedDriverMessage {
        // 先创建不带CRC的消息
        val tempMessage = LeggedDriverMessage(
            timestampMs = timestampMs,
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = messageType,
            crc32 = 0, // 临时设为0
            heartbeat = heartbeat,
            batteryInfo = batteryInfo,
            modeSet = modeSet,
            controlModeSet = controlModeSet,
            velocityCommand = velocityCommand,
            currentMode = currentMode,
            currentControlMode = currentControlMode,
            odometry = odometry
        )

        // 序列化临时消息
        val tempData = protoBuf.encodeToByteArray(tempMessage)

        // 计算CRC32
        val crc32 = calculateCRC32(tempData)

        // 返回带CRC32的最终消息
        return tempMessage.copy(crc32 = crc32)
    }

    /**
     * 序列化消息为字节数组
     */
    fun serializeMessage(message: LeggedDriverMessage): ByteArray {
        return protoBuf.encodeToByteArray(message)
    }

    /**
     * 反序列化字节数组为消息
     */
    fun deserializeMessage(data: ByteArray): LeggedDriverMessage {
        return protoBuf.decodeFromByteArray<LeggedDriverMessage>(data)
    }

    /**
     * 验证消息的CRC32校验码
     */
    fun verifyMessage(message: LeggedDriverMessage): Boolean {
        // 创建不带CRC的临时消息
        val tempMessage = message.copy(crc32 = 0)
        val tempData = protoBuf.encodeToByteArray(tempMessage)
        val calculatedCRC = calculateCRC32(tempData)

        return calculatedCRC == message.crc32
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    fun getCurrentTimestampMs(): Long {
        return System.currentTimeMillis()
    }

    /**
     * 创建心跳消息
     */
    fun createHeartbeatMessage(
        deviceType: DeviceType,
        deviceId: String,
        isConnected: Boolean = true
    ): LeggedDriverMessage {
        return createMessageWithCRC(
            timestampMs = getCurrentTimestampMs(),
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_HEARTBEAT,
            heartbeat = HeartbeatMessage(isConnected = isConnected)
        )
    }

    /**
     * 创建模式设置消息
     */
    fun createModeSetMessage(
        deviceType: DeviceType,
        deviceId: String,
        mode: Mode
    ): LeggedDriverMessage {
        return createMessageWithCRC(
            timestampMs = getCurrentTimestampMs(),
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_MODE_SET,
            modeSet = ModeSetMessage(mode = mode)
        )
    }

    /**
     * 创建控制模式设置消息
     */
    fun createControlModeSetMessage(
        deviceType: DeviceType,
        deviceId: String,
        controlMode: ControlMode
    ): LeggedDriverMessage {
        return createMessageWithCRC(
            timestampMs = getCurrentTimestampMs(),
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_CONTROL_MODE_SET,
            controlModeSet = ControlModeSetMessage(controlMode = controlMode)
        )
    }

    /**
     * 创建速度指令消息
     */
    fun createVelocityCommandMessage(
        deviceType: DeviceType,
        deviceId: String,
        vx: Float,
        vy: Float,
        yawRate: Float
    ): LeggedDriverMessage {
        return createMessageWithCRC(
            timestampMs = getCurrentTimestampMs(),
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_VELOCITY_COMMAND,
            velocityCommand = VelocityCommandMessage(vx = vx, vy = vy, yawRate = yawRate)
        )
    }
}