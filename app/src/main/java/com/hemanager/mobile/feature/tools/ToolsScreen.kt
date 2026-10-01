package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.op.IconBtn4
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import org.json.JSONObject

@Composable
internal fun ToolsScreen(
    serverUrl: String,
    token: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDetailChanged: (Boolean) -> Unit
) {
    val state = rememberNativeActions(serverUrl, token, onLogout)
    var destination by rememberSaveable(serverUrl, token) { mutableStateOf("") }
    var account by remember { mutableStateOf<JSONObject?>(null) }
    val back = { destination = "" }

    suspend fun loadAccount() {
        account = state.api.get("/auth/me")
    }

    LaunchedEffect(Unit) {
        state.run { loadAccount() }
    }

    LaunchedEffect(destination) {
        onDetailChanged(destination.isNotBlank())
    }

    DisposableEffect(Unit) {
        onDispose { onDetailChanged(false) }
    }

    BackHandler {
        if (destination.isNotBlank()) destination = "" else onBack()
    }

    val admin = account?.optBoolean("is_admin") == true

    when (destination) {
        "stats" -> StatsScreen(serverUrl, token, back, onLogout)
        "tags" -> TagsScreen(serverUrl, token, back, onLogout)
        "media" -> NativeMediaScreen(serverUrl, token, back, onLogout)
        "external" -> NativeExternalScreen(serverUrl, token, back, onLogout)
        "pawchive" -> NativePawchiveScreen(serverUrl, token, back, onLogout)
        "recommend" -> NativeRecommendScreen(serverUrl, token, admin, back, onLogout)
        "bd2" -> NativeBd2Screen(serverUrl, token, admin, back, onLogout)
        "dedup" -> NativeDedupScreen(serverUrl, token, back, onLogout)
        "server" -> NativeServerScreen(serverUrl, token, back, onLogout)
        "users" -> NativeUsersScreen(serverUrl, token, back, onLogout)
        else -> {
            ToolPage(
                title = "发现与工具",
                subtitle = "WORKSTATION · UTILITIES",
                onBack = null,
                actions = {
                    if (state.busy) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                                color = HeColors.Yellow
                            )
                        }
                    } else {
                        IconBtn4(
                            icon = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            onClick = { state.run { loadAccount() } }
                        )
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.busy) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = HeColors.Yellow,
                                trackColor = HeColors.OpSurface
                            )
                        }
                    }

                    if (state.error.isNotBlank()) {
                        item { ToolError(state.error) { state.run { loadAccount() } } }
                    }

                    // Hero Workstation Card
                    item {
                        ToolsHeroBanner(
                            username = account?.text("username") ?: "HE Manager",
                            isAdmin = admin
                        )
                    }

                    // Category 1: 库内整理
                    item {
                        ToolsSectionHeader(
                            title = "库内整理",
                            tag = "01 / LIBRARY MAINTENANCE",
                            accentColor = HeColors.VideoAccent
                        )
                    }
                    item {
                        ToolCard(
                            title = "统计看板",
                            subtitle = "数量、容量、年度活跃、增长与待整理作品",
                            icon = Icons.Default.Insights,
                            accentColor = HeColors.ImageAccent,
                            tag = "// STATS",
                            onClick = { destination = "stats" }
                        )
                    }
                    item {
                        ToolCard(
                            title = "标签管理",
                            subtitle = "搜索、重命名、命名空间、合并与清理",
                            icon = Icons.AutoMirrored.Filled.Label,
                            accentColor = HeColors.AudioAccent,
                            tag = "// TAGS",
                            onClick = { destination = "tags" }
                        )
                    }
                    item {
                        ToolCard(
                            title = "媒体管理",
                            subtitle = "分页、筛选、详情编辑、标签、收藏与评分",
                            icon = Icons.Default.EditNote,
                            accentColor = HeColors.VideoAccent,
                            tag = "// MEDIA",
                            onClick = { destination = "media" }
                        )
                    }

                    // Category 2: 发现
                    item {
                        ToolsSectionHeader(
                            title = "发现",
                            tag = "02 / DISCOVERY & INTERACTION",
                            accentColor = HeColors.MangaAccent
                        )
                    }
                    item {
                        ToolCard(
                            title = "漫画推荐",
                            subtitle = "偏好与标签推荐，AI 配置和分析任务",
                            icon = Icons.Default.AutoAwesome,
                            accentColor = HeColors.MangaAccent,
                            tag = "// AI REC",
                            onClick = { destination = "recommend" }
                        )
                    }
                    item {
                        ToolCard(
                            title = "BD2 动态",
                            subtitle = "本地 Spine 动画、皮肤、缩放和播放控制",
                            icon = Icons.Default.Animation,
                            accentColor = Color(0xFFFF5252),
                            tag = "// SPINE 2D",
                            onClick = { destination = "bd2" }
                        )
                    }

                    // Category 3: 导入与管理
                    if (admin) {
                        item {
                            ToolsSectionHeader(
                                title = "导入与管理",
                                tag = "03 / INGESTION & MANAGEMENT",
                                accentColor = Color(0xFF29B6F6)
                            )
                        }
                        item {
                            ToolCard(
                                title = "外部收藏与下载",
                                subtitle = "WNACG、X、ASMR 同步、归档导入与下载任务",
                                icon = Icons.Default.CloudDownload,
                                accentColor = Color(0xFF29B6F6),
                                tag = "// INGEST",
                                onClick = { destination = "external" }
                            )
                        }
                        item {
                            ToolCard(
                                title = "Pawchive",
                                subtitle = "喜欢的作者、帖子、原生附件浏览与下载",
                                icon = Icons.Default.Explore,
                                accentColor = Color(0xFFAB47BC),
                                tag = "// PAWCHIVE",
                                onClick = { destination = "pawchive" }
                            )
                        }
                        item {
                            ToolCard(
                                title = "重复管理",
                                subtitle = "候选对比、批量处理、重检与文件维护",
                                icon = Icons.Default.ContentCopy,
                                accentColor = Color(0xFFFF7043),
                                tag = "// DEDUP",
                                onClick = { destination = "dedup" }
                            )
                        }
                        item {
                            ToolCard(
                                title = "服务器设置",
                                subtitle = "媒体目录、扫描、外部代理与库维护",
                                icon = Icons.Default.Dns,
                                accentColor = Color(0xFF26A69A),
                                tag = "// SERVER",
                                onClick = { destination = "server" }
                            )
                        }
                        item {
                            ToolCard(
                                title = "用户管理",
                                subtitle = "创建、编辑、权限、停用与删除账号",
                                icon = Icons.Default.ManageAccounts,
                                accentColor = Color(0xFF7986CB),
                                tag = "// USERS",
                                onClick = { destination = "users" }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHeroBanner(username: String, isAdmin: Boolean) {
    val heroShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF232838),
                        HeColors.Panel
                    )
                )
            )
            .border(1.dp, HeColors.HairlineRim, heroShape)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(HeColors.Yellow)
                    )
                    Text(
                        text = "SYSTEM WORKSTATION",
                        fontFamily = Oxanium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.4.sp,
                        color = HeColors.Yellow
                    )
                }

                // Online indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HeColors.Online.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(HeColors.Online)
                    )
                    Text(
                        text = "READY",
                        fontFamily = Oxanium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = HeColors.Online
                    )
                }
            }

            // Big headline & user identity
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "让媒体库更完整",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = NotoSansSC,
                    color = HeColors.OpWhite,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "媒体维护 · 动态发现 · 自动化导入与扩展",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeColors.OpWhiteSoft,
                    lineHeight = 16.sp
                )
            }

            // User & role info pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HeColors.Void.copy(alpha = 0.5f))
                    .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(HeColors.Yellow.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = null,
                            tint = HeColors.Yellow,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = username,
                        color = HeColors.OpWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = if (isAdmin) "管理员" else "个人媒体库",
                    fontFamily = NotoSansSC,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = if (isAdmin) HeColors.Yellow else HeColors.OpWhiteSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAdmin) HeColors.YellowSoft else HeColors.OpSurface)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolsSectionHeader(title: String, tag: String, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accentColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = HeColors.OpWhite,
                fontWeight = FontWeight.Bold,
                fontFamily = NotoSansSC,
                fontSize = 14.5.sp
            )
        }
        Text(
            text = tag,
            fontFamily = Oxanium,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            letterSpacing = 1.2.sp,
            color = accentColor.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 9.dp)
        )
    }
}
