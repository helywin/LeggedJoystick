package com.helywin.leggedjoystick.mapping

enum class MapLocalizationStatus {
    UNKNOWN,
    INITIALIZING,
    TRACKING,
    LOST
}

enum class MapControlOwner {
    DISABLED,
    REMOTE_MANUAL,
    NAVIGATION_AUTO
}

data class SavedMapDescriptor(
    val identity: MapIdentityModel,
    val displayName: String,
    val previewHash: String,
    val coordinates: SavedMapCoordinates,
    val contentHash: String = "",
    val createdUtcNs: Long = 0L
)

data class PlanRequestToken(
    val taskId: String,
    val map: MapIdentityModel,
    val selectionGeneration: Long,
    val goal: MappingPose
)

data class MapPathPreview(
    val token: PlanRequestToken,
    val points: List<MappingPose>,
    val lengthM: Double
)

data class MapNavigationState(
    val sessionGeneration: Long = 0L,
    val maps: List<SavedMapDescriptor> = emptyList(),
    val currentMap: MapIdentityModel? = null,
    val loadingMap: MapIdentityModel? = null,
    val selectedMap: MapIdentityModel? = null,
    val localizationStatus: MapLocalizationStatus = MapLocalizationStatus.UNKNOWN,
    val controlOwner: MapControlOwner = MapControlOwner.DISABLED,
    val activeTaskId: String? = null,
    val robotPose: MappingPose? = null,
    val initialPoseDraft: MappingPose? = null,
    val targetDraft: MappingPose? = null,
    val selectionGeneration: Long = 0L,
    val pendingPlan: PlanRequestToken? = null,
    val pathPreview: MapPathPreview? = null
) {
    val initialPoseMap: MapIdentityModel?
        get() = loadingMap ?: currentMap

    fun canLoadSelectedMap(switchMapAllowed: Boolean): Boolean {
        return selectedMap != null && switchMapAllowed
    }
}

sealed interface MapNavigationEvent {
    data class SessionChanged(val generation: Long) : MapNavigationEvent
    data class MapsReceived(
        val generation: Long,
        val maps: List<SavedMapDescriptor>
    ) : MapNavigationEvent

    data class AuthorityUpdated(
        val generation: Long,
        val currentMap: MapIdentityModel?,
        val loadingMap: MapIdentityModel? = null,
        val localizationRuntimeActive: Boolean = true,
        val localizationStatus: MapLocalizationStatus,
        val controlOwner: MapControlOwner,
        val activeTaskId: String?,
        val robotPose: MappingPose?
    ) : MapNavigationEvent

    data class SelectMap(val map: MapIdentityModel) : MapNavigationEvent
    data class EditInitialPose(val pose: MappingPose?) : MapNavigationEvent
    data class EditTarget(val pose: MappingPose?) : MapNavigationEvent
    data class PlanRequested(val taskId: String) : MapNavigationEvent
    data class PathReceived(
        val generation: Long,
        val taskId: String,
        val map: MapIdentityModel,
        val selectionGeneration: Long,
        val points: List<MappingPose>,
        val lengthM: Double
    ) : MapNavigationEvent
}

object MapNavigationReducer {
    fun reduce(state: MapNavigationState, event: MapNavigationEvent): MapNavigationState {
        return when (event) {
            is MapNavigationEvent.SessionChanged -> {
                if (event.generation == state.sessionGeneration) state
                else MapNavigationState(sessionGeneration = event.generation)
            }
            is MapNavigationEvent.MapsReceived -> reduceMaps(state, event)
            is MapNavigationEvent.AuthorityUpdated -> reduceAuthority(state, event)
            is MapNavigationEvent.SelectMap -> selectMap(state, event.map)
            is MapNavigationEvent.EditInitialPose -> {
                if (event.pose.isValidOrNull()) state.copy(initialPoseDraft = event.pose) else state
            }
            is MapNavigationEvent.EditTarget -> editTarget(state, event.pose)
            is MapNavigationEvent.PlanRequested -> requestPlan(state, event.taskId)
            is MapNavigationEvent.PathReceived -> receivePath(state, event)
        }
    }

    private fun reduceMaps(
        state: MapNavigationState,
        event: MapNavigationEvent.MapsReceived
    ): MapNavigationState {
        if (event.generation != state.sessionGeneration) return state
        val validMaps = event.maps.filter { it.isValid() }.distinctBy { it.identity }
        val identities = validMaps.mapTo(mutableSetOf(), SavedMapDescriptor::identity)
        val selected = state.selectedMap?.takeIf(identities::contains)
            ?: state.currentMap?.takeIf(identities::contains)
        return if (selected == state.selectedMap) {
            state.copy(maps = validMaps)
        } else {
            clearCoordinates(state.copy(maps = validMaps, selectedMap = selected))
        }
    }

    private fun reduceAuthority(
        state: MapNavigationState,
        event: MapNavigationEvent.AuthorityUpdated
    ): MapNavigationState {
        if (event.generation != state.sessionGeneration) return state
        val previousRuntimeMap = state.loadingMap ?: state.currentMap
        val runtimeMap = event.loadingMap ?: event.currentMap
        val mapChanged = runtimeMap != previousRuntimeMap
        val localizationLost = state.localizationStatus == MapLocalizationStatus.TRACKING &&
            event.localizationStatus != MapLocalizationStatus.TRACKING
        val localizationStartedTracking =
            state.localizationStatus != MapLocalizationStatus.TRACKING &&
                event.localizationStatus == MapLocalizationStatus.TRACKING
        val manualTakeover = state.controlOwner == MapControlOwner.NAVIGATION_AUTO &&
            event.controlOwner == MapControlOwner.REMOTE_MANUAL

        var next = state.copy(
            currentMap = event.currentMap,
            loadingMap = event.loadingMap,
            localizationStatus = event.localizationStatus,
            controlOwner = event.controlOwner,
            activeTaskId = event.activeTaskId,
            robotPose = event.robotPose?.takeIf { it.isValid() }
        )
        if (!event.localizationRuntimeActive) {
            next = clearCoordinates(next)
        } else if (mapChanged) {
            next = clearCoordinates(next.copy(selectedMap = runtimeMap))
        } else if (localizationStartedTracking) {
            next = next.copy(initialPoseDraft = null)
        } else if (localizationLost) {
            next = clearTarget(next)
        } else if (manualTakeover) {
            next = next.copy(pendingPlan = null, pathPreview = null)
        }
        return next
    }

    private fun selectMap(state: MapNavigationState, map: MapIdentityModel): MapNavigationState {
        if (state.maps.none { it.identity == map } || state.selectedMap == map) return state
        return clearCoordinates(state.copy(selectedMap = map))
    }

    private fun editTarget(state: MapNavigationState, pose: MappingPose?): MapNavigationState {
        if (!pose.isValidOrNull()) return state
        return state.copy(
            targetDraft = pose,
            selectionGeneration = state.selectionGeneration + 1L,
            pendingPlan = null,
            pathPreview = null
        )
    }

    private fun requestPlan(state: MapNavigationState, taskId: String): MapNavigationState {
        val map = state.selectedMap ?: return state
        val goal = state.targetDraft ?: return state
        if (taskId.isBlank() || !goal.isValid()) return state
        return state.copy(
            pendingPlan = PlanRequestToken(taskId, map, state.selectionGeneration, goal),
            pathPreview = null
        )
    }

    private fun receivePath(
        state: MapNavigationState,
        event: MapNavigationEvent.PathReceived
    ): MapNavigationState {
        if (event.generation != state.sessionGeneration) return state
        val pending = state.pendingPlan ?: return state
        if (pending.taskId != event.taskId || pending.map != event.map ||
            pending.selectionGeneration != event.selectionGeneration ||
            event.lengthM < 0.0 || !event.lengthM.isFinite() ||
            event.points.isEmpty() || event.points.any { !it.isValid() }
        ) {
            return state
        }
        return state.copy(
            pendingPlan = null,
            pathPreview = MapPathPreview(pending, event.points, event.lengthM)
        )
    }

    private fun clearCoordinates(state: MapNavigationState): MapNavigationState {
        return state.copy(
            initialPoseDraft = null,
            targetDraft = null,
            selectionGeneration = state.selectionGeneration + 1L,
            pendingPlan = null,
            pathPreview = null
        )
    }

    private fun clearTarget(state: MapNavigationState): MapNavigationState {
        return state.copy(
            targetDraft = null,
            selectionGeneration = state.selectionGeneration + 1L,
            pendingPlan = null,
            pathPreview = null
        )
    }

    private fun SavedMapDescriptor.isValid(): Boolean {
        return identity.mapId.isNotBlank() && identity.revision > 0L &&
            displayName.isNotBlank() && previewHash.isNotBlank() &&
            coordinates.resolutionM.isFinite() && coordinates.resolutionM > 0.0 &&
            coordinates.widthCells > 0 && coordinates.heightCells > 0 &&
            coordinates.origin.isValid()
    }

    private fun MappingPose?.isValidOrNull() = this == null || isValid()

    private fun MappingPose.isValid() = x.isFinite() && y.isFinite() && yaw.isFinite()
}
