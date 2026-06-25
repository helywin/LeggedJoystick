package com.helywin.leggedjoystick.controller

import legged_driver.CommandCode
import legged_driver.MotionStatus

/**
 * 主控页底部动作按钮对应的机器人命令。
 */
enum class RobotAction(
    val displayName: String,
    val commandCode: CommandCode,
    val motionStatus: MotionStatus
) {
    STAND_UP("站立", CommandCode.COMMAND_CODE_STAND_UP, MotionStatus.MOTION_STATUS_STAND_UP),
    CRAWL("匍匐", CommandCode.COMMAND_CODE_CRAWL, MotionStatus.MOTION_STATUS_CRAWL),
    LIE_DOWN("卧倒", CommandCode.COMMAND_CODE_LIE_DOWN, MotionStatus.MOTION_STATUS_LIE_DOWN),
    CLIMB("爬高墙", CommandCode.COMMAND_CODE_CLIMB, MotionStatus.MOTION_STATUS_CLIMB),
    SLIM("过窄墙", CommandCode.COMMAND_CODE_SLIM, MotionStatus.MOTION_STATUS_SLIM),
    LOCKED("锁定", CommandCode.COMMAND_CODE_LOCKED, MotionStatus.MOTION_STATUS_LOCKED)
}
