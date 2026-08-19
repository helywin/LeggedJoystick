package com.helywin.leggedjoystick.proto

import sar.robot_controller.v1.CommandRequest
import sar.robot_controller.v1.CancelNavigationRequest
import sar.robot_controller.v1.ControllerMessage
import sar.robot_controller.v1.DiscardMapRequest
import sar.robot_controller.v1.FinishMappingRequest
import sar.robot_controller.v1.GetMapPreviewRequest
import sar.robot_controller.v1.GetMappingMapRequest
import sar.robot_controller.v1.GetSnapshotRequest
import sar.robot_controller.v1.GoalPose
import sar.robot_controller.v1.Heartbeat
import sar.robot_controller.v1.HelloRequest
import sar.robot_controller.v1.ListMapsRequest
import sar.robot_controller.v1.ManualTakeoverRequest
import sar.robot_controller.v1.MapIdentity
import sar.robot_controller.v1.MessageKind
import sar.robot_controller.v1.Pose2D
import sar.robot_controller.v1.PreviewGoalRequest
import sar.robot_controller.v1.ProtocolVersion
import sar.robot_controller.v1.SaveMapRequest
import sar.robot_controller.v1.SetInitialPoseRequest
import sar.robot_controller.v1.StartNavigationRequest
import sar.robot_controller.v1.StartMappingRequest
import sar.robot_controller.v1.StopRuntimeRequest
import sar.robot_controller.v1.SwitchMapRequest
import sar.robot_controller.v1.TimeSyncChallenge
import sar.robot_controller.v1.TimeSyncCommit
import java.util.concurrent.atomic.AtomicLong

class ControllerProtocol(
    val deviceId: String = "sar-remote",
    initialRequestId: Long = System.currentTimeMillis(),
    private val utcNowNs: () -> Long = { System.currentTimeMillis() * 1_000_000L }
) {
    private val requestId = AtomicLong(initialRequestId)
    private val sequence = AtomicLong(0L)

    fun hello(token: String): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = "",
            helloRequest = HelloRequest(
                requested_version = VERSION,
                device_id = deviceId,
                pre_shared_token = token,
                client_send_utc_ns = utcNowNs(),
                subscribe_state = true
            )
        )
    }

    fun heartbeat(sessionId: String): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = 0L,
            sessionId = sessionId,
            heartbeat = Heartbeat(client_utc_ns = utcNowNs())
        )
    }

    fun getSnapshot(sessionId: String): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            getSnapshotRequest = GetSnapshotRequest()
        )
    }

    fun getLatestMappingMap(
        sessionId: String,
        lastCompleteFrameSequence: Long,
        preferredChunkBytes: Int = DEFAULT_MAP_CHUNK_BYTES
    ): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            getMappingMapRequest = GetMappingMapRequest(
                last_complete_frame_sequence = lastCompleteFrameSequence,
                preferred_chunk_bytes = preferredChunkBytes
            )
        )
    }

    fun listMaps(sessionId: String): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            listMapsRequest = ListMapsRequest()
        )
    }

    fun getMapPreview(
        sessionId: String,
        mapId: String,
        revision: Long,
        preferredChunkBytes: Int = DEFAULT_MAP_CHUNK_BYTES
    ): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            getMapPreviewRequest = GetMapPreviewRequest(
                map = MapIdentity(map_id = mapId, revision = revision),
                preferred_chunk_bytes = preferredChunkBytes
            )
        )
    }

    fun startMapping(sessionId: String, stateRevision: Long, draftName: String): ControllerMessage {
        return command(sessionId, stateRevision) {
            start_mapping(StartMappingRequest(draft_name = draftName))
        }
    }

    fun finishMapping(sessionId: String, stateRevision: Long): ControllerMessage {
        return command(sessionId, stateRevision) { finish_mapping(FinishMappingRequest()) }
    }

    fun saveMap(sessionId: String, stateRevision: Long, displayName: String): ControllerMessage {
        return command(sessionId, stateRevision) {
            save_map(SaveMapRequest(display_name = displayName))
        }
    }

    fun discardMap(sessionId: String, stateRevision: Long): ControllerMessage {
        return command(sessionId, stateRevision) { discard_map(DiscardMapRequest()) }
    }

    fun switchMap(
        sessionId: String,
        stateRevision: Long,
        mapId: String,
        revision: Long
    ): ControllerMessage {
        return command(sessionId, stateRevision) {
            switch_map(SwitchMapRequest(map = MapIdentity(map_id = mapId, revision = revision)))
        }
    }

    fun setInitialPose(
        sessionId: String,
        stateRevision: Long,
        mapId: String,
        revision: Long,
        x: Double,
        y: Double,
        yaw: Double
    ): ControllerMessage {
        return command(sessionId, stateRevision) {
            set_initial_pose(
                SetInitialPoseRequest(
                    map = MapIdentity(map_id = mapId, revision = revision),
                    pose = Pose2D(x = x, y = y, yaw = yaw)
                )
            )
        }
    }

    fun previewGoal(
        sessionId: String,
        stateRevision: Long,
        mapId: String,
        revision: Long,
        x: Double,
        y: Double,
        yaw: Double
    ): ControllerMessage {
        return goalCommand(sessionId, stateRevision, mapId, revision, x, y, yaw) {
            preview_goal(PreviewGoalRequest(goal = it))
        }
    }

    fun startNavigation(
        sessionId: String,
        stateRevision: Long,
        mapId: String,
        revision: Long,
        x: Double,
        y: Double,
        yaw: Double
    ): ControllerMessage {
        return goalCommand(sessionId, stateRevision, mapId, revision, x, y, yaw) {
            start_navigation(StartNavigationRequest(goal = it))
        }
    }

    fun cancelNavigation(
        sessionId: String,
        stateRevision: Long,
        taskId: String
    ): ControllerMessage {
        return command(sessionId, stateRevision) {
            cancel_navigation(CancelNavigationRequest(task_id = taskId))
        }
    }

    fun stopRuntime(sessionId: String, stateRevision: Long): ControllerMessage {
        return command(sessionId, stateRevision) { stop_runtime(StopRuntimeRequest()) }
    }

    fun manualTakeover(sessionId: String, stateRevision: Long): ControllerMessage {
        return command(sessionId, stateRevision) { manual_takeover(ManualTakeoverRequest()) }
    }

    fun timeSyncCommit(
        sessionId: String,
        challenge: TimeSyncChallenge,
        clientReceiveUtcNs: Long = utcNowNs()
    ): ControllerMessage {
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            timeSyncCommit = TimeSyncCommit(
                challenge = challenge.challenge,
                client_send_utc_ns = challenge.client_send_utc_ns,
                server_receive_utc_ns = challenge.server_receive_utc_ns,
                server_send_utc_ns = challenge.server_send_utc_ns,
                client_receive_utc_ns = clientReceiveUtcNs
            )
        )
    }

    private fun command(
        sessionId: String,
        stateRevision: Long,
        configure: CommandRequest.Builder.() -> Unit
    ): ControllerMessage {
        val request = CommandRequest.Builder()
            .expected_state_revision(stateRevision)
            .apply(configure)
            .build()
        return envelope(
            kind = MessageKind.MESSAGE_KIND_REQUEST,
            requestId = nextRequestId(),
            sessionId = sessionId,
            commandRequest = request
        )
    }

    private fun goalCommand(
        sessionId: String,
        stateRevision: Long,
        mapId: String,
        revision: Long,
        x: Double,
        y: Double,
        yaw: Double,
        configure: CommandRequest.Builder.(GoalPose) -> Unit
    ): ControllerMessage {
        val goal = GoalPose(
            map = MapIdentity(map_id = mapId, revision = revision),
            pose = Pose2D(x = x, y = y, yaw = yaw)
        )
        return command(sessionId, stateRevision) { configure(goal) }
    }

    private fun envelope(
        kind: MessageKind,
        requestId: Long,
        sessionId: String,
        helloRequest: HelloRequest? = null,
        heartbeat: Heartbeat? = null,
        getSnapshotRequest: GetSnapshotRequest? = null,
        listMapsRequest: ListMapsRequest? = null,
        getMapPreviewRequest: GetMapPreviewRequest? = null,
        getMappingMapRequest: GetMappingMapRequest? = null,
        commandRequest: CommandRequest? = null,
        timeSyncCommit: TimeSyncCommit? = null
    ): ControllerMessage {
        return ControllerMessage(
            version = VERSION,
            kind = kind,
            session_id = sessionId,
            device_id = deviceId,
            request_id = requestId,
            sequence = sequence.incrementAndGet(),
            sender_utc_ns = utcNowNs(),
            hello_request = helloRequest,
            heartbeat = heartbeat,
            get_snapshot_request = getSnapshotRequest,
            list_maps_request = listMapsRequest,
            get_map_preview_request = getMapPreviewRequest,
            get_mapping_map_request = getMappingMapRequest,
            command_request = commandRequest,
            time_sync_commit = timeSyncCommit
        )
    }

    private fun nextRequestId(): Long = requestId.incrementAndGet()

    companion object {
        val VERSION = ProtocolVersion(major = 1, minor = 0)
        const val DEFAULT_MAP_CHUNK_BYTES = 128 * 1024
    }
}
