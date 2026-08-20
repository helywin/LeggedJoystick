package com.helywin.leggedjoystick.mapping

import kotlin.math.abs

/**
 * 地图适配窗口后的附加视图变换。
 *
 * 坐标换算仍以未缩放的地图视口为基准，绘制时使用 [apply]，触摸命中时使用
 * [invert]，避免把 UI 缩放误写进地图世界坐标。
 */
data class MapViewTransform(
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0
) {
    fun apply(viewport: MapViewport, point: ScreenPoint): ScreenPoint {
        validate(viewport)
        val centerX = viewport.widthPx / 2.0
        val centerY = viewport.heightPx / 2.0
        return ScreenPoint(
            x = centerX + (point.x - centerX) * scale + offsetX,
            y = centerY + (point.y - centerY) * scale + offsetY
        )
    }

    fun invert(viewport: MapViewport, point: ScreenPoint): ScreenPoint {
        validate(viewport)
        val centerX = viewport.widthPx / 2.0
        val centerY = viewport.heightPx / 2.0
        return ScreenPoint(
            x = centerX + (point.x - centerX - offsetX) / scale,
            y = centerY + (point.y - centerY - offsetY) / scale
        )
    }

    fun update(
        viewport: MapViewport,
        centroid: ScreenPoint,
        panX: Double,
        panY: Double,
        zoomFactor: Double,
        minimumScale: Double = MINIMUM_SCALE,
        maximumScale: Double = MAXIMUM_SCALE
    ): MapViewTransform {
        validate(viewport)
        require(centroid.x.isFinite() && centroid.y.isFinite()) { "缩放中心无效" }
        require(panX.isFinite() && panY.isFinite()) { "地图平移量无效" }
        require(zoomFactor.isFinite() && zoomFactor > 0.0) { "地图缩放倍率无效" }
        require(minimumScale > 0.0 && maximumScale >= minimumScale) { "地图缩放范围无效" }

        val nextScale = (scale * zoomFactor).coerceIn(minimumScale, maximumScale)
        val appliedZoom = nextScale / scale
        val centerX = viewport.widthPx / 2.0
        val centerY = viewport.heightPx / 2.0
        val nextOffsetX = centroid.x - centerX -
            (centroid.x - centerX - offsetX) * appliedZoom + panX
        val nextOffsetY = centroid.y - centerY -
            (centroid.y - centerY - offsetY) * appliedZoom + panY
        val maxOffsetX = viewport.widthPx * (nextScale - minimumScale) / 2.0
        val maxOffsetY = viewport.heightPx * (nextScale - minimumScale) / 2.0
        return MapViewTransform(
            scale = nextScale,
            offsetX = nextOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
            offsetY = nextOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
        ).normalized()
    }

    fun isIdentity(): Boolean {
        return abs(scale - 1.0) < EPSILON && abs(offsetX) < EPSILON &&
            abs(offsetY) < EPSILON
    }

    private fun normalized(): MapViewTransform {
        return if (abs(scale - 1.0) < EPSILON) IDENTITY else this
    }

    private fun validate(viewport: MapViewport) {
        require(scale.isFinite() && scale > 0.0 && offsetX.isFinite() && offsetY.isFinite()) {
            "地图视图变换无效"
        }
        require(viewport.widthPx.isFinite() && viewport.widthPx > 0.0 &&
            viewport.heightPx.isFinite() && viewport.heightPx > 0.0
        ) { "地图视口尺寸无效" }
    }

    companion object {
        const val MINIMUM_SCALE = 1.0
        const val MAXIMUM_SCALE = 6.0
        private const val EPSILON = 1.0e-9
        val IDENTITY = MapViewTransform()
    }
}
