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
    fun targetEditInvalidatesPreviewButCancelPreservesReusablePlan() {
        val state = plannedState(session = 1L).copy(
            controlOwner = MapControlOwner.NAVIGATION_AUTO,
            activeTaskId = "nav-1",
            activeNavigationTaskId = "nav-1"
        )
        val navigating = MapNavigationReducer.reduce(
            state,
            navigationPathEvent(
                taskId = "nav-1",
                map = checkNotNull(state.currentMap),
                sequence = 1L
            )
        )
        val edited = MapNavigationReducer.reduce(
            state,
            MapNavigationEvent.EditTarget(MappingPose(9.0, 8.0, 0.2))
        )
        val canceled = MapNavigationReducer.reduce(
            navigating,
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
        assertEquals(state.targetDraft, canceled.targetDraft)
        assertEquals(state.pathPreview, canceled.pathPreview)
        assertNull(canceled.navigationPath)
        assertNull(canceled.activeNavigationTaskId)
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

    @Test
    fun realtimeNavigationPathAcceptsOnlyNewerMatchingRouteAndExplicitClear() {
        val active = baseState(session = 1L).copy(
            controlOwner = MapControlOwner.NAVIGATION_AUTO,
            activeTaskId = "nav-1",
            activeNavigationTaskId = "nav-1"
        )
        val map = checkNotNull(active.currentMap)
        val first = MapNavigationReducer.reduce(
            active,
            navigationPathEvent(taskId = "nav-1", map = map, sequence = 10L)
        )
        val stale = MapNavigationReducer.reduce(
            first,
            navigationPathEvent(taskId = "nav-1", map = map, sequence = 9L)
        )
        val wrongTask = MapNavigationReducer.reduce(
            first,
            navigationPathEvent(taskId = "nav-old", map = map, sequence = 11L)
        )
        val replaced = MapNavigationReducer.reduce(
            first,
            navigationPathEvent(
                taskId = "nav-1",
                map = map,
                sequence = 11L,
                points = listOf(MappingPose(0.0, 0.0, 0.0), MappingPose(4.0, 1.0, 0.2))
            )
        )
        val cleared = MapNavigationReducer.reduce(
            replaced,
            navigationPathEvent(
                taskId = "nav-1",
                map = map,
                sequence = 12L,
                points = emptyList(),
                active = false
            )
        )

        assertEquals(10L, first.navigationPath?.pathSequence)
        assertSame(first, stale)
        assertSame(first, wrongTask)
        assertEquals(11L, replaced.navigationPath?.pathSequence)
        assertEquals(4.0, replaced.navigationPath?.points?.last()?.x ?: Double.NaN, 0.0)
        assertNull(cleared.navigationPath)
    }

    @Test
    fun realtimeNavigationPathClearsWhenAuthorityTaskOrSessionChanges() {
        val active = baseState(session = 1L).copy(
            controlOwner = MapControlOwner.NAVIGATION_AUTO,
            activeTaskId = "nav-1",
            activeNavigationTaskId = "nav-1"
        )
        val withPath = MapNavigationReducer.reduce(
            active,
            navigationPathEvent(
                taskId = "nav-1",
                map = checkNotNull(active.currentMap),
                sequence = 1L
            )
        )
        val completed = MapNavigationReducer.reduce(
            withPath,
            MapNavigationEvent.AuthorityUpdated(
                generation = 1L,
                currentMap = active.currentMap,
                localizationStatus = MapLocalizationStatus.TRACKING,
                controlOwner = MapControlOwner.REMOTE_MANUAL,
                activeTaskId = null,
                activeNavigationTaskId = null,
                robotPose = MappingPose(1.0, 2.0, 0.0)
            )
        )
        val reconnected = MapNavigationReducer.reduce(
            withPath,
            MapNavigationEvent.SessionChanged(2L)
        )

        assertNull(completed.navigationPath)
        assertNull(reconnected.navigationPath)
        assertEquals(2L, reconnected.sessionGeneration)
    }

    @Test
    fun malformedRealtimeNavigationPathDoesNotReplaceValidRoute() {
        val active = baseState(session = 1L).copy(
            activeTaskId = "nav-1",
            activeNavigationTaskId = "nav-1"
        )
        val map = checkNotNull(active.currentMap)
        val valid = MapNavigationReducer.reduce(
            active,
            navigationPathEvent(taskId = "nav-1", map = map, sequence = 1L)
        )
        val malformed = MapNavigationReducer.reduce(
            valid,
            navigationPathEvent(
                taskId = "nav-1",
                map = map,
                sequence = 2L,
                points = listOf(MappingPose(Double.NaN, 0.0, 0.0))
            )
        )

        assertSame(valid, malformed)
    }

    private fun navigationPathEvent(
        taskId: String,
        map: MapIdentityModel,
        sequence: Long,
        points: List<MappingPose> = listOf(
            MappingPose(0.0, 0.0, 0.0),
            MappingPose(2.0, 0.0, 0.0)
        ),
        active: Boolean = true
    ) = MapNavigationEvent.NavigationPathReceived(
        generation = 1L,
        taskId = taskId,
        map = map,
        pathSequence = sequence,
        sourceTimeNs = 100L + sequence,
        frameId = "map",
        points = points,
        lengthM = if (active) 2.0 else 0.0,
        active = active
    )

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
