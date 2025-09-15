/*********************************************************************************
 * FileName: ClientType.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 客户端类型枚举
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

/**
 * 客户端类型
 */
enum class ClientType(val value: String) {
    REMOTE_CONTROLLER("remote_controller"),  // 遥控器
    NAVIGATION("navigation")                 // 导航程序
}