package com.helywin.leggedjoystick.product

import com.helywin.leggedjoystick.BuildConfig
import legged_driver.ProductType

/**
 * 遥控器产品能力的唯一入口。产品差异全部由 Gradle flavor 注入，业务代码不得自行猜测。
 */
object RemoteProductPolicy {
    const val PROTOCOL_VERSION: Int = 2

    val productType: ProductType = when (BuildConfig.PRODUCT_TYPE) {
        "GENERAL_ROBOT" -> ProductType.PRODUCT_TYPE_GENERAL_ROBOT
        "SAR_LEGGED_ROBOT" -> ProductType.PRODUCT_TYPE_SAR_LEGGED_ROBOT
        else -> error("未知遥控器产品类型: ${BuildConfig.PRODUCT_TYPE}")
    }

    val controllerChannelEnabled: Boolean = BuildConfig.CONTROLLER_CHANNEL_ENABLED
    val appModeControlEnabled: Boolean = BuildConfig.APP_MODE_CONTROL_ENABLED
    val taskWorkspaceEnabled: Boolean = BuildConfig.TASK_WORKSPACE_ENABLED
}
