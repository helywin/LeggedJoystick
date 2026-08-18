package com.helywin.leggedjoystick.controller

import com.helywin.leggedjoystick.data.ControlOwnershipState
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.mapping.MappingGridFrame
import com.helywin.leggedjoystick.mapping.MappingGridMetadataModel
import com.helywin.leggedjoystick.mapping.MappingPose
import legged_driver.BatteryDataMessage
import legged_driver.CtrlSource
import legged_driver.FillLightStatus
import legged_driver.HeadDirection
import legged_driver.MotionStatus
import legged_driver.RobotStateMessage
import legged_driver.ConnectionState as DriverConnectionState
import legged_driver.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sar.robot_controller.v1.CommandResponse
import sar.robot_controller.v1.CommandStage
import sar.robot_controller.v1.ControlOwner
import sar.robot_controller.v1.MappingStreamStatus
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.StateSnapshot

class ControllerStateTest {
    @Test
    fun robotControlSourceDoesNotOverrideOwnedStateAfterTakeAck() {
        val state = ControllerState()

        state.updateControlOwnership(ControlOwnershipState.OWNED, "接管成功")
        state.updateControlOwnershipFromSource(CtrlSource.CTRL_SOURCE_SDK)

        assertEquals(ControlOwnershipState.OWNED, state.controlOwnershipState)
    }

    @Test
    fun robotControlSourceDoesNotBlockLocalTakeBeforeAck() {
        val state = ControllerState()

        state.updateControlOwnership(ControlOwnershipState.AVAILABLE, "已连接，等待接管")
        state.updateControlOwnershipFromSource(CtrlSource.CTRL_SOURCE_SDK)

        assertEquals(ControlOwnershipState.AVAILABLE, state.controlOwnershipState)
    }

    @Test
    fun unknownRobotControlSourceDoesNotClearOwnedState() {
        val state = ControllerState()

        state.updateControlOwnership(ControlOwnershipState.OWNED, "接管成功")
        state.updateControlOwnershipFromSource(CtrlSource.CTRL_SOURCE_UNKNOWN)

        assertEquals(ControlOwnershipState.OWNED, state.controlOwnershipState)
    }

    @Test
    fun unknownRobotControlSourceFinishesPendingReleaseWhenAckIsMissing() {
        val state = ControllerState()

        state.updateControlOwnership(ControlOwnershipState.RELEASING, "正在释放")
        state.updateControlOwnershipFromSource(CtrlSource.CTRL_SOURCE_UNKNOWN)

        assertEquals(ControlOwnershipState.AVAILABLE, state.controlOwnershipState)
    }

    @Test
    fun sdkRobotControlSourceDoesNotFinishPendingRelease() {
        val state = ControllerState()

        state.updateControlOwnership(ControlOwnershipState.RELEASING, "正在释放")
        state.updateControlOwnershipFromSource(CtrlSource.CTRL_SOURCE_SDK)

        assertEquals(ControlOwnershipState.RELEASING, state.controlOwnershipState)
    }

    @Test
    fun nonConnectedStateClearsDriverTelemetry() {
        val state = ControllerState()

        state.updateDriverConnectionTelemetry(
            connectionState = DriverConnectionState.CONNECTION_STATE_CONNECTED,
            robotConnected = true
        )
        state.updateBatteryLevel(56)
        state.updateCurrentSpeedValue(0.42)

        state.updateConnectionState(ConnectionState.CONNECTION_TIMEOUT)

        assertEquals(DriverConnectionState.CONNECTION_STATE_DISCONNECTED, state.driverConnectionTelemetry.connectionState)
        assertEquals(false, state.driverConnectionTelemetry.robotConnected)
        assertEquals(0, state.batteryLevel)
        assertEquals(0.0, state.currentSpeedValue, 0.0)
    }

    @Test
    fun robotStateUpdatesBothBatteryLevelsAndAverage() {
        val state = ControllerState()

        state.updateBatteryLevels(
            RobotStateMessage(
                battery = BatteryDataMessage(
                    power1 = 82f,
                    power2 = 64f,
                    present1 = true,
                    present2 = true
                )
            )
        )

        assertEquals(82, state.battery1Level)
        assertEquals(64, state.battery2Level)
        assertEquals(73, state.batteryLevel)
    }

    @Test
    fun missingBatteryIsRepresentedAsUnavailable() {
        val state = ControllerState()

        state.updateBatteryLevels(
            RobotStateMessage(
                battery = BatteryDataMessage(
                    power1 = 82f,
                    present1 = true,
                    present2 = false
                )
            )
        )

        assertEquals(82, state.battery1Level)
        assertEquals(null, state.battery2Level)
        assertEquals(82, state.batteryLevel)
    }

    @Test
    fun robotStateUpdatesAuxiliaryUiState() {
        val state = ControllerState()

        state.updateRobotAuxiliaryState(
            RobotStateMessage(
                front_fill_light = FillLightStatus.FILL_LIGHT_STATUS_ON,
                back_fill_light = FillLightStatus.FILL_LIGHT_STATUS_OFF,
                auto_mode_light = true,
                head_direction = HeadDirection.HEAD_DIRECTION_TAIL,
                motion_status = MotionStatus.MOTION_STATUS_STAND_UP
            )
        )

        assertEquals(true, state.frontLightOn)
        assertEquals(false, state.backLightOn)
        assertEquals(true, state.autoModeLightOn)
        assertEquals(HeadDirection.HEAD_DIRECTION_TAIL, state.headDirection)
        assertEquals(MotionStatus.MOTION_STATUS_STAND_UP, state.motionStatus)
    }

    @Test
    fun manualTransition_waitsForMatchingResponseAfterState() {
        val state = ControllerState()
        state.updateRobotMode(AppMode.APP_MODE_AUTO)
        state.markManualControllerRequest(17L, "人工接管")

        state.updateControllerSnapshot(
            StateSnapshot(control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL)
        )
        assertEquals(AppMode.APP_MODE_AUTO, state.robotMode)
        assertTrue(state.isRobotModeChanging)

        state.completeControllerRequest(
            17L,
            CommandResponse(stage = CommandStage.COMMAND_STAGE_ACCEPTED)
        )
        assertEquals(AppMode.APP_MODE_MANUAL, state.robotMode)
        assertFalse(state.isRobotModeChanging)
    }

    @Test
    fun manualTransition_waitsForAuthoritativeStateAfterResponse() {
        val state = ControllerState()
        state.updateRobotMode(AppMode.APP_MODE_AUTO)
        state.markManualControllerRequest(18L, "人工接管")

        state.completeControllerRequest(
            18L,
            CommandResponse(stage = CommandStage.COMMAND_STAGE_ACCEPTED)
        )
        assertEquals(AppMode.APP_MODE_AUTO, state.robotMode)
        assertTrue(state.isRobotModeChanging)

        state.updateControllerSnapshot(
            StateSnapshot(control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL)
        )
        assertEquals(AppMode.APP_MODE_MANUAL, state.robotMode)
        assertFalse(state.isRobotModeChanging)
    }

    @Test
    fun manualTransition_ignoresStaleAutoStateWithoutClearingPendingRequest() {
        val state = ControllerState()
        state.updateRobotMode(AppMode.APP_MODE_AUTO)
        state.markManualControllerRequest(21L, "人工接管")

        state.updateControllerSnapshot(
            StateSnapshot(control_owner = ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO)
        )
        assertEquals(AppMode.APP_MODE_AUTO, state.robotMode)
        assertTrue(state.isRobotModeChanging)

        state.completeControllerRequest(
            21L,
            CommandResponse(stage = CommandStage.COMMAND_STAGE_ACCEPTED)
        )
        assertTrue(state.isRobotModeChanging)

        state.updateControllerSnapshot(
            StateSnapshot(control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL)
        )
        assertEquals(AppMode.APP_MODE_MANUAL, state.robotMode)
        assertFalse(state.isRobotModeChanging)
    }

    @Test
    fun newMappingSession_clearsPreviousFrameUntilFirstCurrentFrameArrives() {
        val state = ControllerState()
        state.updateMappingFrame(
            MappingGridFrame(
                metadata = MappingGridMetadataModel(
                    frameSequence = 7L,
                    frameId = "map",
                    sourceTimeNs = 10L,
                    resolutionM = 0.1,
                    widthCells = 1,
                    heightCells = 1,
                    origin = MappingPose(0.0, 0.0, 0.0),
                    encodingValue = 1,
                    uncompressedSizeBytes = 1L,
                    compressedSizeBytes = 1L,
                    sha256 = "0".repeat(64)
                ),
                cells = byteArrayOf(0),
                receivedAtMs = 20L
            )
        )

        state.updateControllerSnapshot(
            StateSnapshot(
                operation_mode = OperationMode.OPERATION_MODE_MAPPING_RUNNING,
                mapping_stream = MappingStreamStatus(available = false)
            )
        )

        assertEquals(null, state.mappingFrame)
        assertEquals("等待本轮实时地图首帧", state.mappingError)
    }

    @Test
    fun manualTransition_ignoresWrongResponseAndClearsOnRejection() {
        val state = ControllerState()
        state.updateRobotMode(AppMode.APP_MODE_AUTO)
        state.markManualControllerRequest(19L, "人工接管")
        state.updateControllerSnapshot(
            StateSnapshot(control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL)
        )

        state.completeControllerRequest(
            20L,
            CommandResponse(stage = CommandStage.COMMAND_STAGE_ACCEPTED)
        )
        assertEquals(AppMode.APP_MODE_AUTO, state.robotMode)

        state.completeControllerRequest(
            19L,
            CommandResponse(
                stage = CommandStage.COMMAND_STAGE_REJECTED,
                error_message = "底盘未就绪"
            )
        )
        assertEquals(AppMode.APP_MODE_AUTO, state.robotMode)
        assertFalse(state.isRobotModeChanging)
    }
}
