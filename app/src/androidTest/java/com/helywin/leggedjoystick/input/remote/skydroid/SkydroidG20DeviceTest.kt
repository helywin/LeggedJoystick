package com.helywin.leggedjoystick.input.remote.skydroid

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.helywin.leggedjoystick.input.remote.AndroidRemoteControllerIdentityReader
import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SkydroidG20DeviceTest {
    @Test
    fun device_isDetectedAsG20AndProducesChannelSnapshot() {
        val identity = AndroidRemoteControllerIdentityReader.read()
        assertEquals("G20", identity.rcSdkDeviceType)
        assertEquals("Bengal for arm64", identity.model)
        assertEquals("0", identity.boardVariant)

        val snapshotLatch = CountDownLatch(1)
        val snapshotRef = AtomicReference<RemoteInputSnapshot>()
        val lastStatusRef = AtomicReference<String>()
        val source = SkydroidG20InputSource(
            applicationContext = ApplicationProvider.getApplicationContext(),
            config = SkydroidG20InputConfig(
                normalization = SkydroidG20InputConfig().normalization.copy(
                    timeoutMs = 1_000L
                )
            )
        )

        try {
            source.start(
                object : RemoteInputListener {
                    override fun onStatusChanged(
                        descriptor: RemoteInputSourceDescriptor,
                        status: RemoteInputStatus,
                        message: String
                    ) {
                        lastStatusRef.set("$status: $message")
                        Timber.i("[G20真机测试] 输入状态=%s, 信息=%s", status, message)
                    }

                    override fun onSnapshot(snapshot: RemoteInputSnapshot) {
                        snapshotRef.set(snapshot)
                        Timber.i("[G20真机测试] 通道=%s", snapshot.rawChannels)
                        snapshotLatch.countDown()
                    }
                }
            )

            assertTrue(
                "10 秒内未收到 G20 通道快照，最后状态=${lastStatusRef.get()}",
                snapshotLatch.await(10, TimeUnit.SECONDS)
            )
            val snapshot = snapshotRef.get()
            assertNotNull(snapshot)
            assertEquals(SkydroidG20InputConfig.CHANNEL_COUNT, snapshot.rawChannels.size)
            assertTrue(
                snapshot.rawChannels.all { value ->
                    value in SkydroidG20InputConfig.CHANNEL_MIN..SkydroidG20InputConfig.CHANNEL_MAX
                }
            )
            assertEquals(null, snapshot.speedLevelRequest)
        } finally {
            source.stop()
        }
    }
}
