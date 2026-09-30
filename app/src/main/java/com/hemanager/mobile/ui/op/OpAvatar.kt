package com.hemanager.mobile.ui.op

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hemanager.mobile.ui.theme.HeColors

/**
 * 平滑圆角高质感头像。
 *
 * @param ring true 时外圈是黄色高亮（用于自己的 avatar），false 时是 HairlineMid 灰描边。
 */
@Composable
fun OpAvatar(
    modelUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ring: Boolean = false,
    contentDescription: String? = null,
) {
    val cornerRadius = (size.value * 0.28f).dp
    val shape = RoundedCornerShape(cornerRadius)
    val ringColor: Color = if (ring) HeColors.Yellow else HeColors.HairlineMid

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(HeColors.Panel)
            .border(1.5.dp, ringColor, shape),
    ) {
        if (modelUrl != null) {
            AsyncImage(
                model = modelUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
        }
    }
}
