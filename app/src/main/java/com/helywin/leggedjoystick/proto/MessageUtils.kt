package com.helywin.leggedjoystick.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import timber.log.Timber
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
                var crc = i
                for (j in 0 until 8) {
                    if ((crc and 1) != 0) {
                        crc = (crc ushr 1) xor 0xEDB88320.toInt()
                    } else {
                        crc = crc ushr 1
                    }
                }
                crcTable[i] = crc
            }
            tableComputed = true
        }

        fun calculate(data: ByteArray): Long {
            computeTable()

            var crc = 0xFFFFFFFF.toInt()
            for (byte in data) {
                val index = (crc xor (byte.toInt() and 0xFF)) and 0xFF
                crc = crcTable[index] xor (crc ushr 8)
            }

            return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
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
        Timber.d("开始CRC32测试...")
        
        // 测试已知数据的CRC32值
        val testData = "Hello, World!".toByteArray()
        val crc = calculateCRC32(testData)
        Timber.d("CRC32 of 'Hello, World!': 0x${crc.toString(16).uppercase()}")
        
        // 测试空数据
        val emptyCRC = calculateCRC32(byteArrayOf())
        Timber.d("CRC32 of empty data: 0x${emptyCRC.toString(16).uppercase()}")
        
        // 测试单字节数据
        val singleByteCRC = calculateCRC32(byteArrayOf(0x41)) // 'A'
        Timber.d("CRC32 of 'A': 0x${singleByteCRC.toString(16).uppercase()}")
        
        // 测试消息序列化和CRC32计算
        val testMessage = createHeartbeatMessage(
            DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            "test_device",
            true
        )
        
        val serialized = serializeMessage(testMessage)
        Timber.d("序列化消息长度: ${serialized.size} bytes")
        Timber.d("消息CRC32: 0x${testMessage.crc32.toString(16).uppercase()}")
        
        // 验证消息
        val isValid = verifyMessage(testMessage)
        Timber.d("消息CRC32验证结果: $isValid")
        
        // 测试反序列化
        try {
            val deserialized = deserializeMessage(serialized)
            Timber.d("反序列化成功，CRC32: 0x${deserialized.crc32.toString(16).uppercase()}")
            
            val isDeserializedValid = verifyMessage(deserialized)
            Timber.d("反序列化消息CRC32验证结果: $isDeserializedValid")
        } catch (e: Exception) {
            Timber.e(e, "反序列化失败")
        }
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
     * 注意：CRC32是对整个消息序列化数据计算的，但计算时crc32字段应该为0
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
        // 创建消息，CRC32字段设为0（这样计算CRC32时不会包含CRC32本身）
        val message = LeggedDriverMessage(
            timestampMs = timestampMs,
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = messageType,
            crc32 = 0, // CRC32字段在计算时必须为0
            heartbeat = heartbeat,
            batteryInfo = batteryInfo,
            modeSet = modeSet,
            controlModeSet = controlModeSet,
            velocityCommand = velocityCommand,
            currentMode = currentMode,
            currentControlMode = currentControlMode,
            odometry = odometry
        )

        // 序列化消息（此时crc32=0）
        val messageData = protoBuf.encodeToByteArray(message)
        
        // 计算CRC32校验码
        val crc32 = calculateCRC32(messageData)

        // 返回带正确CRC32的消息
        return message.copy(crc32 = crc32)
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
        // 保存原始CRC32值
        val originalCRC = message.crc32
        
        // 创建CRC32为0的临时消息
        val tempMessage = message.copy(crc32 = 0)
        val tempData = protoBuf.encodeToByteArray(tempMessage)
        
        // 计算CRC32并比较
        val calculatedCRC = calculateCRC32(tempData)
        
        val isValid = calculatedCRC == originalCRC
        
        if (!isValid) {
            Timber.w("CRC32校验失败 - 原始: 0x${originalCRC.toString(16).uppercase()}, " +
                    "计算得到: 0x${calculatedCRC.toString(16).uppercase()}, " +
                    "数据长度: ${tempData.size}")
        }
        
        return isValid
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