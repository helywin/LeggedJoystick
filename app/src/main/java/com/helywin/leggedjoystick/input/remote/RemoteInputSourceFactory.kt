package com.helywin.leggedjoystick.input.remote

import android.content.Context
import android.os.Build
import com.helywin.leggedjoystick.input.remote.skydroid.SkydroidG20InputConfig
import com.helywin.leggedjoystick.input.remote.skydroid.SkydroidG20InputSource
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputConfig
import com.helywin.leggedjoystick.input.remote.unirc.UniRcUdpInputSource
import com.skydroid.rcsdk.common.DeviceType
import com.skydroid.rcsdk.utils.RCSDKUtils

data class RemoteControllerIdentity(
    val rcSdkDeviceType: String,
    val model: String,
    val boardVariant: String
)

data class RemoteInputSourceRequest(
    val applicationContext: Context,
    val normalization: RemoteInputNormalizationConfig,
    val uniRcConfig: UniRcUdpInputConfig
)

data class RemoteInputSourceSelection(
    val providerId: String,
    val identity: RemoteControllerIdentity,
    val source: RemoteInputSource
)

interface RemoteInputSourceProvider {
    val id: String
    fun supports(identity: RemoteControllerIdentity): Boolean
    fun create(request: RemoteInputSourceRequest): RemoteInputSource
}

object AndroidRemoteControllerIdentityReader {
    fun read(): RemoteControllerIdentity {
        return RemoteControllerIdentity(
            rcSdkDeviceType = runCatching { RCSDKUtils.getDeviceType().name }
                .getOrDefault(DeviceType.UNKNOWN.name),
            model = Build.MODEL.orEmpty(),
            boardVariant = runCatching {
                RCSDKUtils.getProperty(G20_BOARD_PROPERTY, "")
            }.getOrDefault("")
        )
    }

    private const val G20_BOARD_PROPERTY = "ro.boot.ZBBoard"
}

class RemoteInputSourceFactory(
    private val providers: List<RemoteInputSourceProvider> = listOf(
        SkydroidG20InputSourceProvider,
        UniRcUdpInputSourceProvider
    )
) {
    init {
        require(providers.isNotEmpty()) { "至少需要注册一个遥控输入 provider" }
    }

    fun selectProviderId(identity: RemoteControllerIdentity): String {
        return selectProvider(identity).id
    }

    fun create(
        identity: RemoteControllerIdentity,
        request: RemoteInputSourceRequest
    ): RemoteInputSourceSelection {
        val provider = selectProvider(identity)
        return RemoteInputSourceSelection(
            providerId = provider.id,
            identity = identity,
            source = provider.create(request)
        )
    }

    private fun selectProvider(identity: RemoteControllerIdentity): RemoteInputSourceProvider {
        return providers.firstOrNull { provider -> provider.supports(identity) }
            ?: error("没有可用的遥控输入 provider")
    }
}

private object SkydroidG20InputSourceProvider : RemoteInputSourceProvider {
    override val id: String = "skydroid_g20_rcsdk"

    override fun supports(identity: RemoteControllerIdentity): Boolean {
        return identity.rcSdkDeviceType == DeviceType.G20.name
    }

    override fun create(request: RemoteInputSourceRequest): RemoteInputSource {
        return SkydroidG20InputSource(
            applicationContext = request.applicationContext,
            config = SkydroidG20InputConfig(
                normalization = request.normalization.copy(
                    min = SkydroidG20InputConfig.CHANNEL_MIN,
                    center = SkydroidG20InputConfig.CHANNEL_CENTER,
                    max = SkydroidG20InputConfig.CHANNEL_MAX
                )
            )
        )
    }
}

private object UniRcUdpInputSourceProvider : RemoteInputSourceProvider {
    override val id: String = "unirc_udp"

    override fun supports(identity: RemoteControllerIdentity): Boolean = true

    override fun create(request: RemoteInputSourceRequest): RemoteInputSource {
        return UniRcUdpInputSource(request.uniRcConfig)
    }
}
