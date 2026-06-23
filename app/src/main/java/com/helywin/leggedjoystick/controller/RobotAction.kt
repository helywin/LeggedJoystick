package com.helywin.leggedjoystick.controller

import legged_driver.CommandCode

/**
 * 主控页底部动作按钮对应的机器人命令。
 */
enum class RobotAction(
    val displayName: String,
    val commandCode: CommandCode
) {
    STAND_UP("站立", CommandCode.COMMAND_CODE_STAND_UP),
    CRAWL("匍匐", CommandCode.COMMAND_CODE_CRAWL),
    LIE_DOWN("卧倒", CommandCode.COMMAND_CODE_LIE_DOWN),
    GAIT("扭一扭", CommandCode.COMMAND_CODE_GAIT),
    CLIMB("爬高墙", CommandCode.COMMAND_CODE_CLIMB),
    SLIM("过窄墙", CommandCode.COMMAND_CODE_SLIM),
    LOCKED("锁定", CommandCode.COMMAND_CODE_LOCKED)
}
