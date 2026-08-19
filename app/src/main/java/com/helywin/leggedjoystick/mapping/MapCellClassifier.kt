package com.helywin.leggedjoystick.mapping

import kotlin.math.abs

object MapCellClassifier {
    fun isUnavailable(
        red: Int,
        green: Int,
        blue: Int,
        allowUnknown: Boolean = false
    ): Boolean {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "地图像素颜色分量无效"
        }
        val occupied = maxOf(red, green, blue) < 80
        val grayscale = abs(red - green) <= 5 && abs(red - blue) <= 5
        val unknown = grayscale && red in 180..230
        return occupied || (!allowUnknown && unknown)
    }
}
