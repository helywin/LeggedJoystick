/*********************************************************************************
 * FileName: ZmqRequest.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: ZMQ请求和响应数据类
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.zmq

import com.google.gson.JsonObject

/**
 * ZMQ请求数据类
 */
data class ZmqRequest(
    val command: String,
    val params: JsonObject? = null
)

/**
 * ZMQ响应数据类
 */
data class ZmqResponse(
    val success: Boolean = false,
    val message: String? = null,
    val result: Int? = null,
    val value: Int? = null,
    val values: List<Float>? = null,
    val mode: Int? = null,
    val connected: Boolean? = null
)