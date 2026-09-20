package com.hemanager.mobile.feature.library

// Library 顶部 header + 搜索过滤面板 + 底部 sheet + 视图模式切换。
// 包括 LibraryHeaderV2, SearchAndFilterPanelV2, FilterBottomSheetV2,
// LibraryViewModeSwitcherV2 以及它们的子组件（QuickFilterRowV2/ChipV2/PillTabV2 等）。

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History

@Composable
internal fun LibraryHeaderV2(
    loading: Boolean,
    viewMode: LibraryViewMode,
    galleryMode: Boolean = false,
    onViewModeSelected: (LibraryViewMode) -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit
) {
    // HE OP TopHud：菜单 + Slash 标识 + viewMode + refresh
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.hemanager.mobile.ui.op.IconBtn4(
                icon = Icons.Default.Menu,
                contentDescription = "打开侧边栏",
                onClick = onMenu,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                com.hemanager.mobile.ui.op.Slash(cn = "媒体库", en = "Library")
                Spacer(Modifier.height(4.dp))
                com.hemanager.mobile.ui.op.OpTitle(
                    cn = "媒体库",
                    en = "LIBRARY · OPERATOR ARCHIVE",
                    sizeSp = 22.sp,
                )
            }
            if (!galleryMode) {
                ViewModeIconButtonV2(viewMode = viewMode, onSelect = onViewModeSelected)
                Spacer(Modifier.width(8.dp))
            }
            if (loading) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                    )
                }
            } else {
                com.hemanager.mobile.ui.op.IconBtn4(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
internal fun ViewModeIconButtonV2(
    viewMode: LibraryViewMode,
    onSelect: (LibraryViewMode) -> Unit
) {
    val ordered = listOf(LibraryViewMode.Large, LibraryViewMode.Grid, LibraryViewMode.Detail)
    val currentIndex = ordered.indexOf(viewMode).coerceAtLeast(0)
    var menuOpen by remember { mutableStateOf(false) }
    // HE OP IconBtn4 — 黑底 hairline 圆形 + 长按弹切换菜单
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(com.hemanager.mobile.ui.theme.HeColors.Ink)
                .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineMid, CircleShape)
                .pointerInput(viewMode) {
                    detectTapGestures(
                        onTap = { onSelect(ordered[(currentIndex + 1) % ordered.size]) },
                        onLongPress = { menuOpen = true }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    (slideInHorizontally(tween(160)) { it / 2 } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(160)) { -it / 2 } + fadeOut(tween(160))) using
                        SizeTransform(clip = false)
                },
                label = "viewModeIcon"
            ) { mode ->
                Icon(
                    viewModeIconV2(mode),
                    contentDescription = "切换视图：${viewModeLabelV2(mode)}",
                    tint = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            ordered.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            viewModeLabelV2(mode),
                            fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                            fontWeight = if (mode == viewMode) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            viewModeIconV2(mode),
                            contentDescription = null,
                            tint = if (mode == viewMode) com.hemanager.mobile.ui.theme.HeColors.Yellow
                                   else com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(mode)
                    }
                )
            }
        }
    }
}

internal fun viewModeIconV2(mode: LibraryViewMode) = when (mode) {
    LibraryViewMode.Large -> Icons.Default.ViewModule
    LibraryViewMode.Grid -> Icons.Default.GridView
    LibraryViewMode.Detail -> Icons.AutoMirrored.Filled.ViewList
}

internal fun viewModeLabelV2(mode: LibraryViewMode) = when (mode) {
    LibraryViewMode.Large -> "大图"
    LibraryViewMode.Grid -> "三列"
    LibraryViewMode.Detail -> "详细"
}

@Composable
internal fun SearchAndFilterPanelV2(
    search: String,
    onSearchChange: (String) -> Unit,
    filters: List<FilterOption>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    statusFilters: List<FilterOption>,
    selectedStatusValue: String,
    onStatusSelected: (String) -> Unit,
    onOpenFilters: () -> Unit
) {
    val selectedMediaLabel = filters.firstOrNull { it.value == selectedValue }?.label ?: "全部"
    val selectedStatusLabel = statusFilters.firstOrNull { it.value == selectedStatusValue }?.label ?: "全部"
    val activeCount = listOf(selectedValue, selectedStatusValue).count { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // HE OP 搜索框：切角 + hairline border + GeistMono 输入 + 黄色光标
        val searchShape = com.hemanager.mobile.ui.theme.CutCornerShape(8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(searchShape)
                .background(com.hemanager.mobile.ui.theme.HeColors.Panel)
                .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineMid, searchShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (search.isBlank()) {
                    Text(
                        "搜索标题、作者、文件夹",
                        color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                        fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                        fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(com.hemanager.mobile.ui.theme.HeColors.Yellow),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = search.isNotBlank()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清除搜索",
                    tint = com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onSearchChange("") },
                )
            }
        }

        QuickFilterRowV2(
            mediaValue = selectedValue,
            statusValue = selectedStatusValue,
            onMediaSelected = onSelected,
            onStatusSelected = onStatusSelected
        )

        // 筛选展开行：切角 + hairline + Slash 前缀
        val expandShape = com.hemanager.mobile.ui.theme.CutCornerShape(8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(expandShape)
                .background(com.hemanager.mobile.ui.theme.HeColors.Panel)
                .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineMid, expandShape)
                .clickable { onOpenFilters() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "//",
                color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "FILTER",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "$selectedMediaLabel · $selectedStatusLabel",
                modifier = Modifier.weight(1f),
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(com.hemanager.mobile.ui.theme.CutCornerShape(4.dp))
                        .background(com.hemanager.mobile.ui.theme.HeColors.Yellow)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        activeCount.toString(),
                        color = com.hemanager.mobile.ui.theme.HeColors.OnYellow,
                        fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "→",
                color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun QuickFilterRowV2(
    mediaValue: String,
    statusValue: String,
    onMediaSelected: (String) -> Unit,
    onStatusSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickFilterChipV2(
            label = "全部",
            selected = mediaValue.isBlank() && statusValue.isBlank(),
            accent = MaterialTheme.colorScheme.primary,
            onClick = {
                onMediaSelected("")
                onStatusSelected("")
            }
        )
        listOf("video" to "视频", "manga" to "漫画", "image" to "图片", "audio" to "音频").forEach { (value, label) ->
            QuickFilterChipV2(
                label = label,
                selected = mediaValue == value,
                accent = filterAccent(value),
                onClick = { onMediaSelected(if (mediaValue == value) "" else value) }
            )
        }
        listOf("viewing" to "继续看", "favorite" to "收藏").forEach { (value, label) ->
            QuickFilterChipV2(
                label = label,
                selected = statusValue == value,
                accent = statusAccentV2(value),
                onClick = { onStatusSelected(if (statusValue == value) "" else value) }
            )
        }
    }
}

@Composable
internal fun QuickFilterChipV2(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    // HE OP — accent 不再决定选中色（统一黄），保留参数兼容签名。
    com.hemanager.mobile.ui.op.FilterTab(
        label = label,
        en = null,
        active = selected,
        onClick = onClick,
    )
}

// HE OP 筛选 sheet 选项常量（中文 only，按 spec 4 分区）
private data class FilterChipOpt(
    val value: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

private val FILTER_MEDIA_OPTS = listOf(
    FilterChipOpt("", "全部"),
    FilterChipOpt("video", "视频"),
    FilterChipOpt("manga", "漫画"),
    FilterChipOpt("image", "图片"),
    FilterChipOpt("audio", "音频"),
)
private val FILTER_STATUS_OPTS = listOf(
    FilterChipOpt("", "全部"),
    FilterChipOpt("viewing", "继续看"),
    FilterChipOpt("favorite", "收藏"),
    FilterChipOpt("viewed", "已看完"),
)
private val FILTER_SOURCE_OPTS = listOf(
    FilterChipOpt("all", "全部来源"),
    FilterChipOpt("local", "本地"),
    FilterChipOpt("x", "X"),
    FilterChipOpt("wnacg", "wnacg"),
    FilterChipOpt("asmr", "ASMR"),
    FilterChipOpt("bd2", "BD2"),
)
private val FILTER_SORT_OPTS = listOf(
    FilterChipOpt("added", "最近添加", Icons.Default.Add),
    FilterChipOpt("opened", "最近打开", Icons.Default.History),
    FilterChipOpt("rating", "评分", Icons.Default.Star),
    FilterChipOpt("name", "名称", Icons.AutoMirrored.Filled.List),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheetV2(
    mediaFilter: String,
    onMediaSelected: (String) -> Unit,
    statusFilter: String,
    onStatusSelected: (String) -> Unit,
    sourceFilter: String,
    onSourceSelected: (String) -> Unit,
    sortFilter: String,
    onSortSelected: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.hemanager.mobile.ui.theme.HeColors.Ink,
        shape = com.hemanager.mobile.ui.theme.CutCornerShape(
            cut = 18.dp,
            tl = true,
            tr = true,
            bl = false,
            br = false,
        ),
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = null,  // 自绘 handle
        tonalElevation = 0.dp,
    ) {
        Box {
            // 顶部 3dp 黄色装饰条 (25%~70% 横向)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            0f    to Color.Transparent,
                            0.25f to Color.Transparent,
                            0.25f to com.hemanager.mobile.ui.theme.HeColors.Yellow,
                            0.70f to com.hemanager.mobile.ui.theme.HeColors.Yellow,
                            0.70f to Color.Transparent,
                            1f    to Color.Transparent,
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 28.dp),
            ) {
                // 居中 handle
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 18.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 3.dp)
                        .background(com.hemanager.mobile.ui.theme.HeColors.HairlineHi)
                )

                // Header: // 筛选 + 重置
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.hemanager.mobile.ui.op.Slash(cn = "筛选", en = null)
                    Text(
                        "重置",
                        color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                        fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clickable(onClick = onReset)
                            .padding(8.dp),
                    )
                }

                // 1. 媒体类型
                FilterSection("媒体类型") {
                    FILTER_MEDIA_OPTS.forEach { o ->
                        com.hemanager.mobile.ui.op.FilterTab(
                            label = o.label,
                            en = null,
                            active = mediaFilter == o.value,
                            onClick = { onMediaSelected(o.value) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                // 2. 观看状态
                FilterSection("观看状态") {
                    FILTER_STATUS_OPTS.forEach { o ->
                        com.hemanager.mobile.ui.op.FilterTab(
                            label = o.label,
                            en = null,
                            active = statusFilter == o.value,
                            onClick = { onStatusSelected(o.value) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                // 3. 来源
                FilterSection("来源") {
                    FILTER_SOURCE_OPTS.forEach { o ->
                        com.hemanager.mobile.ui.op.FilterTab(
                            label = o.label,
                            en = null,
                            active = sourceFilter == o.value,
                            onClick = { onSourceSelected(o.value) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                // 4. 排序（带 icon）
                FilterSection("排序") {
                    FILTER_SORT_OPTS.forEach { o ->
                        SortChipV2(
                            label = o.label,
                            icon = o.icon!!,
                            active = sortFilter == o.value,
                            onClick = { onSortSelected(o.value) },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 应用 CTA
                com.hemanager.mobile.ui.op.YellowCTA(
                    text = "应用 · APPLY",
                    onClick = onDismiss,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    size = com.hemanager.mobile.ui.op.CtaSize.Large,
                    fullWidth = true,
                )
            }
        }
    }
}

/** 分区容器 — // 小标题 + 横滑 chip Row */
@Composable
private fun FilterSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column {
        com.hemanager.mobile.ui.op.Slash(cn = title, en = null, fontSize = 9.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** SortChip — FilterTab 的带 icon 变体，仅用于排序分区 */
@Composable
private fun SortChipV2(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = com.hemanager.mobile.ui.theme.CutCornerShape(7.dp)
    val tint = if (active) com.hemanager.mobile.ui.theme.HeColors.OnYellow
               else com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (active) Modifier.background(com.hemanager.mobile.ui.theme.HeColors.Yellow)
                else Modifier
                    .background(Color.Transparent)
                    .border(1.dp, com.hemanager.mobile.ui.theme.HeColors.HairlineMid, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = if (active) com.hemanager.mobile.ui.theme.HeColors.OnYellow
                    else com.hemanager.mobile.ui.theme.HeColors.OpWhite,
            fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

