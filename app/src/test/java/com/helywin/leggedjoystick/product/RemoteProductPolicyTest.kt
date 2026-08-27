package com.helywin.leggedjoystick.product

import com.helywin.leggedjoystick.BuildConfig
import legged_driver.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProductPolicyTest {
    @Test
    fun flavorCapabilitiesAndApplicationId_areMutuallyConsistent() {
        when (RemoteProductPolicy.productType) {
            ProductType.PRODUCT_TYPE_GENERAL_ROBOT -> {
                assertEquals("com.helywin.leggedjoystick.general", BuildConfig.APPLICATION_ID)
                assertFalse(RemoteProductPolicy.controllerChannelEnabled)
                assertTrue(RemoteProductPolicy.appModeControlEnabled)
                assertFalse(RemoteProductPolicy.taskWorkspaceEnabled)
            }
            ProductType.PRODUCT_TYPE_SAR_LEGGED_ROBOT -> {
                assertEquals("com.helywin.leggedjoystick.sar", BuildConfig.APPLICATION_ID)
                assertTrue(RemoteProductPolicy.controllerChannelEnabled)
                assertFalse(RemoteProductPolicy.appModeControlEnabled)
                assertTrue(RemoteProductPolicy.taskWorkspaceEnabled)
            }
            else -> error("测试不支持未定义产品")
        }
    }
}
