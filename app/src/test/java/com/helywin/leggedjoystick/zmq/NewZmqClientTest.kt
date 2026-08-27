package com.helywin.leggedjoystick.zmq

import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.proto.MessageUtils
import com.helywin.leggedjoystick.product.RemoteProductPolicy
import legged_driver.AppMode
import legged_driver.DeviceType
import legged_driver.LeggedDriverMessage
import legged_driver.MessageType
import legged_driver.SubscriptionTopic
import legged_driver.ConnectionState as DriverConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NewZmqClientTest {
    @Test
    fun connect_receivesHeartbeatAndSubscribesDefaultTopics() {
        val port = findFreePort()
        val server = TestRouterServer(port).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()

            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })
            val subscription = server.waitForMessage(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)

            assertEquals(MessageUtils.defaultStateTopics(), subscription.subscription_request?.topics)
            assertEquals(true, subscription.subscription_request?.subscribe)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun connect_whenServerRejectsAdmission_failsWithoutSubscribing() {
        val port = findFreePort()
        val server = TestRouterServer(
            port = port,
            admitted = false
        ).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()

            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTION_FAILED })
            assertTrue(!client.isAdmitted())
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun connect_whenInitialHeartbeatIsDropped_retriesHandshakeAndConnects() {
        val port = findFreePort()
        val server = TestRouterServer(
            port = port,
            ignoredHeartbeatsBeforeReply = 1
        ).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()

            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })
            val subscription = server.waitForEnvelope(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
            assertEquals(MessageUtils.defaultStateTopics(), subscription.message.subscription_request?.topics)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun connect_afterTimeoutCanCreateFreshAttemptAndRecover() {
        val failedPort = findFreePort()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$failedPort",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()
            assertTrue(waitUntil(timeoutMs = 3500L) {
                client.getConnectionState() == ConnectionState.CONNECTION_TIMEOUT
            })

            val serverPort = findFreePort()
            val server = TestRouterServer(serverPort).start()
            try {
                client.setEndpoint("tcp://127.0.0.1:$serverPort")
                client.connect()

                assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })
                assertEquals(
                    SubscriptionTopic.SUBSCRIPTION_TOPIC_ROBOT_STATE,
                    server.waitForMessage(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
                        .subscription_request
                        ?.topics
                        ?.firstOrNull { it == SubscriptionTopic.SUBSCRIPTION_TOPIC_ROBOT_STATE }
                )
            } finally {
                server.close()
            }
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun connect_afterDisconnectCanReconnectToSameServer() {
        val port = findFreePort()
        val server = TestRouterServer(port).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })
            server.waitForMessage(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)

            client.disconnect()
            assertEquals(ConnectionState.DISCONNECTED, client.getConnectionState())

            client.connect()
            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })
            server.waitForMessage(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun disconnect_sendsClientDisconnectBeforeClosingSocket() {
        val port = findFreePort()
        val server = TestRouterServer(port).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()
            val firstSubscription = server.waitForEnvelope(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })

            client.disconnect()

            val disconnect = server.waitForEnvelope(MessageType.MESSAGE_TYPE_CLIENT_DISCONNECT)
            assertEquals(firstSubscription.identity, disconnect.identity)
            assertEquals(firstSubscription.identity, disconnect.message.device_id)
            assertEquals("normal_disconnect", disconnect.message.client_disconnect?.reason)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    @Test
    fun connect_afterDisconnectUsesFreshDealerIdentity() {
        val port = findFreePort()
        val server = TestRouterServer(port).start()
        val client = NewZmqClient(
            tcpEndpoint = "tcp://127.0.0.1:$port",
            heartbeatIntervalMs = 100L
        )

        try {
            client.connect()
            val firstSubscription = server.waitForEnvelope(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })

            client.disconnect()
            assertEquals(ConnectionState.DISCONNECTED, client.getConnectionState())

            client.connect()
            val secondSubscription = server.waitForEnvelope(MessageType.MESSAGE_TYPE_SUBSCRIPTION_REQUEST)
            assertTrue(waitUntil { client.getConnectionState() == ConnectionState.CONNECTED })

            assertEquals(firstSubscription.identity, firstSubscription.message.device_id)
            assertEquals(secondSubscription.identity, secondSubscription.message.device_id)
            assertNotEquals(firstSubscription.identity, secondSubscription.identity)
        } finally {
            client.disconnect()
            server.close()
        }
    }

    private class TestRouterServer(
        private val port: Int,
        private val admitted: Boolean = true,
        private val ignoredHeartbeatsBeforeReply: Int = 0
    ) : AutoCloseable {
        private val running = AtomicBoolean(false)
        private val ready = CountDownLatch(1)
        private val messages = LinkedBlockingQueue<ReceivedMessage>()
        private var remainingIgnoredHeartbeats = ignoredHeartbeatsBeforeReply
        private var thread: Thread? = null

        fun start(): TestRouterServer {
            running.set(true)
            thread = Thread(::runLoop, "TestZmqRouter-$port").apply {
                isDaemon = true
                start()
            }
            assertTrue("测试 ZMQ ROUTER 未按时启动", ready.await(2, TimeUnit.SECONDS))
            return this
        }

        fun waitForMessage(messageType: MessageType): LeggedDriverMessage {
            return waitForEnvelope(messageType).message
        }

        fun waitForEnvelope(messageType: MessageType): ReceivedMessage {
            val deadline = System.currentTimeMillis() + 2500L
            while (System.currentTimeMillis() < deadline) {
                val envelope = messages.poll(50, TimeUnit.MILLISECONDS) ?: continue
                if (envelope.message.message_type == messageType) {
                    return envelope
                }
            }
            throw AssertionError("未收到消息: $messageType")
        }

        override fun close() {
            running.set(false)
            thread?.join(1000L)
        }

        private fun runLoop() {
            val context = ZContext()
            val socket = context.createSocket(SocketType.ROUTER)
            socket.receiveTimeOut = 50

            try {
                socket.bind("tcp://127.0.0.1:$port")
                ready.countDown()

                while (running.get()) {
                    val identity = socket.recv(0) ?: continue
                    val payload = socket.recv(0) ?: continue
                    val message = MessageUtils.deserializeMessage(payload)
                    if (MessageUtils.verifyMessage(message)) {
                        messages.offer(
                            ReceivedMessage(
                                identity = String(identity, Charsets.UTF_8),
                                message = message
                            )
                        )
                    }
                    when (message.message_type) {
                        MessageType.MESSAGE_TYPE_HEARTBEAT -> {
                            if (remainingIgnoredHeartbeats > 0) {
                                remainingIgnoredHeartbeats -= 1
                            } else {
                                sendHeartbeatSnapshot(socket, identity)
                            }
                        }
                        else -> Unit
                    }
                }
            } finally {
                socket.close()
                context.close()
                ready.countDown()
            }
        }

        private fun sendHeartbeatSnapshot(socket: ZMQ.Socket, identity: ByteArray) {
            socket.sendMore(identity)
            socket.send(
                MessageUtils.serializeMessage(
                    MessageUtils.createHeartbeatMessage(
                        deviceType = DeviceType.DEVICE_TYPE_SERVER,
                        deviceId = "test_server",
                        productType = RemoteProductPolicy.productType,
                        protocolVersion = RemoteProductPolicy.PROTOCOL_VERSION,
                        admitted = admitted,
                        admissionMessage = if (admitted) "准入成功" else "产品不匹配",
                        robotConnected = true,
                        connectionState = DriverConnectionState.CONNECTION_STATE_CONNECTED,
                        appMode = AppMode.APP_MODE_MANUAL
                    )
                ),
                ZMQ.NOBLOCK
            )
        }
    }

    private data class ReceivedMessage(
        val identity: String,
        val message: LeggedDriverMessage
    )

    private companion object {
        fun findFreePort(): Int {
            return ServerSocket(0).use { it.localPort }
        }

        fun waitUntil(timeoutMs: Long = 2500L, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(20L)
            }
            return condition()
        }
    }
}
