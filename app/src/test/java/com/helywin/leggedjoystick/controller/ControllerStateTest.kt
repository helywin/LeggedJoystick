package com.helywin.leggedjoystick.controller

import com.helywin.leggedjoystick.data.ControlOwnershipState
import com.helywin.leggedjoystick.data.ConnectionState
import legged_driver.BatteryDataMessage
import legged_driver.CtrlSource
import legged_driver.FillLightStatus
import legged_driver.HeadDirection
import legged_driver.MotionStatus
import legged_driver.RobotStateMessage
import legged_driver.ConnectionState as DriverConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
