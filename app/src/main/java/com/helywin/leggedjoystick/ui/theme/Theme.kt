package com.helywin.leggedjoystick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 遥控器固定深色主题。
 *
 * 产品界面叠加在视频和地图上，不能跟随系统浅色模式或动态取色，否则 Material
 * 默认文字、卡片和对话框会与深色工作区失去对比。
 */
internal val OperatorColorScheme = darkColorScheme(
    primary = OperatorPrimary,
    onPrimary = OperatorOnPrimary,
    primaryContainer = OperatorPrimaryContainer,
    onPrimaryContainer = OperatorOnPrimaryContainer,
    inversePrimary = OperatorPrimary,
    secondary = OperatorSecondary,
    onSecondary = OperatorOnSecondary,
    secondaryContainer = OperatorSecondaryContainer,
    onSecondaryContainer = OperatorOnSecondaryContainer,
    tertiary = OperatorWarning,
    onTertiary = OperatorOnWarning,
    background = OperatorBackground,
    onBackground = OperatorOnBackground,
    surface = OperatorSurface,
    onSurface = OperatorOnSurface,
    surfaceVariant = OperatorSurfaceContainer,
    onSurfaceVariant = OperatorOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = OperatorOnSurface,
    inverseOnSurface = OperatorBackground,
    error = OperatorError,
    onError = OperatorOnError,
    errorContainer = OperatorErrorContainer,
    onErrorContainer = OperatorOnErrorContainer,
    outline = OperatorOutline,
    outlineVariant = OperatorOutlineVariant,
    scrim = Color.Black,
    surfaceBright = OperatorSurfaceHighest,
    surfaceDim = OperatorBackground,
    surfaceContainer = OperatorSurfaceContainer,
    surfaceContainerHigh = OperatorSurfaceFloating,
    surfaceContainerHighest = OperatorSurfaceHighest,
    surfaceContainerLow = OperatorSurfaceLow,
    surfaceContainerLowest = OperatorBackground
)

@Composable
fun LeggedJoystickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OperatorColorScheme,
        typography = OperatorTypography,
        content = content
    )
}
