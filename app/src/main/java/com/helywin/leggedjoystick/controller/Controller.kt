/*********************************************************************************
 * FileName: Controller.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-16
 * Description: 机器人控制器，管理ZMQ连接和机器人状态，状态管理类
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.data.ControlOwnershipState
import com.helywin.leggedjoystick.data.DriverConnectionTelemetry
import com.helywin.leggedjoystick.data.FaultTelemetry
import com.helywin.leggedjoystick.data.HighLowStance
import com.helywin.leggedjoystick.data.MotionTelemetry
import com.helywin.leggedjoystick.data.OdometryTelemetry
import com.helywin.leggedjoystick.data.SettingsManager
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.data.toFaultTelemetry
import com.helywin.leggedjoystick.data.toMotionTelemetry
import com.helywin.leggedjoystick.data.toOdometryTelemetry
import com.helywin.leggedjoystick.input.remote.HeadControlIntent
import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputAxisMapping
import com.helywin.leggedjoystick.input.remote.RemoteInputChannelMapping
import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputRuntimeState
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceFactory
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceRequest
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.input.remote.RemoteSpeedLevelRequest
import com.helywin.leggedjoystick.input.remote.AndroidRemoteControllerIdentityReader
import com.helywin.leggedjoystick.input.remote.mock.MockRemoteInputConfig
import com.helywin.leggedjoystick.input.remote.mock.MockRemoteInputSource
import com.helywin.leggedjoystick.input.remote.unirc.UniRcRawUdpForwardConfig
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputConfig
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputSource
import com.helywin.leggedjoystick.input.remote.unirc.SiyiUdpBridgeController
import com.helywin.leggedjoystick.zmq.NewZmqClient
import com.helywin.leggedjoystick.zmq.RobotControllerClient
import com.helywin.leggedjoystick.zmq.RobotControllerClientListener
import com.helywin.leggedjoystick.zmq.RobotControllerConnectionState
import com.helywin.leggedjoystick.mapping.MappingGridFrame
import com.helywin.leggedjoystick.mapping.MappingGridMetadataModel
import com.helywin.leggedjoystick.mapping.MappingPose
import com.helywin.leggedjoystick.mapping.MappingClock
import com.helywin.leggedjoystick.mapping.MapControlOwner
import com.helywin.leggedjoystick.mapping.MapIdentityModel
import com.helywin.leggedjoystick.mapping.MapLocalizationStatus
import com.helywin.leggedjoystick.mapping.MapNavigationEvent
import com.helywin.leggedjoystick.mapping.MapNavigationReducer
import com.helywin.leggedjoystick.mapping.MapNavigationState
import com.helywin.leggedjoystick.mapping.MapPreviewCache
import com.helywin.leggedjoystick.mapping.MapPreviewData
import com.helywin.leggedjoystick.mapping.MapPreviewRequestKey
import com.helywin.leggedjoystick.mapping.SavedMapCoordinates
import com.helywin.leggedjoystick.mapping.SavedMapDescriptor
import legged_driver.AppMode
import legged_driver.CommandCode
import legged_driver.CommandResultStage
import legged_driver.CtrlSource
import legged_driver.FillLightStatus
import legged_driver.HeadDirection
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.MotionStatus
import legged_driver.RobotStateMessage
import legged_driver.SportMode
import legged_driver.ConnectionState as DriverConnectionState
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.AllowedAction
import sar.robot_controller.v1.CommandResponse
import sar.robot_controller.v1.CommandStage
import sar.robot_controller.v1.ControlOwner
import sar.robot_controller.v1.HealthState
import sar.robot_controller.v1.LocalizationState
import sar.robot_controller.v1.MapIdentity
import sar.robot_controller.v1.MapInfo
import sar.robot_controller.v1.MappingStreamStatus
import sar.robot_controller.v1.NavigationPath
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.PathPreview
import sar.robot_controller.v1.Pose2D
import sar.robot_controller.v1.PoseSource
import sar.robot_controller.v1.StateSnapshot
import sar.robot_controller.v1.TaskInfo
import sar.robot_controller.v1.TaskType
import sar.robot_controller.v1.TimeSyncStatus
import sar.robot_controller.v1.TimeSyncState
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.hypot
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * 应用状态管理类
 */
@Stable
class ControllerState {
    // 连接状态
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    // 机器人模式（自动/手动）
    var robotMode by mutableStateOf(AppMode.APP_MODE_AUTO)
        private set

    // 运动模式（普通/原地/楼梯）
    var robotCtrlMode by mutableStateOf(SportMode.SPORT_MODE_GENERAL)
        private set

    // 电池电量
    var batteryLevel by mutableStateOf(0)
        private set

    var battery1Level by mutableStateOf<Int?>(null)
        private set

    var battery2Level by mutableStateOf<Int?>(null)
        private set

    // 当前线速度值，单位由 driver 状态消息保持一致。
    var currentSpeedValue by mutableStateOf(0.0)
        private set

    // driver 侧连接与状态订阅数据，用于调试面板和后续联调诊断。
    var driverConnectionTelemetry by mutableStateOf(DriverConnectionTelemetry())
        private set

    var motionTelemetry by mutableStateOf(MotionTelemetry())
        private set

    var faultTelemetry by mutableStateOf(FaultTelemetry())
        private set

    var odometryTelemetry by mutableStateOf(OdometryTelemetry())
        private set

    // 补光灯和头部状态来自 RobotStateMessage。
    var frontLightOn by mutableStateOf(false)
        private set

    var backLightOn by mutableStateOf(false)
        private set

    var autoModeLightOn by mutableStateOf(false)
        private set

    var headAngle by mutableStateOf(0.0)
        private set

    var headDirection by mutableStateOf(HeadDirection.HEAD_DIRECTION_HEAD)
        private set

    var motionStatus by mutableStateOf(MotionStatus.MOTION_STATUS_UNKNOWN)
        private set

    var highLowStance by mutableStateOf(HighLowStance.NORMAL)
        private set

    // 当前控制权状态，只有已接管时才允许发送运动和动作命令。
    var controlOwnershipState by mutableStateOf(ControlOwnershipState.UNKNOWN)
        private set

    var controlOwnershipMessage by mutableStateOf("")
        private set

    // 应用设置
    var settings by mutableStateOf(AppSettings())
        private set

    // 外部遥控输入状态
    var remoteInputState by mutableStateOf(RemoteInputRuntimeState())
        private set

    var lastCommandName by mutableStateOf("无")
        private set

    var lastCommandDetail by mutableStateOf("")
        private set

    var lastCommandAtMs by mutableStateOf(0L)
        private set

    // 模式切换状态
    var isRobotModeChanging by mutableStateOf(false)
        private set

    var isRobotCtrlModeChanging by mutableStateOf(false)
        private set

    // 机器人业务主控状态与实时建图数据。
    var controllerConnectionState by mutableStateOf(RobotControllerConnectionState.DISCONNECTED)
        private set

    var controllerConnectionMessage by mutableStateOf("")
        private set

    var controllerSnapshot by mutableStateOf<StateSnapshot?>(null)
        private set

    var controllerTask by mutableStateOf<TaskInfo?>(null)
        private set

    var controllerTimeSync by mutableStateOf<TimeSyncStatus?>(null)
        private set

    var mappingFrame by mutableStateOf<MappingGridFrame?>(null)
        private set

    var mappingError by mutableStateOf("")
        private set

    var mapNavigationState by mutableStateOf(MapNavigationState())
        private set

    var savedMapPreview by mutableStateOf<MapPreviewData?>(null)
        private set

    var mapNavigationError by mutableStateOf("")
        private set

    var pendingControllerRequestId by mutableStateOf(0L)
        private set

    var controllerCommandMessage by mutableStateOf("")
        private set

    private var pendingPlanRequestId = 0L

    // 衍生状态
    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    val hasControl: Boolean
        get() = controlOwnershipState == ControlOwnershipState.OWNED

    // 更新方法
    fun updateConnectionState(newState: ConnectionState) {
        if (newState != ConnectionState.CONNECTED) {
            clearDriverTelemetry()
        }
        connectionState = newState
    }

    fun updateRobotMode(newMode: AppMode) {
        robotMode = newMode
    }

    fun confirmRobotMode(newMode: AppMode) {
        robotMode = newMode
        isRobotModeChanging = false
    }

    fun updateRobotCtrlMode(newMode: SportMode) {
        robotCtrlMode = newMode
        // 控制模式更新时清除切换状态
        if (isRobotCtrlModeChanging) {
            isRobotCtrlModeChanging = false
        }
    }

    fun updateBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
    }

    fun updateBatteryLevels(robotState: RobotStateMessage) {
        val batteryData = robotState.battery
        battery1Level = batteryData
            ?.takeIf { it.present1 }
            ?.power1
            ?.roundToInt()
            ?.coerceIn(0, 100)
        battery2Level = batteryData
            ?.takeIf { it.present2 }
            ?.power2
            ?.roundToInt()
            ?.coerceIn(0, 100)

        val levels = listOfNotNull(battery1Level, battery2Level)
        updateBatteryLevel(if (levels.isEmpty()) 0 else levels.average().roundToInt())
    }

    fun updateCurrentSpeedValue(value: Double) {
        currentSpeedValue = value.coerceAtLeast(0.0)
    }

    fun updateDriverConnectionTelemetry(
        connectionState: DriverConnectionState,
        robotConnected: Boolean
    ) {
        driverConnectionTelemetry = DriverConnectionTelemetry(
            connectionState = connectionState,
            robotConnected = robotConnected,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun clearDriverTelemetry() {
        driverConnectionTelemetry = DriverConnectionTelemetry()
        motionTelemetry = MotionTelemetry()
        faultTelemetry = FaultTelemetry()
        odometryTelemetry = OdometryTelemetry()
        batteryLevel = 0
        battery1Level = null
        battery2Level = null
        currentSpeedValue = 0.0
        frontLightOn = false
        backLightOn = false
        autoModeLightOn = false
        headAngle = 0.0
        headDirection = HeadDirection.HEAD_DIRECTION_HEAD
        motionStatus = MotionStatus.MOTION_STATUS_UNKNOWN
        highLowStance = HighLowStance.NORMAL
    }

    fun updateMotionTelemetry(telemetry: MotionTelemetry) {
        motionTelemetry = telemetry
    }

    fun updateFaultTelemetry(telemetry: FaultTelemetry) {
        faultTelemetry = telemetry
    }

    fun updateOdometryTelemetry(telemetry: OdometryTelemetry) {
        odometryTelemetry = telemetry
    }

    fun updateRobotAuxiliaryState(robotState: RobotStateMessage) {
        updateFillLightState(robotState.front_fill_light) { frontLightOn = it }
        updateFillLightState(robotState.back_fill_light) { backLightOn = it }
        autoModeLightOn = robotState.auto_mode_light
        headAngle = robotState.head_angle
        headDirection = robotState.head_direction
        motionStatus = robotState.motion_status
    }

    fun updateHeadDirection(direction: HeadDirection) {
        headDirection = direction
    }

    fun updateMotionStatus(status: MotionStatus) {
        motionStatus = status
    }

    fun updateFrontLightState(on: Boolean) {
        frontLightOn = on
    }

    fun updateBackLightState(on: Boolean) {
        backLightOn = on
    }

    fun updateAutoModeLightState(on: Boolean) {
        autoModeLightOn = on
    }

    fun updateHighLowStance(stance: HighLowStance) {
        highLowStance = stance
    }

    fun updateControlOwnership(newState: ControlOwnershipState, message: String = "") {
        controlOwnershipState = newState
        controlOwnershipMessage = message
    }

    fun updateControlOwnershipFromSource(source: CtrlSource) {
        // RobotState.control_source 是机器狗底层控制来源，不是当前 ZMQ 客户端的控制权。
        // 真实部署中本 App 通过 legged_driver 的 SDK 通道控制，未接管和已接管都会看到 CTRL_SOURCE_SDK。
        // 控制权 UI 只能由 TAKE/RELEASE ACK 以及 CONTROL_LOST/CONTROL_AVAILABLE 事件驱动。
        if (
            controlOwnershipState == ControlOwnershipState.RELEASING &&
            (source == CtrlSource.CTRL_SOURCE_UNKNOWN || source == CtrlSource.CTRL_SOURCE_OTHER)
        ) {
            controlOwnershipState = ControlOwnershipState.AVAILABLE
            controlOwnershipMessage = "已释放控制权"
        }
    }

    private fun updateFillLightState(status: FillLightStatus, update: (Boolean) -> Unit) {
        when (status) {
            FillLightStatus.FILL_LIGHT_STATUS_ON -> update(true)
            FillLightStatus.FILL_LIGHT_STATUS_OFF -> update(false)
            FillLightStatus.FILL_LIGHT_STATUS_UNKNOWN -> Unit
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
    }

    fun updateRobotModeChangingState(changing: Boolean) {
        isRobotModeChanging = changing
    }

    fun updateRobotCtrlModeChangingState(changing: Boolean) {
        isRobotCtrlModeChanging = changing
    }

    fun updateControllerConnection(
        state: RobotControllerConnectionState,
        message: String
    ) {
        controllerConnectionState = state
        controllerConnectionMessage = message
        if (state != RobotControllerConnectionState.CONNECTED) {
            controllerSnapshot = null
            controllerTask = null
            controllerTimeSync = null
            pendingControllerRequestId = 0L
            isRobotModeChanging = false
        }
    }

    fun updateControllerSnapshot(snapshot: StateSnapshot) {
        controllerSnapshot = snapshot
        controllerTask = snapshot.active_task
        controllerTimeSync = snapshot.time_sync
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.AuthorityUpdated(
                generation = mapNavigationState.sessionGeneration,
                currentMap = snapshot.current_map?.identity?.toIdentityModel(),
                loadingMap = snapshot.active_task
                    ?.takeIf { it.type == TaskType.TASK_TYPE_LOAD_MAP }
                    ?.map
                    ?.toIdentityModel(),
                localizationRuntimeActive = snapshot.operation_mode.isLocalizationRuntimeActive(),
                localizationStatus = snapshot.localization_state.toMapLocalizationStatus(),
                controlOwner = snapshot.control_owner.toMapControlOwner(),
                activeTaskId = snapshot.active_task?.task_id?.ifEmpty { null },
                activeNavigationTaskId = snapshot.active_task
                    ?.takeIf { it.type == TaskType.TASK_TYPE_NAVIGATE_TO_POSE }
                    ?.task_id
                    ?.ifEmpty { null },
                robotPose = snapshot.robot_pose?.let { MappingPose(it.x, it.y, it.yaw) }
            )
        )
        if ((snapshot.operation_mode == OperationMode.OPERATION_MODE_MAPPING_PREPARING ||
                snapshot.operation_mode == OperationMode.OPERATION_MODE_MAPPING_RUNNING) &&
            snapshot.mapping_stream?.available != true
        ) {
            // 新一轮建图在首帧到达前必须清空上一轮画面，避免旧地图被误认为当前进度。
            mappingFrame = null
            mappingError = "等待本轮实时地图首帧"
        }
        when (snapshot.control_owner) {
            ControlOwner.CONTROL_OWNER_REMOTE_MANUAL -> {
                confirmRobotMode(AppMode.APP_MODE_MANUAL)
            }
            ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO -> {
                confirmRobotMode(AppMode.APP_MODE_AUTO)
            }
            else -> Unit
        }
    }

    fun updateControllerTask(task: TaskInfo) {
        controllerTask = task
    }

    fun updateControllerTimeSync(status: TimeSyncStatus) {
        controllerTimeSync = status
    }

    fun updateMappingFrame(frame: MappingGridFrame) {
        mappingFrame = frame
        mappingError = ""
    }

    fun updateMappingError(reason: String) {
        mappingError = reason
    }

    fun updateMapSession(generation: Long) {
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.SessionChanged(generation)
        )
        savedMapPreview = null
        mapNavigationError = ""
        pendingPlanRequestId = 0L
    }

    fun updateMapList(maps: List<MapInfo>): MapIdentityModel? {
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.MapsReceived(
                generation = mapNavigationState.sessionGeneration,
                maps = maps.mapNotNull { it.toSavedMapDescriptor() }
            )
        )
        mapNavigationError = ""
        if (savedMapPreview?.key?.map != mapNavigationState.selectedMap) {
            savedMapPreview = null
        }
        // 主控快照恢复当前地图后，地图列表会自动补全选择；该路径也必须继续拉取预览。
        return mapNavigationState.selectedMap?.takeIf {
            savedMapPreview?.key?.map != it
        }
    }

    fun updateSavedMapPreview(preview: MapPreviewData) {
        if (preview.key.map == mapNavigationState.selectedMap) {
            savedMapPreview = preview
            mapNavigationError = ""
        }
    }

    fun updatePathPreview(preview: PathPreview) {
        val pending = mapNavigationState.pendingPlan ?: return
        val identity = preview.map?.toIdentityModel() ?: return
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.PathReceived(
                generation = mapNavigationState.sessionGeneration,
                taskId = preview.task_id,
                map = identity,
                selectionGeneration = pending.selectionGeneration,
                points = preview.points.map { MappingPose(it.x, it.y, it.yaw) },
                lengthM = preview.length_m
            )
        )
    }

    fun updateNavigationPath(path: NavigationPath) {
        val identity = path.map?.toIdentityModel() ?: return
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.NavigationPathReceived(
                generation = mapNavigationState.sessionGeneration,
                taskId = path.task_id,
                map = identity,
                pathSequence = path.path_sequence,
                sourceTimeNs = path.source_time_ns,
                frameId = path.frame_id,
                points = path.points.map { MappingPose(it.x, it.y, it.yaw) },
                lengthM = path.length_m,
                active = path.active
            )
        )
    }

    fun updateMapNavigationError(reason: String) {
        mapNavigationError = reason
    }

    fun selectSavedMap(map: MapIdentityModel) {
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.SelectMap(map)
        )
        savedMapPreview = null
        mapNavigationError = ""
    }

    fun editInitialPose(pose: MappingPose?) {
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.EditInitialPose(pose)
        )
    }

    fun editNavigationTarget(pose: MappingPose?) {
        mapNavigationState = MapNavigationReducer.reduce(
            mapNavigationState,
            MapNavigationEvent.EditTarget(pose)
        )
    }

    fun markControllerRequest(requestId: Long, description: String) {
        pendingControllerRequestId = requestId
        controllerCommandMessage = description
    }

    fun markPlanControllerRequest(requestId: Long, description: String) {
        markControllerRequest(requestId, description)
        pendingPlanRequestId = requestId
    }

    fun completeControllerRequest(requestId: Long, response: CommandResponse) {
        if (pendingControllerRequestId == requestId) {
            pendingControllerRequestId = 0L
        }
        controllerCommandMessage = if (response.stage == CommandStage.COMMAND_STAGE_ACCEPTED) {
            if (response.task_id.isEmpty()) "命令已接受" else "命令已接受：${response.task_id}"
        } else {
            response.error_message.ifEmpty { "命令被主控拒绝：${response.error_code}" }
        }
        if (response.stage == CommandStage.COMMAND_STAGE_REJECTED) {
            isRobotModeChanging = false
        }
        if (requestId == pendingPlanRequestId) {
            if (response.stage == CommandStage.COMMAND_STAGE_ACCEPTED &&
                response.task_id.isNotBlank()
            ) {
                mapNavigationState = MapNavigationReducer.reduce(
                    mapNavigationState,
                    MapNavigationEvent.PlanRequested(response.task_id)
                )
            }
            pendingPlanRequestId = 0L
        }
    }

    fun isActionAllowed(action: ActionCode): Boolean {
        return controllerSnapshot?.allowed_actions?.firstOrNull { it.action == action }?.allowed == true
    }

    private fun MapInfo.toSavedMapDescriptor(): SavedMapDescriptor? {
        val mapIdentity = identity?.toIdentityModel() ?: return null
        if (display_name.isBlank() || preview_hash.isBlank() ||
            !resolution_m.isFinite() || resolution_m <= 0.0 ||
            width_cells <= 0 || height_cells <= 0 ||
            !origin_x_m.isFinite() || !origin_y_m.isFinite() ||
            !origin_yaw_rad.isFinite()
        ) {
            return null
        }
        return SavedMapDescriptor(
            identity = mapIdentity,
            displayName = display_name,
            previewHash = preview_hash,
            coordinates = SavedMapCoordinates(
                resolutionM = resolution_m,
                widthCells = width_cells,
                heightCells = height_cells,
                origin = MappingPose(origin_x_m, origin_y_m, origin_yaw_rad)
            ),
            contentHash = content_hash,
            createdUtcNs = created_utc_ns
        )
    }

    private fun sar.robot_controller.v1.MapIdentity.toIdentityModel(): MapIdentityModel? {
        return if (map_id.isBlank() || revision <= 0L) null else MapIdentityModel(map_id, revision)
    }

    private fun LocalizationState.toMapLocalizationStatus(): MapLocalizationStatus {
        return when (this) {
            LocalizationState.LOCALIZATION_STATE_INITIALIZING -> MapLocalizationStatus.INITIALIZING
            LocalizationState.LOCALIZATION_STATE_TRACKING -> MapLocalizationStatus.TRACKING
            LocalizationState.LOCALIZATION_STATE_LOST -> MapLocalizationStatus.LOST
            else -> MapLocalizationStatus.UNKNOWN
        }
    }

    private fun ControlOwner.toMapControlOwner(): MapControlOwner {
        return when (this) {
            ControlOwner.CONTROL_OWNER_REMOTE_MANUAL -> MapControlOwner.REMOTE_MANUAL
            ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO -> MapControlOwner.NAVIGATION_AUTO
            else -> MapControlOwner.DISABLED
        }
    }

    fun setSpeedLevel(level: SpeedLevel) {
        settings = settings.copy(speedLevel = level)
    }

    fun updateRemoteInputStatus(
        descriptor: RemoteInputSourceDescriptor,
        status: RemoteInputStatus,
        message: String = ""
    ) {
        val resetSnapshot = when (status) {
            RemoteInputStatus.TIMEOUT,
            RemoteInputStatus.ERROR,
            RemoteInputStatus.STOPPED -> RemoteInputSnapshot(
                descriptor = descriptor,
                movementIntent = MovementIntent.ZERO,
                headControlIntent = HeadControlIntent.ZERO,
                normalizedAxes = mapOf(
                    "forward" to 0f,
                    "strafeRight" to 0f,
                    "yawRight" to 0f,
                    "headPitchUp" to 0f
                )
            )
            else -> remoteInputState.latestSnapshot
        }

        remoteInputState = remoteInputState.copy(
            status = status,
            sourceName = descriptor.displayName,
            lastError = message,
            latestSnapshot = resetSnapshot
        )
    }

    fun updateRemoteInputSnapshot(snapshot: RemoteInputSnapshot) {
        remoteInputState = remoteInputState.copy(
            status = RemoteInputStatus.RUNNING,
            sourceName = snapshot.descriptor.displayName,
            lastFrameAtMs = snapshot.receivedAtMs,
            lastError = "",
            latestSnapshot = snapshot
        )
    }

    fun updateLastCommand(name: String, detail: String = "") {
        lastCommandName = name
        lastCommandDetail = detail
        lastCommandAtMs = System.currentTimeMillis()
    }
}

private fun OperationMode.isLocalizationRuntimeActive(): Boolean {
    return this == OperationMode.OPERATION_MODE_LOCALIZATION_LOADING ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_LOST
}

/**
 * 全局状态实例
 */
object ControllerRuntime {
    val settingsState = ControllerState()
}

val settingsState: ControllerState
    get() = ControllerRuntime.settingsState

/**
 * 机器人控制器接口
 */
interface Controller {
    fun connect()
    fun disconnect()
    fun cancelConnection()
    fun takeControl()
    fun releaseControl()
    fun setMode(mode: AppMode)
    fun startMapping(draftName: String)
    fun finishMapping()
    fun saveMap(displayName: String)
    fun discardMap()
    fun requestLatestMappingMap()
    fun refreshSavedMaps()
    fun selectSavedMap(mapId: String, revision: Long)
    fun requestSavedMapPreview(mapId: String, revision: Long)
    fun switchSavedMap(mapId: String, revision: Long)
    fun stopLocalizationRuntime()
    fun editInitialPose(pose: MappingPose?)
    fun submitInitialPose()
    fun editNavigationTarget(pose: MappingPose?)
    fun requestNavigationPreview()
    fun startNavigation()
    fun cancelNavigation()
    fun setControlMode(controlMode: SportMode)
    fun setSpeedLevel(level: SpeedLevel)
    fun performAction(action: RobotAction)
    fun setFrontLight(on: Boolean)
    fun setBackLight(on: Boolean)
    fun setAutoModeLight(on: Boolean)
    fun reverseHeadTail()
    fun controlHead(leftRight: Float, upDown: Float)
    fun setHighLowStance(stance: HighLowStance)
    fun updateSettings(settings: AppSettings)
    fun pauseMovementOutput()
    fun resumeMovementOutput()
    fun isConnected(): Boolean
    fun cleanup()
    fun loadSettings()
    fun saveSettings(settings: AppSettings)
}

/**
 * 机器人控制器进程级单例。
 *
 * Activity 只负责呈现 UI 和转发生命周期事件，ZMQ、输入源和状态缓存都归属于本对象。
 */
object RobotControllerImpl : Controller {
    private data class MockSavedMap(
        val info: MapInfo,
        val preview: MapPreviewData
    )

    private val zmqClient = NewZmqClient()
    private val controllerClient = RobotControllerClient()
    private lateinit var settingsManager: SettingsManager
    private var mapPreviewCache: MapPreviewCache? = null

    // 协程相关
    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + supervisorJob)

    // 连接任务
    private var connectJob: Job? = null
    private var mockStateJob: Job? = null
    private var mockSavedMap: MockSavedMap? = null
    private var mockRequestSequence = 0L

    private var remoteInputSource: RemoteInputSource = buildRemoteInputSource(settingsState.settings)
    private var applicationContext: Context? = null
    private lateinit var siyiUdpBridgeController: SiyiUdpBridgeController
    private var remoteInputRequested = false
    @Volatile
    private var currentMovementIntent = MovementIntent.ZERO
    @Volatile
    private var currentHeadControlIntent = HeadControlIntent.ZERO
    @Volatile
    private var lastRemoteSpeedLevelRequest: RemoteSpeedLevelRequest? = null
    @Volatile
    private var remoteSpeedLevelSnapshotSeen = false
    private var lastCommandSent = false  // 跟踪是否发送过速度指令

    // 速度发送任务
    private var velocitySendJob: Job? = null
    private var headControlStopJob: Job? = null
    private var headControlActive = false
    private const val HEAD_CONTROL_PULSE_MS = 250L

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        synchronized(this) {
            ensureActiveScope()
            if (initialized) {
                return
            }

            val appContext = context.applicationContext
            applicationContext = appContext
            settingsManager = SettingsManager(appContext)
            mapPreviewCache = MapPreviewCache(appContext.cacheDir.resolve("map-previews"))
            siyiUdpBridgeController = SiyiUdpBridgeController(appContext)
            zmqClient.setMessageCallback { message ->
                handleIncomingMessage(message)
            }
            zmqClient.setConnectionStateCallback {
                handleConnectionState(it)
            }
            controllerClient.setListener(object : RobotControllerClientListener {
                override fun onSessionChanged(generation: Long) {
                    scope.launch { settingsState.updateMapSession(generation) }
                }

                override fun onConnectionState(
                    state: RobotControllerConnectionState,
                    message: String
                ) {
                    scope.launch {
                        settingsState.updateControllerConnection(state, message)
                        if (state == RobotControllerConnectionState.CONNECTED) {
                            controllerClient.requestMapList()
                        } else if (
                            (state == RobotControllerConnectionState.AUTHENTICATION_FAILED ||
                                state == RobotControllerConnectionState.CONNECTION_FAILED) &&
                            settingsState.connectionState == ConnectionState.CONNECTED
                        ) {
                            requestDriverManualFallback("主控不可用，进入 MANUAL 安全降级")
                        }
                    }
                }

                override fun onSnapshot(snapshot: StateSnapshot) {
                    scope.launch { settingsState.updateControllerSnapshot(snapshot) }
                }

                override fun onTask(task: TaskInfo) {
                    scope.launch { settingsState.updateControllerTask(task) }
                }

                override fun onCommandResponse(requestId: Long, response: CommandResponse) {
                    scope.launch {
                        settingsState.completeControllerRequest(requestId, response)
                        if (response.stage == CommandStage.COMMAND_STAGE_REJECTED) {
                            Timber.w(
                                "[Controller] 主控命令被拒绝: request=%d, code=%s, reason=%s",
                                requestId,
                                response.error_code,
                                response.error_message
                            )
                        }
                    }
                }

                override fun onTimeSyncStatus(status: TimeSyncStatus) {
                    scope.launch { settingsState.updateControllerTimeSync(status) }
                }

                override fun onMappingFrame(frame: MappingGridFrame) {
                    scope.launch { settingsState.updateMappingFrame(frame) }
                }

                override fun onMappingError(reason: String) {
                    scope.launch { settingsState.updateMappingError(reason) }
                }

                override fun onMapList(requestId: Long, maps: List<MapInfo>) {
                    scope.launch {
                        settingsState.updateMapList(maps)?.let { map ->
                            requestSavedMapPreview(map.mapId, map.revision)
                        }
                    }
                }

                override fun onMapPreview(preview: MapPreviewData) {
                    val cache = mapPreviewCache
                    val cached = cache?.put(preview.sha256, preview.bytes) { bytes ->
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: return@put false
                        bitmap.recycle()
                        true
                    } ?: false
                    scope.launch {
                        if (cached) {
                            settingsState.updateSavedMapPreview(preview)
                        } else {
                            settingsState.updateMapNavigationError("地图预览图片解码或缓存失败")
                        }
                    }
                }

                override fun onPathPreview(preview: PathPreview) {
                    scope.launch { settingsState.updatePathPreview(preview) }
                }

                override fun onNavigationPath(path: NavigationPath) {
                    scope.launch { settingsState.updateNavigationPath(path) }
                }

                override fun onMapNavigationError(reason: String) {
                    scope.launch { settingsState.updateMapNavigationError(reason) }
                }
            })
            initialized = true
            loadSettings()
            Timber.i("[Controller] 进程级控制器已初始化")
        }
    }

    private fun ensureInitialized() {
        check(initialized) { "RobotControllerImpl 尚未初始化" }
    }

    private fun ensureActiveScope() {
        if (!supervisorJob.isCancelled) {
            return
        }
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.Main + supervisorJob)
    }

    /**
     * 处理接收到的消息
     */
    private fun handleIncomingMessage(message: LeggedDriverMessage) {
        when (message.message_type) {
            MessageType.MESSAGE_TYPE_HEARTBEAT -> {
                message.heartbeat?.let { heartbeat ->
                    if (settingsState.controllerConnectionState != RobotControllerConnectionState.CONNECTED) {
                        settingsState.confirmRobotMode(heartbeat.app_mode)
                    }
                    settingsState.updateDriverConnectionTelemetry(
                        connectionState = heartbeat.connection_state,
                        robotConnected = heartbeat.robot_connected
                    )
                    Timber.d("[Controller] 收到服务器心跳，机器连接状态: ${heartbeat.robot_connected}")
                }
            }
            MessageType.MESSAGE_TYPE_CONNECTION_STATE -> {
                message.connection_state?.let { connectionState ->
                    settingsState.updateDriverConnectionTelemetry(
                        connectionState = connectionState.connection_state,
                        robotConnected = connectionState.robot_connected
                    )
                    Timber.d(
                        "[Controller] 收到连接状态: %s，机器连接: %s",
                        connectionState.connection_state,
                        connectionState.robot_connected
                    )
                }
            }
            MessageType.MESSAGE_TYPE_APP_MODE_STATE -> {
                message.app_mode_state?.let { appModeState ->
                    if (settingsState.controllerConnectionState != RobotControllerConnectionState.CONNECTED) {
                        settingsState.confirmRobotMode(appModeState.app_mode)
                    }
                    Timber.d("[Controller] 收到当前 AppMode: ${appModeState.app_mode}")
                }
            }
            MessageType.MESSAGE_TYPE_ROBOT_STATE -> {
                message.robot_state?.let { robotState ->
                    val wasReleasing = settingsState.controlOwnershipState == ControlOwnershipState.RELEASING
                    settingsState.updateRobotCtrlMode(robotState.sport_mode)
                    settingsState.updateBatteryLevels(robotState)
                    settingsState.updateCurrentSpeedValue(robotState.toLinearSpeedValue())
                    settingsState.updateRobotAuxiliaryState(robotState)
                    settingsState.updateControlOwnershipFromSource(robotState.control_source)
                    if (wasReleasing && settingsState.controlOwnershipState == ControlOwnershipState.AVAILABLE) {
                        settingsState.updateLastCommand("控制权", "已释放，保持 MANUAL")
                    }
                    Timber.d(
                        "[Controller] 收到机器人状态，运动模式: %s，控制来源: %s",
                        robotState.sport_mode,
                        robotState.control_source
                    )
                }
            }
            MessageType.MESSAGE_TYPE_MOTION_DATA -> {
                message.motion_data?.let { motionData ->
                    settingsState.updateMotionTelemetry(motionData.toMotionTelemetry())
//                    Timber.v("[Controller] 收到运动数据")
                }
            }
            MessageType.MESSAGE_TYPE_FAULT_DATA -> {
                message.fault_data?.let { faultData ->
                    settingsState.updateFaultTelemetry(faultData.toFaultTelemetry())
                    Timber.d("[Controller] 收到故障数据，数量: %d", faultData.faults.size)
                }
            }
            MessageType.MESSAGE_TYPE_ODOMETRY -> {
                message.odometry?.let { odometry ->
                    settingsState.updateOdometryTelemetry(odometry.toOdometryTelemetry())
//                    Timber.v("[Controller] 收到里程数据")
                }
            }
            MessageType.MESSAGE_TYPE_TAKE_CONTROL_ACK -> {
                message.take_control_ack?.let { ack ->
                    if (ack.error_code == 0) {
                        markControlOwned("接管成功")
                    } else {
                        settingsState.updateControlOwnership(
                            ControlOwnershipState.DENIED,
                            ack.reason.ifEmpty { "接管被拒绝: ${ack.error_code}" }
                        )
                    }
                    Timber.i("[Controller] 收到接管 ACK，错误码: %s，原因: %s", ack.error_code, ack.reason)
                }
            }
            MessageType.MESSAGE_TYPE_RELEASE_CONTROL_ACK -> {
                message.release_control_ack?.let { ack ->
                    if (ack.error_code == 0) {
                        markControlReleased("已释放控制权")
                    } else {
                        settingsState.updateControlOwnership(
                            ControlOwnershipState.OWNED,
                            ack.reason.ifEmpty { "释放失败: ${ack.error_code}" }
                        )
                    }
                    Timber.i("[Controller] 收到释放 ACK，错误码: %s，原因: %s", ack.error_code, ack.reason)
                }
            }
            MessageType.MESSAGE_TYPE_COMMAND_RESULT -> {
                message.command_result?.let { result ->
                    handleCommandResult(
                        commandCode = result.command_code,
                        stage = result.stage,
                        errorCode = result.error_code,
                        errorMessage = result.error_message
                    )
                }
            }
            MessageType.MESSAGE_TYPE_CONTROL_LOST -> {
                stopVelocityLoop()
                stopHeadControl()
                settingsState.updateControlOwnership(ControlOwnershipState.LOST, "控制权已丢失")
                Timber.w("[Controller] 收到控制权丢失通知")
            }
            MessageType.MESSAGE_TYPE_CONTROL_AVAILABLE -> {
                if (!settingsState.hasControl || settingsState.controlOwnershipState == ControlOwnershipState.RELEASING) {
                    settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "控制权可用")
                }
                Timber.i("[Controller] 收到控制权可用通知")
            }
            else -> {
//                Timber.d("[Controller] 收到其他消息类型: ${message.message_type}")
            }
        }
    }

    private fun handleCommandResult(
        commandCode: CommandCode,
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (commandCode) {
            CommandCode.COMMAND_CODE_TAKE_CONTROL -> handleTakeControlResult(stage, errorCode, errorMessage)
            CommandCode.COMMAND_CODE_RELEASE_CONTROL -> handleReleaseControlResult(stage, errorCode, errorMessage)
            else -> Unit
        }
    }

    private fun handleTakeControlResult(
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (stage) {
            CommandResultStage.COMMAND_RESULT_STAGE_ACCEPTED -> {
                if (!settingsState.hasControl) {
                    settingsState.updateControlOwnership(ControlOwnershipState.TAKING, "接管请求已接受")
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_COMPLETED -> {
                markControlOwned("已接管控制权")
            }
            CommandResultStage.COMMAND_RESULT_STAGE_REJECTED -> {
                if (!settingsState.hasControl) {
                    settingsState.updateControlOwnership(
                        ControlOwnershipState.DENIED,
                        errorMessage.ifEmpty { "接管被拒绝: $errorCode" }
                    )
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_UNSPECIFIED -> Unit
        }
    }

    private fun handleReleaseControlResult(
        stage: CommandResultStage,
        errorCode: Int,
        errorMessage: String
    ) {
        when (stage) {
            CommandResultStage.COMMAND_RESULT_STAGE_ACCEPTED -> {
                if (settingsState.controlOwnershipState != ControlOwnershipState.AVAILABLE) {
                    settingsState.updateControlOwnership(ControlOwnershipState.RELEASING, "释放请求已接受")
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_COMPLETED -> {
                markControlReleased("已释放控制权")
            }
            CommandResultStage.COMMAND_RESULT_STAGE_REJECTED -> {
                if (settingsState.controlOwnershipState != ControlOwnershipState.AVAILABLE) {
                    settingsState.updateControlOwnership(
                        ControlOwnershipState.OWNED,
                        errorMessage.ifEmpty { "释放被拒绝: $errorCode" }
                    )
                }
            }
            CommandResultStage.COMMAND_RESULT_STAGE_UNSPECIFIED -> Unit
        }
    }

    private fun markControlOwned(message: String) {
        val wasOwned = settingsState.hasControl
        settingsState.updateControlOwnership(ControlOwnershipState.OWNED, message)
        if (velocitySendJob?.isActive != true) {
            startVelocityLoop()
        }
        if (!wasOwned) {
            if (isEngineeringMock()) {
                applyMockInitialCommands()
            } else {
                sendInitialCommandsAfterTake()
            }
        }
    }

    private fun markControlReleased(message: String) {
        settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, message)
        settingsState.updateLastCommand("控制权", "已释放，底盘保持 MANUAL")
    }

    private fun applyMockInitialCommands() {
        settingsState.confirmRobotMode(AppMode.APP_MODE_MANUAL)
        settingsState.setSpeedLevel(SpeedLevel.SLOW)
        if (settingsState.robotCtrlMode == SportMode.SPORT_MODE_UNKNOWN) {
            settingsState.updateRobotCtrlMode(SportMode.SPORT_MODE_GENERAL)
        }
        settingsState.updateLastCommand("Mock", "初始化手动模式")
    }

    private fun sendInitialCommandsAfterTake() {
        settingsState.setSpeedLevel(SpeedLevel.SLOW)
        zmqClient.setSpeedLevel(SpeedLevel.SLOW.protocolSpeedLevel)
        if (settingsState.robotCtrlMode == SportMode.SPORT_MODE_UNKNOWN) {
            zmqClient.setControlMode(SportMode.SPORT_MODE_GENERAL)
        }
        when (settingsState.controllerConnectionState) {
            RobotControllerConnectionState.CONNECTED -> {
                requestCancelForManualControl("driver 接管后取消自动导航")
            }
            RobotControllerConnectionState.CONNECTING -> {
                Timber.i("[Controller] 等待主控握手后再取消自动导航，避免遗留目标")
            }
            else -> requestDriverManualFallback("主控离线，driver 接管后进入 MANUAL 安全降级")
        }
        Timber.i("[Controller] 接管后默认设置低速，模式切换由主控闭环确认")
    }

    private fun requestCancelForManualControl(description: String) {
        if (settingsState.controllerSnapshot?.control_owner ==
            ControlOwner.CONTROL_OWNER_REMOTE_MANUAL
        ) {
            settingsState.confirmRobotMode(AppMode.APP_MODE_MANUAL)
            Timber.i("[Controller] 主控已确认人工控制，无需重复接管")
            return
        }
        val taskId = settingsState.mapNavigationState.activeNavigationTaskId.orEmpty()
        val requestId = controllerClient.cancelNavigation(taskId)
        if (requestId == 0L) {
            settingsState.updateRobotModeChangingState(false)
            Timber.w("[Controller] 无法向主控发送取消导航请求")
            return
        }
        settingsState.updateRobotModeChangingState(true)
        settingsState.markControllerRequest(requestId, description)
        Timber.i("[Controller] 已发送取消导航请求: request=%d", requestId)
    }

    private fun requestDriverManualFallback(description: String) {
        if (zmqClient.setMode(AppMode.APP_MODE_MANUAL)) {
            settingsState.updateRobotModeChangingState(true)
            settingsState.updateLastCommand("AppMode 安全降级", "MANUAL")
            settingsState.updateControllerConnection(
                settingsState.controllerConnectionState,
                description
            )
            Timber.w("[Controller] %s，仅允许 MANUAL，不允许直接 AUTO", description)
        }
    }

    /**
     * 处理连接状态
     */
    private fun handleConnectionState(state: ConnectionState) {
        scope.launch {
            if (settingsState.connectionState == state) {
                if (state == ConnectionState.CONNECTED && !settingsState.hasControl) {
                    markControlOwned("driver 已自动接管")
                }
                return@launch
            }
            if (state == ConnectionState.CONNECTED) {
                settingsState.updateConnectionState(state)
                markControlOwned("driver 已自动接管")
                startRemoteInput()
            } else {
                stopVelocityLoop()
                stopHeadControl()
                stopRemoteInput()
                settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
                settingsState.updateConnectionState(state)
            }
            Timber.i("[Controller] 连接状态更新: $state")
        }
    }

    /**
     * 连接到机器人
     */
    override fun connect() {
        ensureInitialized()
        ensureActiveScope()
        if (settingsState.connectionState == ConnectionState.CONNECTING) {
            Timber.w("[Controller] 正在连接中，忽略重复连接请求")
            return
        }

        if (settingsState.connectionState == ConnectionState.CONNECTED) {
            Timber.w("[Controller] 已经连接，忽略重复连接请求")
            return
        }

        if (isEngineeringMock()) {
            connectMock()
            return
        }

        cancelConnection() // 取消之前的连接任务

        settingsState.updateConnectionState(ConnectionState.CONNECTING)
        settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")

        connectJob = scope.launch {
            try {
                Timber.i("[Controller] 开始连接到机器人...")

                // 构建连接地址
                val endpoint = "tcp://${settingsState.settings.zmqIp}:${settingsState.settings.zmqPort}"
                Timber.i("[Controller] 连接地址: $endpoint")

                // 设置连接地址
                zmqClient.setEndpoint(endpoint)
                controllerClient.configure(
                    endpoint = "tcp://${settingsState.settings.zmqIp}:${settingsState.settings.controllerPort}",
                    token = settingsState.settings.controllerToken
                )

                // 进行连接
                controllerClient.connect()
                zmqClient.connect()


            } catch (e: CancellationException) {
                settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
                Timber.i("[Controller] 连接已取消")
            } catch (e: Exception) {
                settingsState.updateConnectionState(ConnectionState.CONNECTION_FAILED)
                Timber.e(e, "[Controller] 连接异常")
            }
        }
    }

    private fun connectMock() {
        cancelConnection()
        settingsState.updateConnectionState(ConnectionState.CONNECTING)
        settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")

        connectJob = scope.launch {
            delay(120L)
            if (!isEngineeringMock()) {
                settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
                return@launch
            }

            settingsState.confirmRobotMode(AppMode.APP_MODE_MANUAL)
            settingsState.updateRobotCtrlMode(SportMode.SPORT_MODE_GENERAL)
            settingsState.updateHeadDirection(HeadDirection.HEAD_DIRECTION_HEAD)
            settingsState.updateMotionStatus(MotionStatus.MOTION_STATUS_STAND_UP)
            settingsState.updateBatteryLevel(72)
            settingsState.updateCurrentSpeedValue(0.0)
            settingsState.updateDriverConnectionTelemetry(
                connectionState = DriverConnectionState.CONNECTION_STATE_CONNECTED,
                robotConnected = true
            )
            settingsState.updateConnectionState(ConnectionState.CONNECTED)
            settingsState.updateControllerConnection(
                RobotControllerConnectionState.CONNECTED,
                "工程 Mock 主控已连接"
            )
            applyMockControllerState()
            settingsState.updateLastCommand("Mock", "连接")
            startMockStateLoop()
            startRemoteInput()
            markControlOwned("工程 Mock 已自动接管")
            Timber.i("[Controller] 工程 Mock 已连接")
        }
    }

    /**
     * 断开连接
     */
    override fun disconnect() {
        ensureInitialized()
        cancelConnection()
        stopVelocityLoop()
        stopHeadControl()
        stopRemoteInput()
        stopMockStateLoop()
        controllerClient.disconnect()
        zmqClient.disconnect()
        settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
        settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
        Timber.i("[Controller] 已断开连接")
    }

    /**
     * 取消连接
     */
    override fun cancelConnection() {
        connectJob?.cancel()
        connectJob = null
        if (settingsState.connectionState == ConnectionState.CONNECTING) {
            controllerClient.disconnect()
            zmqClient.disconnect()
            stopRemoteInput()
            stopMockStateLoop()
            settingsState.updateConnectionState(ConnectionState.DISCONNECTED)
            settingsState.updateControlOwnership(ControlOwnershipState.UNKNOWN, "")
        }
    }

    override fun takeControl() {
        ensureInitialized()
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法接管控制权")
            return
        }

        when (settingsState.controlOwnershipState) {
            ControlOwnershipState.OWNED -> {
                Timber.i("[Controller] 已拥有控制权，忽略重复接管")
                return
            }
            ControlOwnershipState.TAKING,
            ControlOwnershipState.RELEASING -> {
                Timber.w("[Controller] 控制权请求处理中，忽略重复操作")
                return
            }
            else -> Unit
        }

        settingsState.updateControlOwnership(ControlOwnershipState.TAKING, "正在接管")
        if (isEngineeringMock()) {
            scope.launch {
                delay(80L)
                settingsState.updateLastCommand("控制权", "接管")
                markControlOwned("工程 Mock 已接管")
            }
            return
        }

        scope.launch {
            val success = zmqClient.takeControl()
            if (!success) {
                settingsState.updateControlOwnership(ControlOwnershipState.DENIED, "接管请求发送失败")
            } else {
                settingsState.updateLastCommand("控制权", "接管")
            }
        }
    }

    override fun releaseControl() {
        ensureInitialized()
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法释放控制权")
            return
        }

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 当前没有控制权，忽略释放请求")
            return
        }

        stopVelocityLoop()
        stopHeadControl()
        settingsState.updateControlOwnership(ControlOwnershipState.RELEASING, "正在释放")
        if (isEngineeringMock()) {
            scope.launch {
                delay(80L)
                settingsState.updateControlOwnership(ControlOwnershipState.AVAILABLE, "工程 Mock 已释放")
                settingsState.updateLastCommand("控制权", "释放")
            }
            return
        }

        scope.launch {
            val success = zmqClient.releaseControl()
            if (!success) {
                settingsState.updateControlOwnership(ControlOwnershipState.OWNED, "释放请求发送失败")
                startVelocityLoop()
            } else {
                settingsState.updateLastCommand("控制权", "释放")
            }
        }
    }

    /**
     * 设置机器人模式（自动/手动）
     */
    override fun setMode(mode: AppMode) {
        ensureInitialized()
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法设置模式")
            return
        }

        if (settingsState.isRobotModeChanging) {
            Timber.w("[Controller] 正在切换模式中，请等待")
            return
        }

        if (mode == AppMode.APP_MODE_AUTO) {
            settingsState.updateLastCommand("自动控制", "由导航任务接管，禁止空切 AUTO")
            Timber.w("[Controller] 拒绝脱离导航任务直接切换 AUTO")
            return
        }

        if (isEngineeringMock()) {
            applyMockCancelNavigation()
            return
        }

        if (settingsState.controllerConnectionState == RobotControllerConnectionState.CONNECTED) {
            requestCancelForManualControl("操作者请求取消自动导航")
        } else {
            requestDriverManualFallback("主控离线，操作者请求 MANUAL 安全降级")
        }
    }

    override fun startMapping(draftName: String) {
        submitMappingCommand(
            action = ActionCode.ACTION_CODE_START_MAPPING,
            description = "开始建图"
        ) { controllerClient.startMapping(draftName) }
    }

    override fun finishMapping() {
        submitMappingCommand(
            action = ActionCode.ACTION_CODE_FINISH_MAPPING,
            description = "结束建图"
        ) { controllerClient.finishMapping() }
    }

    override fun saveMap(displayName: String) {
        submitMappingCommand(
            action = ActionCode.ACTION_CODE_SAVE_MAP,
            description = "保存地图"
        ) { controllerClient.saveMap(displayName) }
    }

    override fun discardMap() {
        submitMappingCommand(
            action = ActionCode.ACTION_CODE_DISCARD_MAP,
            description = "放弃地图"
        ) { controllerClient.discardMap() }
    }

    override fun requestLatestMappingMap() {
        if (!isEngineeringMock() && !controllerClient.requestLatestMap()) {
            Timber.w("[Controller] 当前无法请求最新实时地图")
        }
    }

    override fun refreshSavedMaps() {
        ensureInitialized()
        if (isEngineeringMock()) {
            settingsState.updateMapList(listOf(ensureMockSavedMap().info))
            return
        }
        if (controllerClient.requestMapList() == 0L) {
            settingsState.updateMapNavigationError("当前无法获取已保存地图列表")
        }
    }

    override fun selectSavedMap(mapId: String, revision: Long) {
        val identity = MapIdentityModel(mapId, revision)
        settingsState.selectSavedMap(identity)
        requestSavedMapPreview(mapId, revision)
    }

    override fun requestSavedMapPreview(mapId: String, revision: Long) {
        ensureInitialized()
        val identity = MapIdentityModel(mapId, revision)
        val descriptor = settingsState.mapNavigationState.maps
            .firstOrNull { it.identity == identity }
        if (descriptor == null) {
            settingsState.updateMapNavigationError("地图不存在或版本已变化，请刷新列表")
            return
        }
        if (isEngineeringMock()) {
            val preview = ensureMockSavedMap().preview
            if (preview.key.map == identity) {
                settingsState.updateSavedMapPreview(preview)
            } else {
                settingsState.updateMapNavigationError("工程 Mock 地图版本已变化，请刷新列表")
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val cached = mapPreviewCache?.read(descriptor.previewHash)
            if (cached != null) {
                val preview = MapPreviewData(
                    key = MapPreviewRequestKey(0L, identity),
                    sha256 = descriptor.previewHash.removePrefix("sha256:"),
                    bytes = cached,
                    receivedAtMs = MappingClock.elapsedRealtimeMs()
                )
                withContext(Dispatchers.Main) {
                    settingsState.updateSavedMapPreview(preview)
                }
            } else if (!isEngineeringMock() &&
                controllerClient.requestMapPreview(mapId, revision) == 0L
            ) {
                withContext(Dispatchers.Main) {
                    settingsState.updateMapNavigationError("当前无法获取地图预览")
                }
            }
        }
    }

    override fun switchSavedMap(mapId: String, revision: Long) {
        if (isEngineeringMock()) {
            applyMockSwitchMap(MapIdentityModel(mapId, revision))
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_SWITCH_MAP,
            description = "切换地图"
        ) { controllerClient.switchMap(mapId, revision) }
    }

    override fun stopLocalizationRuntime() {
        if (isEngineeringMock()) {
            val current = settingsState.controllerSnapshot
            settingsState.updateControllerSnapshot(
                mockSnapshot(
                    OperationMode.OPERATION_MODE_STANDBY,
                    (current?.state_revision ?: 0L) + 1L
                )
            )
            settingsState.updateLastCommand("定位", "工程 Mock 已停止运行时")
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_STOP_RUNTIME,
            description = "停止定位运行时"
        ) { controllerClient.stopRuntime() }
    }

    override fun editInitialPose(pose: MappingPose?) {
        settingsState.editInitialPose(pose)
    }

    override fun submitInitialPose() {
        val state = settingsState.mapNavigationState
        val map = state.initialPoseMap
            ?: return settingsState.updateMapNavigationError("当前没有待初始化地图")
        val pose = state.initialPoseDraft
            ?: return settingsState.updateMapNavigationError("请先在地图上选择初始位姿")
        if (isEngineeringMock()) {
            settingsState.updateControllerSnapshot(
                mockLocalizationSnapshot(
                    identity = map,
                    mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
                    localization = LocalizationState.LOCALIZATION_STATE_TRACKING,
                    robotPose = pose
                )
            )
            settingsState.updateLastCommand("初始位姿", "工程 Mock 已确认")
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_SET_INITIAL_POSE,
            description = "设置初始位姿"
        ) {
            controllerClient.setInitialPose(map.mapId, map.revision, pose.x, pose.y, pose.yaw)
        }
    }

    override fun editNavigationTarget(pose: MappingPose?) {
        settingsState.editNavigationTarget(pose)
    }

    override fun requestNavigationPreview() {
        val state = settingsState.mapNavigationState
        val map = state.currentMap ?: return settingsState.updateMapNavigationError("当前没有已加载地图")
        val target = state.targetDraft
            ?: return settingsState.updateMapNavigationError("请先在地图上选择导航目标")
        if (isEngineeringMock()) {
            applyMockPlan(map, target)
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_PREVIEW_GOAL,
            description = "规划路径",
            markPlanRequest = true
        ) {
            controllerClient.previewGoal(
                map.mapId, map.revision, target.x, target.y, target.yaw
            )
        }
    }

    override fun startNavigation() {
        val state = settingsState.mapNavigationState
        val map = state.currentMap ?: return settingsState.updateMapNavigationError("当前没有已加载地图")
        val target = state.targetDraft
            ?: return settingsState.updateMapNavigationError("请先选择导航目标")
        val preview = state.pathPreview
        if (preview == null || preview.token.map != map || preview.token.goal != target ||
            preview.token.selectionGeneration != state.selectionGeneration
        ) {
            settingsState.updateMapNavigationError("目标尚无当前版本的有效规划预览")
            return
        }
        if (isEngineeringMock()) {
            val taskId = "mock-navigation-${nextMockRequestId()}"
            settingsState.updateControllerSnapshot(
                mockLocalizationSnapshot(
                    identity = map,
                    mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
                    localization = LocalizationState.LOCALIZATION_STATE_TRACKING,
                    robotPose = state.robotPose,
                    controlOwner = ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO,
                    activeTaskId = taskId
                )
            )
            settingsState.updateNavigationPath(
                NavigationPath(
                    task_id = taskId,
                    map = MapIdentity(map_id = map.mapId, revision = map.revision),
                    path_sequence = 1L,
                    source_time_ns = System.currentTimeMillis() * 1_000_000L,
                    frame_id = "map",
                    points = preview.points.map {
                        Pose2D(x = it.x, y = it.y, yaw = it.yaw)
                    },
                    length_m = preview.lengthM,
                    active = true
                )
            )
            settingsState.updateLastCommand("导航", "工程 Mock 已开始：$taskId")
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_START_NAVIGATION,
            description = "开始导航"
        ) {
            controllerClient.startNavigation(
                map.mapId, map.revision, target.x, target.y, target.yaw
            )
        }
    }

    override fun cancelNavigation() {
        val state = settingsState.mapNavigationState
        val taskId = state.activeNavigationTaskId.orEmpty()
        if (taskId.isEmpty() &&
            state.controlOwner != MapControlOwner.NAVIGATION_AUTO
        ) {
            settingsState.updateMapNavigationError("当前没有可取消的导航任务")
            return
        }
        if (isEngineeringMock()) {
            val state = settingsState.mapNavigationState
            val map = state.currentMap
                ?: return settingsState.updateMapNavigationError("当前没有已加载地图")
            settingsState.updateControllerSnapshot(
                mockLocalizationSnapshot(
                    identity = map,
                    mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
                    localization = LocalizationState.LOCALIZATION_STATE_TRACKING,
                    robotPose = state.robotPose
                )
            )
            settingsState.updateLastCommand("取消导航", "工程 Mock 已取消：$taskId")
            return
        }
        submitNavigationCommand(
            action = ActionCode.ACTION_CODE_CANCEL_NAVIGATION,
            description = "取消导航"
        ) { controllerClient.cancelNavigation(taskId) }
    }

    private fun submitNavigationCommand(
        action: ActionCode,
        description: String,
        markPlanRequest: Boolean = false,
        send: () -> Long
    ) {
        ensureInitialized()
        if (isEngineeringMock()) {
            settingsState.updateMapNavigationError("工程 Mock 暂不执行$description")
            return
        }
        if (settingsState.controllerConnectionState != RobotControllerConnectionState.CONNECTED) {
            settingsState.updateMapNavigationError("机器人主控未连接，无法$description")
            return
        }
        if (!settingsState.isActionAllowed(action)) {
            val reasons = settingsState.controllerSnapshot?.allowed_actions
                ?.firstOrNull { it.action == action }
                ?.blocking_reasons
                ?.joinToString("、")
                .orEmpty()
            settingsState.updateMapNavigationError(
                if (reasons.isEmpty()) "主控当前不允许$description" else "$description 被阻止：$reasons"
            )
            return
        }
        val requestId = send()
        if (requestId == 0L) {
            settingsState.updateMapNavigationError("$description 请求未进入主控发送队列")
            return
        }
        if (markPlanRequest) {
            settingsState.markPlanControllerRequest(requestId, description)
        } else {
            settingsState.markControllerRequest(requestId, description)
        }
    }

    private fun submitMappingCommand(
        action: ActionCode,
        description: String,
        send: () -> Long
    ) {
        ensureInitialized()
        if (isEngineeringMock()) {
            applyMockMappingCommand(action)
            return
        }
        if (settingsState.controllerConnectionState != RobotControllerConnectionState.CONNECTED) {
            settingsState.updateMappingError("机器人主控未连接，无法$description")
            return
        }
        if (!settingsState.isActionAllowed(action)) {
            val reasons = settingsState.controllerSnapshot?.allowed_actions
                ?.firstOrNull { it.action == action }
                ?.blocking_reasons
                ?.joinToString("、")
                .orEmpty()
            settingsState.updateMappingError(
                if (reasons.isEmpty()) "主控当前不允许$description" else "$description 被阻止：$reasons"
            )
            return
        }
        val requestId = send()
        if (requestId == 0L) {
            settingsState.updateMappingError("$description 请求未进入主控发送队列")
            return
        }
        settingsState.markControllerRequest(requestId, description)
    }

    /**
     * 设置机器人控制模式（站立/趴下/阻尼）
     */
    override fun setControlMode(controlMode: SportMode) {
        ensureInitialized()
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法设置运动模式")
            return
        }

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 未接管控制权，无法设置运动模式")
            return
        }

        if (settingsState.isRobotCtrlModeChanging) {
            Timber.w("[Controller] 正在切换运动模式中，请等待")
            return
        }

        settingsState.updateRobotCtrlModeChangingState(true)

        if (isEngineeringMock()) {
            settingsState.updateRobotCtrlMode(controlMode)
            settingsState.updateLastCommand("运动模式", controlMode.name)
            Timber.i("[Controller] 工程 Mock 更新运动模式: %s", controlMode)
            return
        }

        scope.launch {
            try {
                val success = zmqClient.setControlMode(controlMode)
                if (success) {
                    settingsState.updateLastCommand("运动模式", controlMode.name)
                    Timber.i("[Controller] 运动模式设置请求已发送: $controlMode")
                    // 实际运动模式更新由消息回调处理
                } else {
                    settingsState.updateRobotCtrlModeChangingState(false)
                    Timber.e("[Controller] 运动模式设置失败: $controlMode")
                }
            } catch (e: Exception) {
                settingsState.updateRobotCtrlModeChangingState(false)
                Timber.e(e, "[Controller] 设置运动模式异常: $controlMode")
            }
        }
    }

    /**
     * 设置速度档位
     */
    override fun setSpeedLevel(level: SpeedLevel) {
        ensureInitialized()
        settingsState.setSpeedLevel(level)
        if (isEngineeringMock() && settingsState.isConnected && settingsState.hasControl) {
            settingsState.updateLastCommand("速度档位", level.displayName)
        } else if (settingsState.isConnected && settingsState.hasControl) {
            if (zmqClient.setSpeedLevel(level.protocolSpeedLevel)) {
                settingsState.updateLastCommand("速度档位", level.displayName)
            }
        } else if (settingsState.isConnected) {
            Timber.w("[Controller] 未接管控制权，仅保存速度档位: %s", level.displayName)
        }
        // 自动保存更新后的设置
        saveSettings(settingsState.settings)
        Timber.i("[Controller] 已切换到${level.displayName}并保存设置")
    }

    /**
     * 发送主控页底部动作按钮命令。
     */
    override fun performAction(action: RobotAction) {
        ensureInitialized()
        if (!canSendControlledCommand("动作命令: ${action.displayName}")) return

        if (isEngineeringMock()) {
            settingsState.updateMotionStatus(action.motionStatus)
            settingsState.updateLastCommand("动作", action.displayName)
            Timber.i("[Controller] 工程 Mock 记录动作: %s", action.displayName)
            return
        }

        scope.launch {
            try {
                val success = zmqClient.sendSimpleCommand(action.commandCode)
                if (success) {
                    settingsState.updateLastCommand("动作", action.displayName)
                    Timber.i("[Controller] 动作命令已发送: %s", action.displayName)
                } else {
                    Timber.w("[Controller] 动作命令入队失败: %s", action.displayName)
                }
            } catch (e: Exception) {
                Timber.e(e, "[Controller] 发送动作命令异常: %s", action.displayName)
            }
        }
    }

    override fun setFrontLight(on: Boolean) {
        ensureInitialized()
        if (!canSendControlledCommand("前补光灯")) return

        if (isEngineeringMock()) {
            settingsState.updateFrontLightState(on)
            settingsState.updateLastCommand("前补光灯", if (on) "开" else "关")
            return
        }

        scope.launch {
            val success = zmqClient.setFrontLight(on)
            if (success) {
                settingsState.updateLastCommand("前补光灯", if (on) "开" else "关")
                Timber.i("[Controller] 前补光灯请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 前补光灯请求入队失败: %s", on)
            }
        }
    }

    override fun setBackLight(on: Boolean) {
        ensureInitialized()
        if (!canSendControlledCommand("后补光灯")) return

        if (isEngineeringMock()) {
            settingsState.updateBackLightState(on)
            settingsState.updateLastCommand("后补光灯", if (on) "开" else "关")
            return
        }

        scope.launch {
            val success = zmqClient.setBackLight(on)
            if (success) {
                settingsState.updateLastCommand("后补光灯", if (on) "开" else "关")
                Timber.i("[Controller] 后补光灯请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 后补光灯请求入队失败: %s", on)
            }
        }
    }

    override fun setAutoModeLight(on: Boolean) {
        ensureInitialized()
        if (!canSendControlledCommand("自动补光")) return

        if (isEngineeringMock()) {
            settingsState.updateAutoModeLightState(on)
            settingsState.updateLastCommand("自动补光", if (on) "开" else "关")
            return
        }

        scope.launch {
            val success = zmqClient.setAutoModeLight(on)
            if (success) {
                settingsState.updateLastCommand("自动补光", if (on) "开" else "关")
                Timber.i("[Controller] 自动补光请求已发送: %s", on)
            } else {
                Timber.w("[Controller] 自动补光请求入队失败: %s", on)
            }
        }
    }

    override fun reverseHeadTail() {
        ensureInitialized()
        if (!canSendControlledCommand("头尾方向切换")) return

        if (isEngineeringMock()) {
            val nextDirection = if (settingsState.headDirection == HeadDirection.HEAD_DIRECTION_TAIL) {
                HeadDirection.HEAD_DIRECTION_HEAD
            } else {
                HeadDirection.HEAD_DIRECTION_TAIL
            }
            settingsState.updateHeadDirection(nextDirection)
            settingsState.updateLastCommand(
                "头尾方向",
                if (nextDirection == HeadDirection.HEAD_DIRECTION_TAIL) "尾向" else "头向"
            )
            Timber.i("[Controller] 工程 Mock 切换头尾方向: %s", nextDirection)
            return
        }

        scope.launch {
            val success = zmqClient.sendSimpleCommand(CommandCode.COMMAND_CODE_REVERSE_HEAD_TAIL)
            if (success) {
                settingsState.updateLastCommand("头尾方向", "切换")
                Timber.i("[Controller] 头尾方向切换请求已发送")
            } else {
                Timber.w("[Controller] 头尾方向切换请求入队失败")
            }
        }
    }

    override fun controlHead(leftRight: Float, upDown: Float) {
        ensureInitialized()
        if (!canSendControlledCommand("头部控制")) return
        if (!settingsState.isInPlaceModeForAuxCommand("头部控制")) return

        val clampedLeftRight = leftRight.coerceIn(-1f, 1f)
        val clampedUpDown = upDown.coerceIn(-1f, 1f)

        if (isEngineeringMock()) {
            handleMockHeadControl(clampedLeftRight, clampedUpDown)
            return
        }

        scope.launch {
            val success = zmqClient.controlHead(clampedLeftRight, clampedUpDown)
            if (!success) {
                Timber.w("[Controller] 头部控制请求入队失败: leftRight=%s, upDown=%s", clampedLeftRight, clampedUpDown)
                return@launch
            }

            val isStopCommand = clampedLeftRight == 0f && clampedUpDown == 0f
            headControlActive = !isStopCommand
            headControlStopJob?.cancel()

            if (isStopCommand) {
                settingsState.updateLastCommand("头部控制", "停止")
                Timber.i("[Controller] 头部控制停止请求已发送")
            } else {
                settingsState.updateLastCommand(
                    "头部控制",
                    "左右 %.2f，俯仰 %.2f".format(clampedLeftRight, clampedUpDown)
                )
                Timber.i("[Controller] 头部控制短脉冲已发送: leftRight=%s, upDown=%s", clampedLeftRight, clampedUpDown)
                headControlStopJob = scope.launch {
                    delay(HEAD_CONTROL_PULSE_MS)
                    stopHeadControl()
                }
            }
        }
    }

    override fun setHighLowStance(stance: HighLowStance) {
        ensureInitialized()
        if (!canSendControlledCommand("高低站姿")) return
        if (!settingsState.isInPlaceModeForAuxCommand("高低站姿")) return

        if (isEngineeringMock()) {
            settingsState.updateHighLowStance(stance)
            settingsState.updateLastCommand("高低站姿", stance.displayName)
            return
        }

        scope.launch {
            val success = zmqClient.setHighLowStance(stance.protocolValue)
            if (success) {
                settingsState.updateHighLowStance(stance)
                settingsState.updateLastCommand("高低站姿", stance.displayName)
                Timber.i("[Controller] 高低站姿请求已发送: %s", stance.displayName)
            } else {
                Timber.w("[Controller] 高低站姿请求入队失败: %s", stance.displayName)
            }
        }
    }

    /**
     * 更新设置
     */
    override fun updateSettings(settings: AppSettings) {
        ensureInitialized()
        val mockModeChanged = settingsState.settings.engineeringMockEnabled != settings.engineeringMockEnabled
        if (mockModeChanged && settingsState.connectionState != ConnectionState.DISCONNECTED) {
            disconnect()
        }
        settingsState.updateSettings(settings)
        rebuildRemoteInputSource(settings)
        // 自动保存设置
        saveSettings(settings)
        Timber.d("[Controller] 设置已更新并保存")
    }

    /**
     * 加载设置
     */
    override fun loadSettings() {
        ensureInitialized()
        try {
            val settings = settingsManager.loadSettings()
            settingsState.updateSettings(settings)
            rebuildRemoteInputSource(settings)
            Timber.i(
                "[Controller] 设置已从存储中加载: IP=%s, driverPort=%d, controllerPort=%d",
                settings.zmqIp,
                settings.zmqPort,
                settings.controllerPort
            )
        } catch (e: Exception) {
            Timber.e(e, "[Controller] 加载设置失败，使用默认设置")
        }
    }

    /**
     * 保存设置
     */
    override fun saveSettings(settings: AppSettings) {
        ensureInitialized()
        try {
            settingsManager.saveSettings(settings)
            Timber.d("[Controller] 设置已保存到存储")
        } catch (e: Exception) {
            Timber.e(e, "[Controller] 保存设置失败")
        }
    }

    /**
     * 检查是否连接
     */
    override fun isConnected(): Boolean {
        return settingsState.isConnected
    }

    override fun pauseMovementOutput() {
        currentMovementIntent = MovementIntent.ZERO
        currentHeadControlIntent = HeadControlIntent.ZERO
        stopVelocityLoop()
        stopHeadControl()
        Timber.i("[Controller] 已暂停移动输出")
    }

    override fun resumeMovementOutput() {
        ensureInitialized()
        ensureActiveScope()
        if (settingsState.isConnected) {
            startRemoteInput()
            startVelocityLoop()
        }
        Timber.i("[Controller] 已恢复输入采集")
    }

    /**
     * 开始速度发送循环
     */
    private fun startVelocityLoop() {
        stopVelocityLoop()

        velocitySendJob = scope.launch {
            while (isActive && settingsState.isConnected) {
                try {
                    // 只有已接管且处于手动模式时才发送移动指令。
                    if (settingsState.hasControl && settingsState.robotMode == AppMode.APP_MODE_MANUAL) {
                        val intent = currentMovementIntent.clamped()
                        val headIntent = currentHeadControlIntent.clamped()

                        if (settingsState.robotCtrlMode == SportMode.SPORT_MODE_IN_PLACE) {
                            sendInPlaceHeadControl(intent, headIntent)
                            sendMovementFromIntent(intent.copy(yawRight = 0f))
                        } else {
                            if (headControlActive) {
                                stopHeadControl()
                            }
                            sendMovementFromIntent(intent)
                        }
                    } else {
                        if (lastCommandSent) {
                            if (!isEngineeringMock()) {
                                zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
                            }
                            settingsState.updateLastCommand("移动", "停止")
                            Timber.v("[Controller] 控制权或手动模式不满足，发送停止移动指令")
                            lastCommandSent = false
                        }
                        if (headControlActive) {
                            stopHeadControl()
                        }
                    }

                    delay(40) // 25Hz 发送频率

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Timber.e(e, "[Controller] 速度发送异常")
                    delay(100) // 出错时稍微等待一下
                }
            }
        }
    }

    private fun sendMovementFromIntent(intent: MovementIntent) {
        // 只有当外部遥控器有非零移动输入时才发送移动指令。
        if (!intent.isZero) {
            if (!isEngineeringMock()) {
                zmqClient.sendOperatorMoveCommand(
                    strafeRight = intent.strafeRight,
                    forward = intent.forward,
                    yawRight = intent.yawRight
                )
            }
            settingsState.updateLastCommand(
                "移动",
                "前进 %.2f，平移 %.2f，转向 %.2f".format(
                    intent.forward,
                    intent.strafeRight,
                    intent.yawRight
                )
            )
            Timber.v(
                "[Controller] 发送移动指令: forward=${intent.forward}, strafeRight=${intent.strafeRight}, yawRight=${intent.yawRight}"
            )
            lastCommandSent = true
        } else if (lastCommandSent) {
            // 只有之前发送过指令，且现在摇杆都在中心位置时，才发送一次停止指令。
            if (!isEngineeringMock()) {
                zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
            }
            settingsState.updateLastCommand("移动", "停止")
            Timber.v("[Controller] 发送停止移动指令")
            lastCommandSent = false
        }
    }

    private fun sendInPlaceHeadControl(intent: MovementIntent, headIntent: HeadControlIntent) {
        val leftRight = -intent.yawRight
        val upDown = headIntent.pitchUp

        if (leftRight == 0f && upDown == 0f) {
            if (headControlActive) {
                stopHeadControl()
            }
            return
        }

        headControlStopJob?.cancel()
        headControlStopJob = null
        if (!isEngineeringMock()) {
            zmqClient.controlHead(leftRight, upDown)
        }
        headControlActive = true
        settingsState.updateLastCommand(
            "原地姿态",
            "左右 %.2f，俯仰 %.2f".format(intent.yawRight, upDown)
        )
        Timber.v(
            "[Controller] 发送原地姿态指令: leftRight=$leftRight, upDown=$upDown"
        )
    }

    private fun handleRemoteSpeedLevelRequest(request: RemoteSpeedLevelRequest) {
        val speedLevel = when (request) {
            RemoteSpeedLevelRequest.LOW -> SpeedLevel.SLOW
            RemoteSpeedLevelRequest.MEDIUM -> SpeedLevel.MEDIUM
            RemoteSpeedLevelRequest.HIGH -> SpeedLevel.FAST
        }
        Timber.i("[Controller] 外部遥控速度键触发: %s -> %s", request, speedLevel.displayName)
        setSpeedLevel(speedLevel)
    }

    /**
     * 停止速度发送循环
     */
    private fun stopVelocityLoop() {
        if (lastCommandSent) {
            if (!isEngineeringMock()) {
                zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)
            }
            settingsState.updateLastCommand("移动", "停止")
        }
        currentMovementIntent = MovementIntent.ZERO
        currentHeadControlIntent = HeadControlIntent.ZERO
        velocitySendJob?.cancel()
        velocitySendJob = null
        lastCommandSent = false  // 重置命令发送标志
    }

    private fun stopHeadControl() {
        headControlStopJob?.cancel()
        headControlStopJob = null
        if (headControlActive) {
            if (!isEngineeringMock()) {
                zmqClient.controlHead(0f, 0f)
            }
            settingsState.updateLastCommand("头部控制", "停止")
            headControlActive = false
            Timber.v("[Controller] 发送头部停止指令")
        }
    }

    private fun handleMockHeadControl(leftRight: Float, upDown: Float) {
        val isStopCommand = leftRight == 0f && upDown == 0f
        headControlActive = !isStopCommand
        headControlStopJob?.cancel()

        if (isStopCommand) {
            settingsState.updateLastCommand("头部控制", "停止")
            return
        }

        settingsState.updateLastCommand(
            "头部控制",
            "左右 %.2f，俯仰 %.2f".format(leftRight, upDown)
        )
        headControlStopJob = scope.launch {
            delay(HEAD_CONTROL_PULSE_MS)
            stopHeadControl()
        }
    }

    private fun applyMockCancelNavigation() {
        val current = settingsState.controllerSnapshot ?: mockSnapshot(OperationMode.OPERATION_MODE_STANDBY)
        settingsState.updateRobotModeChangingState(true)
        val currentMap = settingsState.mapNavigationState.currentMap
        if (currentMap == null) {
            settingsState.updateControllerSnapshot(
                current.copy(
                    state_revision = current.state_revision + 1L,
                    control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL,
                    active_task = null,
                    recent_transition = "mock_navigation_canceled"
                )
            )
        } else {
            settingsState.updateControllerSnapshot(
                mockLocalizationSnapshot(
                    identity = currentMap,
                    mode = OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING,
                    localization = LocalizationState.LOCALIZATION_STATE_TRACKING,
                    robotPose = settingsState.mapNavigationState.robotPose
                )
            )
        }
        settingsState.updateLastCommand("取消导航", "工程 Mock 已确认并恢复 MANUAL")
    }

    private fun applyMockControllerState() {
        settingsState.updateControllerSnapshot(mockSnapshot(OperationMode.OPERATION_MODE_STANDBY))
        settingsState.updateControllerTimeSync(
            TimeSyncStatus(
                state = TimeSyncState.TIME_SYNC_STATE_APPLIED,
                message = "工程 Mock 对时完成",
                system_clock_applied = true,
                ptp_recovery_requested = true,
                ptp_recovery_succeeded = true
            )
        )
    }

    private fun applyMockMappingCommand(action: ActionCode) {
        val current = settingsState.controllerSnapshot ?: mockSnapshot(OperationMode.OPERATION_MODE_STANDBY)
        val nextMode = when (action) {
            ActionCode.ACTION_CODE_START_MAPPING -> OperationMode.OPERATION_MODE_MAPPING_RUNNING
            ActionCode.ACTION_CODE_FINISH_MAPPING -> OperationMode.OPERATION_MODE_MAPPING_REVIEW
            ActionCode.ACTION_CODE_SAVE_MAP,
            ActionCode.ACTION_CODE_DISCARD_MAP -> OperationMode.OPERATION_MODE_STANDBY
            else -> current.operation_mode
        }
        settingsState.updateControllerSnapshot(mockSnapshot(nextMode, current.state_revision + 1L))
        settingsState.updateLastCommand("建图", action.name)
        if (nextMode == OperationMode.OPERATION_MODE_MAPPING_RUNNING) {
            settingsState.updateMappingFrame(mockMappingFrame())
        }
    }

    private fun applyMockSwitchMap(identity: MapIdentityModel) {
        if (!requireMockActionAllowed(ActionCode.ACTION_CODE_SWITCH_MAP, "加载地图")) return
        val mock = ensureMockSavedMap()
        if (mock.preview.key.map != identity) {
            settingsState.updateMapNavigationError("工程 Mock 地图版本已变化，请刷新列表")
            return
        }
        settingsState.updateControllerSnapshot(
            mockLocalizationSnapshot(
                identity = identity,
                mode = OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE,
                localization = LocalizationState.LOCALIZATION_STATE_INITIALIZING,
                robotPose = MappingPose(2.2, 1.1, 0.35)
            )
        )
        settingsState.updateLastCommand("加载地图", "工程 Mock 已加载 ${mock.info.display_name}")
    }

    private fun applyMockPlan(identity: MapIdentityModel, target: MappingPose) {
        if (!requireMockActionAllowed(ActionCode.ACTION_CODE_PREVIEW_GOAL, "规划路径")) return
        val requestId = nextMockRequestId()
        val taskId = "mock-plan-$requestId"
        val start = settingsState.mapNavigationState.robotPose ?: MappingPose(2.2, 1.1, 0.35)
        settingsState.markPlanControllerRequest(requestId, "工程 Mock 规划路径")
        settingsState.completeControllerRequest(
            requestId,
            CommandResponse(
                stage = CommandStage.COMMAND_STAGE_ACCEPTED,
                task_id = taskId
            )
        )
        val points = (0..8).map { index ->
            val ratio = index / 8.0
            Pose2D(
                x = start.x + (target.x - start.x) * ratio,
                y = start.y + (target.y - start.y) * ratio,
                yaw = start.yaw + (target.yaw - start.yaw) * ratio
            )
        }
        settingsState.updatePathPreview(
            PathPreview(
                task_id = taskId,
                map = MapIdentity(map_id = identity.mapId, revision = identity.revision),
                points = points,
                length_m = hypot(target.x - start.x, target.y - start.y)
            )
        )
        settingsState.updateLastCommand("规划路径", "工程 Mock 已生成 ${points.size} 个路径点")
    }

    private fun mockLocalizationSnapshot(
        identity: MapIdentityModel,
        mode: OperationMode,
        localization: LocalizationState,
        robotPose: MappingPose?,
        controlOwner: ControlOwner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL,
        activeTaskId: String? = null
    ): StateSnapshot {
        val current = settingsState.controllerSnapshot
        val map = ensureMockSavedMap().info
        val allowed = if (controlOwner == ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO) {
            setOf(
                ActionCode.ACTION_CODE_CANCEL_NAVIGATION
            )
        } else {
            when (localization) {
                LocalizationState.LOCALIZATION_STATE_INITIALIZING -> setOf(
                    ActionCode.ACTION_CODE_SWITCH_MAP,
                    ActionCode.ACTION_CODE_SET_INITIAL_POSE,
                    ActionCode.ACTION_CODE_STOP_RUNTIME
                )
                LocalizationState.LOCALIZATION_STATE_TRACKING -> setOf(
                    ActionCode.ACTION_CODE_SWITCH_MAP,
                    ActionCode.ACTION_CODE_SET_INITIAL_POSE,
                    ActionCode.ACTION_CODE_PREVIEW_GOAL,
                    ActionCode.ACTION_CODE_START_NAVIGATION,
                    ActionCode.ACTION_CODE_STOP_RUNTIME
                )
                else -> setOf(
                    ActionCode.ACTION_CODE_SWITCH_MAP,
                    ActionCode.ACTION_CODE_STOP_RUNTIME
                )
            }
        }
        return StateSnapshot(
            state_revision = (current?.state_revision ?: 0L) + 1L,
            operation_mode = mode,
            control_owner = controlOwner,
            health_state = HealthState.HEALTH_STATE_READY,
            localization_state = localization,
            current_map = map.copy(
                identity = MapIdentity(map_id = identity.mapId, revision = identity.revision),
                current = true
            ),
            active_task = activeTaskId?.let {
                TaskInfo(
                    task_id = it,
                    type = TaskType.TASK_TYPE_NAVIGATE_TO_POSE,
                    map = MapIdentity(map_id = identity.mapId, revision = identity.revision)
                )
            },
            robot_pose = robotPose?.let { Pose2D(x = it.x, y = it.y, yaw = it.yaw) },
            robot_pose_frame_id = if (robotPose == null) "" else "map",
            robot_pose_source_time_ns = if (robotPose == null) {
                0L
            } else {
                System.currentTimeMillis() * 1_000_000L
            },
            robot_pose_source = if (robotPose == null) {
                PoseSource.POSE_SOURCE_UNSPECIFIED
            } else {
                PoseSource.POSE_SOURCE_LOCALIZATION
            },
            allowed_actions = ActionCode.entries
                .filter { it != ActionCode.ACTION_CODE_UNSPECIFIED }
                .map { action -> AllowedAction(action = action, allowed = action in allowed) },
            time_sync = TimeSyncStatus(
                state = TimeSyncState.TIME_SYNC_STATE_APPLIED,
                message = "工程 Mock 对时完成",
                system_clock_applied = true,
                ptp_recovery_requested = true,
                ptp_recovery_succeeded = true
            ),
            recent_transition = "mock_${mode.name.lowercase()}"
        )
    }

    private fun requireMockActionAllowed(action: ActionCode, description: String): Boolean {
        if (settingsState.isActionAllowed(action)) return true
        settingsState.updateMapNavigationError("工程 Mock 当前不允许$description")
        return false
    }

    private fun nextMockRequestId(): Long {
        mockRequestSequence += 1L
        return mockRequestSequence
    }

    private fun ensureMockSavedMap(): MockSavedMap {
        mockSavedMap?.let { return it }
        val width = 320
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }
        canvas.drawColor(Color.rgb(205, 205, 205))
        paint.color = Color.rgb(248, 248, 248)
        canvas.drawRect(18f, 15f, 302f, 185f, paint)
        paint.color = Color.rgb(24, 28, 29)
        canvas.drawRect(18f, 15f, 302f, 20f, paint)
        canvas.drawRect(18f, 180f, 302f, 185f, paint)
        canvas.drawRect(18f, 15f, 23f, 185f, paint)
        canvas.drawRect(297f, 15f, 302f, 185f, paint)
        canvas.drawRect(132f, 20f, 138f, 122f, paint)
        canvas.drawRect(132f, 142f, 138f, 180f, paint)
        canvas.drawRect(208f, 76f, 297f, 82f, paint)
        canvas.drawRect(58f, 120f, 102f, 126f, paint)
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "工程 Mock 地图预览编码失败"
            }
            output.toByteArray()
        }
        bitmap.recycle()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        val identity = MapIdentityModel("mock-warehouse-a", 3L)
        return MockSavedMap(
            info = MapInfo(
                identity = MapIdentity(map_id = identity.mapId, revision = identity.revision),
                display_name = "工程 Mock 仓库地图",
                content_hash = "sha256:$hash",
                preview_hash = "sha256:$hash",
                resolution_m = 0.05,
                origin_x_m = -2.0,
                origin_y_m = -1.5,
                width_cells = width,
                height_cells = height,
                created_utc_ns = System.currentTimeMillis() * 1_000_000L,
                origin_yaw_rad = 0.18
            ),
            preview = MapPreviewData(
                key = MapPreviewRequestKey(1L, identity),
                sha256 = hash,
                bytes = bytes,
                receivedAtMs = MappingClock.elapsedRealtimeMs()
            )
        ).also { mockSavedMap = it }
    }

    private fun mockSnapshot(
        mode: OperationMode,
        revision: Long = 1L
    ): StateSnapshot {
        val mappingVisible = mode == OperationMode.OPERATION_MODE_MAPPING_RUNNING ||
            mode == OperationMode.OPERATION_MODE_MAPPING_REVIEW ||
            mode == OperationMode.OPERATION_MODE_MAPPING_SAVING
        val allowed = when (mode) {
            OperationMode.OPERATION_MODE_STANDBY -> setOf(
                ActionCode.ACTION_CODE_START_MAPPING,
                ActionCode.ACTION_CODE_SWITCH_MAP
            )
            OperationMode.OPERATION_MODE_MAPPING_RUNNING -> setOf(ActionCode.ACTION_CODE_FINISH_MAPPING)
            OperationMode.OPERATION_MODE_MAPPING_REVIEW -> setOf(
                ActionCode.ACTION_CODE_SAVE_MAP,
                ActionCode.ACTION_CODE_DISCARD_MAP
            )
            else -> emptySet()
        }
        return StateSnapshot(
            state_revision = revision,
            operation_mode = mode,
            control_owner = ControlOwner.CONTROL_OWNER_REMOTE_MANUAL,
            health_state = HealthState.HEALTH_STATE_READY,
            robot_pose = if (mappingVisible) Pose2D(x = 1.8, y = 1.4, yaw = 0.45) else null,
            robot_pose_frame_id = if (mappingVisible) "map" else "",
            robot_pose_source_time_ns = if (mappingVisible) {
                System.currentTimeMillis() * 1_000_000L
            } else {
                0L
            },
            robot_pose_source = if (mappingVisible) {
                PoseSource.POSE_SOURCE_MAPPING_ODOMETRY
            } else {
                PoseSource.POSE_SOURCE_UNSPECIFIED
            },
            mapping_stream = MappingStreamStatus(
                available = mappingVisible,
                latest_frame_sequence = if (mode == OperationMode.OPERATION_MODE_STANDBY) 0L else 1L,
                latest_source_time_ns = System.currentTimeMillis() * 1_000_000L
            ),
            allowed_actions = ActionCode.entries
                .filter { it != ActionCode.ACTION_CODE_UNSPECIFIED }
                .map { action -> AllowedAction(action = action, allowed = action in allowed) },
            time_sync = TimeSyncStatus(
                state = TimeSyncState.TIME_SYNC_STATE_APPLIED,
                message = "工程 Mock 对时完成",
                system_clock_applied = true,
                ptp_recovery_requested = true,
                ptp_recovery_succeeded = true
            ),
            recent_transition = "mock_${mode.name.lowercase()}"
        )
    }

    private fun mockMappingFrame(sequence: Long = 1L): MappingGridFrame {
        val width = 160
        val height = 100
        val cells = ByteArray(width * height) { -1 }
        for (y in 8 until height - 8) {
            for (x in 8 until width - 8) {
                val wall = x == 8 || y == 8 || x == width - 9 || y == height - 9 ||
                    (x in 72..75 && y in 25..82)
                cells[y * width + x] = if (wall) 100 else 0
            }
        }
        return MappingGridFrame(
            metadata = MappingGridMetadataModel(
                frameSequence = sequence,
                frameId = "map",
                sourceTimeNs = System.currentTimeMillis() * 1_000_000L,
                resolutionM = 0.05,
                widthCells = width,
                heightCells = height,
                origin = MappingPose(-1.0, -1.0, 0.0),
                encodingValue = 1,
                uncompressedSizeBytes = cells.size.toLong(),
                compressedSizeBytes = 1L,
                sha256 = "0".repeat(64)
            ),
            cells = cells,
            receivedAtMs = MappingClock.elapsedRealtimeMs()
        )
    }

    private fun startMockStateLoop() {
        stopMockStateLoop()
        mockStateJob = scope.launch {
            while (isActive && isEngineeringMock() && settingsState.isConnected) {
                settingsState.updateBatteryLevel(72)
                settingsState.updateDriverConnectionTelemetry(
                    connectionState = DriverConnectionState.CONNECTION_STATE_CONNECTED,
                    robotConnected = true
                )
                settingsState.updateCurrentSpeedValue(
                    if (currentMovementIntent.isZero) {
                        0.0
                    } else {
                        hypot(currentMovementIntent.forward.toDouble(), currentMovementIntent.strafeRight.toDouble())
                    }
                )
                val snapshot = settingsState.controllerSnapshot
                if (snapshot?.operation_mode == OperationMode.OPERATION_MODE_MAPPING_RUNNING) {
                    val nextSequence = (settingsState.mappingFrame?.metadata?.frameSequence ?: 0L) + 1L
                    val phase = (nextSequence % 40L) / 40.0 * Math.PI * 2.0
                    val nowNs = System.currentTimeMillis() * 1_000_000L
                    settingsState.updateMappingFrame(mockMappingFrame(nextSequence))
                    settingsState.updateControllerSnapshot(
                        snapshot.copy(
                            state_revision = snapshot.state_revision + 1L,
                            robot_pose = Pose2D(
                                x = 1.8 + kotlin.math.cos(phase) * 0.7,
                                y = 1.4 + kotlin.math.sin(phase) * 0.5,
                                yaw = phase + Math.PI / 2.0
                            ),
                            robot_pose_source_time_ns = nowNs,
                            mapping_stream = snapshot.mapping_stream?.copy(
                                latest_frame_sequence = nextSequence,
                                latest_source_time_ns = nowNs
                            )
                        )
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun stopMockStateLoop() {
        mockStateJob?.cancel()
        mockStateJob = null
    }

    /**
     * 清理资源
     */
    override fun cleanup() {
        ensureInitialized()
        disconnect()
        stopRemoteInput()
        supervisorJob.cancel()
    }

    private fun startRemoteInput() {
        ensureInitialized()
        remoteInputRequested = true
        if (
            !isEngineeringMock() &&
            remoteInputSource.descriptor.id == "unirc_udp" &&
            SiyiUdpBridgeController.shouldUseForHost(settingsState.settings.remoteInputHost)
        ) {
            siyiUdpBridgeController.ensureBridgeOpen()
        }
        remoteInputSource.start(remoteInputListener)
    }

    private fun stopRemoteInput() {
        ensureInitialized()
        remoteInputRequested = false
        remoteInputSource.stop()
        siyiUdpBridgeController.release()
        currentMovementIntent = MovementIntent.ZERO
        currentHeadControlIntent = HeadControlIntent.ZERO
        lastRemoteSpeedLevelRequest = null
        remoteSpeedLevelSnapshotSeen = false
    }

    private fun rebuildRemoteInputSource(settings: AppSettings) {
        val shouldRestart = remoteInputRequested
        if (shouldRestart) {
            remoteInputSource.stop()
            siyiUdpBridgeController.release()
        }

        remoteInputSource = buildRemoteInputSource(settings)

        if (shouldRestart) {
            startRemoteInput()
        }
    }

    private fun buildRemoteInputSource(settings: AppSettings): RemoteInputSource {
        val normalization = RemoteInputNormalizationConfig(
            deadZone = settings.remoteInputDeadZone,
            timeoutMs = settings.remoteInputTimeoutMs,
            mapping = RemoteInputChannelMapping(
                forward = RemoteInputAxisMapping(
                    channel = settings.remoteInputForwardChannel,
                    inverted = settings.remoteInputForwardInverted
                ),
                strafeRight = RemoteInputAxisMapping(
                    channel = settings.remoteInputStrafeRightChannel,
                    inverted = settings.remoteInputStrafeRightInverted
                ),
                yawRight = RemoteInputAxisMapping(
                    channel = settings.remoteInputYawRightChannel,
                    inverted = settings.remoteInputYawRightInverted
                ),
                headPitchUp = RemoteInputAxisMapping(
                    channel = settings.remoteInputHeadPitchChannel,
                    inverted = settings.remoteInputHeadPitchInverted
                )
            )
        )

        if (settings.engineeringMockEnabled) {
            return MockRemoteInputSource(
                MockRemoteInputConfig(normalization = normalization)
            )
        }

        val appContext = applicationContext
        if (appContext == null) {
            return UniRcUdpInputSource(
                UniRcUdpInputConfig(
                    remoteHost = settings.remoteInputHost,
                    remotePort = settings.remoteInputPort,
                    localPort = settings.remoteInputLocalPort,
                    rawForward = UniRcRawUdpForwardConfig(
                        enabled = settings.remoteInputRawForwardEnabled,
                        targetPort = settings.remoteInputRawForwardPort
                    ),
                    normalization = normalization
                )
            )
        }

        val identity = AndroidRemoteControllerIdentityReader.read()
        val selection = RemoteInputSourceFactory().create(
            identity = identity,
            request = RemoteInputSourceRequest(
                applicationContext = appContext,
                normalization = normalization,
                uniRcConfig = UniRcUdpInputConfig(
                    remoteHost = settings.remoteInputHost,
                    remotePort = settings.remoteInputPort,
                    localPort = settings.remoteInputLocalPort,
                    rawForward = UniRcRawUdpForwardConfig(
                        enabled = settings.remoteInputRawForwardEnabled,
                        targetPort = settings.remoteInputRawForwardPort
                    ),
                    normalization = normalization
                )
            )
        )
        Timber.i(
            "[Controller] 自动选择遥控输入源: provider=%s, rcSdkDevice=%s, model=%s, boardVariant=%s",
            selection.providerId,
            selection.identity.rcSdkDeviceType,
            selection.identity.model,
            selection.identity.boardVariant
        )
        return selection.source
    }

    private fun isEngineeringMock(): Boolean {
        return settingsState.settings.engineeringMockEnabled
    }

    private fun canSendControlledCommand(commandName: String): Boolean {
        if (!settingsState.isConnected) {
            Timber.w("[Controller] 未连接，无法发送%s", commandName)
            return false
        }

        if (!settingsState.hasControl) {
            Timber.w("[Controller] 未接管控制权，无法发送%s", commandName)
            return false
        }

        if (settingsState.robotMode != AppMode.APP_MODE_MANUAL) {
            Timber.w("[Controller] 当前由自动导航控制，需先取消导航才能发送%s", commandName)
            return false
        }

        return true
    }

    private fun ControllerState.isInPlaceModeForAuxCommand(commandName: String): Boolean {
        if (robotCtrlMode == SportMode.SPORT_MODE_IN_PLACE) return true

        Timber.w("[Controller] 当前不是原地模式，无法发送%s", commandName)
        return false
    }

    private val remoteInputListener = object : RemoteInputListener {
        override fun onStatusChanged(
            descriptor: RemoteInputSourceDescriptor,
            status: RemoteInputStatus,
            message: String
        ) {
            if (status == RemoteInputStatus.TIMEOUT || status == RemoteInputStatus.ERROR) {
                currentMovementIntent = MovementIntent.ZERO
                currentHeadControlIntent = HeadControlIntent.ZERO
                lastRemoteSpeedLevelRequest = null
                remoteSpeedLevelSnapshotSeen = false
            } else if (status == RemoteInputStatus.STOPPED) {
                currentMovementIntent = MovementIntent.ZERO
                currentHeadControlIntent = HeadControlIntent.ZERO
                lastRemoteSpeedLevelRequest = null
                remoteSpeedLevelSnapshotSeen = false
            }
            Timber.d("[Controller] 外部遥控输入状态: %s %s", status, message)

            scope.launch {
                settingsState.updateRemoteInputStatus(descriptor, status, message)
            }
        }

        override fun onSnapshot(snapshot: RemoteInputSnapshot) {
            currentMovementIntent = snapshot.movementIntent.clamped()
            currentHeadControlIntent = snapshot.headControlIntent.clamped()
            val speedLevelRequest = snapshot.speedLevelRequest
            if (!remoteSpeedLevelSnapshotSeen) {
                remoteSpeedLevelSnapshotSeen = true
                lastRemoteSpeedLevelRequest = speedLevelRequest
                Timber.i("[Controller] 外部遥控速度基线: %s", speedLevelRequest ?: "无")
            } else if (speedLevelRequest == null) {
                lastRemoteSpeedLevelRequest = null
            } else if (speedLevelRequest != lastRemoteSpeedLevelRequest) {
                lastRemoteSpeedLevelRequest = speedLevelRequest
                scope.launch {
                    handleRemoteSpeedLevelRequest(speedLevelRequest)
                }
            }
            scope.launch {
                settingsState.updateRemoteInputSnapshot(snapshot)
            }
        }
    }

    private fun RobotStateMessage.toLinearSpeedValue(): Double {
        val speedData = speed ?: return 0.0
        return hypot(speedData.line, speedData.translation)
    }

}
