package com.helywin.leggedjoystick.mapping

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class SavedMapCoordinates(
    val resolutionM: Double,
    val widthCells: Int,
    val heightCells: Int,
    val origin: MappingPose
)

data class MapViewport(
    val widthPx: Double,
    val heightPx: Double
)

data class ScreenPoint(val x: Double, val y: Double)

object MapNavigationCoordinates {
    fun worldToScreen(
        metadata: SavedMapCoordinates,
        viewport: MapViewport,
        world: MappingPose
    ): ScreenPoint {
        validate(metadata, viewport)
        require(world.x.isFinite() && world.y.isFinite()) { "地图世界坐标无效" }
        val dx = world.x - metadata.origin.x
        val dy = world.y - metadata.origin.y
        val localX = cos(metadata.origin.yaw) * dx + sin(metadata.origin.yaw) * dy
        val localY = -sin(metadata.origin.yaw) * dx + cos(metadata.origin.yaw) * dy
        val fit = fit(metadata, viewport)
        return ScreenPoint(
            x = fit.offsetX + localX / metadata.resolutionM * fit.scale,
            y = fit.offsetY +
                (metadata.heightCells - localY / metadata.resolutionM) * fit.scale
        )
    }

    fun screenToWorld(
        metadata: SavedMapCoordinates,
        viewport: MapViewport,
        screen: ScreenPoint
    ): MappingPose? {
        validate(metadata, viewport)
        if (!screen.x.isFinite() || !screen.y.isFinite()) return null
        val fit = fit(metadata, viewport)
        val imageX = (screen.x - fit.offsetX) / fit.scale
        val imageY = (screen.y - fit.offsetY) / fit.scale
        if (imageX < 0.0 || imageX >= metadata.widthCells ||
            imageY < 0.0 || imageY >= metadata.heightCells
        ) {
            return null
        }
        val localX = imageX * metadata.resolutionM
        val localY = (metadata.heightCells - imageY) * metadata.resolutionM
        return MappingPose(
            x = metadata.origin.x +
                cos(metadata.origin.yaw) * localX - sin(metadata.origin.yaw) * localY,
            y = metadata.origin.y +
                sin(metadata.origin.yaw) * localX + cos(metadata.origin.yaw) * localY,
            yaw = metadata.origin.yaw
        )
    }

    fun screenToImagePixel(
        metadata: SavedMapCoordinates,
        viewport: MapViewport,
        screen: ScreenPoint
    ): Pair<Int, Int>? {
        validate(metadata, viewport)
        if (!screen.x.isFinite() || !screen.y.isFinite()) return null
        val fit = fit(metadata, viewport)
        val imageX = (screen.x - fit.offsetX) / fit.scale
        val imageY = (screen.y - fit.offsetY) / fit.scale
        if (imageX < 0.0 || imageX >= metadata.widthCells ||
            imageY < 0.0 || imageY >= metadata.heightCells
        ) {
            return null
        }
        return imageX.toInt() to imageY.toInt()
    }

    fun dragToWorldYaw(
        metadata: SavedMapCoordinates,
        start: ScreenPoint,
        end: ScreenPoint,
        minimumDragPx: Double = 8.0
    ): Double? {
        if (!start.x.isFinite() || !start.y.isFinite() ||
            !end.x.isFinite() || !end.y.isFinite()
        ) {
            return null
        }
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (hypot(dx, dy) < minimumDragPx) return null
        return normalizeYaw(atan2(-dy, dx) + metadata.origin.yaw)
    }

    private data class Fit(val scale: Double, val offsetX: Double, val offsetY: Double)

    private fun fit(metadata: SavedMapCoordinates, viewport: MapViewport): Fit {
        val scale = min(
            viewport.widthPx / metadata.widthCells,
            viewport.heightPx / metadata.heightCells
        )
        return Fit(
            scale = scale,
            offsetX = (viewport.widthPx - metadata.widthCells * scale) / 2.0,
            offsetY = (viewport.heightPx - metadata.heightCells * scale) / 2.0
        )
    }

    private fun validate(metadata: SavedMapCoordinates, viewport: MapViewport) {
        require(metadata.resolutionM.isFinite() && metadata.resolutionM > 0.0) {
            "地图分辨率无效"
        }
        require(metadata.widthCells > 0 && metadata.heightCells > 0) { "地图尺寸无效" }
        require(metadata.origin.x.isFinite() && metadata.origin.y.isFinite() &&
            metadata.origin.yaw.isFinite()
        ) { "地图原点无效" }
        require(viewport.widthPx.isFinite() && viewport.widthPx > 0.0 &&
            viewport.heightPx.isFinite() && viewport.heightPx > 0.0
        ) { "地图视口尺寸无效" }
    }

    private fun normalizeYaw(value: Double): Double {
        var normalized = value
        while (normalized > PI) normalized -= 2.0 * PI
        while (normalized <= -PI) normalized += 2.0 * PI
        return normalized
    }
}
