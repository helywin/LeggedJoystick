package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCellClassifierTest {
    @Test
    fun `轻微着色的深色像素仍判定为占用区域`() {
        assertTrue(MapCellClassifier.isUnavailable(24, 28, 29))
    }

    @Test
    fun `灰色像素判定为未知区域`() {
        assertTrue(MapCellClassifier.isUnavailable(205, 205, 205))
    }

    @Test
    fun `初始位姿允许落在未知区域`() {
        assertFalse(MapCellClassifier.isUnavailable(205, 205, 205, allowUnknown = true))
    }

    @Test
    fun `初始位姿仍拒绝占用区域`() {
        assertTrue(MapCellClassifier.isUnavailable(20, 20, 20, allowUnknown = true))
    }

    @Test
    fun `浅色像素允许选点`() {
        assertFalse(MapCellClassifier.isUnavailable(248, 248, 248))
    }
}
