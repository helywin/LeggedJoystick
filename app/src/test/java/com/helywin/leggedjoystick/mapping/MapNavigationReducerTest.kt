package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapNavigationReducerTest {
    @Test
    fun currentMapCanBeReloadedWhenControllerAllowsSwitching() {
        val current = map().identity
        val state = MapNavigationState(currentMap = current, selectedMap = current)

        assertTrue(state.canLoadSelectedMap(switchMapAllowed = true))
        assertFalse(state.canLoadSelectedMap(switchMapAllowed = false))
        assertFalse(MapNavigationState().canLoadSelectedMap(switchMapAllowed = true))
    }

    @Test
    fun newSessionClearsAllDraftsAndOldEventsAreIgnored() {
        val state = plannedState(session = 1L)

        val changed = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.SessionChanged(2L)
        )
        val afterOldMaps = MapNavigationReducer.reduce(
            changed,
            MapNavigationEvent.MapsReceived(1L, listOf(map()))
        )

        assertEquals(2L, changed.sessionGeneration)
        assertNull(changed.targetDraft)
        assertNull(changed.pathPreview)
        assertTrue(afterOldMaps.maps.isEmpty())
    }

    @Test
    fun mapRevisionChangeClearsCoordinateDraftsAndPath() {
        val state = plannedState(session = 1L)
        val nextMap = MapIdentityModel("map-a", 8L)

        val result = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = nextMap,
                localizationStatus = MapLocalizationStatus.INITIALIZING,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = "load-map",
                robotPose = null
            )
        )

        assertEquals(nextMap, result.currentMap)
        assertEquals(nextMap, result.selectedMap)
        assertNull(result.initialPoseDraft)
        assertNull(result.targetDraft)
        assertNull(result.pathPreview)
    }

    @Test
    fun loadingTaskMapIsUsedForInitialPoseBeforeCurrentMapChanges() {
        val oldMap = map()
        val loadingMap = MapIdentityModel("map-b", 1L)
        val state = baseState(session = 1L).copy(
            maps = listOf(oldMap, map(identity = loadingMap)),
            currentMap = oldMap.identity,
            selectedMap = oldMap.identity
        )

        val result = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = oldMap.identity,
                loadingMap = loadingMap,
                localizationStatus = MapLocalizationStatus.INITIALIZING,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = "load-map-b",
                robotPose = null
            )
        )

        assertEquals(oldMap.identity, result.currentMap)
        assertEquals(loadingMap, result.loadingMap)
        assertEquals(loadingMap, result.initialPoseMap)
        assertEquals(loadingMap, result.selectedMap)
        assertNull(result.initialPoseDraft)
        assertNull(result.targetDraft)
    }

    @Test
    fun localizationTrackingClearsSubmittedInitialPoseDraft() {
        val state = baseState(session = 1L).copy(
            localizationStatus = MapLocalizationStatus.INITIALIZING,
            targetDraft = null
        )

        val result = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = state.currentMap,
                localizationStatus = MapLocalizationStatus.TRACKING,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = null,
                robotPose = MappingPose(1.0, 2.0, 0.0)
            )
        )

        assertNull(result.initialPoseDraft)
        assertEquals(state.currentMap, result.currentMap)
    }

    @Test
    fun runtimeStopClearsAllDraftsButKeepsCurrentMap() {
        val state = plannedState(session = 1L)

        val result = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = state.currentMap,
                localizationRuntimeActive = false,
                localizationStatus = MapLocalizationStatus.UNKNOWN,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = null,
                robotPose = null
            )
        )

        assertEquals(state.currentMap, result.currentMap)
        assertEquals(state.selectedMap, result.selectedMap)
        assertNull(result.initialPoseDraft)
        assertNull(result.targetDraft)
        assertNull(result.pathPreview)
    }

    @Test
    fun localizationLossClearsTargetButKeepsCurrentMapAndInitialDraft() {
        val state = plannedState(session = 1L)

        val result = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = state.currentMap,
                localizationStatus = MapLocalizationStatus.LOST,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = null,
                robotPose = null
            )
        )

        assertEquals(state.currentMap, result.currentMap)
        assertEquals(state.initialPoseDraft, result.initialPoseDraft)
        assertNull(result.targetDraft)
        assertNull(result.pathPreview)
    }

    @Test
    fun targetEditAndManualTakeoverInvalidateNavigationConfirmation() {
        val state = plannedState(session = 1L).copy(controlOwner = MapControlOwner.NAVIGATION_AUTO)
        val edited = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.EditTarget(MappingPose(9.0, 8.0, 0.2))
        )
        val manual = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = state.currentMap,
                localizationStatus = MapLocalizationStatus.TRACKING,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = null,
                robotPose = MappingPose(1.0, 2.0, 0.0)
            )
        )

        assertTrue(edited.selectionGeneration > state.selectionGeneration)
        assertNull(edited.pathPreview)
        assertNull(manual.pathPreview)
        assertNull(manual.pendingPlan)
    }

    @Test
    fun onlyMatchingTaskMapAndSelectionMayPublishPath() {
        val state = baseState(session = 1L)
        val requested = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.PlanRequested("plan-1")
        )
        val stale = MapNavigationReducer.reduce(
            requested,
            MapNavigationEvent.PathReceived(
                generation = 1L,
                taskId = "plan-old",
                map = checkNotNull(state.selectedMap),
                selectionGeneration = state.selectionGeneration,
                points = listOf(MappingPose(1.0, 1.0, 0.0)),
                lengthM = 1.0
            )
        )
        val accepted = MapNavigationReducer.reduce(
            requested,
            MapNavigationEvent.PathReceived(
                generation = 1L,
                taskId = "plan-1",
                map = checkNotNull(state.selectedMap),
                selectionGeneration = state.selectionGeneration,
                points = listOf(MappingPose(1.0, 1.0, 0.0)),
                lengthM = 1.0
            )
        )

        assertSame(requested, stale)
        assertEquals("plan-1", accepted.pathPreview!!.token.taskId)
        assertNull(accepted.pendingPlan)
    }

    private fun plannedState(session: Long): MapNavigationState {
        val requested = MapNavigationReducer.reduce(
            baseState(session),
            MapNavigationEvent.PlanRequested("plan-1")
        )
        return MapNavigationReducer.reduce(
            requested,
            MapNavigationEvent.PathReceived(
                generation = session,
                taskId = "plan-1",
                map = checkNotNull(requested.selectedMap),
                selectionGeneration = requested.selectionGeneration,
                points = listOf(MappingPose(1.0, 1.0, 0.0)),
                lengthM = 2.0
            )
        )
    }

    private fun baseState(session: Long): MapNavigationState {
        val map = map()
        return MapNavigationState(
            sessionGeneration = session,
            maps = listOf(map),
            currentMap = map.identity,
            selectedMap = map.identity,
            localizationStatus = MapLocalizationStatus.TRACKING,
            controlOwner = MapControlOwner.REMOTE_MANUAL,
            initialPoseDraft = MappingPose(0.0, 0.0, 0.0),
            targetDraft = MappingPose(2.0, 3.0, 0.5),
            selectionGeneration = 4L
        )
    }

    private fun map(
        identity: MapIdentityModel = MapIdentityModel("map-a", 7L)
    ) = SavedMapDescriptor(
        identity = identity,
        displayName = "地图 A",
        previewHash = "sha256:${"0".repeat(64)}",
        coordinates = SavedMapCoordinates(
            resolutionM = 0.05,
            widthCells = 100,
            heightCells = 80,
            origin = MappingPose(-1.0, -2.0, 0.25)
        )
    )
}
