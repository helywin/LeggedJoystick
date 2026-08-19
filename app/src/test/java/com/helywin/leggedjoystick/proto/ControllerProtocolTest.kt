package com.helywin.leggedjoystick.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import legged_driver.DeviceType
import sar.robot_controller.v1.MappingGridEncoding
import sar.robot_controller.v1.MapInfo
import sar.robot_controller.v1.PoseSource

class ControllerProtocolTest {
    @Test
    fun protocolAppendOnlyValues_matchNxAndDriverContracts() {
        assertEquals(4, DeviceType.DEVICE_TYPE_ROBOT_CONTROLLER.value)
        assertEquals(1, MappingGridEncoding.MAPPING_GRID_ENCODING_ZLIB_INT8.value)
        assertEquals(1, PoseSource.POSE_SOURCE_MAPPING_ODOMETRY.value)
        assertEquals(2, PoseSource.POSE_SOURCE_LOCALIZATION.value)
        val encoded = MapInfo(origin_yaw_rad = 0.625).encode()
        assertEquals(0x61, encoded.first().toInt() and 0xff)
        assertEquals(0.625, MapInfo.ADAPTER.decode(encoded).origin_yaw_rad, 0.0)
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
    fun mapAndNavigationRequestsCarryMapRevisionPoseAndTaskIdentity() {
        val protocol = ControllerProtocol(deviceId = "remote-test", initialRequestId = 10L)

        val list = protocol.listMaps("session")
        val preview = protocol.getMapPreview("session", "map-a", 7L, 4096)
        val switch = protocol.switchMap("session", 8L, "map-a", 7L)
        val initial = protocol.setInitialPose("session", 9L, "map-a", 7L, 1.0, 2.0, 0.3)
        val plan = protocol.previewGoal("session", 10L, "map-a", 7L, 3.0, 4.0, 0.5)
        val navigation = protocol.startNavigation(
            "session", 11L, "map-a", 7L, 3.0, 4.0, 0.5
        )
        val cancel = protocol.cancelNavigation("session", 12L, "task-nav")
        val stop = protocol.stopRuntime("session", 13L)

        assertTrue(list.list_maps_request != null)
        assertEquals("map-a", preview.get_map_preview_request?.map?.map_id)
        assertEquals(7L, preview.get_map_preview_request?.map?.revision)
        assertEquals(4096, preview.get_map_preview_request?.preferred_chunk_bytes)
        assertEquals(8L, switch.command_request?.expected_state_revision)
        assertEquals(0.3, initial.command_request?.set_initial_pose?.pose?.yaw ?: Double.NaN, 0.0)
        assertEquals(7L, plan.command_request?.preview_goal?.goal?.map?.revision)
        assertEquals(
            3.0,
            navigation.command_request?.start_navigation?.goal?.pose?.x ?: Double.NaN,
            0.0
        )
        assertEquals("task-nav", cancel.command_request?.cancel_navigation?.task_id)
        assertTrue(stop.command_request?.stop_runtime != null)
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
