package com.helywin.leggedjoystick.data

import legged_driver.ConnectionState as DriverConnectionState
import legged_driver.FaultCode
import legged_driver.FaultDataMessage
import legged_driver.FaultLevel
import legged_driver.FaultListMessage
import legged_driver.MotionDataMessage
import legged_driver.OdometryMessage
import legged_driver.Vector3

data class DriverConnectionTelemetry(
    val connectionState: DriverConnectionState = DriverConnectionState.CONNECTION_STATE_DISCONNECTED,
    val robotConnected: Boolean = false,
    val updatedAtMs: Long = 0L
)

data class Vector3Telemetry(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    companion object {
        fun from(vector: Vector3?): Vector3Telemetry {
            return Vector3Telemetry(
                x = vector?.x ?: 0f,
                y = vector?.y ?: 0f,
                z = vector?.z ?: 0f
            )
        }
    }
}

data class MotionTelemetry(
    val bodyVelocity: Vector3Telemetry = Vector3Telemetry(),
    val worldVelocity: Vector3Telemetry = Vector3Telemetry(),
    val position: Vector3Telemetry = Vector3Telemetry(),
    val bodyAngularVelocity: Vector3Telemetry = Vector3Telemetry(),
    val updatedAtMs: Long = 0L
)

data class OdometryTelemetry(
    val position: Vector3Telemetry = Vector3Telemetry(),
    val linearVelocity: Vector3Telemetry = Vector3Telemetry(),
    val angularVelocity: Vector3Telemetry = Vector3Telemetry(),
    val updatedAtMs: Long = 0L
)

data class FaultTelemetry(
    val faultCount: Int = 0,
    val highestLevel: FaultLevel = FaultLevel.FAULT_LEVEL_UNKNOWN,
    val highestCode: FaultCode = FaultCode.FAULT_CODE_UNKNOWN,
    val highestMessage: String = "",
    val updatedAtMs: Long = 0L
)

fun MotionDataMessage.toMotionTelemetry(updatedAtMs: Long = System.currentTimeMillis()): MotionTelemetry {
    return MotionTelemetry(
        bodyVelocity = Vector3Telemetry.from(body_velocity),
        worldVelocity = Vector3Telemetry.from(world_velocity),
        position = Vector3Telemetry.from(position),
        bodyAngularVelocity = Vector3Telemetry.from(body_angular_velocity),
        updatedAtMs = updatedAtMs
    )
}

fun OdometryMessage.toOdometryTelemetry(updatedAtMs: Long = System.currentTimeMillis()): OdometryTelemetry {
    return OdometryTelemetry(
        position = Vector3Telemetry.from(position),
        linearVelocity = Vector3Telemetry.from(linear_velocity),
        angularVelocity = Vector3Telemetry.from(angular_velocity),
        updatedAtMs = updatedAtMs
    )
}

fun FaultListMessage.toFaultTelemetry(updatedAtMs: Long = System.currentTimeMillis()): FaultTelemetry {
    val highestFault = faults.maxWithOrNull(
        compareBy<FaultDataMessage> { it.level.severityRank() }
            .thenBy { it.code.value }
    )

    return FaultTelemetry(
        faultCount = faults.size,
        highestLevel = highestFault?.level ?: FaultLevel.FAULT_LEVEL_UNKNOWN,
        highestCode = highestFault?.code ?: FaultCode.FAULT_CODE_UNKNOWN,
        highestMessage = highestFault?.message.orEmpty(),
        updatedAtMs = updatedAtMs
    )
}

private fun FaultLevel.severityRank(): Int {
    return when (this) {
        FaultLevel.FAULT_LEVEL_FATAL_ERROR -> 4
        FaultLevel.FAULT_LEVEL_ERROR -> 3
        FaultLevel.FAULT_LEVEL_WARN -> 2
        FaultLevel.FAULT_LEVEL_UNKNOWN -> 1
    }
}
