package com.helywin.leggedjoystick.input.remote

import kotlin.math.abs
import kotlin.math.sign

enum class RemoteInputStatus {
    STOPPED,
    STARTING,
    RUNNING,
    TIMEOUT,
    ERROR
}

data class RemoteInputSourceDescriptor(
    val id: String,
    val displayName: String,
    val transport: String
)

data class MovementIntent(
    val forward: Float,
    val strafeRight: Float,
    val yawRight: Float
) {
    val isZero: Boolean
        get() = forward == 0f && strafeRight == 0f && yawRight == 0f

    fun clamped(): MovementIntent {
        return MovementIntent(
            forward = forward.coerceIn(-1f, 1f),
            strafeRight = strafeRight.coerceIn(-1f, 1f),
            yawRight = yawRight.coerceIn(-1f, 1f)
        )
    }

    companion object {
        val ZERO = MovementIntent(0f, 0f, 0f)
    }
}

data class HeadControlIntent(
    val pitchUp: Float
) {
    val isZero: Boolean
        get() = pitchUp == 0f

    fun clamped(): HeadControlIntent {
        return HeadControlIntent(
            pitchUp = pitchUp.coerceIn(-1f, 1f)
        )
    }

    companion object {
        val ZERO = HeadControlIntent(0f)
    }
}

enum class RemoteSpeedLevelRequest {
    LOW,
    MEDIUM,
    HIGH
}

data class RemoteInputSnapshot(
    val descriptor: RemoteInputSourceDescriptor,
    val movementIntent: MovementIntent,
    val headControlIntent: HeadControlIntent = HeadControlIntent.ZERO,
    val speedLevelRequest: RemoteSpeedLevelRequest? = null,
    val rawChannels: List<Int> = emptyList(),
    val normalizedAxes: Map<String, Float> = emptyMap(),
    val sequence: Int = 0,
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class RemoteInputRuntimeState(
    val status: RemoteInputStatus = RemoteInputStatus.STOPPED,
    val sourceName: String = "",
    val lastFrameAtMs: Long = 0,
    val lastError: String = "",
    val latestSnapshot: RemoteInputSnapshot? = null
)

data class RemoteInputAxisMapping(
    val channel: Int,
    val inverted: Boolean = false
)

data class RemoteInputSpeedSelectorMapping(
    val channel: Int,
    val lowRaw: Int = 1000,
    val mediumRaw: Int = 1200,
    val highRaw: Int = 1400,
    val tolerance: Int = 90
)

data class RemoteInputChannelMapping(
    val forward: RemoteInputAxisMapping = RemoteInputAxisMapping(channel = 3),
    val strafeRight: RemoteInputAxisMapping = RemoteInputAxisMapping(channel = 4),
    val yawRight: RemoteInputAxisMapping = RemoteInputAxisMapping(channel = 1),
    val headPitchUp: RemoteInputAxisMapping = RemoteInputAxisMapping(channel = 2, inverted = true),
    val speedSelector: RemoteInputSpeedSelectorMapping = RemoteInputSpeedSelectorMapping(channel = 5)
)

data class RemoteInputNormalizationConfig(
    val min: Int = 1050,
    val center: Int = 1500,
    val max: Int = 1950,
    val deadZone: Float = 0.08f,
    val timeoutMs: Long = 250L,
    val mapping: RemoteInputChannelMapping = RemoteInputChannelMapping()
) {
    init {
        require(min < center) { "通道最小值必须小于中位值" }
        require(center < max) { "通道中位值必须小于最大值" }
        require(deadZone in 0f..0.95f) { "死区必须在 0 到 0.95 之间" }
    }
}

interface RemoteInputSource {
    val descriptor: RemoteInputSourceDescriptor
    fun start(listener: RemoteInputListener)
    fun stop()
}

interface RemoteInputListener {
    fun onStatusChanged(descriptor: RemoteInputSourceDescriptor, status: RemoteInputStatus, message: String = "")
    fun onSnapshot(snapshot: RemoteInputSnapshot)
}

fun normalizeRemoteChannel(
    raw: Int,
    axisMapping: RemoteInputAxisMapping,
    config: RemoteInputNormalizationConfig
): Float {
    val centered = raw - config.center
    val normalized = if (centered >= 0) {
        centered.toFloat() / (config.max - config.center).toFloat()
    } else {
        centered.toFloat() / (config.center - config.min).toFloat()
    }

    val clamped = normalized.coerceIn(-1f, 1f)
    val directed = if (axisMapping.inverted) -clamped else clamped
    return applyDeadZone(directed, config.deadZone)
}

private fun applyDeadZone(value: Float, deadZone: Float): Float {
    val magnitude = abs(value)
    if (magnitude <= deadZone) return 0f
    return sign(value) * ((magnitude - deadZone) / (1f - deadZone))
}
