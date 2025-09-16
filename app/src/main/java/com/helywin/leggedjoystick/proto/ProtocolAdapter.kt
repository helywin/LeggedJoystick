/*********************************************************************************
 * FileName: ProtocolAdapter.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-16
 * Description: 协议适配器，将新协议的枚举值映射到现有数据类型
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.proto

import com.helywin.leggedjoystick.data.RobotCtrlMode
import com.helywin.leggedjoystick.data.RobotMode

/**
 * 协议适配器
 * 负责在新协议枚举和现有数据类型之间进行转换
 */
object ProtocolAdapter {
    
    // ========== Mode 转换 ==========
    
    /**
     * 将协议中的Mode转换为RobotMode
     */
    fun protoModeToRobotMode(mode: Mode): RobotMode {
        return when (mode) {
            Mode.MODE_MANUAL -> RobotMode.MANUAL
            Mode.MODE_AUTO -> RobotMode.AUTO
            else -> RobotMode.AUTO // 默认自动模式
        }
    }
    
    /**
     * 将RobotMode转换为协议中的Mode
     */
    fun robotModeToProtoMode(robotMode: RobotMode): Mode {
        return when (robotMode) {
            RobotMode.MANUAL -> Mode.MODE_MANUAL
            RobotMode.AUTO -> Mode.MODE_AUTO
        }
    }
    
    // ========== ControlMode 转换 ==========
    
    /**
     * 将协议中的ControlMode转换为RobotCtrlMode
     */
    fun protoControlModeToRobotCtrlMode(controlMode: ControlMode): RobotCtrlMode {
        return when (controlMode) {
            ControlMode.CONTROL_MODE_DAMPING -> RobotCtrlMode.PASSIVE
            ControlMode.CONTROL_MODE_LIE_DOWN -> RobotCtrlMode.LIE_DOWN
            ControlMode.CONTROL_MODE_STAND_UP -> RobotCtrlMode.STAND
            else -> RobotCtrlMode.STAND // 默认站立模式
        }
    }
    
    /**
     * 将RobotCtrlMode转换为协议中的ControlMode
     */
    fun robotCtrlModeToProtoControlMode(robotCtrlMode: RobotCtrlMode): ControlMode {
        return when (robotCtrlMode) {
            RobotCtrlMode.PASSIVE -> ControlMode.CONTROL_MODE_DAMPING
            RobotCtrlMode.LIE_DOWN -> ControlMode.CONTROL_MODE_LIE_DOWN
            RobotCtrlMode.STAND -> ControlMode.CONTROL_MODE_STAND_UP
        }
    }
}