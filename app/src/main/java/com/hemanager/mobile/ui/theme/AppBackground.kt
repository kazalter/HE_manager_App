package com.hemanager.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush

/**
 * App 的标志性深色径向渐变背景。
 *
 * Login/Library/Creators 顶层容器都用这个背景，确保跨屏视觉一致。
 * 颜色当前是硬编码，未来如需主题变体可迁到 [HeColors]。
 */
@Composable
fun AppBackgroundBrush(): Brush {
    return Brush.radialGradient(
        listOf(
            HeColors.Yellow.copy(alpha = 0.032f),
            HeColors.Ink,
            HeColors.Void
        ),
        radius = 1250f
    )
}
