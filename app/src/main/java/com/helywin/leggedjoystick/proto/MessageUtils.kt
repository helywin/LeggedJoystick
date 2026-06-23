package com.helywin.leggedjoystick.proto

import legged_driver.AppMode
import legged_driver.AutoModeLightParams
import legged_driver.BackLightParams
import legged_driver.CommandCode
import legged_driver.CommandRequestMessage
import legged_driver.ConnectionState
import legged_driver.ControlHeadCommandParams
import legged_driver.DeviceType
import legged_driver.FrontLightParams
import legged_driver.HeartbeatMessage
import legged_driver.HighLowStanceCommandParams
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.MotionStatus
import legged_driver.MoveCommandParams
import legged_driver.SetAppModeParams
import legged_driver.SetSpeedLevelParams
import legged_driver.SetSportModeParams
import legged_driver.SpeedLevel
import legged_driver.SportMode
import legged_driver.SubscriptionRequestMessage
import legged_driver.SubscriptionTopic
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object MessageUtils {
    private val requestId = AtomicLong(System.currentTimeMillis())

    /**
     * CRC32 工具类，算法与 driver 端一致。
     */
    private object CRC32Utils {
        private val crcTable = IntArray(256)
        private var tableComputed = false

        private fun computeTable() {
            if (tableComputed) return

            for (i in 0 until 256) {
                var crc = i
                repeat(8) {
                    crc = if ((crc and 1) != 0) {
                        (crc ushr 1) xor 0xEDB88320.toInt()
                    } else {
                        crc ushr 1
                    }
                }
                crcTable[i] = crc
            }
            tableComputed = true
        }

        fun calculate(data: ByteArray): Long {
            computeTable()

            var crc = 0xFFFFFFFF.toInt()
            data.forEach { byte ->
                val index = (crc xor (byte.toInt() and 0xFF)) and 0xFF
                crc = crcTable[index] xor (crc ushr 8)
            }

            return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
        }
    }

    fun calculateCRC32(data: ByteArray): Int {
        return CRC32Utils.calculate(data).toInt()
    }

    fun getCurrentTimestampMs(): Long {
        return System.currentTimeMillis()
    }

    fun nextRequestId(): Long {
        return requestId.incrementAndGet()
    }

    fun generateDeviceId(deviceType: DeviceType): String {
        val prefix = when (deviceType) {
            DeviceType.DEVICE_TYPE_SERVER -> "server"
            DeviceType.DEVICE_TYPE_NAVIGATION -> "nav"
            DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER -> "remote"
            else -> "unknown"
        }
        return "${prefix}_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    fun serializeMessage(message: LeggedDriverMessage): ByteArray {
        return message.encode()
    }

    fun deserializeMessage(data: ByteArray): LeggedDriverMessage {
        return LeggedDriverMessage.ADAPTER.decode(data)
    }

    fun verifyMessage(message: LeggedDriverMessage): Boolean {
        val originalCrc = message.crc32
        val calculatedCrc = calculateCRC32(
            serializeMessage(message.newBuilder().crc32(0).build())
        )
        val isValid = calculatedCrc == originalCrc

        if (!isValid) {
            Timber.w(
                "CRC32 校验失败，收到=%s，计算=%s，消息类型=%s",
                originalCrc.toHex32(),
                calculatedCrc.toHex32(),
                message.message_type
            )
        }

        return isValid
    }

    fun defaultStateTopics(): List<SubscriptionTopic> {
        return listOf(
            SubscriptionTopic.SUBSCRIPTION_TOPIC_HEARTBEAT,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_CONNECTION_STATE,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_APP_MODE_STATE,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_ROBOT_STATE,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_MOTION_DATA,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_FAULT_DATA,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_ODOMETRY,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_CONTROL_LOST,
            SubscriptionTopic.SUBSCRIPTION_TOPIC_CONTROL_AVAILABLE
        )
    }

    fun createHeartbeatMessage(
        deviceType: DeviceType,
        deviceId: String,
        robotConnected: Boolean = true,
        connectionState: ConnectionState = ConnectionState.CONNECTION_STATE_CONNECTED,
        appMode: AppMode = AppMode.APP_MODE_MANUAL
    ): LeggedDriverMessage {
        return createEnvelope(
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_HEARTBEAT
        ) {
            heartbeat(
                HeartbeatMessage(
                    robot_connected = robotConnected,
                    connection_state = connectionState,
                    app_mode = appMode
                )
            )
        }
    }

    fun createSubscriptionRequestMessage(
        deviceType: DeviceType,
        deviceId: String,
        topics: List<SubscriptionTopic> = defaultStateTopics(),
        subscribe: Boolean = true,
        requestId: Long = nextRequestId()
    ): LeggedDriverMessage {
        return createEnvelope(
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST
        ) {
            subscription_request(
                SubscriptionRequestMessage(
                    request_id = requestId,
                    subscribe = subscribe,
                    topics = topics
                )
            )
        }
    }

    fun createCommandRequestMessage(
        deviceType: DeviceType,
        deviceId: String,
        commandCode: CommandCode,
        timeoutMs: Int = DEFAULT_COMMAND_TIMEOUT_MS,
        requestId: Long = nextRequestId(),
        params: CommandRequestMessage.Builder.() -> Unit = {}
    ): LeggedDriverMessage {
        val commandRequest = CommandRequestMessage.Builder()
            .request_id(requestId)
            .command_code(commandCode)
            .timeout_ms(timeoutMs)
            .apply(params)
            .build()

        return createEnvelope(
            deviceType = deviceType,
            deviceId = deviceId,
            messageType = MessageType.MESSAGE_TYPE_COMMAND_REQUEST
        ) {
            command_request(commandRequest)
        }
    }

    fun createSetAppModeCommand(
        deviceType: DeviceType,
        deviceId: String,
        mode: AppMode
    ): LeggedDriverMessage {
        return createCommandRequestMessage(
            deviceType = deviceType,
            deviceId = deviceId,
            commandCode = CommandCode.COMMAND_CODE_SET_APP_MODE
        ) {
            set_app_mode(SetAppModeParams(mode = mode))
        }
    }

    fun createSetSportModeCommand(
        deviceType: DeviceType,
        deviceId: String,
        mode: SportMode
    ): LeggedDriverMessage {
        return createCommandRequestMessage(
            deviceType = deviceType,
            deviceId = deviceId,
            commandCode = CommandCode.COMMAND_CODE_SET_SPORT_MODE
        ) {
            set_sport_mode(SetSportModeParams(mode = mode.toProtocolValue()))
        }
    }

    fun createSetSpeedLevelCommand(
        deviceType: DeviceType,
        deviceId: String,
        speedLevel: SpeedLevel
    ): LeggedDriverMessage {
        return createCommandRequestMessage(
            deviceType = deviceType,
            deviceId = deviceId,
            commandCode = CommandCode.COMMAND_CODE_SET_SPEED_LEVEL
        ) {
            set_speed_level(SetSpeedLevelParams(speed_level = speedLevel.toProtocolValue()))
        }
    }

    fun createSimpleCommand(
        deviceType: DeviceType,
        deviceId: String,
        commandCode: CommandCode
    ): LeggedDriverMessage {
        return createCommandRequestMessage(
            deviceType = deviceType,
            deviceId = deviceId,
            commandCode = commandCode
        )
    }

    fun createMoveCommand(
        deviceType: DeviceType,
        deviceId: String,
        leftRight: Float,
        forwardBack: Float,
        yaw: Float
    ): LeggedDriverMessage {
        return createCommandRequestMessage(
            deviceType = deviceType,
            deviceId = deviceId,
            commandCode = CommandCode.COMMAND_CODE_MOVE,
            timeoutMs = MOVE_COMMAND_TIMEOUT_MS
        ) {
            move(
                MoveCommandParams(
                    left_right = leftRight.clampUnit(),
                    forward_back = forwardBack.clampUnit(),
                    yaw = yaw.clampUnit()
                )
            )
        }
    }

    /**
     * 操作者视角：前进、右平移、右转为正；发送给 driver 时右平移和右转需要取反。
     */
    fun createMoveCommandFromOperatorIntent(
        deviceType: DeviceType,
        deviceId: String,
        strafeRight: Float,
        forward: Float,
        yawRight: Float
    ): LeggedDriverMessage {
        return createMoveCommand(
            deviceType = deviceType,
            deviceId = deviceId,
            leftRight = -strafeRight,
            forwardBack = forward,
            yaw = -yawRight
        )
    }

    fun createFrontLightCommand(
        deviceType: DeviceType,
        deviceId: String,
        on: Boolean
    ): LeggedDriverMessage {
        return createCommandRequestMessage(deviceType, deviceId, CommandCode.COMMAND_CODE_FRONT_LIGHT) {
            front_light(FrontLightParams(on = on))
        }
    }

    fun createBackLightCommand(
        deviceType: DeviceType,
        deviceId: String,
        on: Boolean
    ): LeggedDriverMessage {
        return createCommandRequestMessage(deviceType, deviceId, CommandCode.COMMAND_CODE_BACK_LIGHT) {
            back_light(BackLightParams(on = on))
        }
    }

    fun createAutoModeLightCommand(
        deviceType: DeviceType,
        deviceId: String,
        on: Boolean
    ): LeggedDriverMessage {
        return createCommandRequestMessage(deviceType, deviceId, CommandCode.COMMAND_CODE_AUTO_MODE_LIGHT) {
            auto_mode_light(AutoModeLightParams(on = on))
        }
    }

    fun createControlHeadCommand(
        deviceType: DeviceType,
        deviceId: String,
        leftRight: Float,
        upDown: Float
    ): LeggedDriverMessage {
        return createCommandRequestMessage(deviceType, deviceId, CommandCode.COMMAND_CODE_CONTROL_HEAD) {
            control_head(
                ControlHeadCommandParams(
                    left_right = leftRight.clampUnit(),
                    up_down = upDown.clampUnit()
                )
            )
        }
    }

    fun createHighLowStanceCommand(
        deviceType: DeviceType,
        deviceId: String,
        stance: Int
    ): LeggedDriverMessage {
        return createCommandRequestMessage(deviceType, deviceId, CommandCode.COMMAND_CODE_HIGH_LOW_STANCE) {
            high_low_stance(HighLowStanceCommandParams(stance = stance))
        }
    }

    private fun createEnvelope(
        deviceType: DeviceType,
        deviceId: String,
        messageType: MessageType,
        timestampMs: Long = getCurrentTimestampMs(),
        payload: LeggedDriverMessage.Builder.() -> Unit
    ): LeggedDriverMessage {
        val unsignedMessage = LeggedDriverMessage.Builder()
            .timestamp_ms(timestampMs)
            .device_type(deviceType)
            .device_id(deviceId)
            .message_type(messageType)
            .crc32(0)
            .apply(payload)
            .build()

        val crc32 = calculateCRC32(serializeMessage(unsignedMessage))
        return unsignedMessage.newBuilder().crc32(crc32).build()
    }

    private fun Float.clampUnit(): Float {
        return coerceIn(-1f, 1f)
    }

    private fun Int.toHex32(): String {
        return java.lang.Integer.toUnsignedString(this, 16).uppercase().padStart(8, '0')
    }

    private fun SpeedLevel.toProtocolValue(): Int {
        return when (this) {
            SpeedLevel.SPEED_LEVEL_SLOW -> 1
            SpeedLevel.SPEED_LEVEL_MEDIUM -> 2
            SpeedLevel.SPEED_LEVEL_HIGH -> 3
            else -> 0
        }
    }

    private fun SportMode.toProtocolValue(): Int {
        return when (this) {
            SportMode.SPORT_MODE_GENERAL -> 1
            SportMode.SPORT_MODE_IN_PLACE -> 2
            SportMode.SPORT_MODE_STAIR -> 3
            else -> 0
        }
    }

    private const val DEFAULT_COMMAND_TIMEOUT_MS = 1000
    private const val MOVE_COMMAND_TIMEOUT_MS = 200
}

val AppMode.displayName: String
    get() = when (this) {
        AppMode.APP_MODE_AUTO -> "自动模式"
        AppMode.APP_MODE_MANUAL -> "手动模式"
    }

val SportMode.displayName: String
    get() = when (this) {
        SportMode.SPORT_MODE_GENERAL -> "普通模式"
        SportMode.SPORT_MODE_IN_PLACE -> "原地模式"
        SportMode.SPORT_MODE_STAIR -> "楼梯模式"
        else -> "未知运动模式"
    }

val SpeedLevel.displayName: String
    get() = when (this) {
        SpeedLevel.SPEED_LEVEL_SLOW -> "低速"
        SpeedLevel.SPEED_LEVEL_MEDIUM -> "中速"
        SpeedLevel.SPEED_LEVEL_HIGH -> "高速"
        else -> "未知速度"
    }

val MotionStatus.displayName: String
    get() = when (this) {
        MotionStatus.MOTION_STATUS_STAND_UP -> "站立"
        MotionStatus.MOTION_STATUS_LIE_DOWN -> "卧倒"
        MotionStatus.MOTION_STATUS_CRAWL -> "匍匐"
        MotionStatus.MOTION_STATUS_LOCKED -> "锁定"
        MotionStatus.MOTION_STATUS_GENERAL -> "普通"
        MotionStatus.MOTION_STATUS_IN_PLACE -> "原地"
        MotionStatus.MOTION_STATUS_STAIR -> "楼梯"
        MotionStatus.MOTION_STATUS_CLIMB -> "爬高墙"
        MotionStatus.MOTION_STATUS_SLIM -> "过窄墙"
        MotionStatus.MOTION_STATUS_GAIT -> "扭一扭"
        else -> "未知状态"
    }
