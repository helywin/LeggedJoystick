package com.helywin.leggedjoystick.mapping

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class MappingPose(
    val x: Double,
    val y: Double,
    val yaw: Double
)

data class MappingGridMetadataModel(
    val frameSequence: Long,
    val frameId: String,
    val sourceTimeNs: Long,
    val resolutionM: Double,
    val widthCells: Int,
    val heightCells: Int,
    val origin: MappingPose,
    val encodingValue: Int,
    val uncompressedSizeBytes: Long,
    val compressedSizeBytes: Long,
    val sha256: String
)

data class MappingGridChunkModel(
    val metadata: MappingGridMetadataModel,
    val chunkIndex: Int,
    val chunkCount: Int,
    val data: ByteArray
)

data class MappingGridFrame(
    val metadata: MappingGridMetadataModel,
    val cells: ByteArray,
    val receivedAtMs: Long
)

data class GridPoint(val x: Double, val y: Double)

object MappingClock {
    fun elapsedRealtimeMs(): Long = System.nanoTime() / 1_000_000L
}

object MappingFreshness {
    fun ageMs(
        tickerElapsedMs: Long,
        observedElapsedMs: Long,
        receivedAtElapsedMs: Long
    ): Long {
        return max(tickerElapsedMs, observedElapsedMs)
            .minus(receivedAtElapsedMs)
            .coerceAtLeast(0L)
    }
}

object MappingCoordinates {
    fun worldToGrid(metadata: MappingGridMetadataModel, world: MappingPose): GridPoint {
        require(metadata.resolutionM.isFinite() && metadata.resolutionM > 0.0) {
            "地图分辨率无效"
        }
        val dx = world.x - metadata.origin.x
        val dy = world.y - metadata.origin.y
        val originYaw = metadata.origin.yaw
        return GridPoint(
            x = (cos(originYaw) * dx + sin(originYaw) * dy) / metadata.resolutionM,
            y = (-sin(originYaw) * dx + cos(originYaw) * dy) / metadata.resolutionM
        )
    }

    fun gridToNormalizedScreen(
        metadata: MappingGridMetadataModel,
        world: MappingPose
    ): GridPoint {
        val grid = worldToGrid(metadata, world)
        return GridPoint(
            x = grid.x / metadata.widthCells,
            y = 1.0 - grid.y / metadata.heightCells
        )
    }

    fun screenYaw(metadata: MappingGridMetadataModel, worldYaw: Double): Double {
        return -(worldYaw - metadata.origin.yaw)
    }
}
