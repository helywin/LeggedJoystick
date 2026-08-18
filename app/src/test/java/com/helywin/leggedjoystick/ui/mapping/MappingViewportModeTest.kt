package com.helywin.leggedjoystick.ui.mapping

import org.junit.Assert.assertEquals
import org.junit.Test

class MappingViewportModeTest {
    @Test
    fun `地图视图连续点击时在工作区与全屏之间切换`() {
        val fullscreen = MappingViewportMode.WORKSPACE.toggled()
        val restored = fullscreen.toggled()

        assertEquals(MappingViewportMode.FULLSCREEN, fullscreen)
        assertEquals(MappingViewportMode.WORKSPACE, restored)
    }
}
