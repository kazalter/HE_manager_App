package com.hemanager.mobile.player.model

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * 画面比例模式。
 *
 * 每个模式必须对应一个**不同**的 [AspectRatioFrameLayout] resize mode——旧版里
 * FILL 和 STRETCH 都映射到 RESIZE_MODE_FILL、ORIGINAL 映射到 FIXED_WIDTH 却叫
 * "原始比例"，用户在面板里切换时看不出区别。现在五个模式各自唯一：
 *
 *  - [FIT]          等比缩放，完整显示（默认）
 *  - [CROP]         等比放大铺满，超出部分裁掉
 *  - [STRETCH]      不保持比例强行拉伸铺满
 *  - [FIXED_WIDTH]  宽度对齐屏幕，高度按原比例（上下可能溢出）
 *  - [FIXED_HEIGHT] 高度对齐屏幕，宽度按原比例（左右可能溢出）
 */
enum class AspectMode(val label: String, val resizeMode: Int) {
    FIT("适应屏幕", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    CROP("裁剪填充", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("拉伸铺满", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIXED_WIDTH("宽度铺满", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT("高度铺满", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);

    companion object {
        /** 未知/历史值（旧的 "FILL" / "ORIGINAL"）一律回落到 [FIT]。 */
        fun fromName(name: String?): AspectMode =
            entries.firstOrNull { it.name == name } ?: FIT
    }
}
