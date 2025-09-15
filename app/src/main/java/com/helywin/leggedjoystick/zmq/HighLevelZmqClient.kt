/*********************************************************************************
 * FileName: HighLevelZmqClient.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: HighLevel ZMQ服务的客户端实现
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.helywin.leggedjoystick.data.RobotCtrlMode
import com.helywin.leggedjoystick.data.RobotMode
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import timber.log.Timber
import java.nio.charset.StandardCharsets

/**
 * HighLevel ZMQ客户端
 */
class HighLevelZmqClient {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
    }

    private var clientType: ClientType = ClientType.REMOTE_CONTROLLER
    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var connected: Boolean = false
    private val gson = Gson()

    /**
     * 连接到服务端
     */
    fun connect(
        clientType: ClientType = ClientType.REMOTE_CONTROLLER,
        tcpEndpoint: String = DEFAULT_TCP_ENDPOINT
    ): Boolean {
        if (connected) {
            return true
        }
        this.clientType = clientType
        try {
            context = ZContext()
            socket = context?.createSocket(SocketType.DEALER)

            // 设置接收超时（毫秒）
            socket?.receiveTimeOut = 5000 // 5秒超时

            // 设置唯一的客户端标识
            val clientTypeStr = if (clientType == ClientType.REMOTE_CONTROLLER) "rc" else "nav"
            val identity = "${clientTypeStr}_${System.currentTimeMillis()}"
            socket?.identity = identity.toByteArray(StandardCharsets.UTF_8)

            socket?.connect(tcpEndpoint)
            connected = true

            // 发送注册信息
            val registerRequest = JsonObject().apply {
                addProperty("command", "register")
                add("params", JsonObject().apply {
                    addProperty("client_type", clientType.value)
                })
            }

            val response = sendRequest(registerRequest)
            if (response?.success != true) {
                Timber.e("[HighLevelZmqClient] 客户端注册失败: ${response?.message ?: "unknown error"}")
                disconnect()
                return false
            }

            Timber.i("[HighLevelZmqClient] 连接到服务端: $tcpEndpoint")
            return true
        } catch (e: Exception) {
            Timber.e(e, "[HighLevelZmqClient] 连接失败")
            connected = false
            return false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        if (connected) {
            connected = false
            socket?.close()
            context?.close()
            socket = null
            context = null
            Timber.i("[HighLevelZmqClient] 已断开连接")
        }
    }

    /**
     * 发送心跳
     */
    fun sendHeartbeat(): Boolean {
        val request = JsonObject().apply {
            addProperty("command", "heartbeat")
        }
        val response = sendRequest(request)
        return response?.success == true
    }

    /**
     * 设置模式（只有遥控器客户端可以调用）
     */
    fun setMode(mode: RobotMode): Boolean {
        if (clientType != ClientType.REMOTE_CONTROLLER) {
            Timber.e("[HighLevelZmqClient] 只有遥控器客户端可以设置模式")
            return false
        }

        val request = JsonObject().apply {
            addProperty("command", "setMode")
            add("params", JsonObject().apply {
                addProperty("mode", mode.ordinal)
            })
        }
        val response = sendRequest(request)
        return response?.success == true
    }

    /**
     * 获取当前模式
     */
    fun getCurrentMode(): RobotMode? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取当前模式")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getCurrentMode")
        }
        val response = sendRequest(request)
        if (response?.success != true) {
            Timber.w("[HighLevelZmqClient] 获取当前模式失败: ${response?.message}")
            return null
        }

        return when (response.mode) {
            RobotMode.MANUAL.ordinal -> RobotMode.MANUAL
            RobotMode.AUTO.ordinal -> RobotMode.AUTO
            else -> {
                Timber.w("[HighLevelZmqClient] 未知的机器人模式: ${response.mode}")
                null
            }
        }
    }

    /**
     * 发送请求并接收响应
     */
    private fun sendRequest(request: JsonObject): ZmqResponse? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 尝试发送请求但连接已断开")
            return ZmqResponse(success = false, message = "Not connected to service")
        }

        if (socket == null || context == null) {
            Timber.w("[HighLevelZmqClient] Socket或Context为null，连接已无效")
            connected = false
            return ZmqResponse(success = false, message = "Connection is invalid")
        }

        return try {
            val requestStr = gson.toJson(request)
            socket?.send(requestStr.toByteArray(StandardCharsets.UTF_8))

            val replyBytes = socket?.recv()
            if (replyBytes != null) {
                val replyStr = String(replyBytes, StandardCharsets.UTF_8)
                gson.fromJson(replyStr, ZmqResponse::class.java)
            } else {
                Timber.w("[HighLevelZmqClient] 未收到响应，可能连接已断开")
                // 未收到响应可能表示连接已断开
                connected = false
                ZmqResponse(success = false, message = "No response received")
            }
        } catch (e: Exception) {
            Timber.e(e, "[HighLevelZmqClient] 请求失败，可能连接已断开")
            // 请求异常时标记连接为断开状态
            connected = false
            ZmqResponse(success = false, message = "Request failed: ${e.message}")
        }
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = connected

    /**
     * 获取客户端类型
     */
    fun getClientType(): ClientType = clientType

    // ========== 基础控制接口 ==========
    // 注意：以下控制方法返回UInt值，0表示成功，非0表示错误代码

    /**
     * 检查连接状态
     */
    fun checkConnect(): Boolean {
        if (!connected || socket == null || context == null) {
            return false
        }

        val request = JsonObject().apply {
            addProperty("command", "checkConnect")
        }
        val response = sendRequest(request)
        val isConnected = response?.connected == true

        if (!isConnected) {
            Timber.w("[HighLevelZmqClient] 服务器报告连接已断开")
            connected = false
        }

        return isConnected
    }

    /**
     * 执行连接状态检查（更强健的检查）
     */
    fun performHealthCheck(): Boolean {
        if (!connected || socket == null || context == null) {
            Timber.w("[HighLevelZmqClient] 本地连接状态检查失败")
            return false
        }

        return try {
            // 尝试发送一个轻量级的检查请求
            val request = JsonObject().apply {
                addProperty("command", "healthCheck")
            }

            // 使用较短的超时进行健康检查
            val originalTimeout = socket?.receiveTimeOut ?: 5000
            socket?.receiveTimeOut = 2000 // 2秒超时

            val response = sendRequest(request)

            // 恢复原来的超时设置
            socket?.receiveTimeOut = originalTimeout

            val isHealthy = response?.success == true || response?.connected == true
            if (!isHealthy) {
                Timber.w("[HighLevelZmqClient] 健康检查失败")
                connected = false
            }

            isHealthy
        } catch (e: Exception) {
            Timber.e(e, "[HighLevelZmqClient] 健康检查异常")
            connected = false
            false
        }
    }

    /**
     * 站立
     * @return 0表示成功，非0表示错误代码
     */
    fun standUp(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "standUp")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 趴下
     * @return 0表示成功，非0表示错误代码
     */
    fun lieDown(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "lieDown")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 被动模式
     * @return 0表示成功，非0表示错误代码
     */
    fun passive(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "passive")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 移动控制
     * @return 0表示成功，非0表示错误代码
     */
    fun move(vx: Float, vy: Float, yawRate: Float): UInt {
        val request = JsonObject().apply {
            addProperty("command", "move")
            add("params", JsonObject().apply {
                addProperty("vx", vx)
                addProperty("vy", vy)
                addProperty("yaw_rate", yawRate)
            })
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 跳跃
     * @return 0表示成功，非0表示错误代码
     */
    fun jump(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "jump")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 前空翻
     * @return 0表示成功，非0表示错误代码
     */
    fun frontJump(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "frontJump")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 后空翻
     * @return 0表示成功，非0表示错误代码
     */
    fun backflip(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "backflip")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 姿态控制
     * @return 0表示成功，非0表示错误代码
     */
    fun attitudeControl(rollVel: Float, pitchVel: Float, yawVel: Float, heightVel: Float): UInt {
        val request = JsonObject().apply {
            addProperty("command", "attitudeControl")
            add("params", JsonObject().apply {
                addProperty("roll_vel", rollVel)
                addProperty("pitch_vel", pitchVel)
                addProperty("yaw_vel", yawVel)
                addProperty("height_vel", heightVel)
            })
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    // ========== 高级动作接口 ==========
    // 注意：以下高级动作方法返回UInt值，0表示成功，非0表示错误代码

    /**
     * 握手
     * @return 0表示成功，非0表示错误代码
     */
    fun shakeHand(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "shakeHand")
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 双腿站立
     * @return 0表示成功，非0表示错误代码
     */
    fun twoLegStand(vx: Float = 0.0f, yawRate: Float = 0.0f): UInt {
        val request = JsonObject().apply {
            addProperty("command", "twoLegStand")
            add("params", JsonObject().apply {
                addProperty("vx", vx)
                addProperty("yaw_rate", yawRate)
            })
        }
        val response = sendRequest(request)
        return response?.result?.toUInt() ?: 0u
    }

    /**
     * 取消双腿站立
     */
    fun cancelTwoLegStand() {
        val request = JsonObject().apply {
            addProperty("command", "cancelTwoLegStand")
        }
        sendRequest(request)
    }

    // ========== 传感器数据查询接口 ==========

    /**
     * 获取四元数
     */
    fun getQuaternion(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取四元数")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getQuaternion")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取四元数失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取欧拉角（Roll, Pitch, Yaw）
     */
    fun getRPY(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取欧拉角")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getRPY")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取欧拉角失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取机身加速度
     */
    fun getBodyAcc(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取机身加速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getBodyAcc")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取机身加速度失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取机身角速度
     */
    fun getBodyGyro(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取机身角速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getBodyGyro")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取机身角速度失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取位置
     */
    fun getPosition(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取位置")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getPosition")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取位置失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取世界坐标系速度
     */
    fun getWorldVelocity(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取世界坐标系速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getWorldVelocity")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取世界坐标系速度失败: ${response?.message}")
            return null
        }

        return response.values
    }

    /**
     * 获取机身坐标系速度
     */
    fun getBodyVelocity(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取机身坐标系速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getBodyVelocity")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取机身坐标系速度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认机身速度
    }

    // ========== 关节数据查询接口 ==========

    /**
     * 获取腿部Abad关节角度
     */
    fun getLegAbadJoint(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Abad关节角度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJoint")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Abad关节角度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节角度
     */
    fun getLegHipJoint(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Hip关节角度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegHipJoint")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Hip关节角度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节角度
     */
    fun getLegKneeJoint(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Knee关节角度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJoint")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Knee关节角度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Abad关节角速度
     */
    fun getLegAbadJointVel(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Abad关节角速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJointVel")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Abad关节角速度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节角速度
     */
    fun getLegHipJointVel(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Hip关节角速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegHipJointVel")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Hip关节角速度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节角速度
     */
    fun getLegKneeJointVel(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Knee关节角速度")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJointVel")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Knee关节角速度失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Abad关节扭矩
     */
    fun getLegAbadJointTorque(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Abad关节扭矩")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJointTorque")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Abad关节扭矩失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节扭矩
     */
    fun getLegHipJointTorque(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Hip关节扭矩")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegHipJointTorque")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Hip关节扭矩失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节扭矩
     */
    fun getLegKneeJointTorque(): List<Float>? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取腿部Knee关节扭矩")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJointTorque")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.values == null) {
            Timber.w("[HighLevelZmqClient] 获取腿部Knee关节扭矩失败: ${response?.message}")
            return null
        }

        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    // ========== 状态查询接口 ==========

    /**
     * 获取当前控制模式
     */
    fun getCurrentCtrlmode(): RobotCtrlMode? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取当前控制模式")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getCurrentCtrlmode")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.value == null) {
            Timber.w("[HighLevelZmqClient] 获取当前控制模式失败: ${response?.message}")
            return null
        }

        return when (response?.value?.toUInt()) {
            0U, 10U -> RobotCtrlMode.PASSIVE
            18U -> RobotCtrlMode.STAND
            51U -> RobotCtrlMode.LIE_DOWN
            else -> {
                Timber.w("[HighLevelZmqClient] 未知的机器人控制模式: ${response?.value}")
                null
            }
        }
    }

    /**
     * 获取电池电量
     */
    fun getBatteryPower(): UInt? {
        if (!connected) {
            Timber.w("[HighLevelZmqClient] 连接已断开，无法获取电池电量")
            return null
        }

        val request = JsonObject().apply {
            addProperty("command", "getBatteryPower")
        }
        val response = sendRequest(request)
        if (response?.success != true && response?.value == null) {
            Timber.w("[HighLevelZmqClient] 获取电池电量失败: ${response?.message}")
            return null
        }

        return response?.value?.toUInt()
    }
}