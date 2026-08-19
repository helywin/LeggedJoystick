package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class MapNavigationCoordinatesTest {
    @Test
    fun roundTripIncludesOriginRotationVerticalFlipAndLetterbox() {
        val metadata = SavedMapCoordinates(
            resolutionM = 0.1,
            widthCells = 100,
            heightCells = 50,
            origin = MappingPose(10.0, -5.0, PI / 2.0)
        )
        val viewport = MapViewport(widthPx = 300.0, heightPx = 300.0)
        val world = MappingPose(8.0, -2.0, 0.0)

        val screen = MapNavigationCoordinates.worldToScreen(metadata, viewport, world)
        val restored = MapNavigationCoordinates.screenToWorld(metadata, viewport, screen)

        assertEquals(90.0, screen.x, 1e-9)
        assertEquals(165.0, screen.y, 1e-9)
        assertEquals(world.x, restored!!.x, 1e-9)
        assertEquals(world.y, restored.y, 1e-9)
    }

    @Test
    fun screenPointInsideLetterboxIsRejected() {
        val metadata = metadata()
        val viewport = MapViewport(widthPx = 400.0, heightPx = 400.0)

        assertNull(
            MapNavigationCoordinates.screenToWorld(
                metadata,
                viewport,
                ScreenPoint(200.0, 50.0)
            )
        )
    }

    @Test
    fun dragDirectionConvertsScreenDownToNegativeMapYaw() {
        val metadata = metadata(originYaw = PI / 2.0)

        val right = MapNavigationCoordinates.dragToWorldYaw(
            metadata,
            ScreenPoint(10.0, 10.0),
            ScreenPoint(30.0, 10.0)
        )
        val down = MapNavigationCoordinates.dragToWorldYaw(
            metadata,
            ScreenPoint(10.0, 10.0),
            ScreenPoint(10.0, 30.0)
        )

        assertEquals(PI / 2.0, right!!, 1e-9)
        assertEquals(0.0, down!!, 1e-9)
        assertNull(
            MapNavigationCoordinates.dragToWorldYaw(
                metadata,
                ScreenPoint(10.0, 10.0),
                ScreenPoint(11.0, 11.0)
            )
        )
        assertTrue(right.isFinite())
    }

    private fun metadata(originYaw: Double = 0.0) = SavedMapCoordinates(
        resolutionM = 0.1,
        widthCells = 100,
        heightCells = 50,
        origin = MappingPose(0.0, 0.0, originYaw)
    )
}
