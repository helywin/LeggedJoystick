package com.helywin.leggedjoystick.controller

import com.helywin.leggedjoystick.data.ControlOwnershipState
import legged_driver.CtrlSource
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
}
