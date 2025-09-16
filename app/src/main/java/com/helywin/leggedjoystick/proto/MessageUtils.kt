package com.helywin.leggedjoystick.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.util.UUID

object MessageUtils {
    @OptIn(ExperimentalSerializationApi::class)
    private val protoBuf = ProtoBuf

    /**
     * CRC32工具类，实现与C++代码一致的CRC32算法
     */
    private object CRC32Utils {
        private val crcTable = IntArray(256)
        private var tableComputed = false

        private fun computeTable() {
            if (tableComputed) return

            for (i in 0 until 256) {
                var crc = i.toLong() and 0xFFFFFFFFL
                for (j in 0 until 8) {
                    if ((crc and 1L) != 0L) {
                        crc = (crc ushr 1) xor 0xEDB88320L
                    } else {
                        crc = crc ushr 1
                    }
                }
                crcTable[i] = crc.toInt()
            }
            tableComputed = true
        }

        fun calculate(data: ByteArray): Long {
            computeTable()

            var crc = 0xFFFFFFFFL
            for (byte in data) {
                val index = ((crc xor (byte.toLong() and 0xFF)) and 0xFF).toInt()
                crc = (crcTable[index].toLong() and 0xFFFFFFFFL) xor (crc ushr 8)
            }

            return crc xor 0xFFFFFFFFL
        }
    }

    /**
     * 计算CRC32校验码（使用与C++一致的算法）
     */
    fun calculateCRC32(data: ByteArray): Int {
        return CRC32Utils.calculate(data).toInt()
    }

    /**
     * 测试CRC32实现（用于调试）
     */
    fun testCRC32() {
        // 测试已知数据的CRC32值
        val testData = "Hello, World!".toByteArray()
        val crc = calculateCRC32(testData)
        println("CRC32 of 'Hello, World!': 0x${crc.toString(16).uppercase()}")
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
    @OptIn(ExperimentalSerializationApi::class)
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
    @OptIn(ExperimentalSerializationApi::class)
    fun serializeMessage(message: LeggedDriverMessage): ByteArray {
        return protoBuf.encodeToByteArray(message)
    }

    /**
     * 反序列化字节数组为消息
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun deserializeMessage(data: ByteArray): LeggedDriverMessage {
        return protoBuf.decodeFromByteArray<LeggedDriverMessage>(data)
    }

    /**
     * 验证消息的CRC32校验码
     */
    @OptIn(ExperimentalSerializationApi::class)
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