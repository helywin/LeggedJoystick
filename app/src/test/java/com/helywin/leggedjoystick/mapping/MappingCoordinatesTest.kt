package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class MappingCoordinatesTest {
    @Test
    fun worldToGrid_appliesOriginTranslationResolutionAndScreenFlip() {
        val metadata = metadata(origin = MappingPose(10.0, 20.0, 0.0))

        val grid = MappingCoordinates.worldToGrid(metadata, MappingPose(11.0, 21.0, 0.0))
        val screen = MappingCoordinates.gridToNormalizedScreen(
            metadata,
            MappingPose(11.0, 21.0, 0.0)
        )

        assertEquals(10.0, grid.x, 1e-9)
        assertEquals(10.0, grid.y, 1e-9)
        assertEquals(0.1, screen.x, 1e-9)
        assertEquals(0.9, screen.y, 1e-9)
    }

    @Test
    fun worldToGrid_appliesInverseOriginRotation() {
        val metadata = metadata(origin = MappingPose(0.0, 0.0, PI / 2.0))

        val grid = MappingCoordinates.worldToGrid(metadata, MappingPose(0.0, 1.0, PI / 2.0))

        assertEquals(10.0, grid.x, 1e-9)
        assertEquals(0.0, grid.y, 1e-9)
        assertEquals(0.0, MappingCoordinates.screenYaw(metadata, PI / 2.0), 1e-9)
    }

    private fun metadata(origin: MappingPose) = MappingGridMetadataModel(
        frameSequence = 1,
        frameId = "map",
        sourceTimeNs = 1,
        resolutionM = 0.1,
        widthCells = 100,
        heightCells = 100,
        origin = origin,
        encodingValue = 1,
        uncompressedSizeBytes = 10_000,
        compressedSizeBytes = 1,
        sha256 = "0".repeat(64)
    )
}
