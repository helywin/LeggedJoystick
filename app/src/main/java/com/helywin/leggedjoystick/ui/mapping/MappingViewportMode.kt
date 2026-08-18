package com.helywin.leggedjoystick.ui.mapping

internal enum class MappingViewportMode {
    WORKSPACE,
    FULLSCREEN;

    fun toggled(): MappingViewportMode = when (this) {
        WORKSPACE -> FULLSCREEN
        FULLSCREEN -> WORKSPACE
    }
}
