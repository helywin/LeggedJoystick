package com.helywin.leggedjoystick.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContractTest {
    @Test
    fun operatorColorSchemeKeepsDarkSurfacesAndReadableText() {
        assertEquals(OperatorBackground, OperatorColorScheme.background)
        assertEquals(OperatorSurfaceFloating, OperatorColorScheme.surfaceContainerHigh)
        assertEquals(OperatorPrimary, OperatorColorScheme.primary)
        assertTrue(OperatorColorScheme.background.luminance() < 0.02f)
        assertTrue(contrastRatio(
            OperatorColorScheme.background,
            OperatorColorScheme.onBackground
        ) >= 7.0f)
        assertTrue(contrastRatio(
            OperatorColorScheme.surfaceContainerHigh,
            OperatorColorScheme.onSurface
        ) >= 7.0f)
        assertTrue(contrastRatio(
            OperatorColorScheme.primary,
            OperatorColorScheme.onPrimary
        ) >= 4.5f)
    }

    @Test
    fun operatorTypographyDefinesStableChineseInterfaceHierarchy() {
        assertEquals(22.sp, OperatorTypography.titleLarge.fontSize)
        assertEquals(FontWeight.SemiBold, OperatorTypography.titleLarge.fontWeight)
        assertEquals(14.sp, OperatorTypography.bodyMedium.fontSize)
        assertEquals(14.sp, OperatorTypography.labelLarge.fontSize)
        assertEquals(FontWeight.Medium, OperatorTypography.labelLarge.fontWeight)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
