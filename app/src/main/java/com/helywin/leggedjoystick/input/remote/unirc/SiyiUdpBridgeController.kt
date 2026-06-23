package com.helywin.leggedjoystick.input.remote.unirc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import timber.log.Timber

/**
 * 控制遥控器系统内置的 SIYI UDP 桥服务。
 *
 * 该服务在本机 `19856` 端口监听 UDP，并通过 `/dev/ttyHS3` 转发 UniRC SDK 帧。
 * 这里不接入厂商 SDK，只在本机 UDP 输入启用时尝试打开系统桥接服务。
 */
class SiyiUdpBridgeController(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    private var serviceBinder: IBinder? = null

    fun ensureBridgeOpen() {
        val binder = serviceBinder
        if (binder?.isBinderAlive == true) {
            transactOpenSerial(binder)
            return
        }

        synchronized(lock) {
            if (serviceConnection != null) return
        }

        val intent = Intent().setComponent(SERVICE_COMPONENT)
        try {
            appContext.startService(intent)
        } catch (e: Exception) {
            Timber.w(e, "[SIYI UDP] 启动系统 UDP 桥服务失败，继续尝试绑定")
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceBinder = service
                transactOpenSerial(service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceBinder = null
                clearConnection(this)
                Timber.w("[SIYI UDP] 系统 UDP 桥服务连接已断开")
            }

            override fun onBindingDied(name: ComponentName) {
                serviceBinder = null
                clearConnection(this)
                Timber.w("[SIYI UDP] 系统 UDP 桥服务绑定失效")
            }

            override fun onNullBinding(name: ComponentName) {
                serviceBinder = null
                clearConnection(this)
                Timber.w("[SIYI UDP] 系统 UDP 桥服务返回空绑定")
            }
        }

        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Timber.w(e, "[SIYI UDP] 绑定系统 UDP 桥服务失败")
            false
        }

        if (bound) {
            synchronized(lock) {
                serviceConnection = connection
            }
            Timber.i("[SIYI UDP] 已绑定系统 UDP 桥服务")
        } else {
            Timber.w("[SIYI UDP] 系统 UDP 桥服务绑定被拒绝")
        }
    }

    fun release() {
        val connection = synchronized(lock) {
            serviceConnection.also {
                serviceConnection = null
                serviceBinder = null
            }
        } ?: return

        try {
            appContext.unbindService(connection)
            Timber.i("[SIYI UDP] 已解绑系统 UDP 桥服务")
        } catch (e: Exception) {
            Timber.w(e, "[SIYI UDP] 解绑系统 UDP 桥服务失败")
        }
    }

    private fun transactOpenSerial(service: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeInt(1)
            val success = service.transact(TRANSACTION_SET_SERIAL_OPEN, data, reply, 0)
            if (success) {
                reply.readException()
                Timber.i("[SIYI UDP] 已请求打开本机 UDP 串口桥")
            } else {
                Timber.w("[SIYI UDP] 系统 UDP 桥服务拒绝打开串口桥")
            }
        } catch (e: Exception) {
            Timber.w(e, "[SIYI UDP] 请求打开本机 UDP 串口桥失败")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun clearConnection(connection: ServiceConnection) {
        synchronized(lock) {
            if (serviceConnection === connection) {
                serviceConnection = null
            }
        }
    }

    companion object {
        private val SERVICE_COMPONENT = ComponentName(
            "com.siyi.udpservice",
            "com.siyi.udpservice.UdpService"
        )
        private const val SERVICE_DESCRIPTOR = "com.siyi.udpservice.ISerialAidlInterface"
        private const val TRANSACTION_SET_SERIAL_OPEN = 1

        fun shouldUseForHost(host: String): Boolean {
            return host == "127.0.0.1" || host == "localhost" || host == "::1"
        }
    }
}
