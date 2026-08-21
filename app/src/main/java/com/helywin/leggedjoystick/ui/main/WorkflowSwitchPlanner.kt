package com.helywin.leggedjoystick.ui.main

import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.OperationMode

enum class TaskWorkspace {
    MAPPING,
    NAVIGATION
}

enum class MappingExitChoice {
    SAVE,
    DISCARD
}

enum class WorkflowSwitchCommand {
    STOP_RUNTIME,
    FINISH_MAPPING,
    SAVE_MAP,
    DISCARD_MAP
}

data class WorkflowSwitchRequest(
    val target: TaskWorkspace,
    val sessionGeneration: Long,
    val mappingExitChoice: MappingExitChoice? = null,
    val mapDisplayName: String = ""
)

data class WorkflowSwitchAuthority(
    val sessionGeneration: Long,
    val connected: Boolean,
    val mode: OperationMode,
    val allowedActions: Set<ActionCode>,
    val requestInFlight: Boolean
)

sealed interface WorkflowSwitchPlan {
    data object Complete : WorkflowSwitchPlan
    data class Execute(val command: WorkflowSwitchCommand) : WorkflowSwitchPlan
    data class Wait(val message: String) : WorkflowSwitchPlan
    data class Failed(val message: String) : WorkflowSwitchPlan
}

object WorkflowSwitchPlanner {
    private val validMapName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    fun plan(
        request: WorkflowSwitchRequest,
        authority: WorkflowSwitchAuthority,
        dispatched: WorkflowSwitchCommand?
    ): WorkflowSwitchPlan {
        if (!authority.connected) {
            return WorkflowSwitchPlan.Failed("机器人主控连接已中断，请重连后重新发起切换")
        }
        if (request.sessionGeneration != authority.sessionGeneration) {
            return WorkflowSwitchPlan.Failed("主控会话已经变化，请根据最新状态重新发起切换")
        }
        if (authority.requestInFlight) {
            return WorkflowSwitchPlan.Wait("等待当前主控命令返回")
        }
        return when (request.target) {
            TaskWorkspace.MAPPING -> planToMapping(authority, dispatched)
            TaskWorkspace.NAVIGATION -> planToNavigation(request, authority, dispatched)
        }
    }

    private fun planToMapping(
        authority: WorkflowSwitchAuthority,
        dispatched: WorkflowSwitchCommand?
    ): WorkflowSwitchPlan = when {
        authority.mode.isMappingMode() || authority.mode == OperationMode.OPERATION_MODE_STANDBY -> {
            WorkflowSwitchPlan.Complete
        }
        authority.mode.isLocalizationMode() -> {
            commandPlan(
                authority = authority,
                command = WorkflowSwitchCommand.STOP_RUNTIME,
                action = ActionCode.ACTION_CODE_STOP_RUNTIME,
                dispatched = dispatched,
                waitingMessage = "正在取消导航并停止定位运行时"
            )
        }
        else -> WorkflowSwitchPlan.Failed(
            "当前运行态 ${authority.mode.name} 不能切换到建图，请先恢复到待机"
        )
    }

    private fun planToNavigation(
        request: WorkflowSwitchRequest,
        authority: WorkflowSwitchAuthority,
        dispatched: WorkflowSwitchCommand?
    ): WorkflowSwitchPlan = when {
        authority.mode.isLocalizationMode() ||
            authority.mode == OperationMode.OPERATION_MODE_STANDBY -> {
            WorkflowSwitchPlan.Complete
        }
        authority.mode == OperationMode.OPERATION_MODE_MAPPING_PREPARING -> {
            WorkflowSwitchPlan.Wait("建图正在准备，等待主控进入运行态")
        }
        authority.mode == OperationMode.OPERATION_MODE_MAPPING_RUNNING -> {
            commandPlan(
                authority = authority,
                command = WorkflowSwitchCommand.FINISH_MAPPING,
                action = ActionCode.ACTION_CODE_FINISH_MAPPING,
                dispatched = dispatched,
                waitingMessage = "正在结束当前建图并等待复核状态"
            )
        }
        authority.mode == OperationMode.OPERATION_MODE_MAPPING_REVIEW -> {
            when (request.mappingExitChoice) {
                MappingExitChoice.SAVE -> {
                    if (!validMapName.matches(request.mapDisplayName)) {
                        WorkflowSwitchPlan.Failed("地图名不合法，请使用字母、数字、点、下划线或连字符")
                    } else {
                        commandPlan(
                            authority = authority,
                            command = WorkflowSwitchCommand.SAVE_MAP,
                            action = ActionCode.ACTION_CODE_SAVE_MAP,
                            dispatched = dispatched,
                            waitingMessage = "正在保存地图并等待主控回到待机"
                        )
                    }
                }
                MappingExitChoice.DISCARD -> commandPlan(
                    authority = authority,
                    command = WorkflowSwitchCommand.DISCARD_MAP,
                    action = ActionCode.ACTION_CODE_DISCARD_MAP,
                    dispatched = dispatched,
                    waitingMessage = "正在放弃本次建图并等待主控回到待机"
                )
                null -> WorkflowSwitchPlan.Failed("请选择保存或放弃当前建图")
            }
        }
        authority.mode == OperationMode.OPERATION_MODE_MAPPING_SAVING -> {
            WorkflowSwitchPlan.Wait("地图正在保存，等待主控回到待机")
        }
        else -> WorkflowSwitchPlan.Failed(
            "当前运行态 ${authority.mode.name} 不能切换到定位导航，请先恢复到待机"
        )
    }

    private fun commandPlan(
        authority: WorkflowSwitchAuthority,
        command: WorkflowSwitchCommand,
        action: ActionCode,
        dispatched: WorkflowSwitchCommand?,
        waitingMessage: String
    ): WorkflowSwitchPlan {
        if (dispatched == command) {
            return WorkflowSwitchPlan.Wait(waitingMessage)
        }
        if (action !in authority.allowedActions) {
            return WorkflowSwitchPlan.Wait("等待主控允许${command.displayName()}")
        }
        return WorkflowSwitchPlan.Execute(command)
    }
}

fun WorkflowSwitchCommand.displayName(): String = when (this) {
    WorkflowSwitchCommand.STOP_RUNTIME -> "停止定位运行时"
    WorkflowSwitchCommand.FINISH_MAPPING -> "结束建图"
    WorkflowSwitchCommand.SAVE_MAP -> "保存地图"
    WorkflowSwitchCommand.DISCARD_MAP -> "放弃地图"
}

fun OperationMode?.isMappingMode(): Boolean {
    return this == OperationMode.OPERATION_MODE_MAPPING_PREPARING ||
        this == OperationMode.OPERATION_MODE_MAPPING_RUNNING ||
        this == OperationMode.OPERATION_MODE_MAPPING_REVIEW ||
        this == OperationMode.OPERATION_MODE_MAPPING_SAVING
}

fun OperationMode?.isLocalizationMode(): Boolean {
    return this == OperationMode.OPERATION_MODE_LOCALIZATION_LOADING ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING ||
        this == OperationMode.OPERATION_MODE_LOCALIZATION_LOST
}
