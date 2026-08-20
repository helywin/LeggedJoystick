package com.helywin.leggedjoystick.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val OperatorDialogShape = RoundedCornerShape(20.dp)

/** 为需要自定义布局的 Dialog 提供与标准确认框一致的浮层表面。 */
@Composable
fun OperatorDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .widthIn(min = 420.dp, max = 520.dp)
            .border(1.dp, colors.outlineVariant, OperatorDialogShape),
        shape = OperatorDialogShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        content = content
    )
}

/**
 * 产品对话框的唯一视觉入口。
 *
 * 业务对话框只提供内容和动作，浮层颜色、文字层级、尺寸、圆角与描边由这里统一。
 */
@Composable
fun OperatorAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier
            .widthIn(min = 420.dp, max = 520.dp)
            .border(1.dp, colors.outlineVariant, OperatorDialogShape),
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { titleContent ->
            {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    titleContent()
                }
            }
        },
        text = text?.let { textContent ->
            {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    textContent()
                }
            }
        },
        shape = OperatorDialogShape,
        containerColor = colors.surfaceContainerHigh,
        iconContentColor = colors.primary,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        tonalElevation = 0.dp,
        properties = properties
    )
}
