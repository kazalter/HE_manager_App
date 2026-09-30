package com.hemanager.mobile.ui.op

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.theme.CutCornerShape
import com.hemanager.mobile.ui.theme.Geist
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.Oxanium

enum class CtaSize { Small, Medium, Large }

private data class CtaMetrics(
    val padV: Dp, val padH: Dp, val fontSize: TextUnit, val cut: Dp, val iconSize: Dp,
)

private fun metricsFor(size: CtaSize): CtaMetrics = when (size) {
    CtaSize.Small  -> CtaMetrics(9.dp,  18.dp, 13.5.sp, 12.dp, 15.dp)
    CtaSize.Medium -> CtaMetrics(12.dp, 22.dp, 15.sp,   14.dp, 17.dp)
    CtaSize.Large  -> CtaMetrics(15.dp, 28.dp, 16.5.sp, 16.dp, 19.dp)
}

/**
 * 主 CTA：黄底圆角，OnYellow 文字，高对比度清晰排版。
 */
@Composable
fun YellowCTA(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: CtaSize = CtaSize.Medium,
    fullWidth: Boolean = false,
) {
    val m = metricsFor(size)
    val shape = CutCornerShape(m.cut)
    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(HeColors.Yellow)
            .clickable { onClick() }
            .padding(horizontal = m.padH, vertical = m.padV),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = HeColors.OnYellow,
                    modifier = Modifier.size(m.iconSize),
                )
            }
            Text(
                text = text,
                color = HeColors.OnYellow,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Bold,
                fontSize = m.fontSize,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}

/**
 * 次级按钮：深色背景 + 1dp HairlineMid 描边 + 白字 + 平滑圆角。
 */
@Composable
fun GhostCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: CtaSize = CtaSize.Medium,
    fullWidth: Boolean = false,
) {
    val m = metricsFor(size)
    val shape = CutCornerShape(m.cut)
    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(HeColors.Ink)
            .border(1.dp, HeColors.HairlineMid, shape)
            .clickable { onClick() }
            .padding(horizontal = m.padH, vertical = m.padV),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = HeColors.OpWhite,
                    modifier = Modifier.size(m.iconSize),
                )
            }
            Text(
                text = text,
                color = HeColors.OpWhite,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Bold,
                fontSize = m.fontSize,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}

/**
 * 筛选条上的平滑圆角小药丸：active 时黄底，否则半透明 + hairline 描边。
 */
@Composable
fun FilterTab(
    label: String,
    en: String? = null,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CutCornerShape(10.dp)
    val bgMod = if (active)
        Modifier.background(HeColors.Yellow)
    else
        Modifier.background(HeColors.Panel.copy(alpha = 0.65f)).border(1.dp, HeColors.HairlineMid, shape)
    val labelColor = if (active) HeColors.OnYellow else HeColors.OpWhiteSoft

    Box(
        modifier = modifier
            .clip(shape)
            .then(bgMod)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (active) Diamond(6.dp, color = HeColors.OnYellow)
            Text(
                text = label,
                color = labelColor,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
            if (en != null) {
                Text(
                    text = en,
                    color = labelColor,
                    fontFamily = Oxanium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.alpha(0.7f),
                )
            }
        }
    }
}

/**
 * 圆形描边 icon 按钮（顶栏菜单 / 搜索 / 返回）。
 *
 * 36dp 圆，Ink 背景 + 1dp HairlineMid 描边，居中 icon。
 */
@Composable
fun IconBtn4(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    iconSize: Dp = 17.dp,
    tint: Color = HeColors.OpWhite,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(HeColors.Ink)
            .border(1.dp, HeColors.HairlineMid, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
