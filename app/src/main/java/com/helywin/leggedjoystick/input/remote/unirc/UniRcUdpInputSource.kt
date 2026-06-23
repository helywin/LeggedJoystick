package com.helywin.leggedjoystick.input.remote.unirc

import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class UniRcUdpInputConfig(
    val remoteHost: String = "192.168.144.20",
    val remotePort: Int = 19856,
    val localPort: Int = 0,
    val frequency: UniRcChannelFrequency = UniRcChannelFrequency.HZ_20,
    val subscribeRepeatCount: Int = 3,
    val receiveTimeoutMs: Int = 100,
    val resubscribeIntervalMs: Long = 1000L,
    val normalization: RemoteInputNormalizationConfig = RemoteInputNormalizationConfig()
)

class UniRcUdpInputSource(
    private val config: UniRcUdpInputConfig = UniRcUdpInputConfig()
) : RemoteInputSource {
    override val descriptor = RemoteInputSourceDescriptor(
        id = "unirc_udp",
        displayName = "UniRC UDP",
        transport = "udp"
    )

    private val running = AtomicBoolean(false)

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var future: Future<*>? = null

    override fun start(listener: RemoteInputListener) {
        if (!running.compareAndSet(false, true)) {
            Timber.w("[UniRC] UDP 输入源已经启动")
            return
        }

        listener.onStatusChanged(descriptor, RemoteInputStatus.STARTING)
        executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "UniRcUdpInput").apply {
                isDaemon = true
            }
        }
        future = executor?.submit { runLoop(listener) }
    }

    override fun stop() {
        running.set(false)
        future?.cancel(true)
        future = null
        socket?.close()
        socket = null

        executor?.shutdownNow()
        try {
            executor?.awaitTermination(800, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            executor = null
        }
    }

    private fun runLoop(listener: RemoteInputListener) {
        val remoteAddress = InetSocketAddress(config.remoteHost, config.remotePort)
        var lastFrameAtMs = 0L
        var lastSubscribeAtMs = 0L
        var timeoutEmitted = false

        try {
            DatagramSocket(config.localPort).use { currentSocket ->
                socket = currentSocket
                currentSocket.soTimeout = config.receiveTimeoutMs
                listener.onStatusChanged(descriptor, RemoteInputStatus.RUNNING)
                Timber.i(
                    "[UniRC] UDP 输入源启动，本地端口=%d，远端=%s:%d",
                    currentSocket.localPort,
                    config.remoteHost,
                    config.remotePort
                )

                sendSubscribeFrames(currentSocket, remoteAddress)
                lastSubscribeAtMs = System.currentTimeMillis()

                val buffer = ByteArray(512)
                while (running.get() && !Thread.currentThread().isInterrupted) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        currentSocket.receive(packet)

                        val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                        val frame = UniRcProtocol.parseChannelFrame(data)
                        val mapping = UniRcProtocol.mapMovement(frame.channels, config.normalization)
                        val snapshot = RemoteInputSnapshot(
                            descriptor = descriptor,
                            movementIntent = mapping.movementIntent,
                            rawChannels = frame.channels,
                            normalizedAxes = mapping.normalizedAxes,
                            sequence = frame.sequence,
                            receivedAtMs = System.currentTimeMillis()
                        )

                        lastFrameAtMs = snapshot.receivedAtMs
                        timeoutEmitted = false
                        listener.onSnapshot(snapshot)
                    } catch (e: SocketTimeoutException) {
                        val now = System.currentTimeMillis()
                        if (lastFrameAtMs > 0 && now - lastFrameAtMs > config.normalization.timeoutMs) {
                            if (!timeoutEmitted) {
                                listener.onStatusChanged(descriptor, RemoteInputStatus.TIMEOUT, "UniRC 输入超时")
                                listener.onSnapshot(createTimeoutSnapshot(now))
                                timeoutEmitted = true
                            }
                            if (now - lastSubscribeAtMs > config.resubscribeIntervalMs) {
                                sendSubscribeFrames(currentSocket, remoteAddress)
                                lastSubscribeAtMs = now
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        listener.onStatusChanged(descriptor, RemoteInputStatus.ERROR, e.message.orEmpty())
                        Timber.w(e, "[UniRC] 丢弃无效通道帧")
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                listener.onStatusChanged(descriptor, RemoteInputStatus.ERROR, e.message.orEmpty())
                Timber.e(e, "[UniRC] UDP 输入源异常退出")
            }
        } finally {
            running.set(false)
            socket = null
            listener.onStatusChanged(descriptor, RemoteInputStatus.STOPPED)
            Timber.i("[UniRC] UDP 输入源已停止")
        }
    }

    private fun sendSubscribeFrames(socket: DatagramSocket, remoteAddress: InetSocketAddress) {
        repeat(config.subscribeRepeatCount) { index ->
            val frame = UniRcProtocol.createChannelFrequencyFrame(index, config.frequency)
            socket.send(DatagramPacket(frame, frame.size, remoteAddress))
        }
        Timber.d("[UniRC] 已发送通道订阅请求，频率=%s", config.frequency)
    }

    private fun createTimeoutSnapshot(now: Long): RemoteInputSnapshot {
        return RemoteInputSnapshot(
            descriptor = descriptor,
            movementIntent = MovementIntent.ZERO,
            receivedAtMs = now
        )
    }
}
