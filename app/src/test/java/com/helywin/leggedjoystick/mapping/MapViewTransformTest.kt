package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewTransformTest {
    private val viewport = MapViewport(widthPx = 1000.0, heightPx = 600.0)

    @Test
    fun identityKeepsScreenCoordinatesUnchanged() {
        val point = ScreenPoint(123.0, 456.0)

        assertEquals(point, MapViewTransform.IDENTITY.apply(viewport, point))
        assertEquals(point, MapViewTransform.IDENTITY.invert(viewport, point))
        assertTrue(MapViewTransform.IDENTITY.isIdentity())
    }

    @Test
    fun zoomKeepsGestureCentroidAnchoredAndRoundTrips() {
        val centroid = ScreenPoint(260.0, 180.0)
        val transformed = MapViewTransform.IDENTITY.update(
            viewport = viewport,
            centroid = centroid,
            panX = 0.0,
            panY = 0.0,
            zoomFactor = 2.5
        )
        val source = ScreenPoint(740.0, 420.0)

        assertPointEquals(centroid, transformed.apply(viewport, centroid))
        assertPointEquals(source, transformed.invert(viewport, transformed.apply(viewport, source)))
    }

    @Test
    fun panAndScaleRemainBoundedAndResettable() {
        val transformed = MapViewTransform.IDENTITY.update(
            viewport = viewport,
            centroid = ScreenPoint(500.0, 300.0),
            panX = 10_000.0,
            panY = -10_000.0,
            zoomFactor = 20.0
        )

        assertEquals(MapViewTransform.MAXIMUM_SCALE, transformed.scale, 1.0e-9)
        assertEquals(2500.0, transformed.offsetX, 1.0e-9)
        assertEquals(-1500.0, transformed.offsetY, 1.0e-9)
        assertTrue(MapViewTransform.IDENTITY.isIdentity())
    }

    @Test
    fun zoomedMapSelectionStillResolvesToOriginalWorldPosition() {
        val coordinates = SavedMapCoordinates(
            resolutionM = 0.05,
            widthCells = 800,
            heightCells = 500,
            origin = MappingPose(x = -12.0, y = 3.0, yaw = 0.4)
        )
        val world = MappingPose(x = 8.0, y = 12.0, yaw = 0.0)
        val transform = MapViewTransform.IDENTITY.update(
            viewport = viewport,
            centroid = ScreenPoint(310.0, 220.0),
            panX = 75.0,
            panY = -40.0,
            zoomFactor = 3.0
        )

        val baseScreen = MapNavigationCoordinates.worldToScreen(coordinates, viewport, world)
        val touchedScreen = transform.apply(viewport, baseScreen)
        val restoredWorld = MapNavigationCoordinates.screenToWorld(
            coordinates,
            viewport,
            transform.invert(viewport, touchedScreen)
        )

        assertEquals(world.x, restoredWorld!!.x, 1.0e-8)
        assertEquals(world.y, restoredWorld.y, 1.0e-8)
    }

    private fun assertPointEquals(expected: ScreenPoint, actual: ScreenPoint) {
        assertEquals(expected.x, actual.x, 1.0e-8)
        assertEquals(expected.y, actual.y, 1.0e-8)
    }
}
