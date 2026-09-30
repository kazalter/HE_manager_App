package com.hemanager.mobile.feature.library

// Library 模块复用的小型 composable 集合：badge / pill / chip / placeholder / loading skeleton / 玻璃面板等。
// 命名以 V2 结尾的是 V2 重写版本，对应已删除的 V1。
// 同时包含 BackToTopButtonV2, MediaDetailRowV2 等中等大小的复用组件。

import com.hemanager.mobile.MediaItem
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.data.image.coverDecodeBucketPx

@Composable
internal fun BackToTopButtonV2(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "backToTopScale"
    )

    Box(
        modifier = modifier
            .size(46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(com.hemanager.mobile.ui.theme.HeColors.Ink)
            .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineHi, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "Back to top",
            tint = com.hemanager.mobile.ui.theme.HeColors.Yellow,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun MediaDetailRowV2(
    serverUrl: String,
    token: String,
    item: MediaItem,
    controller: SwipeRevealController,
    onOpen: (Boolean) -> Unit,
    onQuickAction: (String) -> Unit
) {
    val progress = progressFraction(item)
    val accent = typeAccent(item.mediaType)
    SwipeRevealCard(
        itemKey = "detail-${item.id}",
        controller = controller,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        actionsWidth = 156.dp,
        actions = { progress ->
            QuickActionsPaneV2(
                item = item,
                onAction = { action -> onQuickAction(action) },
                progress = progress
            )
        }
    ) {
        com.hemanager.mobile.ui.op.AngularPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = {
                    if (controller.openKey != null) controller.close() else onOpen(false)
                }),
            cut = 14.dp,
            background = com.hemanager.mobile.ui.theme.HeColors.Panel,
            contentPadding = PaddingValues(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .width(84.dp)
                    .height(116.dp)
                ) {
                    RemoteCoverV2(
                        url = coverUrl(serverUrl, token, item),
                        label = mediaTypeLabelV2(item.mediaType),
                        accent = accent,
                        decodeWidthPx = 220,
                        decodeHeightPx = 308,
                        cutDp = 10.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.hemanager.mobile.ui.op.CodeChip(
                            text = fakeCode(item),
                            color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                        )
                        if (item.favorite) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "收藏",
                                tint = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        if (item.missing) {
                            TinyBadgeV2("MISSING", com.hemanager.mobile.ui.theme.HeColors.OpDanger)
                        }
                    }
                    Text(
                        item.title,
                        color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                        fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (progress != null) {
                        com.hemanager.mobile.ui.op.ProgressO(
                            value = progress,
                            height = 2.dp,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            progressTextV2(item),
                            modifier = Modifier.weight(1f),
                            color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                            fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.mediaType == "manga" && (item.viewStatus == "viewing" || item.viewStatus == "viewed")) {
                            ActionPillV2("RESTART", com.hemanager.mobile.ui.theme.HeColors.OpWhite) {
                                if (controller.openKey != null) controller.close() else onOpen(true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TinyBadgeV2(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
    ) {
        Text(
            label,
            color = color,
            fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RemoteCoverV2(
    url: String?,
    label: String,
    accent: Color,
    decodeWidthPx: Int = 240,
    decodeHeightPx: Int = 340,
    modifier: Modifier = Modifier,
    cutDp: Dp = 12.dp,
) {
    val decodeWidth = remember(decodeWidthPx) { coverDecodeBucketPx(decodeWidthPx) }
    val decodeHeight = remember(decodeHeightPx) { coverDecodeBucketPx(decodeHeightPx) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cutDp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(com.hemanager.mobile.ui.theme.HeColors.Panel),
        contentAlignment = Alignment.Center
    ) {
        // 占位文字（图未加载时显示）
        Text(
            label,
            color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteFaint,
            fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )
        CoilCoverImage(
            url = url,
            label = label,
            decodeWidthPx = decodeWidth,
            decodeHeightPx = decodeHeight,
            modifier = Modifier.fillMaxSize()
        )
        // 底部 ink 渐变，给文字制造对比；上半部分基本透明
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f   to com.hemanager.mobile.ui.theme.HeColors.Ink.copy(alpha = 0.78f),
                    )
                )
        )
    }
}

@Composable
internal fun FloatingPlayButtonV2(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(com.hemanager.mobile.ui.theme.HeColors.Yellow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = com.hemanager.mobile.ui.theme.HeColors.OnYellow,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = com.hemanager.mobile.ui.theme.HeColors.OnYellow,
            fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
internal fun EmptyStateV2() {
    com.hemanager.mobile.ui.op.AngularPanel(
        modifier = Modifier.fillMaxWidth(),
        cut = 14.dp,
        background = com.hemanager.mobile.ui.theme.HeColors.Panel,
        contentPadding = PaddingValues(vertical = 36.dp, horizontal = 18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.hemanager.mobile.ui.op.Slash(cn = "无匹配", en = "EMPTY")
            Spacer(Modifier.height(14.dp))
            Text(
                "没有匹配的媒体",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "换个关键词、类型或状态再探索一下",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun ErrorPanelV2(error: String, loading: Boolean, onRetry: () -> Unit) {
    com.hemanager.mobile.ui.op.AngularPanel(
        modifier = Modifier.fillMaxWidth(),
        cut = 10.dp,
        background = com.hemanager.mobile.ui.theme.HeColors.Panel,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "// ERR",
                    color = com.hemanager.mobile.ui.theme.HeColors.OpDanger,
                    fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    error,
                    color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                    fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            com.hemanager.mobile.ui.op.GhostCta(
                text = "RETRY",
                onClick = onRetry,
                size = com.hemanager.mobile.ui.op.CtaSize.Small,
            )
        }
    }
}

@Composable
internal fun LoadingLineV2() {
    com.hemanager.mobile.ui.op.AngularPanel(
        modifier = Modifier.fillMaxWidth(),
        cut = 8.dp,
        background = com.hemanager.mobile.ui.theme.HeColors.Panel,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "// STREAM · 同步媒体库",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
internal fun LoadingCardSkeletonV2(index: Int) {
    val transition = rememberInfiniteTransition(label = "skeletonV2$index")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(860, delayMillis = index * 90, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonV2Alpha$index"
    )
    com.hemanager.mobile.ui.op.AngularPanel(
        modifier = Modifier.fillMaxWidth(),
        cut = 14.dp,
        background = com.hemanager.mobile.ui.theme.HeColors.Panel,
        contentPadding = PaddingValues(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlockV2(
                Modifier.fillMaxWidth().aspectRatio(1.55f),
                alpha
            )
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBlockV2(Modifier.fillMaxWidth(0.62f).height(18.dp), alpha)
                SkeletonBlockV2(Modifier.fillMaxWidth().height(3.dp), alpha * 0.6f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlockV2(Modifier.width(54.dp).height(20.dp), alpha * 0.7f)
                    SkeletonBlockV2(Modifier.width(74.dp).height(20.dp), alpha * 0.55f)
                }
            }
        }
    }
}

@Composable
internal fun SkeletonBlockV2(modifier: Modifier, alpha: Float) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(com.hemanager.mobile.ui.theme.HeColors.OpWhite.copy(alpha = alpha))
    )
}

@Composable
internal fun StatusPillV2(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = color,
            fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ActionPillV2(label: String, color: Color, onClick: () -> Unit) {
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(com.hemanager.mobile.ui.theme.HeColors.Panel.copy(alpha = 0.75f))
            .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineMid, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
            fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AppBackgroundBrushV2(): Brush {
    // HE OP — 顶部极淡 amber bloom 渐变到 Ink，模拟工业终端的环境光感
    return Brush.radialGradient(
        colorStops = arrayOf(
            0f   to com.hemanager.mobile.ui.theme.HeColors.Yellow.copy(alpha = 0.024f),
            0.4f to com.hemanager.mobile.ui.theme.HeColors.Ink,
            1f   to com.hemanager.mobile.ui.theme.HeColors.Void
        ),
        radius = 1400f
    )
}
