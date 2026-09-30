package com.hemanager.mobile.feature.library

// Library 侧边抽屉相关 composable 集群。
// 包括 AppDrawerV2（顶层抽屉容器）+ 7 个子组件（profile/server/stats/cells/nav rows）。
// 全部 internal — 跨同包文件可见。同包顶层 helper（filterAccent 等）自动可见无需 import。

import com.hemanager.mobile.MediaItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.hemanager.mobile.ui.util.toastComingSoon

@Composable
internal fun AppDrawerV2(
    serverUrl: String,
    items: List<MediaItem>,
    filters: List<FilterOption>,
    selectedValue: String,
    selectedStatusValue: String,
    loading: Boolean,
    onSelected: (String) -> Unit,
    onStatusSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onOpenCreators: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = com.hemanager.mobile.ui.theme.HeColors.Void,
        drawerContentColor = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(com.hemanager.mobile.ui.theme.HeColors.Void)) {
            // 顶部 3dp 黄色 strip（左侧 35% 长度）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f     to com.hemanager.mobile.ui.theme.HeColors.Yellow,
                            0.35f  to com.hemanager.mobile.ui.theme.HeColors.Yellow,
                            0.351f to Color.Transparent,
                            1f     to Color.Transparent,
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 17.dp, top = 24.dp, bottom = 18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Slash 标题
                    com.hemanager.mobile.ui.op.Slash(cn = "操作员面板", en = "Operator")

                    // 2. 操作员档案 — 头像 + ADMIN + LVL + UID
                    DrawerProfileHeaderV2()

                    // 3. 服务连接
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.hemanager.mobile.ui.op.Slash(cn = "服务连接", en = "Server Link", fontSize = 12.5.sp)
                        DrawerServerCardV2(
                            serverUrl = serverUrl,
                            online = true,
                        )
                    }

                    // 4. 导航
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.hemanager.mobile.ui.op.Slash(cn = "导航", en = "Navigate", fontSize = 12.5.sp)
                        Spacer(Modifier.height(6.dp))
                        DNavRow(
                            cn = "媒体库", en = "Library",
                            icon = Icons.Default.Home,
                            active = selectedValue.isBlank() && selectedStatusValue.isBlank(),
                            onClick = {
                                onSelected("")
                                onStatusSelected("")
                            }
                        )
                        DNavRow(
                            cn = "继续观看", en = "Continue",
                            icon = Icons.Default.History,
                            active = selectedStatusValue == "viewing",
                            count = items.count { it.viewStatus == "viewing" },
                            onClick = { onStatusSelected("viewing") }
                        )
                        DNavRow(
                            cn = "收藏夹", en = "Starred",
                            icon = Icons.Default.StarBorder,
                            active = selectedStatusValue == "favorite",
                            count = items.count { it.favorite },
                            onClick = { onStatusSelected("favorite") }
                        )
                        DNavRow(
                            cn = "创作者", en = "Curators",
                            icon = Icons.Default.LocalOffer,
                            active = false,
                            onClick = onOpenCreators
                        )

                        DNavRow(
                            cn = "文件夹", en = "Folders",
                            icon = Icons.Default.Folder,
                            active = false,
                            onClick = { context.toastComingSoon("文件夹视图") }
                        )
                        DNavRow(
                            cn = "设置", en = "Settings",
                            icon = Icons.Default.Settings,
                            active = false,
                            onClick = onSettings
                        )
                    }

                    // 5. 媒体分类
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.hemanager.mobile.ui.op.Slash(cn = "媒体分类", en = "Library", fontSize = 12.5.sp)
                        Spacer(Modifier.height(6.dp))
                        filters.forEach { option ->
                            DNavRow(
                                cn = option.label,
                                en = categoryEnLabel(option.value),
                                icon = categoryIcon(option.value),
                                active = selectedValue == option.value && selectedStatusValue.isBlank(),
                                count = countForFilter(items, option.value),
                                onClick = { onSelected(option.value) }
                            )
                        }
                    }
                }

                // 底部分隔 + 刷新 + DISCONNECT
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(com.hemanager.mobile.ui.theme.HeColors.HairlineMid)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.hemanager.mobile.ui.op.IconBtn4(
                        icon = Icons.Default.Refresh,
                        contentDescription = if (loading) "正在刷新" else "刷新媒体库",
                        onClick = onRefresh,
                        tint = if (loading) com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted
                               else com.hemanager.mobile.ui.theme.HeColors.Yellow,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .border(
                                1.dp,
                                com.hemanager.mobile.ui.theme.HeColors.OpDanger.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(onClick = onLogout)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = com.hemanager.mobile.ui.theme.HeColors.OpDanger,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "DISCONNECT",
                                color = com.hemanager.mobile.ui.theme.HeColors.OpDanger,
                                fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.8.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "退出登录",
                                color = com.hemanager.mobile.ui.theme.HeColors.OpDanger.copy(alpha = 0.85f),
                                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryEnLabel(value: String): String = when (value) {
    "" -> "All"
    "video" -> "Video"
    "manga" -> "Manga"
    "image" -> "Images"
    "audio" -> "Audio"
    else -> ""
}

private fun categoryIcon(value: String): androidx.compose.ui.graphics.vector.ImageVector = when (value) {
    "video" -> Icons.Default.PlayArrow
    "manga" -> Icons.Default.Folder
    "image" -> Icons.Default.GridView
    "audio" -> Icons.Default.History
    else -> Icons.Default.Home
}

@Composable
internal fun DrawerProfileHeaderV2() {
    // HE OP 操作员档案：切角头像（黄环）+ ADMIN/ARCHIVIST + UID
    // 头像 modelUrl=null 时 OpAvatar 显示空 Panel 底色，再叠一个"HE"占位
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            com.hemanager.mobile.ui.op.OpAvatar(
                modelUrl = null,
                size = 56.dp,
                ring = true,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(
                    "HE",
                    color = com.hemanager.mobile.ui.theme.HeColors.Yellow,
                    fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ADMIN",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhite,
                fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "ARCHIVIST · LVL 14",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(4.dp))
            com.hemanager.mobile.ui.op.CodeChip(
                text = "UID:1416-176-661",
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteFaint,
            )
        }
    }
}

@Composable
internal fun DrawerServerCardV2(serverUrl: String, online: Boolean = true) {
    // HE OP 服务连接卡：小切角面板 + 6dp 绿圆点 + ● ONLINE + serverHost CodeChip
    com.hemanager.mobile.ui.op.AngularPanel(
        modifier = Modifier.fillMaxWidth(),
        cut = 12.dp,
        background = com.hemanager.mobile.ui.theme.HeColors.Panel,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (online) com.hemanager.mobile.ui.theme.HeColors.Online
                        else com.hemanager.mobile.ui.theme.HeColors.OpDanger
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (online) "ONLINE" else "OFFLINE",
                color = if (online) com.hemanager.mobile.ui.theme.HeColors.Online
                        else com.hemanager.mobile.ui.theme.HeColors.OpDanger,
                fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.weight(1f))
            com.hemanager.mobile.ui.op.CodeChip(
                text = serverHostV2(serverUrl),
                color = com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
            )
        }
    }
}

/**
 * HE OP DNavO — Drawer 双层中英导航行。
 *
 * Active 时：左侧 3dp 黄竖条 + Surface 背景 + 10dp 圆角。
 * 默认时：透明。Icon (18dp) → 双层 EN/CN → 右侧 GeistMono count。
 */
@Composable
private fun DNavRow(
    cn: String,
    en: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.clip(shape).background(com.hemanager.mobile.ui.theme.HeColors.OpSurface) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧 active 黄竖条
        if (active) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(com.hemanager.mobile.ui.theme.HeColors.Yellow)
            )
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) com.hemanager.mobile.ui.theme.HeColors.Yellow
                   else com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (en.isNotEmpty()) {
                Text(
                    en,
                    color = if (active) com.hemanager.mobile.ui.theme.HeColors.Yellow
                            else com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                    fontFamily = com.hemanager.mobile.ui.theme.Oxanium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(1.dp))
            }
            Text(
                cn,
                color = if (active) com.hemanager.mobile.ui.theme.HeColors.OpWhite
                        else com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft,
                fontFamily = com.hemanager.mobile.ui.theme.NotoSansSC,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
            )
        }
        if (count != null) {
            Text(
                count.toString().padStart(2, '0'),
                color = if (active) com.hemanager.mobile.ui.theme.HeColors.Yellow
                        else com.hemanager.mobile.ui.theme.HeColors.OpWhiteMuted,
                fontFamily = com.hemanager.mobile.ui.theme.GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

