package com.hemanager.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

/**
 * App 的标志性深色纵深背景。
 *
 * 顶部深靛蓝天光微光晕染，向下平滑过渡至墨黑与虚空黑，
 * 彻底消除纯色平铺的单调素气，赋予全屏沉浸空间感。
 */
@Composable
fun AppBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to Color(0xFF131722),
        0.22f to HeColors.Ink,
        0.70f to Color(0xFF090B0F),
        1f to HeColors.Void,
    )
}
