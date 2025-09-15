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
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import timber.log.Timber
import java.nio.charset.StandardCharsets

/**
 * HighLevel ZMQ客户端
 */
class HighLevelZmqClient(
    private val clientType: ClientType,
    private val tcpEndpoint: String = DEFAULT_TCP_ENDPOINT
) {
    companion object {
        private const val DEFAULT_TCP_ENDPOINT = "tcp://127.0.0.1:33445"
    }

    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var connected: Boolean = false
    private val gson = Gson()

    /**
     * 连接到服务端
     */
    fun connect(): Boolean {
        if (connected) {
            return true
        }

        try {
            context = ZContext()
            socket = context?.createSocket(SocketType.DEALER)
            
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
    fun setMode(mode: String): Boolean {
        if (clientType != ClientType.REMOTE_CONTROLLER) {
            Timber.e("[HighLevelZmqClient] 只有遥控器客户端可以设置模式")
            return false
        }

        val request = JsonObject().apply {
            addProperty("command", "setMode")
            add("params", JsonObject().apply {
                addProperty("mode", mode)
            })
        }
        val response = sendRequest(request)
        return response?.success == true
    }

    /**
     * 获取当前模式
     */
    fun getCurrentMode(): String {
        val request = JsonObject().apply {
            addProperty("command", "getCurrentMode")
        }
        val response = sendRequest(request)
        return response?.mode ?: "auto"
    }

    /**
     * 发送请求并接收响应
     */
    private fun sendRequest(request: JsonObject): ZmqResponse? {
        if (!connected) {
            return ZmqResponse(success = false, message = "Not connected to service")
        }

        return try {
            val requestStr = gson.toJson(request)
            socket?.send(requestStr.toByteArray(StandardCharsets.UTF_8))

            val replyBytes = socket?.recv()
            if (replyBytes != null) {
                val replyStr = String(replyBytes, StandardCharsets.UTF_8)
                gson.fromJson(replyStr, ZmqResponse::class.java)
            } else {
                ZmqResponse(success = false, message = "No response received")
            }
        } catch (e: Exception) {
            Timber.e(e, "[HighLevelZmqClient] 请求失败")
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

    /**
     * 初始化机器人
     */
    fun initRobot(localIp: String, localPort: Int, dogIp: String = "192.168.234.1"): Boolean {
        val request = JsonObject().apply {
            addProperty("command", "initRobot")
            add("params", JsonObject().apply {
                addProperty("local_ip", localIp)
                addProperty("local_port", localPort)
                addProperty("dog_ip", dogIp)
            })
        }
        val response = sendRequest(request)
        return response?.success == true
    }

    /**
     * 去初始化机器人
     */
    fun deinitRobot(): Boolean {
        val request = JsonObject().apply {
            addProperty("command", "deinitRobot")
        }
        val response = sendRequest(request)
        return response?.success == true
    }

    /**
     * 检查连接状态
     */
    fun checkConnect(): Boolean {
        val request = JsonObject().apply {
            addProperty("command", "checkConnect")
        }
        val response = sendRequest(request)
        return response?.connected == true
    }

    /**
     * 站立
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

    /**
     * 握手
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
    fun getQuaternion(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getQuaternion")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 1.0f) // 默认四元数
    }

    /**
     * 获取欧拉角（Roll, Pitch, Yaw）
     */
    fun getRPY(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getRPY")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认RPY
    }

    /**
     * 获取机身加速度
     */
    fun getBodyAcc(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getBodyAcc")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认加速度
    }

    /**
     * 获取机身角速度
     */
    fun getBodyGyro(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getBodyGyro")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认角速度
    }

    /**
     * 获取位置
     */
    fun getPosition(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getPosition")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认位置
    }

    /**
     * 获取世界坐标系速度
     */
    fun getWorldVelocity(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getWorldVelocity")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认世界速度
    }

    /**
     * 获取机身坐标系速度
     */
    fun getBodyVelocity(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getBodyVelocity")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f) // 默认机身速度
    }

    // ========== 关节数据查询接口 ==========

    /**
     * 获取腿部Abad关节角度
     */
    fun getLegAbadJoint(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJoint")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节角度
     */
    fun getLegHipJoint(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegHipJoint")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节角度
     */
    fun getLegKneeJoint(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJoint")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Abad关节角速度
     */
    fun getLegAbadJointVel(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJointVel")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节角速度
     */
    fun getLegHipJointVel(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegHipJointVel")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节角速度
     */
    fun getLegKneeJointVel(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJointVel")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Abad关节扭矩
     */
    fun getLegAbadJointTorque(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegAbadJointTorque")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Hip关节扭矩
     */
    fun getLegHipJointTorque(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegHipJointTorque")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    /**
     * 获取腿部Knee关节扭矩
     */
    fun getLegKneeJointTorque(): List<Float> {
        val request = JsonObject().apply {
            addProperty("command", "getLegKneeJointTorque")
        }
        val response = sendRequest(request)
        return response?.values ?: listOf(0.0f, 0.0f, 0.0f, 0.0f) // 4条腿默认值
    }

    // ========== 状态查询接口 ==========

    /**
     * 获取当前控制模式
     */
    fun getCurrentCtrlmode(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "getCurrentCtrlmode")
        }
        val response = sendRequest(request)
        return response?.value?.toUInt() ?: 0u
    }

    /**
     * 获取电池电量
     */
    fun getBatteryPower(): UInt {
        val request = JsonObject().apply {
            addProperty("command", "getBatteryPower")
        }
        val response = sendRequest(request)
        return response?.value?.toUInt() ?: 0u
    }
}