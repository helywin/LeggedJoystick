package com.helywin.leggedjoystick.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import legged_driver.DeviceType
import sar.robot_controller.v1.MappingGridEncoding
import sar.robot_controller.v1.PoseSource

class ControllerProtocolTest {
    @Test
    fun protocolAppendOnlyValues_matchNxAndDriverContracts() {
        assertEquals(4, DeviceType.DEVICE_TYPE_ROBOT_CONTROLLER.value)
        assertEquals(1, MappingGridEncoding.MAPPING_GRID_ENCODING_ZLIB_INT8.value)
        assertEquals(1, PoseSource.POSE_SOURCE_MAPPING_ODOMETRY.value)
        assertEquals(2, PoseSource.POSE_SOURCE_LOCALIZATION.value)
    }

    @Test
    fun hello_usesVersionIdentityTokenAndStateSubscription() {
        val protocol = ControllerProtocol(
            deviceId = "remote-test",
            initialRequestId = 40L,
            utcNowNs = { 123L }
        )

        val message = protocol.hello("secret")

        assertEquals(1, message.version?.major)
        assertEquals("remote-test", message.device_id)
        assertEquals(41L, message.request_id)
        assertEquals("secret", message.hello_request?.pre_shared_token)
        assertEquals(true, message.hello_request?.subscribe_state)
    }

    @Test
    fun commands_areMonotonicAndCarryExpectedStateRevision() {
        val protocol = ControllerProtocol(deviceId = "remote-test", initialRequestId = 8L)

        val start = protocol.startMapping("session", 7L, "floor-a")
        val finish = protocol.finishMapping("session", 8L)

        assertTrue(start.command_request?.start_mapping != null)
        assertEquals("floor-a", start.command_request?.start_mapping?.draft_name)
        assertEquals(7L, start.command_request?.expected_state_revision)
        assertNotEquals(start.request_id, finish.request_id)
        assertEquals("session", finish.session_id)
    }

    @Test
    fun timeSyncCommit_echoesChallengeAndAddsClientReceiveTime() {
        val protocol = ControllerProtocol(
            deviceId = "remote-test",
            initialRequestId = 1L,
            utcNowNs = { 999L }
        )
        val challenge = sar.robot_controller.v1.TimeSyncChallenge(
            challenge = "nonce",
            client_send_utc_ns = 1L,
            server_receive_utc_ns = 2L,
            server_send_utc_ns = 3L
        )

        val commit = protocol.timeSyncCommit("session", challenge)

        assertEquals("nonce", commit.time_sync_commit?.challenge)
        assertEquals(999L, commit.time_sync_commit?.client_receive_utc_ns)
    }
}
