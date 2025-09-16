package com.helywin.leggedjoystick.proto

import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.util.zip.CRC32

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
}