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

/**
 * HE OP 主面板容器：切角 + hairline 描边 + 可选黄角封口。
 *
 * **实装注意**：hairline 描边走 `Modifier.border(1.dp, color, shape)`——直接画在 shape 上，
 * 与切角天然贴合，不会有 sub-pixel 抖动（这是文档里反复强调的坑）。
 *
 * @param corners 哪些角切角，默认 TR + BL
 * @param yellowCorner true 时在 TR 角叠一个黄色三角"封口"装饰
 * @param hairline true 时画 1dp `HairlineMid` 描边
 */
@Composable
fun AngularPanel(
    modifier: Modifier = Modifier,
    cut: Dp = 18.dp,
    corners: Set<Corner> = setOf(Corner.TL, Corner.TR, Corner.BR, Corner.BL),
    background: Color = HeColors.Panel,
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
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (hairline) Modifier.border(1.dp, HeColors.HairlineMid, shape) else Modifier)
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
