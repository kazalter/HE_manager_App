package com.hemanager.mobile.ui.op

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.Oxanium

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush

/**
 * 现代平滑光感进度条。圆角端点 + 渐变填充。
 *
 * @param value 0..1 进度；自动 coerce 到 [0.02, 1] 保证最少有一截可见。
 * @param height 卡片上用 2.5dp，hero 上用 3.5dp。
 */
@Composable
fun ProgressO(
    value: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    color: Color = HeColors.Yellow,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(HeColors.HairlineMid),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0.02f, 1f))
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            color.copy(alpha = 0.82f),
                            color,
                        )
                    )
                ),
        )
    }
}

/**
 * 双行数字 + ALL-CAPS 标签：用于 hero / creator detail 的 KPI 块。
 *
 * 显示形如：`12 / 47` 上 + `WORKS` 下。total 可省略只显示当前值。
 */
@Composable
fun StatNumber(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    total: Int? = null,
    accent: Color = HeColors.OpWhite,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$value",
                color = accent,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 0.sp,
            )
            if (total != null) {
                Text(
                    text = "/",
                    color = HeColors.OpWhiteMuted,
                    fontFamily = GeistMono,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 3.dp, end = 3.dp),
                )
                Text(
                    text = "$total",
                    color = HeColors.OpWhiteMuted,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = HeColors.OpWhiteMuted,
            fontFamily = Oxanium,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.6.sp,
        )
    }
}
