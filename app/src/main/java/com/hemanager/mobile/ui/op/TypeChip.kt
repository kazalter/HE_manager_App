package com.hemanager.mobile.ui.op

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors

/**
 * 媒体类型小药丸：根据媒体类型独立赋予特征色（Amber/Emerald/Cyan/Purple），
 * 搭配微透发光玻璃底色与柔和边框，解决纯黄单色造成的平淡素气。
 *
 * 映射：video → VID / manga → MNG / image → IMG / audio → AUD / 其他 → MED。
 */
@Composable
fun TypeChip(
    mediaType: String,
    modifier: Modifier = Modifier,
    onYellow: Boolean = false,
) {
    val code = when (mediaType.lowercase()) {
        "video" -> "VID"
        "manga" -> "MNG"
        "image" -> "IMG"
        "audio" -> "AUD"
        else -> "MED"
    }
    val accent = HeColors.mediaAccent(mediaType)
    val bg = if (onYellow) HeColors.Yellow else HeColors.mediaChipBg(mediaType)
    val fg = if (onYellow) HeColors.OnYellow else accent
    val border = if (onYellow) Color.Transparent else HeColors.mediaChipBorder(mediaType)
    val shape = RoundedCornerShape(7.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(horizontal = 8.5.dp, vertical = 3.5.dp),
    ) {
        Text(
            text = code,
            color = fg,
            fontFamily = GeistMono,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
