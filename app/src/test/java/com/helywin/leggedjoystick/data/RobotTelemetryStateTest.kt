package com.helywin.leggedjoystick.data

import legged_driver.FaultCode
import legged_driver.FaultDataMessage
import legged_driver.FaultLevel
import legged_driver.FaultListMessage
import legged_driver.MotionDataMessage
import legged_driver.OdometryMessage
import legged_driver.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

class RobotTelemetryStateTest {
    @Test
    fun toMotionTelemetry_mapsMotionVectors() {
        val telemetry = MotionDataMessage(
            body_velocity = Vector3(x = 1.1f, y = 2.2f, z = 3.3f),
            world_velocity = Vector3(x = 4.4f, y = 5.5f, z = 6.6f),
            position = Vector3(x = 7.7f, y = 8.8f, z = 9.9f),
            body_angular_velocity = Vector3(x = 0.1f, y = 0.2f, z = 0.3f)
        ).toMotionTelemetry(updatedAtMs = 123L)

        assertEquals(1.1f, telemetry.bodyVelocity.x, FLOAT_DELTA)
        assertEquals(5.5f, telemetry.worldVelocity.y, FLOAT_DELTA)
        assertEquals(9.9f, telemetry.position.z, FLOAT_DELTA)
        assertEquals(0.3f, telemetry.bodyAngularVelocity.z, FLOAT_DELTA)
        assertEquals(123L, telemetry.updatedAtMs)
    }

    @Test
    fun toOdometryTelemetry_mapsOdometryVectors() {
        val telemetry = OdometryMessage(
            position = Vector3(x = 1f, y = 2f, z = 3f),
            linear_velocity = Vector3(x = 4f, y = 5f, z = 6f),
            angular_velocity = Vector3(x = 7f, y = 8f, z = 9f)
        ).toOdometryTelemetry(updatedAtMs = 456L)

        assertEquals(2f, telemetry.position.y, FLOAT_DELTA)
        assertEquals(4f, telemetry.linearVelocity.x, FLOAT_DELTA)
        assertEquals(9f, telemetry.angularVelocity.z, FLOAT_DELTA)
        assertEquals(456L, telemetry.updatedAtMs)
    }

    @Test
    fun toFaultTelemetry_picksHighestSeverityFault() {
        val telemetry = FaultListMessage(
            faults = listOf(
                FaultDataMessage(
                    code = FaultCode.FAULT_CODE_ACTUATOR_TEMP_WARN,
                    level = FaultLevel.FAULT_LEVEL_WARN,
                    message = "电机温度预警"
                ),
                FaultDataMessage(
                    code = FaultCode.FAULT_CODE_CAN_BROKEN,
                    level = FaultLevel.FAULT_LEVEL_ERROR,
                    message = "CAN 中断"
                )
            )
        ).toFaultTelemetry(updatedAtMs = 789L)

        assertEquals(2, telemetry.faultCount)
        assertEquals(FaultLevel.FAULT_LEVEL_ERROR, telemetry.highestLevel)
        assertEquals(FaultCode.FAULT_CODE_CAN_BROKEN, telemetry.highestCode)
        assertEquals("CAN 中断", telemetry.highestMessage)
        assertEquals(789L, telemetry.updatedAtMs)
    }

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
