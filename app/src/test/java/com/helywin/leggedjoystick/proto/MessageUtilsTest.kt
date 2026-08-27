package com.helywin.leggedjoystick.proto

import com.helywin.leggedjoystick.product.RemoteProductPolicy
import legged_driver.CommandCode
import legged_driver.ConnectionState
import legged_driver.DeviceType
import legged_driver.MessageType
import legged_driver.SpeedLevel
import legged_driver.SubscriptionRequestMessage
import legged_driver.SubscriptionTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageUtilsTest {
    @Test
    fun heartbeatMessage_hasValidCrcAfterRoundTrip() {
        val message = MessageUtils.createHeartbeatMessage(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            productType = RemoteProductPolicy.productType,
            protocolVersion = RemoteProductPolicy.PROTOCOL_VERSION,
            robotConnected = true,
            connectionState = ConnectionState.CONNECTION_STATE_CONNECTED
        )

        val decoded = MessageUtils.deserializeMessage(MessageUtils.serializeMessage(message))

        assertEquals(MessageType.MESSAGE_TYPE_HEARTBEAT, decoded.message_type)
        assertTrue(MessageUtils.verifyMessage(decoded))
        assertEquals(true, decoded.heartbeat?.robot_connected)
        assertEquals(ConnectionState.CONNECTION_STATE_CONNECTED, decoded.heartbeat?.connection_state)
        assertEquals(RemoteProductPolicy.productType, decoded.heartbeat?.product_type)
    }

    @Test
    fun subscriptionRequest_usesRequestedTopics() {
        val message = MessageUtils.createSubscriptionRequestMessage(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            topics = listOf(
                SubscriptionTopic.SUBSCRIPTION_TOPIC_ROBOT_STATE,
                SubscriptionTopic.SUBSCRIPTION_TOPIC_ODOMETRY
            ),
            requestId = 7L
        )

        val request = message.subscription_request

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST, message.message_type)
        assertEquals(7L, request?.request_id)
        assertEquals(true, request?.subscribe)
        assertEquals(
            listOf(
                SubscriptionTopic.SUBSCRIPTION_TOPIC_ROBOT_STATE,
                SubscriptionTopic.SUBSCRIPTION_TOPIC_ODOMETRY
            ),
            request?.topics
        )
    }

    @Test
    fun subscriptionRequestTopics_usePackedEncodingForDriverCrcCompatibility() {
        val request = SubscriptionRequestMessage(
            request_id = 1L,
            subscribe = true,
            topics = listOf(
                SubscriptionTopic.SUBSCRIPTION_TOPIC_HEARTBEAT,
                SubscriptionTopic.SUBSCRIPTION_TOPIC_CONNECTION_STATE,
                SubscriptionTopic.SUBSCRIPTION_TOPIC_APP_MODE_STATE
            )
        )

        val encoded = SubscriptionRequestMessage.ADAPTER.encode(request)

        assertTrue(encoded.containsSubsequence(byteArrayOf(0x1A, 0x03, 0x01, 0x02, 0x03)))
        assertFalse(encoded.containsSubsequence(byteArrayOf(0x18, 0x01)))
    }

    @Test
    fun clientDisconnect_hasPayloadAndValidCrc() {
        val message = MessageUtils.createClientDisconnectMessage(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            reason = "unit_test"
        )

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(MessageType.MESSAGE_TYPE_CLIENT_DISCONNECT, message.message_type)
        assertEquals("unit_test", message.client_disconnect?.reason)
    }

    @Test
    fun speedLevelCommand_usesProtocolEnumValue() {
        val message = MessageUtils.createSetSpeedLevelCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            speedLevel = SpeedLevel.SPEED_LEVEL_HIGH
        )

        val request = message.command_request

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(CommandCode.COMMAND_CODE_SET_SPEED_LEVEL, request?.command_code)
        assertEquals(3, request?.set_speed_level?.speed_level)
    }

    @Test
    fun moveCommandFromOperatorIntent_convertsRightPositiveAxesToDriverProtocol() {
        val message = MessageUtils.createMoveCommandFromOperatorIntent(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            strafeRight = 0.25f,
            forward = 0.5f,
            yawRight = 0.75f
        )

        val move = message.command_request?.move

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(CommandCode.COMMAND_CODE_MOVE, message.command_request?.command_code)
        assertEquals(-0.25f, move?.left_right ?: 0f, FLOAT_DELTA)
        assertEquals(0.5f, move?.forward_back ?: 0f, FLOAT_DELTA)
        assertEquals(-0.75f, move?.yaw ?: 0f, FLOAT_DELTA)
    }

    @Test
    fun takeControlCommand_usesControlCommandTimeout() {
        val message = MessageUtils.createTakeControlCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID
        )

        val request = message.command_request

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(MessageType.MESSAGE_TYPE_COMMAND_REQUEST, message.message_type)
        assertEquals(CommandCode.COMMAND_CODE_TAKE_CONTROL, request?.command_code)
        assertEquals(CONTROL_TIMEOUT_MS, request?.timeout_ms)
    }

    @Test
    fun releaseControlCommand_usesControlCommandTimeout() {
        val message = MessageUtils.createReleaseControlCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID
        )

        val request = message.command_request

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(MessageType.MESSAGE_TYPE_COMMAND_REQUEST, message.message_type)
        assertEquals(CommandCode.COMMAND_CODE_RELEASE_CONTROL, request?.command_code)
        assertEquals(CONTROL_TIMEOUT_MS, request?.timeout_ms)
    }

    @Test
    fun lightCommands_useRequestedOnOffPayloads() {
        val front = MessageUtils.createFrontLightCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            on = true
        )
        val back = MessageUtils.createBackLightCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            on = false
        )
        val auto = MessageUtils.createAutoModeLightCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            on = true
        )

        assertTrue(MessageUtils.verifyMessage(front))
        assertTrue(MessageUtils.verifyMessage(back))
        assertTrue(MessageUtils.verifyMessage(auto))
        assertEquals(CommandCode.COMMAND_CODE_FRONT_LIGHT, front.command_request?.command_code)
        assertEquals(true, front.command_request?.front_light?.on)
        assertEquals(CommandCode.COMMAND_CODE_BACK_LIGHT, back.command_request?.command_code)
        assertEquals(false, back.command_request?.back_light?.on)
        assertEquals(CommandCode.COMMAND_CODE_AUTO_MODE_LIGHT, auto.command_request?.command_code)
        assertEquals(true, auto.command_request?.auto_mode_light?.on)
    }

    @Test
    fun controlHeadCommand_clampsAxesToProtocolRange() {
        val message = MessageUtils.createControlHeadCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            leftRight = 2.0f,
            upDown = -2.0f
        )

        val controlHead = message.command_request?.control_head

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(CommandCode.COMMAND_CODE_CONTROL_HEAD, message.command_request?.command_code)
        assertEquals(1.0f, controlHead?.left_right ?: 0f, FLOAT_DELTA)
        assertEquals(-1.0f, controlHead?.up_down ?: 0f, FLOAT_DELTA)
    }

    @Test
    fun highLowStanceCommand_usesProtocolValue() {
        val message = MessageUtils.createHighLowStanceCommand(
            deviceType = DeviceType.DEVICE_TYPE_REMOTE_CONTROLLER,
            deviceId = DEVICE_ID,
            stance = 2
        )

        val request = message.command_request

        assertTrue(MessageUtils.verifyMessage(message))
        assertEquals(CommandCode.COMMAND_CODE_HIGH_LOW_STANCE, request?.command_code)
        assertEquals(2, request?.high_low_stance?.stance)
    }

    private companion object {
        const val DEVICE_ID = "remote_test"
        const val FLOAT_DELTA = 0.0001f
        const val CONTROL_TIMEOUT_MS = 5000
    }
}

private fun ByteArray.containsSubsequence(subsequence: ByteArray): Boolean {
    if (subsequence.isEmpty() || subsequence.size > size) return false
    return indices.any { start ->
        start + subsequence.size <= size &&
            subsequence.indices.all { offset -> this[start + offset] == subsequence[offset] }
    }
}
