package com.helywin.leggedjoystick.ui.mapping

import com.helywin.leggedjoystick.mapping.MapLocalizationStatus
import com.helywin.leggedjoystick.mapping.MapControlOwner
import com.helywin.leggedjoystick.mapping.MapNavigationState
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.StateSnapshot

internal enum class NavigationWorkflowStep {
    WAITING_FOR_CONTROLLER,
    SELECT_MAP,
    LOAD_MAP,
    LOADING_MAP,
    SET_INITIAL_POSE,
    CONFIRM_INITIAL_POSE,
    SELECT_GOAL,
    PLAN_ROUTE,
    START_NAVIGATION,
    NAVIGATING,
    LOCALIZATION_LOST,
    UNAVAILABLE
}

internal enum class NavigationStatusTone {
    GOOD,
    NEUTRAL,
    WARNING
}

internal data class NavigationBlockingReason(
    val message: String,
    val code: String
)

internal data class NavigationWorkflowPresentation(
    val step: NavigationWorkflowStep,
    val stageText: String,
    val stepLabel: String,
    val primaryAction: ActionCode?,
    val actionAllowed: Boolean?,
    val blockingReasons: List<NavigationBlockingReason>,
    val localizationLabel: String,
    val localizationTone: NavigationStatusTone
)

internal object NavigationWorkflowPresenter {
    fun present(
        snapshot: StateSnapshot?,
        navigation: MapNavigationState
    ): NavigationWorkflowPresentation {
        val operationMode = snapshot?.operation_mode
        val navigationActive = navigation.activeNavigationTaskId != null ||
            navigation.controlOwner == MapControlOwner.NAVIGATION_AUTO
        val step = when {
            snapshot == null -> NavigationWorkflowStep.WAITING_FOR_CONTROLLER
            navigationActive -> NavigationWorkflowStep.NAVIGATING
            operationMode == OperationMode.OPERATION_MODE_STANDBY &&
                navigation.selectedMap != null -> NavigationWorkflowStep.LOAD_MAP
            operationMode == OperationMode.OPERATION_MODE_STANDBY ->
                NavigationWorkflowStep.SELECT_MAP
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_LOADING ->
                NavigationWorkflowStep.LOADING_MAP
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE &&
                navigation.initialPoseDraft == null -> NavigationWorkflowStep.SET_INITIAL_POSE
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE ->
                NavigationWorkflowStep.CONFIRM_INITIAL_POSE
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING &&
                navigation.targetDraft == null -> NavigationWorkflowStep.SELECT_GOAL
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING &&
                navigation.pathPreview == null -> NavigationWorkflowStep.PLAN_ROUTE
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING ->
                NavigationWorkflowStep.START_NAVIGATION
            operationMode == OperationMode.OPERATION_MODE_LOCALIZATION_LOST ->
                NavigationWorkflowStep.LOCALIZATION_LOST
            else -> NavigationWorkflowStep.UNAVAILABLE
        }
        val primaryAction = step.primaryAction()
        val actionState = primaryAction?.let { action ->
            snapshot?.allowed_actions?.firstOrNull { it.action == action }
        }
        val blockers = actionState?.blocking_reasons
            .orEmpty()
            .distinct()
            .map(::describeBlockingReason)
        val localization = localizationPresentation(operationMode, navigation.localizationStatus)
        return NavigationWorkflowPresentation(
            step = step,
            stageText = step.stageText(),
            stepLabel = step.stepLabel(),
            primaryAction = primaryAction,
            actionAllowed = actionState?.allowed,
            blockingReasons = blockers,
            localizationLabel = localization.first,
            localizationTone = localization.second
        )
    }

    private fun NavigationWorkflowStep.primaryAction(): ActionCode? {
        return when (this) {
            NavigationWorkflowStep.LOAD_MAP -> ActionCode.ACTION_CODE_SWITCH_MAP
            NavigationWorkflowStep.SET_INITIAL_POSE,
            NavigationWorkflowStep.CONFIRM_INITIAL_POSE ->
                ActionCode.ACTION_CODE_SET_INITIAL_POSE
            NavigationWorkflowStep.SELECT_GOAL,
            NavigationWorkflowStep.PLAN_ROUTE -> ActionCode.ACTION_CODE_PREVIEW_GOAL
            NavigationWorkflowStep.START_NAVIGATION ->
                ActionCode.ACTION_CODE_START_NAVIGATION
            NavigationWorkflowStep.NAVIGATING ->
                ActionCode.ACTION_CODE_CANCEL_NAVIGATION
            else -> null
        }
    }

    private fun NavigationWorkflowStep.stageText(): String {
        return when (this) {
            NavigationWorkflowStep.WAITING_FOR_CONTROLLER -> "等待主控权威状态"
            NavigationWorkflowStep.SELECT_MAP -> "下一步：选择地图"
            NavigationWorkflowStep.LOAD_MAP -> "下一步：加载选中地图"
            NavigationWorkflowStep.LOADING_MAP -> "地图加载中，等待定位运行时"
            NavigationWorkflowStep.SET_INITIAL_POSE -> "下一步：设置初始位姿"
            NavigationWorkflowStep.CONFIRM_INITIAL_POSE -> "下一步：确认初始位姿"
            NavigationWorkflowStep.SELECT_GOAL -> "下一步：设置导航目标"
            NavigationWorkflowStep.PLAN_ROUTE -> "下一步：规划路线"
            NavigationWorkflowStep.START_NAVIGATION -> "下一步：检查路线并开始导航"
            NavigationWorkflowStep.NAVIGATING -> "自动导航进行中"
            NavigationWorkflowStep.LOCALIZATION_LOST -> "定位已丢失，等待恢复"
            NavigationWorkflowStep.UNAVAILABLE -> "当前模式不提供导航操作"
        }
    }

    private fun NavigationWorkflowStep.stepLabel(): String {
        return when (this) {
            NavigationWorkflowStep.WAITING_FOR_CONTROLLER -> "等待主控"
            NavigationWorkflowStep.SELECT_MAP -> "选择地图"
            NavigationWorkflowStep.LOAD_MAP -> "加载地图"
            NavigationWorkflowStep.LOADING_MAP -> "等待地图加载"
            NavigationWorkflowStep.SET_INITIAL_POSE,
            NavigationWorkflowStep.CONFIRM_INITIAL_POSE -> "设置初始位姿"
            NavigationWorkflowStep.SELECT_GOAL,
            NavigationWorkflowStep.PLAN_ROUTE -> "规划导航目标"
            NavigationWorkflowStep.START_NAVIGATION -> "开始导航"
            NavigationWorkflowStep.NAVIGATING -> "取消导航"
            NavigationWorkflowStep.LOCALIZATION_LOST -> "等待定位恢复"
            NavigationWorkflowStep.UNAVAILABLE -> "无导航操作"
        }
    }

    private fun localizationPresentation(
        operationMode: OperationMode?,
        localizationStatus: MapLocalizationStatus
    ): Pair<String, NavigationStatusTone> {
        when (operationMode) {
            OperationMode.OPERATION_MODE_STANDBY ->
                return "未启动" to NavigationStatusTone.NEUTRAL
            OperationMode.OPERATION_MODE_LOCALIZATION_LOADING ->
                return "地图加载中" to NavigationStatusTone.NEUTRAL
            OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE ->
                return "待初始位姿" to NavigationStatusTone.NEUTRAL
            OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING ->
                return "跟踪正常" to NavigationStatusTone.GOOD
            OperationMode.OPERATION_MODE_LOCALIZATION_LOST ->
                return "已丢失" to NavigationStatusTone.WARNING
            else -> Unit
        }
        return when (localizationStatus) {
            MapLocalizationStatus.INITIALIZING ->
                "初始化中" to NavigationStatusTone.NEUTRAL
            MapLocalizationStatus.TRACKING ->
                "跟踪正常" to NavigationStatusTone.GOOD
            MapLocalizationStatus.LOST ->
                "已丢失" to NavigationStatusTone.WARNING
            MapLocalizationStatus.UNKNOWN ->
                "未知" to NavigationStatusTone.WARNING
        }
    }

    private fun describeBlockingReason(code: String): NavigationBlockingReason {
        val fact = code.substringBefore(':')
        val message = when (fact) {
            "startup_reconciliation" -> "启动核对尚未完成"
            "remote_session" -> "遥控器会话未就绪"
            "driver_authority" -> "底盘控制权未就绪"
            "system_clock" -> "NX 系统时间未就绪"
            "ptp_runtime" -> "PTP 服务未就绪"
            "lidar_ptp" -> "雷达时间同步未就绪"
            "runtime" -> "定位导航运行时未就绪"
            "localization" -> "定位状态未就绪"
            "navigation_safety" -> "导航安全状态未就绪"
            "operation_mode_conflict" -> "当前运行模式不允许此操作"
            "initial_pose_not_requested" -> "当前不需要设置初始位姿"
            "localization_not_tracking" -> "定位尚未进入跟踪状态"
            "navigation_not_active" -> "当前没有活动导航任务"
            else -> "当前步骤暂不可执行"
        }
        return NavigationBlockingReason(message = message, code = code)
    }
}
