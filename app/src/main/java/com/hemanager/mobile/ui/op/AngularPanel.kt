package com.hemanager.mobile.ui.op

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.hemanager.mobile.ui.theme.Corner
import com.hemanager.mobile.ui.theme.CutCornerShape
import com.hemanager.mobile.ui.theme.HeColors

import androidx.compose.ui.graphics.Brush

/**
 * HE OP 主面板容器：现代圆角 + 表面微光渐变 + 顶边高光 rim hairline 描边。
 *
 * 通过顶部到深处的微妙渐变（PanelLight → Panel）配合顶边缘集光描边，
 * 解决原本整块纯色深灰造成的「UI 太素、像平铺贴纸」的视觉痛点。
 *
 * @param cut 圆角尺寸（dp）
 * @param corners 哪些角切角/圆角
 * @param background 背景纯色（默认 HeColors.Panel，此时自动启用高级微光渐变）
 * @param brush 自定义渐变笔刷（非空时优先使用）
 * @param hairline true 时绘制高质感双色 rim 描边
 */
@Composable
fun AngularPanel(
    modifier: Modifier = Modifier,
    cut: Dp = 18.dp,
    corners: Set<Corner> = setOf(Corner.TL, Corner.TR, Corner.BR, Corner.BL),
    background: Color = HeColors.Panel,
    brush: Brush? = null,
    yellowCorner: Boolean = false,
    hairline: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = CutCornerShape(
        cut = cut,
        tr = Corner.TR in corners,
        tl = Corner.TL in corners,
        br = Corner.BR in corners,
        bl = Corner.BL in corners,
    )

    val bgModifier = when {
        brush != null -> Modifier.background(brush)
        background == HeColors.Panel -> Modifier.background(HeColors.PanelGradient)
        else -> Modifier.background(background)
    }

    val borderModifier = if (hairline) {
        Modifier.border(1.dp, HeColors.HairlineRim, shape)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(bgModifier)
            .then(borderModifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 保持兼容签名的切角封口装饰（圆滑化为平滑无干涉）。
 */
@Composable
fun YellowCornerSeal(
    size: Dp = 10.dp,
    color: Color = HeColors.Yellow,
    modifier: Modifier = Modifier,
) {
    // 现代圆滑风格下不再在卡片圆角上叠生硬直角三角
}
